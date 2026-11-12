/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.parse;

import org.opendocs.reader.core.model.Cell;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Sheet;
import org.opendocs.reader.core.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Parseur CSV / TSV conforme RFC 4180, avec deviations du monde reel.
 *
 * <p>Geré : separateur detecte automatiquement (virgule, point-virgule,
 * tabulation, pipe), guillemets et guillemets doubles, sauts de ligne a
 * l'interieur des champs, CRLF/LF/CR, BOM UTF-8/UTF-16, repli Latin-1,
 * lignes vides, en-tete detecte, nombres avec virgule decimale.</p>
 */
public final class CsvParser {

    public static final int MAX_ROWS = 100_000;
    public static final int MAX_COLS = 512;
    private static final char[] CANDIDATES = {',', ';', '\t', '|'};

    private CsvParser() {
    }

    public static void parse(byte[] data, ParsedDocument doc) {
        String text = TextUtil.decode(data);
        char delimiter = sniffDelimiter(text);
        List<List<String>> raw = tokenize(text, delimiter);

        Sheet sheet = new Sheet(TextUtil.withoutExtension(doc.fileName));
        if (sheet.name.isEmpty()) sheet.name = "CSV";
        boolean header = looksLikeHeader(raw);
        int columns = 0;
        for (int r = 0; r < raw.size(); r++) {
            List<String> values = raw.get(r);
            List<Cell> row = new ArrayList<>(values.size());
            for (int c = 0; c < values.size() && c < MAX_COLS; c++) {
                String value = values.get(c);
                Cell cell = toCell(value, delimiter);
                if (r == 0 && header) {
                    cell.header = true;
                    cell.bold = true;
                }
                row.add(cell);
            }
            columns = Math.max(columns, row.size());
            sheet.rows.add(row);
        }
        sheet.densify();
        for (int c = 0; c < columns; c++) {
            int max = 4;
            for (int r = 0; r < Math.min(sheet.rows.size(), 400); r++) {
                String value = sheet.cellText(r, c);
                if (value.length() > max) max = Math.min(60, value.length());
            }
            while (sheet.columnWidths.size() <= c) sheet.columnWidths.add(8.43f);
            sheet.columnWidths.set(c, Math.max(6f, max + 1.5f));
        }
        if (header) sheet.frozenRow = 1;
        doc.sheets.add(sheet);
        doc.metadata.putStat("Separateur detecte", delimiterName(delimiter));
        doc.metadata.putStat("Encodage", encodingName(data));
        doc.metadata.putExtra("En-tete", header ? "oui" : "non");
        if (raw.size() >= MAX_ROWS) {
            sheet.truncated = true;
            doc.warn("Fichier tronque a " + MAX_ROWS + " lignes");
        }
    }

    /** Detecte le separateur le plus frequent et le plus stable sur les premieres lignes. */
    public static char sniffDelimiter(String text) {
        String sample = firstLines(text, 12);
        char best = ',';
        int bestScore = -1;
        for (char candidate : CANDIDATES) {
            int total = 0;
            int lines = 0;
            int min = Integer.MAX_VALUE;
            boolean quoted = false;
            int perLine = 0;
            for (int i = 0; i < sample.length(); i++) {
                char c = sample.charAt(i);
                if (c == '"') quoted = !quoted;
                if (!quoted && c == candidate) {
                    total++;
                    perLine++;
                }
                if (c == '\n') {
                    if (perLine > 0) {
                        lines++;
                        min = Math.min(min, perLine);
                    }
                    perLine = 0;
                }
            }
            if (perLine > 0) {
                lines++;
                min = Math.min(min, perLine);
            }
            if (total == 0) continue;
            // Score : occurrences regulieres sur plusieurs lignes.
            int score = total + (lines > 1 ? min * lines * 2 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static String firstLines(String text, int maxLines) {
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && ++lines >= maxLines) return text.substring(0, i + 1);
        }
        return text.length() > 8192 ? text.substring(0, 8192) : text;
    }

    /** Machine a etats RFC 4180. */
    public static List<List<String>> tokenize(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < length && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"' && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
                continue;
            }
            if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                continue;
            }
            if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                if (!isEmptyRow(row)) {
                    rows.add(row);
                    if (rows.size() >= MAX_ROWS) return rows;
                }
                row = new ArrayList<>();
                continue;
            }
            field.append(c);
            fieldStarted = true;
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            if (!isEmptyRow(row) && rows.size() < MAX_ROWS) rows.add(row);
        }
        return rows;
    }

    private static boolean isEmptyRow(List<String> row) {
        for (String value : row) {
            if (!value.trim().isEmpty()) return false;
        }
        return true;
    }

    /** En-tete probable : premiere ligne non numerique et au moins une ligne de donnees numeriques. */
    private static boolean looksLikeHeader(List<List<String>> rows) {
        if (rows.size() < 2) return false;
        List<String> first = rows.get(0);
        if (first.isEmpty()) return false;
        int textual = 0;
        for (String value : first) {
            if (value.trim().isEmpty()) return false;
            if (!isNumeric(value, ',')) textual++;
        }
        if (textual < Math.max(1, first.size() / 2)) return false;
        int numericRows = 0;
        for (int r = 1; r < Math.min(rows.size(), 6); r++) {
            for (String value : rows.get(r)) {
                if (isNumeric(value, ',') || isNumeric(value, ';')) {
                    numericRows++;
                    break;
                }
            }
        }
        return numericRows > 0 || first.size() > 1;
    }

    private static Cell toCell(String value, char delimiter) {
        String trimmed = value == null ? "" : value.trim();
        Cell cell = new Cell(trimmed);
        if (isNumeric(trimmed, delimiter)) {
            String normalized = delimiter == ';' ? trimmed.replace(',', '.') : trimmed;
            normalized = normalized.replace(" ", "").replace("\u00A0", "");
            try {
                cell.number = Double.parseDouble(normalized);
                cell.align = Cell.ALIGN_RIGHT;
            } catch (NumberFormatException ignored) {
                cell.align = Cell.ALIGN_LEFT;
            }
        } else {
            cell.align = Cell.ALIGN_LEFT;
        }
        return cell;
    }

    private static boolean isNumeric(String value, char delimiter) {
        if (value == null || value.isEmpty()) return false;
        String v = value.trim().replace(" ", "").replace("\u00A0", "");
        if (delimiter == ';' && v.indexOf(',') >= 0 && v.indexOf('.') < 0) v = v.replace(',', '.');
        boolean digit = false;
        boolean dot = false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if (c == '.' && !dot) {
                dot = true;
            } else if ((c == '-' || c == '+') && i == 0) {
                // signe
            } else if ((c == 'e' || c == 'E') && digit) {
                // exposant
            } else {
                return false;
            }
        }
        return digit;
    }

    private static String delimiterName(char delimiter) {
        switch (delimiter) {
            case ',': return "virgule (,)";
            case ';': return "point-virgule (;)";
            case '\t': return "tabulation";
            case '|': return "pipe (|)";
            default: return String.valueOf(delimiter);
        }
    }

    private static String encodingName(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return "UTF-8 (BOM)";
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) return "UTF-16 LE";
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) return "UTF-16 BE";
        return TextUtil.isValidUtf8(data) ? "UTF-8" : "ISO-8859-1";
    }
}
