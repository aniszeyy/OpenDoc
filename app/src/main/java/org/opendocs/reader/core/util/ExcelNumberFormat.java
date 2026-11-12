/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interprete un large sous-ensemble des formats numeriques Excel (numFmt).
 *
 * <p>Supporte : General, 0 / # / ?, separateurs de milliers, decimales,
 * pourcentages, notation scientifique, echelle par milliers (0,,),
 * sections positif;negatif;zero, conditions [&gt;100], couleurs ignorees,
 * monnaies [$EUR-1], litteraux ("texte", \x, _x, *x), dates et heures
 * (yy, yyyy, m, mmm, mmmm, d, ddd, h, hh, s, ss, AM/PM, [h] duree cumulee).</p>
 *
 * <p>Repli propre (format General) pour les cas exotiques : fractions,
 * textes conditionnels. Objectif : affichage fidele des classeurs reels.</p>
 */
public final class ExcelNumberFormat {

    private static final String[] MONTHS_SHORT = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] MONTHS_LONG = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};
    private static final String[] DAYS_SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] DAYS_LONG = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    private ExcelNumberFormat() {
    }

    public static String format(double value, String formatCode, boolean date1904) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return String.valueOf(value);
        String code = formatCode == null ? "" : formatCode.trim();
        if (code.isEmpty() || "general".equalsIgnoreCase(code)) return general(value);

        List<String> sections = splitSections(code);
        int index = chooseSection(sections, value);
        String section = sections.get(index);

        String cleaned = stripBrackets(section);
        if (cleaned.trim().equals("@")) return general(value);

        // Section negative explicite ou condition : la valeur est affichee sans signe.
        boolean absValue = (index == 1 && sections.size() > 1 && !hasCondition(section)) || hasCondition(section);
        double working = absValue ? Math.abs(value) : value;

        if (isDateFormat(cleaned)) return formatDate(value, cleaned, date1904);
        return formatNumeric(working, cleaned);
    }

    /* ------------------------------------------------------------------ */
    /* Sections                                                            */
    /* ------------------------------------------------------------------ */

    /** Decoupe sur ';' en respectant guillemets, crochets et echappements. */
    static List<String> splitSections(String code) {
        List<String> out = new ArrayList<>(2);
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int brackets = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\\' && i + 1 < code.length()) {
                current.append(c).append(code.charAt(++i));
                continue;
            }
            if (c == '"') quoted = !quoted;
            if (!quoted && c == '[') brackets++;
            if (!quoted && c == ']') brackets = Math.max(0, brackets - 1);
            if (c == ';' && !quoted && brackets == 0) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static int chooseSection(List<String> sections, double value) {
        boolean anyCondition = false;
        for (String s : sections) {
            if (hasCondition(s)) {
                anyCondition = true;
                break;
            }
        }
        if (anyCondition) {
            int fallback = -1;
            for (int i = 0; i < sections.size(); i++) {
                double[] cond = condition(sections.get(i));
                if (cond == null) {
                    if (fallback < 0) fallback = i;
                    continue;
                }
                if (matches(cond, value)) return i;
            }
            return fallback < 0 ? 0 : fallback;
        }
        if (sections.size() == 1) return 0;
        if (value < 0) return 1;
        if (value == 0 && sections.size() > 2) return 2;
        return 0;
    }

    private static boolean hasCondition(String section) {
        return condition(section) != null;
    }

    /** {operateur, seuil} : 0 '=', 1 '&lt;', 2 '&gt;', 3 '&lt;=', 4 '&gt;=', 5 '&lt;&gt;'. */
    private static double[] condition(String section) {
        int open = section.indexOf('[');
        if (open < 0) return null;
        int close = section.indexOf(']', open);
        if (close < 0) return null;
        String body = section.substring(open + 1, close).trim();
        if (body.isEmpty()) return null;
        int op;
        int from;
        if (body.startsWith("<=")) {
            op = 3;
            from = 2;
        } else if (body.startsWith(">=")) {
            op = 4;
            from = 2;
        } else if (body.startsWith("<>")) {
            op = 5;
            from = 2;
        } else if (body.startsWith("<")) {
            op = 1;
            from = 1;
        } else if (body.startsWith(">")) {
            op = 2;
            from = 1;
        } else if (body.startsWith("=")) {
            op = 0;
            from = 1;
        } else {
            return null;
        }
        try {
            return new double[]{op, Double.parseDouble(body.substring(from).trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean matches(double[] cond, double value) {
        switch ((int) cond[0]) {
            case 0:
                return value == cond[1];
            case 1:
                return value < cond[1];
            case 2:
                return value > cond[1];
            case 3:
                return value <= cond[1];
            case 4:
                return value >= cond[1];
            case 5:
                return value != cond[1];
            default:
                return false;
        }
    }

    /**
     * Retire les crochets (couleurs, conditions, locales) mais conserve
     * le symbole monetaire de [$EUR-1] et les marqueurs de duree [h]/[m]/[s].
     */
    static String stripBrackets(String section) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < section.length(); i++) {
            char c = section.charAt(i);
            if (c == '\\' && i + 1 < section.length()) {
                sb.append(c).append(section.charAt(++i));
                continue;
            }
            if (c == '[') {
                int close = section.indexOf(']', i);
                if (close < 0) break;
                String body = section.substring(i + 1, close);
                if (body.startsWith("$")) {
                    sb.append(currencySymbol(body));
                } else if (isElapsedMarker(body)) {
                    sb.append('[').append(body).append(']');
                }
                i = close;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isElapsedMarker(String body) {
        String b = body.toLowerCase(Locale.ROOT);
        return b.equals("h") || b.equals("hh") || b.equals("m") || b.equals("mm") || b.equals("s") || b.equals("ss");
    }

    private static String currencySymbol(String bracketBody) {
        String body = bracketBody.startsWith("$") ? bracketBody.substring(1) : bracketBody;
        int dash = body.indexOf('-');
        return (dash < 0 ? body : body.substring(0, dash)).trim();
    }

    /* ------------------------------------------------------------------ */
    /* Dates et heures                                                     */
    /* ------------------------------------------------------------------ */

    /** Un format est temporel s'il contient un marqueur de date hors litteraux. */
    static boolean isDateFormat(String cleaned) {
        boolean quoted = false;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (quoted) continue;
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '[') {
                int close = cleaned.indexOf(']', i);
                if (close > i) {
                    if (isElapsedMarker(cleaned.substring(i + 1, close))) return true;
                    i = close;
                }
                continue;
            }
            char lower = Character.toLowerCase(c);
            if (lower == 'y' || lower == 'd' || lower == 'h' || lower == 's' || lower == 'm') return true;
        }
        return false;
    }

    /** Jeton de format : soit un code date, soit du texte litteral. */
    private static final class Token {
        final String value;
        final boolean code;

        Token(String value, boolean code) {
            this.value = value;
            this.code = code;
        }

        /**
         * Premiere lettre du jeton. Pour un marqueur de duree ([h], [mm], [s]) la
         * lettre utile est celle entre crochets, sinon "mm" apres "[h]" serait lu
         * comme un mois au lieu de minutes.
         */
        char codeChar() {
            if (!code) return 0;
            if (value.charAt(0) == '[' && value.length() > 1) {
                return Character.toLowerCase(value.charAt(1));
            }
            return Character.toLowerCase(value.charAt(0));
        }
    }

    private static List<Token> tokenizeDate(String cleaned) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        boolean quoted = false;
        int i = 0;
        while (i < cleaned.length()) {
            char c = cleaned.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                i++;
                continue;
            }
            if (quoted) {
                literal.append(c);
                i++;
                continue;
            }
            if (c == '\\') {
                if (i + 1 < cleaned.length()) literal.append(cleaned.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '_') {
                literal.append(' ');
                i += 2;
                continue;
            }
            if (c == '*') {
                i += 2;
                continue;
            }
            if (c == '[') {
                int close = cleaned.indexOf(']', i);
                if (close > i) {
                    if (literal.length() > 0) {
                        tokens.add(new Token(literal.toString(), false));
                        literal.setLength(0);
                    }
                    tokens.add(new Token(cleaned.substring(i, close + 1), true));
                    i = close + 1;
                    continue;
                }
            }
            if (startsWithIgnoreCase(cleaned, i, "am/pm") || startsWithIgnoreCase(cleaned, i, "a/p")) {
                int len = startsWithIgnoreCase(cleaned, i, "am/pm") ? 5 : 3;
                if (literal.length() > 0) {
                    tokens.add(new Token(literal.toString(), false));
                    literal.setLength(0);
                }
                tokens.add(new Token(cleaned.substring(i, i + len), true));
                i += len;
                continue;
            }
            char lower = Character.toLowerCase(c);
            if (lower == 'y' || lower == 'm' || lower == 'd' || lower == 'h' || lower == 's' || lower == 'e') {
                int run = 1;
                while (i + run < cleaned.length() && Character.toLowerCase(cleaned.charAt(i + run)) == lower) run++;
                if (literal.length() > 0) {
                    tokens.add(new Token(literal.toString(), false));
                    literal.setLength(0);
                }
                tokens.add(new Token(cleaned.substring(i, i + run), true));
                i += run;
                continue;
            }
            literal.append(c);
            i++;
        }
        if (literal.length() > 0) tokens.add(new Token(literal.toString(), false));
        return tokens;
    }

    private static String formatDate(double value, String cleaned, boolean date1904) {
        ExcelDate.Parts p = ExcelDate.fromSerial(value, date1904);
        List<Token> tokens = tokenizeDate(cleaned);
        boolean amPm = usesAmPm(cleaned);

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (!t.code) {
                out.append(t.value);
                continue;
            }
            String v = t.value;
            if (v.startsWith("[")) {
                String body = v.substring(1, v.length() - 1).toLowerCase(Locale.ROOT);
                long total;
                if (body.startsWith("h")) total = p.totalDays * 24 + p.hour;
                else if (body.startsWith("m")) total = p.totalDays * 1440 + p.hour * 60 + p.minute;
                else total = p.totalDays * 86400 + p.hour * 3600 + p.minute * 60 + p.second;
                out.append(pad(total, body.length()));
                continue;
            }
            if (v.equalsIgnoreCase("am/pm")) {
                out.append(p.hour < 12 ? "AM" : "PM");
                continue;
            }
            if (v.equalsIgnoreCase("a/p")) {
                out.append(p.hour < 12 ? "A" : "P");
                continue;
            }
            char c = t.codeChar();
            int run = v.length();
            switch (c) {
                case 'y':
                case 'e':
                    out.append(c == 'e' || run > 2 ? pad(p.year, 4) : pad(p.year % 100, 2));
                    break;
                case 'd':
                    if (run == 3) out.append(DAYS_SHORT[(p.dayOfWeek - 1) % 7]);
                    else if (run >= 4) out.append(DAYS_LONG[(p.dayOfWeek - 1) % 7]);
                    else out.append(pad(p.day, run));
                    break;
                case 'h':
                    out.append(pad(amPm ? (p.hour % 12 == 0 ? 12 : p.hour % 12) : p.hour, run));
                    break;
                case 's':
                    out.append(pad(p.second, run));
                    break;
                case 'm':
                    if (isMinutes(tokens, i)) out.append(pad(p.minute, run));
                    else if (run == 3) out.append(MONTHS_SHORT[(p.month - 1) % 12]);
                    else if (run >= 4) out.append(MONTHS_LONG[(p.month - 1) % 12]);
                    else out.append(pad(p.month, run));
                    break;
                default:
                    out.append(v);
                    break;
            }
        }
        return out.toString();
    }

    /**
     * Regle Excel : 'm' signifie minutes s'il suit un jeton 'h' ou s'il precede
     * un jeton 's' (les litteraux comme ":" sont ignores). Sinon c'est le mois.
     */
    private static boolean isMinutes(List<Token> tokens, int index) {
        for (int i = index - 1; i >= 0; i--) {
            Token t = tokens.get(i);
            if (!t.code) continue;
            char c = t.codeChar();
            if (c == 'h') return true;
            break;
        }
        for (int i = index + 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (!t.code) continue;
            return t.codeChar() == 's';
        }
        return false;
    }

    private static boolean usesAmPm(String cleaned) {
        String lower = cleaned.toLowerCase(Locale.ROOT);
        return lower.contains("am/pm") || lower.contains("a/p");
    }

    private static boolean startsWithIgnoreCase(String s, int from, String needle) {
        return s.regionMatches(true, from, needle, 0, needle.length());
    }

    /* ------------------------------------------------------------------ */
    /* Nombres                                                             */
    /* ------------------------------------------------------------------ */

    private static String formatNumeric(double value, String cleaned) {
        int first = -1;
        int last = -1;
        boolean quoted = false;
        int percentCount = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (quoted) continue;
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '0' || c == '#' || c == '?') {
                if (first < 0) first = i;
                last = i;
            } else if (c == '%') {
                percentCount++;
            }
        }
        if (first < 0) return literals(cleaned);

        // Le "coeur" numerique s'etend aux virgules d'echelle et aux '%' qui suivent
        // le dernier chiffre (ex. 0.00,,"M" ou 0.0%).
        int coreEnd = last;
        StringBuilder percentSuffix = new StringBuilder();
        while (coreEnd + 1 < cleaned.length()) {
            char c = cleaned.charAt(coreEnd + 1);
            if (c == ',') {
                coreEnd++;
            } else if (c == '%') {
                percentSuffix.append('%');
                coreEnd++;
            } else {
                break;
            }
        }
        String core = cleaned.substring(first, coreEnd + 1);
        String prefix = literals(cleaned.substring(0, first));
        String suffix = percentSuffix + literals(cleaned.substring(coreEnd + 1));

        double working = value;
        for (int i = 0; i < percentCount; i++) working *= 100d;

        // Virgules finales = echelle par milliers (0,, = millions).
        int scaleCommas = 0;
        while (core.endsWith(",")) {
            scaleCommas++;
            core = core.substring(0, core.length() - 1);
        }
        for (int i = 0; i < scaleCommas; i++) working /= 1000d;

        int dot = core.indexOf('.');
        String intMask = dot < 0 ? core : core.substring(0, dot);
        String decMask = dot < 0 ? "" : core.substring(dot + 1);
        boolean grouping = intMask.indexOf(',') >= 0;
        int decimals = 0;
        // '#' = decimale facultative, '0'/'?' = decimale obligatoire : sans cette
        // distinction le format "#.##" affichait ".50" au lieu de ".5".
        int minDecimals = 0;
        for (int i = 0; i < decMask.length(); i++) {
            char c = decMask.charAt(i);
            if (c == '0' || c == '#' || c == '?') decimals++;
            else if (c == 'E' || c == 'e') break;
            if (c == '0' || c == '?') minDecimals++;
        }
        int minInt = countChar(intMask, '0');

        String exponent = exponentMask(core);
        String number = exponent != null
                ? scientific(working, decimals, minDecimals, minInt, exponent)
                : decimal(working, decimals, minDecimals, minInt, grouping);
        return prefix + number + suffix;
    }

    private static String exponentMask(String core) {
        int e = core.indexOf('E');
        if (e < 0) e = core.indexOf('e');
        return e < 0 ? null : core.substring(e);
    }

    private static String scientific(double value, int decimals, int minDecimals, int minInt,
                                    String exponentMask) {
        int exp = 0;
        double mantissa = value;
        if (mantissa != 0) {
            exp = (int) Math.floor(Math.log10(Math.abs(mantissa)));
            BigDecimal rounded = BigDecimal.valueOf(mantissa / Math.pow(10, exp))
                    .setScale(Math.min(decimals, 15), RoundingMode.HALF_UP);
            if (rounded.abs().compareTo(BigDecimal.TEN) >= 0) {
                rounded = rounded.divide(BigDecimal.TEN, Math.min(decimals, 15), RoundingMode.HALF_UP);
                exp++;
            }
            mantissa = rounded.doubleValue();
        }
        String m = decimal(mantissa, decimals, minDecimals, Math.max(1, minInt), false);
        int expDigits = 0;
        for (int i = 1; i < exponentMask.length(); i++) {
            char c = exponentMask.charAt(i);
            if (c == '0' || c == '#') expDigits++;
        }
        String sign = exp < 0 ? "-" : (exponentMask.indexOf('+') >= 0 ? "+" : "");
        return m + "E" + sign + pad(Math.abs(exp), Math.max(2, expDigits));
    }

    private static String decimal(double value, int decimals, int minDecimals, int minInt,
                                 boolean grouping) {
        BigDecimal bd;
        try {
            bd = BigDecimal.valueOf(value).setScale(Math.min(decimals, 15), RoundingMode.HALF_UP);
        } catch (ArithmeticException e) {
            return general(value);
        }
        boolean negative = bd.signum() < 0;
        String plain = bd.abs().toPlainString();
        String intPart = plain;
        String fracPart = "";
        int dot = plain.indexOf('.');
        if (dot >= 0) {
            intPart = plain.substring(0, dot);
            fracPart = plain.substring(dot + 1);
        }
        // Les '#' finaux sont supprimes, sans descendre sous le minimum impose par les '0'.
        while (fracPart.length() > minDecimals && fracPart.endsWith("0")) {
            fracPart = fracPart.substring(0, fracPart.length() - 1);
        }
        while (intPart.length() < minInt) intPart = "0" + intPart;
        if (intPart.equals("0") && minInt == 0 && !fracPart.isEmpty()) intPart = "";
        if (grouping) intPart = group(intPart);
        StringBuilder sb = new StringBuilder();
        if (negative) sb.append('-');
        sb.append(intPart);
        if (!fracPart.isEmpty()) sb.append('.').append(fracPart);
        return sb.toString();
    }

    private static String group(String digits) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            if (++count % 3 == 0 && i > 0) sb.append(',');
        }
        return sb.reverse().toString();
    }

    /** Convertit les litteraux d'un masque en texte affichable. */
    static String literals(String mask) {
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < mask.length(); i++) {
            char c = mask.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                sb.append(c);
                continue;
            }
            if (c == '\\') {
                if (i + 1 < mask.length()) sb.append(mask.charAt(++i));
                continue;
            }
            if (c == '_' || c == '*') {
                if (c == '_') sb.append(' ');
                i++;
                continue;
            }
            if (c == '@') continue;
            sb.append(c);
        }
        return sb.toString();
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    private static String pad(long value, int width) {
        String s = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder();
        if (value < 0) sb.append('-');
        for (int i = s.length(); i < width; i++) sb.append('0');
        return sb.append(s).toString();
    }

    /** Format "General" d'Excel : 11 chiffres significatifs, sans zeros inutiles. */
    public static String general(double value) {
        if (value == 0) return "0";
        double abs = Math.abs(value);
        if (abs >= 1e11 || abs < 1e-9) {
            String s = String.format(Locale.US, "%.5E", value);
            int e = s.indexOf('E');
            String mantissa = s.substring(0, e);
            if (mantissa.indexOf('.') >= 0) {
                int end = mantissa.length();
                while (end > 0 && mantissa.charAt(end - 1) == '0') end--;
                if (end > 0 && mantissa.charAt(end - 1) == '.') end--;
                mantissa = mantissa.substring(0, end);
            }
            return mantissa + s.substring(e);
        }
        BigDecimal bd = new BigDecimal(Double.toString(value)).round(new MathContext(11, RoundingMode.HALF_UP));
        return bd.stripTrailingZeros().toPlainString();
    }
}
