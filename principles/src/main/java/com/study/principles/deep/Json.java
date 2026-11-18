package com.study.principles.deep;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON for flat objects with string and integer values — just enough for the kata,
 * so the module does not need Jackson. Not a general-purpose parser.
 */
public final class Json {

    private Json() {
    }

    /** Serializes a flat object; values must be {@link String} or {@link Number}. Strings are escaped. */
    public static String object(Map<String, ?> fields) {
        StringBuilder sb = new StringBuilder("{");
        fields.forEach((name, value) -> {
            if (sb.length() > 1) sb.append(',');
            sb.append(quote(name)).append(':');
            sb.append(value instanceof Number ? value.toString() : quote(String.valueOf(value)));
        });
        return sb.append('}').toString();
    }

    /**
     * Parses a flat object of string / integer values; numbers come back as their decimal text.
     *
     * @throws IllegalArgumentException if the text is not such an object
     */
    public static Map<String, String> parse(String json) {
        if (json == null) throw new IllegalArgumentException("no JSON");
        return new Parser(json).object();
    }

    /** Lenient lookup: {@code null} when the text is missing, malformed or has no such field. */
    public static String field(String json, String name) {
        try {
            return parse(json).get(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        Map<String, String> object() {
            Map<String, String> result = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
            } else {
                while (true) {
                    skipWs();
                    String name = string();
                    skipWs();
                    expect(':');
                    skipWs();
                    result.put(name, peek() == '"' ? string() : number());
                    skipWs();
                    char c = next();
                    if (c == '}') break;
                    if (c != ',') throw error("expected ',' or '}'");
                }
            }
            skipWs();
            if (pos != s.length()) throw error("trailing characters");
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c < 0x20) throw error("unescaped control character");
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw error("bad \\u escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("bad escape \\" + e);
                }
            }
        }

        private String number() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            if (pos == start || (pos == start + 1 && s.charAt(start) == '-')) throw error("expected a value");
            return s.substring(start, pos);
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private char peek() {
            if (pos >= s.length()) throw error("unexpected end");
            return s.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw error("expected '" + c + "'");
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException(what + " at " + pos + " in " + s);
        }
    }
}
