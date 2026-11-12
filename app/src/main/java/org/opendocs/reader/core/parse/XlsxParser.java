/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.parse;

import org.opendocs.reader.core.model.Cell;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Sheet;
import org.opendocs.reader.core.util.ExcelNumberFormat;
import org.opendocs.reader.core.util.OoxmlColor;
import org.opendocs.reader.core.xml.XmlNode;
import org.opendocs.reader.core.xml.XmlTree;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parseur XLSX (SpreadsheetML) ecrit a la main, en streaming.
 *
 * <p>Couvert : liste et ordre des feuilles, chaines partagees (sharedStrings),
 * styles (polices grasses/italiques, couleurs, trames, alignements), formats
 * numeriques personnalises et integres, dates (systemes 1900 et 1904),
 * formules (affichees dans le detail de cellule), valeurs en ligne, booleens,
 * erreurs, cellules fusionnees, largeurs de colonnes, lignes figees.</p>
 *
 * <p>Les feuilles sont lues element par element : un classeur de 100 000 lignes
 * ne charge jamais tout le XML en memoire. Au-dela des garde-fous, la feuille
 * est marquee "tronquee" plutot que de faire planter l'application.</p>
 */
public final class XlsxParser {

    public static final int MAX_ROWS = 100_000;
    public static final int MAX_COLS = 1024;
    private static final int MAX_SHARED_STRINGS = 500_000;

    private final OoxmlPackage pkg;
    private final ParsedDocument doc;
    private final List<String> sharedStrings = new ArrayList<>();
    private final Map<Integer, String> numberFormats = new HashMap<>();
    private final List<Font> fonts = new ArrayList<>();
    private final List<Integer> fills = new ArrayList<>();
    private final List<Xf> cellFormats = new ArrayList<>();
    private boolean date1904;

    private XlsxParser(OoxmlPackage pkg, ParsedDocument doc) {
        this.pkg = pkg;
        this.doc = doc;
    }

    public static void parse(OoxmlPackage pkg, ParsedDocument doc) throws IOException {
        new XlsxParser(pkg, doc).run();
    }

    private void run() throws IOException {
        DocProps.read(pkg, doc.metadata);
        XmlNode workbook = pkg.xml("xl/workbook.xml");
        if (workbook == null) throw new IOException("xl/workbook.xml absent : ce n'est pas un XLSX valide");
        XmlNode workbookPr = workbook.first("workbookPr");
        date1904 = workbookPr != null && workbookPr.boolAttr("date1904", false);

        readSharedStrings();
        readStyles();

        Relationships rels = Relationships.forPart(pkg, "xl/workbook.xml");
        XmlNode sheets = workbook.first("sheets");
        if (sheets == null) {
            doc.warn("Aucune feuille declaree");
            return;
        }
        int index = 1;
        for (XmlNode sheetNode : sheets.children("sheet")) {
            String name = sheetNode.attr("name", "Feuille" + index);
            String relId = sheetNode.attr("id");
            String path = rels.resolve("xl/workbook.xml", relId);
            if (path == null) path = "xl/worksheets/sheet" + index + ".xml";
            if ("hidden".equals(sheetNode.attr("state")) || "veryHidden".equals(sheetNode.attr("state"))) {
                doc.warn("La feuille \"" + name + "\" est masquee dans le classeur");
            }
            Sheet sheet = readSheet(path, name);
            doc.sheets.add(sheet);
            index++;
        }
        if (doc.sheets.isEmpty()) doc.warn("Classeur vide");
    }

    /* ------------------------------------------------------------------ */
    /* Chaines partagees                                                   */
    /* ------------------------------------------------------------------ */

