/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.parse;

import org.opendocs.reader.core.model.Block;
import org.opendocs.reader.core.model.Paragraph;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Run;
import org.opendocs.reader.core.model.Table;
import org.opendocs.reader.core.util.Emu;
import org.opendocs.reader.core.util.OoxmlColor;
import org.opendocs.reader.core.util.TextUtil;
import org.opendocs.reader.core.xml.XmlNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parseur DOCX (WordprocessingML) ecrit a la main.
 *
 * <p>Couvert : paragraphes, runs et styles (heritage basedOn + docDefaults),
 * titres (outlineLvl / styles "Heading n"), listes a puces et numerotees
 * (numbering.xml, compteurs multiniveaux), tableaux (fusions horizontales,
 * en-tetes, trames), images inline et ancrees (DrawingML + VML), hyperliens,
 * champs simples, sauts de page/ligne, tabulations, modifications suivies
 * (insertions acceptees, suppressions ignorees), zones de contenu (sdt).</p>
 *
 * <p>Non couvert (limite assumee d'un lecteur leger) : mise en page exacte
 * (colonnes, césures, en-tetes/pieds de page), zones de texte flottantes,
 * equations OMML, graphiques SmartArt, notes de bas de page numerotees.</p>
 */
public final class DocxParser {

    private static final int MAX_BLOCKS = 60000;
    private static final int MAX_IMAGES = 300;
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;
    private static final int MAX_STYLE_DEPTH = 12;

    private final OoxmlPackage pkg;
    private final ParsedDocument doc;
    private final Map<String, Style> styles = new HashMap<>();
    private final Map<String, AbstractNumbering> abstractNumberings = new HashMap<>();
    private final Map<String, String> numToAbstract = new HashMap<>();
    private final Map<String, int[]> counters = new HashMap<>();

    private Relationships rels = Relationships.empty();
    private final Style defaults = new Style();
    private int imageCount;

    private DocxParser(OoxmlPackage pkg, ParsedDocument doc) {
        this.pkg = pkg;
        this.doc = doc;
    }

    public static void parse(OoxmlPackage pkg, ParsedDocument doc) throws IOException {
        new DocxParser(pkg, doc).run();
    }

    private void run() throws IOException {
        DocProps.read(pkg, doc.metadata);
        rels = Relationships.forPart(pkg, "word/document.xml");
        readStyles();
        readNumbering();

        XmlNode document = pkg.xml("word/document.xml");
        if (document == null) throw new IOException("word/document.xml absent : ce n'est pas un DOCX valide");
        XmlNode body = document.first("body");
        if (body == null) {
            doc.warn("Corps du document introuvable");
            return;
        }
        parseInto(body, doc.blocks);
        if (doc.blocks.isEmpty()) doc.warn("Le document ne contient aucun texte exploitable");
    }

    /* ------------------------------------------------------------------ */
    /* Styles                                                              */
    /* ------------------------------------------------------------------ */

    private static final class Style {
        String id = "";
        String name = "";
        String basedOn = "";
        String type = "paragraph";
        Boolean bold;
        Boolean italic;
        Boolean underline;
        Boolean strike;
        float sizePt;
        int color;
        int align = -1;
        float spaceBeforePt = -1;
        float spaceAfterPt = -1;
        int outlineLevel = -1;
        int indentLevel = -1;
        String numId;
        int ilvl;

        void mergeFrom(Style parent) {
            if (parent == null) return;
            if (bold == null) bold = parent.bold;
            if (italic == null) italic = parent.italic;
            if (underline == null) underline = parent.underline;
            if (strike == null) strike = parent.strike;
            if (sizePt == 0) sizePt = parent.sizePt;
            if (color == 0) color = parent.color;
            if (align < 0) align = parent.align;
            if (spaceBeforePt < 0) spaceBeforePt = parent.spaceBeforePt;
            if (spaceAfterPt < 0) spaceAfterPt = parent.spaceAfterPt;
            if (outlineLevel < 0) outlineLevel = parent.outlineLevel;
            if (indentLevel < 0) indentLevel = parent.indentLevel;
            if (numId == null) numId = parent.numId;
        }

        Style copy() {
            Style s = new Style();
            s.id = id;
            s.name = name;
            s.basedOn = basedOn;
            s.type = type;
            s.bold = bold;
            s.italic = italic;
            s.underline = underline;
            s.strike = strike;
            s.sizePt = sizePt;
            s.color = color;
            s.align = align;
            s.spaceBeforePt = spaceBeforePt;
            s.spaceAfterPt = spaceAfterPt;
            s.outlineLevel = outlineLevel;
            s.indentLevel = indentLevel;
            s.numId = numId;
            s.ilvl = ilvl;
            return s;
        }
    }

    private void readStyles() throws IOException {
        XmlNode root = pkg.xml("word/styles.xml");
        if (root == null) return;
        XmlNode docDefaults = root.first("docDefaults");
        if (docDefaults != null) {
            XmlNode rPrDefault = docDefaults.find("rPrDefault/rPr");
            applyRunPropsToStyle(rPrDefault, defaults);
            XmlNode pPrDefault = docDefaults.find("pPrDefault/pPr");
            applyParagraphPropsToStyle(pPrDefault, defaults);
        }
        for (XmlNode node : root.children("style")) {
            String id = node.attr("styleId");
            if (id == null) continue;
            Style style = new Style();
            style.id = id;
            style.type = node.attr("type", "paragraph");
            XmlNode nameNode = node.first("name");
            style.name = nameNode == null ? id : nameNode.attr("val", id);
            XmlNode basedOn = node.first("basedOn");
            style.basedOn = basedOn == null ? "" : basedOn.attr("val", "");
            applyParagraphPropsToStyle(node.first("pPr"), style);
            applyRunPropsToStyle(node.first("rPr"), style);
            styles.put(id, style);
        }
    }

    private void applyRunPropsToStyle(XmlNode rPr, Style style) {
        if (rPr == null) return;
        Boolean b = boolProp(rPr, "b");
        if (b != null) style.bold = b;
        Boolean i = boolProp(rPr, "i");
        if (i != null) style.italic = i;
        XmlNode u = rPr.first("u");
        if (u != null) style.underline = !"none".equals(u.attr("val", "single"));
        XmlNode strike = rPr.first("strike");
        if (strike != null) style.strike = strike.boolAttr("val", true);
        XmlNode sz = rPr.first("sz");
        if (sz != null) style.sizePt = Emu.halfPointsToPoints(sz.longAttr("val", 0));
        XmlNode color = rPr.first("color");
        if (color != null) style.color = OoxmlColor.parseHex(color.attr("val"));
    }

    private void applyParagraphPropsToStyle(XmlNode pPr, Style style) {
        if (pPr == null) return;
        XmlNode jc = pPr.first("jc");
        if (jc != null) style.align = alignment(jc.attr("val", "left"));
        XmlNode spacing = pPr.first("spacing");
        if (spacing != null) {
            if (spacing.attr("before") != null) {
                style.spaceBeforePt = Emu.twipsToPoints(spacing.longAttr("before", 0));
            }
            if (spacing.attr("after") != null) {
                style.spaceAfterPt = Emu.twipsToPoints(spacing.longAttr("after", 0));
            }
        }
        XmlNode outline = pPr.first("outlineLvl");
        if (outline != null) style.outlineLevel = outline.intAttr("val", -1);
        XmlNode numPr = pPr.first("numPr");
        if (numPr != null) {
            XmlNode numId = numPr.first("numId");
            XmlNode ilvl = numPr.first("ilvl");
            if (numId != null) style.numId = numId.attr("val");
            if (ilvl != null) style.ilvl = ilvl.intAttr("val", 0);
        }
        XmlNode ind = pPr.first("ind");
        if (ind != null && ind.attr("left") != null) {
            style.indentLevel = (int) Math.min(6, ind.longAttr("left", 0) / 720);
        }
    }

    /** Resout l'heritage basedOn (avec garde anti-cycle). */
    private Style resolveStyle(String styleId) {
        if (styleId == null || styleId.isEmpty()) {
            Style empty = defaults.copy();
            empty.id = "";
            return empty;
        }
        List<Style> chain = new ArrayList<>(4);
        String current = styleId;
        int guard = 0;
        while (current != null && !current.isEmpty() && guard++ < MAX_STYLE_DEPTH) {
            Style s = styles.get(current);
            if (s == null) break;
            if (chain.contains(s)) break;
            chain.add(s);
            current = s.basedOn;
        }
        if (chain.isEmpty()) {
            Style unknown = defaults.copy();
            unknown.id = styleId;
            return unknown;
        }
        // Le style le plus specifique gagne ; on complete avec les parents puis docDefaults.
        Style result = chain.get(0).copy();
        for (int i = 1; i < chain.size(); i++) result.mergeFrom(chain.get(i));
        result.mergeFrom(defaults);
        // Detection des titres par le nom du style (Word localise les noms).
        if (result.outlineLevel < 0) {
            int level = headingFromName(result.name);
            if (level > 0) result.outlineLevel = level - 1;
        }
        return result;
    }

    private static int headingFromName(String name) {
        if (name == null) return 0;
        String lower = name.toLowerCase(Locale.ROOT).trim();
        String[] prefixes = {"heading ", "titre ", "heading", "titre"};
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                String digits = lower.substring(prefix.length()).trim();
                try {
                    int level = Integer.parseInt(digits);
                    if (level >= 1 && level <= 9) return level;
                } catch (NumberFormatException ignored) {
                    // nom non numerote
                }
            }
        }
        if (lower.equals("title") || lower.equals("titre")) return 1;
        return 0;
    }

    /* ------------------------------------------------------------------ */
    /* Numerotation                                                        */
    /* ------------------------------------------------------------------ */

    private static final class Level {
        int start = 1;
        String format = "decimal";
        String text = "%1.";
    }

    private static final class AbstractNumbering {
        final Map<Integer, Level> levels = new HashMap<>();
    }

    private void readNumbering() throws IOException {
        XmlNode root = pkg.xml("word/numbering.xml");
        if (root == null) return;
        for (XmlNode abs : root.children("abstractNum")) {
            String id = abs.attr("abstractNumId");
            if (id == null) continue;
            AbstractNumbering numbering = new AbstractNumbering();
            for (XmlNode lvl : abs.children("lvl")) {
                int ilvl = lvl.intAttr("ilvl", 0);
                Level level = new Level();
                XmlNode start = lvl.first("start");
                if (start != null) level.start = start.intAttr("val", 1);
                XmlNode fmt = lvl.first("numFmt");
                if (fmt != null) level.format = fmt.attr("val", "decimal");
                XmlNode txt = lvl.first("lvlText");
                if (txt != null) level.text = txt.attr("val", "%1.");
                numbering.levels.put(ilvl, level);
            }
            abstractNumberings.put(id, numbering);
        }
        for (XmlNode num : root.children("num")) {
            String numId = num.attr("numId");
            XmlNode absId = num.first("abstractNumId");
            if (numId != null && absId != null) numToAbstract.put(numId, absId.attr("val", ""));
        }
    }

    /** Construit l'etiquette ("•", "1.", "a)", "1.2.") et met a jour les compteurs. */
    private String numberingLabel(String numId, int ilvl) {
        if (numId == null || numId.isEmpty() || "0".equals(numId)) return "";
        String abstractId = numToAbstract.get(numId);
        AbstractNumbering numbering = abstractId == null ? null : abstractNumberings.get(abstractId);
        if (numbering == null) return "\u2022";
        Level level = numbering.levels.get(ilvl);
        if (level == null) level = numbering.levels.get(0);
        if (level == null) return "\u2022";
        if ("bullet".equals(level.format)) {
            String glyph = level.text == null || level.text.isEmpty() ? "\u2022" : level.text;
            // Les polices Symbol/Wingdings affichent des glyphes prives : on normalise.
            if (glyph.codePointAt(0) >= 0xF000) glyph = "\u2022";
            return glyph;
        }
        if ("none".equals(level.format)) return "";

        int[] values = counters.get(numId);
        if (values == null) {
            values = new int[10];
            for (int i = 0; i < values.length; i++) {
                Level l = numbering.levels.get(i);
                values[i] = (l == null ? 1 : l.start) - 1;
            }
            counters.put(numId, values);
        }
        int index = Math.max(0, Math.min(9, ilvl));
        values[index]++;
        for (int i = index + 1; i < values.length; i++) {
            Level l = numbering.levels.get(i);
            values[i] = (l == null ? 1 : l.start) - 1;
        }
        String label = level.text == null ? "%1." : level.text;
        for (int i = 0; i <= index; i++) {
            Level l = numbering.levels.get(i);
            String format = l == null ? "decimal" : l.format;
            int value = Math.max(values[i], 0) + (values[i] < 0 ? 1 : 0);
            if (values[i] < 0) value = (l == null ? 1 : l.start);
            label = label.replace("%" + (i + 1), formatNumber(value, format));
        }
        return label;
    }

    private static String formatNumber(int value, String format) {
        switch (format) {
            case "lowerLetter":
                return value >= 1 && value <= 26 ? String.valueOf((char) ('a' + value - 1)) : String.valueOf(value);
            case "upperLetter":
                return value >= 1 && value <= 26 ? String.valueOf((char) ('A' + value - 1)) : String.valueOf(value);
            case "lowerRoman":
                return roman(value).toLowerCase(Locale.ROOT);
            case "upperRoman":
                return roman(value);
            case "decimalZero":
                return value < 10 ? "0" + value : String.valueOf(value);
            case "ordinal":
                return value + ".";
            default:
                return String.valueOf(value);
        }
    }

    private static String roman(int value) {
        if (value <= 0 || value > 3999) return String.valueOf(value);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                sb.append(symbols[i]);
                remaining -= values[i];
            }
        }
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /* Corps du document                                                   */
    /* ------------------------------------------------------------------ */

    private void parseInto(XmlNode container, List<Block> out) throws IOException {
        for (XmlNode node : container.children()) {
            if (out.size() >= MAX_BLOCKS) {
                doc.warn("Document tronque : plus de " + MAX_BLOCKS + " blocs");
                return;
            }
            switch (node.name()) {
                case "p":
                    emitParagraph(node, out);
                    break;
                case "tbl":
                    out.add(new Block.TableBlock(parseTable(node)));
                    break;
                case "sdt": {
                    XmlNode content = node.first("sdtContent");
                    if (content != null) parseInto(content, out);
                    break;
                }
                case "sectPr":
                    break;
                default:
                    break;
            }
        }
    }

    private void emitParagraph(XmlNode node, List<Block> out) throws IOException {
        List<Block.ImageBlock> images = new ArrayList<>(0);
        Paragraph paragraph = parseParagraph(node, images);
        if (!paragraph.isEmpty() || !images.isEmpty() || paragraph.runs.isEmpty()) {
            out.add(new Block.ParagraphBlock(paragraph));
        }
        for (Block.ImageBlock image : images) out.add(image);
    }

    /** Parse un w:p ; les images rencontrees sont ajoutees a la liste fournie. */
    private Paragraph parseParagraph(XmlNode node, List<Block.ImageBlock> images) throws IOException {
        Paragraph paragraph = new Paragraph();
        XmlNode pPr = node.first("pPr");
        String styleId = null;
        if (pPr != null) {
            XmlNode pStyle = pPr.first("pStyle");
            if (pStyle != null) styleId = pStyle.attr("val");
        }
        Style style = resolveStyle(styleId);
        paragraph.styleName = style.name == null ? "" : style.name;
        if (style.align >= 0) paragraph.align = style.align;
        if (style.spaceBeforePt >= 0) paragraph.spaceBeforePt = style.spaceBeforePt;
        if (style.spaceAfterPt >= 0) paragraph.spaceAfterPt = style.spaceAfterPt;
        if (style.outlineLevel >= 0 && style.outlineLevel <= 5) paragraph.headingLevel = style.outlineLevel + 1;
        if (style.indentLevel > 0) paragraph.indentLevel = style.indentLevel;

        String numId = style.numId;
        int ilvl = style.ilvl;
        if (pPr != null) {
            XmlNode jc = pPr.first("jc");
            if (jc != null) paragraph.align = alignment(jc.attr("val", "left"));
            XmlNode spacing = pPr.first("spacing");
            if (spacing != null) {
                if (spacing.attr("before") != null) {
                    paragraph.spaceBeforePt = Emu.twipsToPoints(spacing.longAttr("before", 0));
                }
                if (spacing.attr("after") != null) {
                    paragraph.spaceAfterPt = Emu.twipsToPoints(spacing.longAttr("after", 0));
                }
                XmlNode line = spacing;
                if (line.attr("line") != null && "auto".equals(line.attr("lineRule", "auto"))) {
                    paragraph.lineSpacing = Math.max(0.8f, line.longAttr("line", 240) / 240f);
                }
            }
            XmlNode outline = pPr.first("outlineLvl");
            if (outline != null) {
                int level = outline.intAttr("val", -1);
                if (level >= 0 && level <= 5) paragraph.headingLevel = level + 1;
            }
            XmlNode numPr = pPr.first("numPr");
            if (numPr != null) {
                XmlNode numIdNode = numPr.first("numId");
                XmlNode ilvlNode = numPr.first("ilvl");
                if (numIdNode != null) numId = numIdNode.attr("val");
                if (ilvlNode != null) ilvl = ilvlNode.intAttr("val", 0);
            }
            XmlNode ind = pPr.first("ind");
            if (ind != null && ind.attr("left") != null) {
                paragraph.indentLevel = (int) Math.min(6, ind.longAttr("left", 0) / 720);
            }
            XmlNode shd = pPr.first("shd");
            if (shd != null) paragraph.backgroundColor = OoxmlColor.parseHex(shd.attr("fill"));
            XmlNode pageBreakBefore = pPr.first("pageBreakBefore");
            if (pageBreakBefore != null && pageBreakBefore.boolAttr("val", true)) paragraph.spaceBeforePt += 12;
        }

        if (numId != null && !"0".equals(numId)) {
            String label = numberingLabel(numId, ilvl);
            if (!label.isEmpty()) {
                paragraph.numberLabel = label;
                paragraph.bullet = "\u2022".equals(label);
                if (paragraph.indentLevel < ilvl + 1) paragraph.indentLevel = ilvl + 1;
            }
        }

        Run template = new Run();
        if (style.bold != null) template.bold = style.bold;
        if (style.italic != null) template.italic = style.italic;
        if (style.underline != null) template.underline = style.underline;
        if (style.strike != null) template.strike = style.strike;
        template.sizePt = style.sizePt;
        template.color = style.color;
        if (pPr != null) applyRunProps(pPr.first("rPr"), template);

        collectRuns(node, template, paragraph, images, null);
        return paragraph;
    }

    /** Parcours recursif des enfants d'un paragraphe (runs, liens, insertions, champs). */
    private void collectRuns(XmlNode parent, Run template, Paragraph paragraph,
                             List<Block.ImageBlock> images, String link) throws IOException {
        for (XmlNode node : parent.children()) {
            switch (node.name()) {
                case "r": {
                    Run run = new Run().copyStyleFrom(template);
                    if (link != null) run.link = link;
                    applyRunProps(node.first("rPr"), run);
                    appendRunContent(node, run, paragraph, images);
                    break;
                }
                case "hyperlink": {
                    String id = node.attr("id");
                    String target = id != null ? rels.target(id) : null;
                    if (target == null) {
                        String anchor = node.attr("anchor");
                        if (anchor != null) target = "#" + anchor;
                    }
                    collectRuns(node, template, paragraph, images, target);
                    break;
                }
                case "fldSimple": {
                    String instr = node.attr("instr", "");
                    String target = extractHyperlinkField(instr);
                    collectRuns(node, template, paragraph, images, target != null ? target : link);
                    break;
                }
                case "ins":
                case "moveTo":
                case "smartTag":
                    collectRuns(node, template, paragraph, images, link);
                    break;
                case "sdt": {
                    XmlNode content = node.first("sdtContent");
                    if (content != null) collectRuns(content, template, paragraph, images, link);
                    break;
                }
                case "del":
                case "moveFrom":
                case "proofErr":
                case "bookmarkStart":
                case "bookmarkEnd":
                case "commentRangeStart":
                case "commentRangeEnd":
                case "pPr":
                    break;
                default:
                    break;
            }
        }
    }

    private static String extractHyperlinkField(String instruction) {
        if (instruction == null) return null;
        String trimmed = instruction.trim();
        if (!trimmed.toUpperCase(Locale.ROOT).startsWith("HYPERLINK")) return null;
        int first = trimmed.indexOf('"');
        int last = trimmed.lastIndexOf('"');
        if (first >= 0 && last > first) return trimmed.substring(first + 1, last);
        return null;
    }

    private void appendRunContent(XmlNode runNode, Run run, Paragraph paragraph,
                                  List<Block.ImageBlock> images) throws IOException {
        StringBuilder buffer = new StringBuilder();
        boolean wroteText = false;
        for (XmlNode node : runNode.children()) {
            switch (node.name()) {
                case "t":
                    buffer.append(node.text());
                    wroteText = true;
                    break;
                case "tab":
                    buffer.append('\t');
                    break;
                case "br": {
                    String type = node.attr("type", "textWrapping");
                    buffer.append("page".equals(type) ? "\n" : "\n");
                    break;
                }
                case "cr":
                    buffer.append('\n');
                    break;
                case "noBreakHyphen":
                    buffer.append('\u2011');
                    break;
                case "softHyphen":
                    break;
                case "sym":
                    buffer.append('\u25A1');
                    break;
                case "drawing":
                case "pict":
                case "object": {
                    if (buffer.length() > 0) {
                        paragraph.add(new Run(buffer.toString()).copyStyleFrom(run));
                        buffer.setLength(0);
                        wroteText = false;
                    }
                    Block.ImageBlock image = parseImage(node);
                    if (image != null) images.add(image);
                    break;
                }
                case "footnoteReference":
                case "endnoteReference":
                    buffer.append(" \u2020");
                    break;
                case "instrText":
                case "fldChar":
                case "delText":
                case "rPr":
                    break;
                default:
                    break;
            }
        }
        if (buffer.length() > 0 || wroteText) {
            paragraph.add(new Run(buffer.toString()).copyStyleFrom(run));
        }
    }

    private void applyRunProps(XmlNode rPr, Run run) {
        if (rPr == null) return;
        Boolean b = boolProp(rPr, "b");
        if (b != null) run.bold = b;
        Boolean i = boolProp(rPr, "i");
        if (i != null) run.italic = i;
        XmlNode u = rPr.first("u");
        if (u != null) run.underline = !"none".equals(u.attr("val", "single"));
        XmlNode strike = rPr.first("strike");
        if (strike != null) run.strike = strike.boolAttr("val", true);
        XmlNode dstrike = rPr.first("dstrike");
        if (dstrike != null) run.strike = dstrike.boolAttr("val", true);
        XmlNode sz = rPr.first("sz");
        if (sz != null) {
            float points = Emu.halfPointsToPoints(sz.longAttr("val", 0));
            if (points > 0) run.sizePt = points;
        }
        XmlNode color = rPr.first("color");
        if (color != null) {
            int argb = OoxmlColor.parseHex(color.attr("val"));
            if (argb != OoxmlColor.NONE) run.color = argb;
        }
        XmlNode highlight = rPr.first("highlight");
        if (highlight != null) run.highlight = OoxmlColor.wordHighlight(highlight.attr("val"));
        XmlNode shd = rPr.first("shd");
        if (shd != null && run.highlight == OoxmlColor.NONE) {
            run.highlight = OoxmlColor.parseHex(shd.attr("fill"));
        }
        XmlNode fonts = rPr.first("rFonts");
        if (fonts != null) run.font = fonts.attr("ascii", fonts.attr("hAnsi"));
        XmlNode vertAlign = rPr.first("vertAlign");
        if (vertAlign != null) {
            String value = vertAlign.attr("val", "");
            run.superscript = "superscript".equals(value);
            run.subscript = "subscript".equals(value);
        }
        XmlNode rStyle = rPr.first("rStyle");
        if (rStyle != null) {
            Style character = resolveStyle(rStyle.attr("val"));
            if (character.bold != null && boolProp(rPr, "b") == null) run.bold = character.bold;
            if (character.italic != null && boolProp(rPr, "i") == null) run.italic = character.italic;
            if (character.underline != null && rPr.first("u") == null) run.underline = character.underline;
            if (character.sizePt > 0 && rPr.first("sz") == null) run.sizePt = character.sizePt;
            if (character.color != 0 && rPr.first("color") == null) run.color = character.color;
        }
    }

    /** Propriete booleenne OOXML : null si absente (heritage), sinon valeur explicite. */
    private static Boolean boolProp(XmlNode parent, String name) {
        XmlNode node = parent.first(name);
        if (node == null) return null;
        return node.boolAttr("val", true);
    }

    private static int alignment(String value) {
        if (value == null) return Paragraph.ALIGN_LEFT;
        switch (value) {
            case "center":
                return Paragraph.ALIGN_CENTER;
            case "right":
            case "end":
                return Paragraph.ALIGN_RIGHT;
            case "both":
            case "justify":
            case "distribute":
                return Paragraph.ALIGN_JUSTIFY;
            default:
                return Paragraph.ALIGN_LEFT;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Images                                                              */
    /* ------------------------------------------------------------------ */

    private Block.ImageBlock parseImage(XmlNode container) throws IOException {
        if (imageCount >= MAX_IMAGES) {
            doc.warn("Trop d'images : seules les " + MAX_IMAGES + " premieres sont affichees");
            return null;
        }
        String relId = null;
        long cx = 0;
        long cy = 0;
        String alt = "";
        String name = "";

        List<XmlNode> blips = container.all("blip");
        if (!blips.isEmpty()) {
            relId = blips.get(0).attr("embed");
            if (relId == null) relId = blips.get(0).attr("link");
        }
        if (relId == null) {
            List<XmlNode> imageData = container.all("imagedata");
            if (!imageData.isEmpty()) relId = imageData.get(0).attr("id");
        }
        List<XmlNode> extents = container.all("extent");
        if (!extents.isEmpty()) {
            cx = extents.get(0).longAttr("cx", 0);
            cy = extents.get(0).longAttr("cy", 0);
        }
        List<XmlNode> docPrs = container.all("docPr");
        if (!docPrs.isEmpty()) {
            alt = docPrs.get(0).attr("descr", "");
            name = docPrs.get(0).attr("name", "");
        }
        if (relId == null) return null;
        String path = rels.resolve("word/document.xml", relId);
        if (path == null || path.startsWith("http")) {
            doc.warn("Image externe ignoree : " + relId);
            return null;
        }
        byte[] data = pkg.read(path);
        if (data == null) {
            doc.warn("Image introuvable dans l'archive : " + path);
            return null;
        }
        if (data.length > MAX_IMAGE_BYTES) {
            doc.warn("Image trop lourde ignoree : " + path);
            return null;
        }
        imageCount++;
        if (name.isEmpty()) {
            int slash = path.lastIndexOf('/');
            name = slash < 0 ? path : path.substring(slash + 1);
        }
        int width = cx > 0 ? Math.round(Emu.toPixels(cx)) : 0;
        int height = cy > 0 ? Math.round(Emu.toPixels(cy)) : 0;
        return new Block.ImageBlock(data, name, alt, width, height);
    }

    /* ------------------------------------------------------------------ */
    /* Tableaux                                                            */
    /* ------------------------------------------------------------------ */

    private Table parseTable(XmlNode tbl) throws IOException {
        Table table = new Table();
        XmlNode grid = tbl.first("tblGrid");
        if (grid != null) {
            List<XmlNode> columns = grid.children("gridCol");
            if (!columns.isEmpty()) {
                float[] weights = new float[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    weights[i] = Math.max(1, columns.get(i).longAttr("w", 1440));
                }
                table.columnWeights = weights;
            }
        }
        boolean first = true;
        for (XmlNode tr : tbl.children("tr")) {
            List<Table.Cell> row = new ArrayList<>();
            XmlNode trPr = tr.first("trPr");
            boolean header = first && trPr != null && trPr.first("tblHeader") != null;
            for (XmlNode tc : tr.children("tc")) {
                Table.Cell cell = new Table.Cell();
                XmlNode tcPr = tc.first("tcPr");
                if (tcPr != null) {
                    XmlNode span = tcPr.first("gridSpan");
                    if (span != null) cell.colSpan = Math.max(1, span.intAttr("val", 1));
                    XmlNode shd = tcPr.first("shd");
                    if (shd != null) cell.background = OoxmlColor.parseHex(shd.attr("fill"));
                    XmlNode vAlign = tcPr.first("vAlign");
                    if (vAlign != null && "center".equals(vAlign.attr("val"))) {
                        cell.align = Paragraph.ALIGN_CENTER;
                    }
                }
                cell.header = header || (first && isBoldRow(tc));
                for (XmlNode child : tc.children()) {
                    if ("p".equals(child.name())) {
                        cell.paragraphs.add(parseParagraph(child, new ArrayList<>(0)));
                    } else if ("tbl".equals(child.name())) {
                        // Tableau imbrique : on aplatit son texte (limite assumee).
                        Table nested = parseTable(child);
                        Paragraph flattened = new Paragraph();
                        flattened.add(Run.of(nested.text().trim()));
                        cell.paragraphs.add(flattened);
                    }
                }
                if (cell.paragraphs.isEmpty()) cell.paragraphs.add(new Paragraph());
                row.add(cell);
            }
            if (!row.isEmpty()) table.rows.add(row);
            first = false;
        }
        if (!table.rows.isEmpty()) {
            boolean allBold = true;
            for (Table.Cell cell : table.rows.get(0)) {
                if (!cell.header) {
                    allBold = false;
                    break;
                }
            }
            table.firstRowIsHeader = allBold;
        }
        return table;
    }

    private static boolean isBoldRow(XmlNode tc) {
        for (XmlNode p : tc.children("p")) {
            for (XmlNode r : p.children("r")) {
                XmlNode rPr = r.first("rPr");
                if (rPr == null) return false;
                Boolean bold = boolProp(rPr, "b");
                if (bold == null || !bold) return false;
            }
        }
        return true;
    }

    /** Utilitaire de debug : resume du document (utilise par les tests). */
    public static String describe(ParsedDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("DOCX ").append(doc.blocks.size()).append(" blocs\n");
        for (Block b : doc.blocks) {
            sb.append(b.kind()).append(": ").append(TextUtil.ellipsize(b.text().replace('\n', ' '), 90)).append('\n');
        }
        return sb.toString();
    }
}
