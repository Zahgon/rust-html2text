// Copyright (c) David Tolnay; ported in jugglerchris/rust-html2text. serde_json is licensed under the Apache
// License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <http://opensource.org/licenses/MIT>, at your option.
//
// Java port of serde_json 1.0.154's from_str::<Value> and to_string (ser.rs, compact formatter), with the
// preserve_order feature.

package io.github.jugglerchris.html2text.json;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes JSON exactly as serde_json (with {@code preserve_order}) does.
 *
 * <p>Parsed values are {@link #NULL}, {@link Boolean}, {@link JsonNumber}, {@link String}, {@code List<Object>} (an
 * {@code ArrayList}) and {@code Map<String, Object>} (a {@code LinkedHashMap}: members in the order of their first
 * occurrence, a repeated key keeping its first position and taking its last value).
 */
public final class Json {
    /**
     * JSON {@code null} in parsed values ({@code Value::Null}). A sentinel rather than Java {@code null}, so that a
     * member whose value is null is distinguishable from an absent member in {@code Map.get}.
     */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private static final String HEX = "0123456789abcdef";

    private Json() {}

    /**
     * Parses one JSON value followed by optional whitespace ({@code serde_json::from_str::<Value>}).
     *
     * @throws JsonException with serde_json's error message, e.g. {@code "expected ident at line 1 column 2"}
     */
    public static Object parse(String text) {
        return new Parser(text.getBytes(StandardCharsets.UTF_8)).parseDocument();
    }

    /**
     * Serialises a value compactly ({@code serde_json::to_string}, which is also {@code Value}'s Display). Accepts
     * the parsed value types and also Java {@code null} (as null), {@link Integer}, {@link Long}, {@link Double} (NaN
     * and infinities as null), any {@code Map} with string keys (in its iteration order) and any {@code List}.
     *
     * @throws IllegalArgumentException for any other type, or a map key that is not a string
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case Boolean b -> out.append(b.booleanValue());
            case String s -> writeString(out, s);
            case JsonNumber n -> out.append(n);
            case Integer n -> out.append(n.intValue());
            case Long n -> out.append(n.longValue());
            case Double d -> out.append(Double.isFinite(d) ? Zmij.format(d) : "null");
            case List<?> list -> {
                out.append('[');
                String separator = "";
                for (Object item : list) {
                    write(out.append(separator), item);
                    separator = ",";
                }
                out.append(']');
            }
            case Map<?, ?> map -> {
                out.append('{');
                String separator = "";
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!(e.getKey() instanceof String key)) {
                        throw new IllegalArgumentException("key must be a string");
                    }
                    writeString(out.append(separator), key);
                    write(out.append(':'), e.getValue());
                    separator = ",";
                }
                out.append('}');
            }
            default -> {
                if (value != NULL) {
                    throw new IllegalArgumentException("not a JSON value: " + value.getClass().getName());
                }
                out.append("null");
            }
        }
    }

    /** Writes a string, escaping only '"', '\\' and the control characters U+0000 to U+001F. */
    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 0xf));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