    private void readSharedStrings() throws IOException {
        InputStream in = pkg.open("xl/sharedStrings.xml");
        if (in == null) return;
        try {
            XmlTree.stream(in, "si", node -> {
                if (sharedStrings.size() >= MAX_SHARED_STRINGS) return;
                StringBuilder sb = new StringBuilder();
                for (XmlNode child : node.children()) {
                    if ("t".equals(child.name())) {
                        sb.append(child.text());
                    } else if ("r".equals(child.name())) {
                        XmlNode t = child.first("t");
                        if (t != null) sb.append(t.text());
                    }
                }
                sharedStrings.add(sb.toString());
            });
        } finally {
            OoxmlPackage.closeQuietly(in);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Styles                                                              */
    /* ------------------------------------------------------------------ */

    private static final class Font {
        boolean bold;
        boolean italic;
        int color;
    }

    private static final class Xf {
        int numFmtId;
        int fontId;
        int fillId;
        int align = Cell.ALIGN_GENERAL;
    }

    private void readStyles() throws IOException {
        XmlNode styles = pkg.xml("xl/styles.xml");
        if (styles == null) return;
        XmlNode numFmts = styles.first("numFmts");
        if (numFmts != null) {
            for (XmlNode fmt : numFmts.children("numFmt")) {
                numberFormats.put(fmt.intAttr("numFmtId", 0), fmt.attr("formatCode", "General"));
            }
        }
        XmlNode fontsNode = styles.first("fonts");
        if (fontsNode != null) {
            for (XmlNode fontNode : fontsNode.children("font")) {
                Font font = new Font();
                XmlNode b = fontNode.first("b");
                font.bold = b != null && b.boolAttr("val", true);
                XmlNode i = fontNode.first("i");
                font.italic = i != null && i.boolAttr("val", true);
                XmlNode color = fontNode.first("color");
                if (color != null) font.color = OoxmlColor.parseHex(color.attr("rgb"));
                fonts.add(font);
            }
        }
        XmlNode fillsNode = styles.first("fills");
        if (fillsNode != null) {
            for (XmlNode fillNode : fillsNode.children("fill")) {
                XmlNode pattern = fillNode.first("patternFill");
                int color = OoxmlColor.NONE;
                if (pattern != null && "solid".equals(pattern.attr("patternType", "solid"))) {
                    XmlNode fg = pattern.first("fgColor");
                    if (fg != null) color = OoxmlColor.parseHex(fg.attr("rgb"));
                }
                fills.add(color);
            }
        }
        XmlNode xfs = styles.first("cellXfs");
        if (xfs != null) {
            for (XmlNode xfNode : xfs.children("xf")) {
                Xf xf = new Xf();
                xf.numFmtId = xfNode.intAttr("numFmtId", 0);
                xf.fontId = xfNode.intAttr("fontId", 0);
                xf.fillId = xfNode.intAttr("fillId", 0);
                XmlNode alignment = xfNode.first("alignment");
                if (alignment != null) {
                    String horizontal = alignment.attr("horizontal", "general");
                    switch (horizontal) {
                        case "left":
                            xf.align = Cell.ALIGN_LEFT;
                            break;
                        case "center":
                        case "centerContinuous":
                            xf.align = Cell.ALIGN_CENTER;
                            break;
                        case "right":
                            xf.align = Cell.ALIGN_RIGHT;
                            break;
                        default:
                            xf.align = Cell.ALIGN_GENERAL;
                    }
                }
                cellFormats.add(xf);
            }
        }
    }

    /** Formats numeriques integres a Excel (identifiants 0 a 49). */
    private static String builtinFormat(int id) {
        switch (id) {
            case 0: return "General";
            case 1: return "0";
            case 2: return "0.00";
            case 3: return "#,##0";
            case 4: return "#,##0.00";
            case 5: return "#,##0_);(#,##0)";
            case 6: return "#,##0_);[Red](#,##0)";
            case 7: return "#,##0.00_);(#,##0.00)";
            case 8: return "#,##0.00_);[Red](#,##0.00)";
            case 9: return "0%";
            case 10: return "0.00%";
            case 11: return "0.00E+00";
            case 12: return "# ?/?";
            case 13: return "# ??/??";
            case 14: return "dd/mm/yyyy";
            case 15: return "d-mmm-yy";
            case 16: return "d-mmm";
            case 17: return "mmm-yy";
            case 18: return "h:mm AM/PM";
            case 19: return "h:mm:ss AM/PM";
            case 20: return "h:mm";
            case 21: return "h:mm:ss";
            case 22: return "dd/mm/yyyy h:mm";
            case 37: return "#,##0_);(#,##0)";
            case 38: return "#,##0_);[Red](#,##0)";
            case 39: return "#,##0.00_);(#,##0.00)";
            case 40: return "#,##0.00_);[Red](#,##0.00)";
            case 45: return "mm:ss";
            case 46: return "[h]:mm:ss";
            case 47: return "mmss.0";
            case 48: return "##0.0E+0";
            case 49: return "@";
            default: return "General";
        }
    }

    private String formatCode(int numFmtId) {
        String custom = numberFormats.get(numFmtId);
        return custom != null ? custom : builtinFormat(numFmtId);
    }

    /* ------------------------------------------------------------------ */
    /* Feuille                                                             */
    /* ------------------------------------------------------------------ */

    private Sheet readSheet(String path, String name) throws IOException {
        Sheet sheet = new Sheet(name);
        InputStream in = pkg.open(path);
        if (in == null) {
            doc.warn("Feuille introuvable : " + path);
            return sheet;
        }
        final int[] maxCol = {0};
        final boolean[] truncated = {false};
        Set<String> targets = new HashSet<>(Arrays.asList("row", "col", "mergeCell", "pane"));
        try {
            XmlTree.stream(in, targets, (element, node) -> {
                switch (element) {
                    case "col": {
                        int min = Math.max(1, node.intAttr("min", 1));
                        int max = Math.min(MAX_COLS, node.intAttr("max", min));
                        float width = (float) node.doubleAttr("width", 8.43);
                        for (int c = min; c <= max; c++) {
                            while (sheet.columnWidths.size() < c) sheet.columnWidths.add(8.43f);
                            sheet.columnWidths.set(c - 1, width);
                        }
                        break;
                    }
                    case "mergeCell": {
                        int[] range = parseRange(node.attr("ref", ""));
                        if (range != null) sheet.merges.add(range);
                        break;
                    }
                    case "pane": {
                        if ("frozen".equals(node.attr("state", "")) || "frozenSplit".equals(node.attr("state", ""))) {
                            sheet.frozenRow = node.intAttr("ySplit", 0);
                        }
                        break;
                    }
                    case "row": {
                        if (sheet.rows.size() >= MAX_ROWS) {
                            truncated[0] = true;
                            return;
                        }
                        int rowIndex = node.intAttr("r", sheet.rows.size() + 1) - 1;
                        if (rowIndex < 0) rowIndex = sheet.rows.size();
                        while (sheet.rows.size() < rowIndex) sheet.rows.add(new ArrayList<>(0));
                        List<Cell> row = new ArrayList<>();
                        for (XmlNode cellNode : node.children("c")) {
                            int colIndex = columnIndex(cellNode.attr("r", "")) - 1;
                            if (colIndex < 0) colIndex = row.size();
                            if (colIndex >= MAX_COLS) {
                                truncated[0] = true;
                                break;
                            }
                            while (row.size() < colIndex) row.add(new Cell());
                            row.add(readCell(cellNode));
                        }
                        if (row.size() > maxCol[0]) maxCol[0] = row.size();
                        sheet.rows.add(row);
                        break;
                    }
                    default:
                        break;
                }
            });
        } finally {
            OoxmlPackage.closeQuietly(in);
        }
        sheet.truncated = truncated[0];
        if (sheet.truncated) {
            doc.warn("Feuille \"" + name + "\" tronquee (limites : " + MAX_ROWS + " lignes / " + MAX_COLS + " colonnes)");
        }
        sheet.densify();
        applyMerges(sheet);
        autoSizeColumns(sheet, maxCol[0]);
        return sheet;
    }

    private Cell readCell(XmlNode node) {
        Cell cell = new Cell();
        String type = node.attr("t", "n");
        XmlNode vNode = node.first("v");
        XmlNode fNode = node.first("f");
        if (fNode != null) {
            String formula = fNode.deepText().trim();
            if (!formula.isEmpty()) cell.formula = formula;
        }
        Xf xf = null;
        int styleIndex = node.intAttr("s", -1);
        if (styleIndex >= 0 && styleIndex < cellFormats.size()) xf = cellFormats.get(styleIndex);
        if (xf != null) {
            if (xf.fontId >= 0 && xf.fontId < fonts.size()) {
                Font font = fonts.get(xf.fontId);
                cell.bold = font.bold;
                cell.italic = font.italic;
                cell.color = font.color;
            }
            if (xf.fillId >= 0 && xf.fillId < fills.size()) cell.background = fills.get(xf.fillId);
            cell.align = xf.align;
        }

        String raw = vNode == null ? "" : vNode.text();
        switch (type) {
            case "s": {
                int index = parseInt(raw, -1);
                cell.text = index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
                break;
            }
            case "inlineStr": {
                XmlNode is = node.first("is");
                StringBuilder sb = new StringBuilder();
                if (is != null) {
                    for (XmlNode child : is.children()) {
                        if ("t".equals(child.name())) sb.append(child.text());
                        else if ("r".equals(child.name())) {
                            XmlNode t = child.first("t");
                            if (t != null) sb.append(t.text());
                        }
                    }
                }
                cell.text = sb.toString();
                break;
            }
            case "b":
                cell.text = "1".equals(raw.trim()) ? "VRAI" : "FAUX";
                cell.align = Cell.ALIGN_CENTER;
                break;
            case "e":
                cell.text = raw.isEmpty() ? "#ERREUR" : raw;
                cell.color = 0xFFC00000;
                break;
            case "str":
                cell.text = raw;
                break;
            default: {
                if (raw.isEmpty()) {
                    cell.text = "";
                    break;
                }
                double value = parseDouble(raw);
                if (Double.isNaN(value)) {
                    cell.text = raw;
                    break;
                }
                cell.number = value;
                String code = xf == null ? "General" : formatCode(xf.numFmtId);
                cell.text = ExcelNumberFormat.format(value, code, date1904);
                if (cell.align == Cell.ALIGN_GENERAL) cell.align = Cell.ALIGN_RIGHT;
                break;
            }
        }
        if (cell.align == Cell.ALIGN_GENERAL) cell.align = Cell.ALIGN_LEFT;
        return cell;
    }

    private static void applyMerges(Sheet sheet) {
        for (int[] range : sheet.merges) {
            for (int r = range[0]; r <= range[2]; r++) {
                for (int c = range[1]; c <= range[3]; c++) {
                    if (r == range[0] && c == range[1]) continue;
                    Cell cell = sheet.cell(r, c);
                    if (cell != null) cell.mergedAway = true;
                }
            }
        }
    }

    private static void autoSizeColumns(Sheet sheet, int columns) {
        int limit = Math.min(columns, MAX_COLS);
        for (int c = 0; c < limit; c++) {
            if (c < sheet.columnWidths.size() && sheet.columnWidths.get(c) != null
                    && sheet.columnWidths.get(c) != 8.43f) {
                continue;
            }
            int max = 4;
            int sampled = 0;
            for (List<Cell> row : sheet.rows) {
                if (sampled++ > 500) break;
                if (c < row.size()) {
                    String text = row.get(c).text;
                    if (text != null && text.length() > max) max = Math.min(60, text.length());
                }
            }
            while (sheet.columnWidths.size() <= c) sheet.columnWidths.add(8.43f);
            sheet.columnWidths.set(c, Math.max(6f, max + 1.5f));
        }
    }

    /* ------------------------------------------------------------------ */
    /* References A1                                                       */
    /* ------------------------------------------------------------------ */

    /** "BC42" -> 55 (1-based). Renvoie 0 si absent/invalide. */
    public static int columnIndex(String reference) {
        if (reference == null || reference.isEmpty()) return 0;
        int column = 0;
        for (int i = 0; i < reference.length(); i++) {
            char c = reference.charAt(i);
            if (c >= 'A' && c <= 'Z') column = column * 26 + (c - 'A' + 1);
            else if (c >= 'a' && c <= 'z') column = column * 26 + (c - 'a' + 1);
            else break;
        }
        return column;
    }

    /** 0-based -> "A", "Z", "AA"... */
    public static String columnName(int index) {
        int value = index + 1;
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            int remainder = (value - 1) % 26;
            sb.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return sb.toString();
    }

    /** "A1:C3" -> {0,0,2,2} (row1, col1, row2, col2). */
    public static int[] parseRange(String reference) {
        if (reference == null || reference.isEmpty()) return null;
        String[] parts = reference.split(":");
        int[] first = parseAddress(parts[0]);
        int[] last = parts.length > 1 ? parseAddress(parts[1]) : first;
        if (first == null || last == null) return null;
        return new int[]{Math.min(first[0], last[0]), Math.min(first[1], last[1]),
                Math.max(first[0], last[0]), Math.max(first[1], last[1])};
    }

    /** "B7" -> {6, 1} (ligne, colonne 0-based). */
    public static int[] parseAddress(String reference) {
        if (reference == null) return null;
        String cleaned = reference.replace("$", "").trim();
        int i = 0;
        while (i < cleaned.length() && Character.isLetter(cleaned.charAt(i))) i++;
        if (i == 0 || i >= cleaned.length()) return null;
        int column = columnIndex(cleaned.substring(0, i)) - 1;
        int row = parseInt(cleaned.substring(i), 0) - 1;
        if (column < 0 || row < 0) return null;
        return new int[]{row, column};
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
