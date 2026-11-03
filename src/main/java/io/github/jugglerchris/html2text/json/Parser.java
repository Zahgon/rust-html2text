// Copyright (c) David Tolnay; ported in jugglerchris/rust-html2text. serde_json is licensed under the Apache
// License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <http://opensource.org/licenses/MIT>, at your option.
//
// Java port of serde_json 1.0.154's Deserializer over a StrRead (de.rs, read.rs) as driven by
// serde_json::from_str::<Value>, with the preserve_order feature and without float_roundtrip, arbitrary_precision
// and unbounded_depth. Positions are byte offsets in the UTF-8 text, as in SliceRead.

package io.github.jugglerchris.html2text.json;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Parser {
    private static final String EOF_LIST = "EOF while parsing a list";
    private static final String EOF_OBJECT = "EOF while parsing an object";
    private static final String EOF_STRING = "EOF while parsing a string";
    private static final String EOF_VALUE = "EOF while parsing a value";
    private static final String EXPECTED_COLON = "expected `:`";
    private static final String EXPECTED_LIST_COMMA_OR_END = "expected `,` or `]`";
    private static final String EXPECTED_OBJECT_COMMA_OR_END = "expected `,` or `}`";
    private static final String EXPECTED_SOME_IDENT = "expected ident";
    private static final String EXPECTED_SOME_VALUE = "expected value";
    private static final String INVALID_ESCAPE = "invalid escape";
    private static final String INVALID_NUMBER = "invalid number";
    private static final String NUMBER_OUT_OF_RANGE = "number out of range";
    private static final String CONTROL_CHARACTER = "control character (\\u0000-\\u001F) found while parsing a string";
    private static final String KEY_MUST_BE_A_STRING = "key must be a string";
    private static final String LONE_LEADING_SURROGATE = "lone leading surrogate in hex escape";
    private static final String TRAILING_COMMA = "trailing comma";
    private static final String TRAILING_CHARACTERS = "trailing characters";
    private static final String UNEXPECTED_END_OF_HEX_ESCAPE = "unexpected end of hex escape";
    private static final String RECURSION_LIMIT_EXCEEDED = "recursion limit exceeded";

    /** u64::MAX / 10; u64::MAX % 10 is 5. */
    private static final long U64_MAX_DIV_10 = Long.divideUnsigned(-1L, 10);
    private static final double[] POW10 = new double[309];

    static {
        for (int i = 0; i < POW10.length; i++) {
            POW10[i] = Double.parseDouble("1e" + i);
        }
    }

    private final byte[] s;
    /** Index of the next byte to read. */
    private int i;
    private int remainingDepth = 128;

    Parser(byte[] s) {
        this.s = s;
    }

    /** {@code from_str}: one value, then only whitespace. */
    Object parseDocument() {
        Object value = parseValue();
        if (parseWhitespace() >= 0) {
            throw peekError(TRAILING_CHARACTERS);
        }
        return value;
    }

    private int peek() {
        return i < s.length ? s[i] & 0xff : -1;
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    /** An error caused by the byte just read. */
    private JsonException error(String code) {
        return errorAt(code, i);
    }

    /** An error caused by the byte about to be read. */
    private JsonException peekError(String code) {
        return errorAt(code, Math.min(s.length, i + 1));
    }

    private JsonException errorAt(String code, int index) {
        int start = index;
        while (start > 0 && s[start - 1] != '\n') {
            start--;
        }
        int line = 1;
        for (int k = 0; k < start; k++) {
            line += s[k] == '\n' ? 1 : 0;
        }
        return new JsonException(code, line, index - start);
    }

    /** Skips whitespace; returns the next byte (not consumed), or -1 at the end. */
    private int parseWhitespace() {
        for (int c; (c = peek()) >= 0; i++) {
            if (c != ' ' && c != '\n' && c != '\t' && c != '\r') {
                return c;
            }
        }
        return -1;
    }

    private Object parseValue() {
        int c = parseWhitespace();
        return switch (c) {
            case -1 -> throw peekError(EOF_VALUE);
            case 'n' -> parseIdent("ull", Json.NULL);
            case 't' -> parseIdent("rue", Boolean.TRUE);
            case 'f' -> parseIdent("alse", Boolean.FALSE);
            case '-' -> {
                i++;
                yield parseInteger(false);
            }
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseInteger(true);
            case '"' -> {
                i++;
                yield parseString();
            }
            case '[', '{' -> {
                if (--remainingDepth == 0) {
                    throw peekError(RECURSION_LIMIT_EXCEEDED);
                }
                i++;
                Object value = c == '[' ? parseArray() : parseObject();
                remainingDepth++;
                i++; // the closing bracket, which ended the loop above
                yield value;
            }
            default -> throw peekError(EXPECTED_SOME_VALUE);
        };
    }

    private Object parseIdent(String rest, Object value) {
        i++;
        for (int k = 0; k < rest.length(); k++) {
            if (i == s.length) {
                throw error(EOF_VALUE);
            }
            if (s[i++] != rest.charAt(k)) {
                throw error(EXPECTED_SOME_IDENT);
            }
        }
        return value;
    }

    /** After a separator: the next byte must start an element (or key). */
    private int afterComma(int c, int end, String separatorError) {
        if (c != ',') {
            throw peekError(separatorError);
        }
        i++;
        c = parseWhitespace();
        if (c == end) {
            throw peekError(TRAILING_COMMA);
        } else if (c < 0) {
            throw peekError(EOF_VALUE);
        }
        return c;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        for (boolean first = true; ; first = false) {
            int c = parseWhitespace();
            if (c < 0) {
                throw peekError(EOF_LIST);
            } else if (c == ']') {
                return list;
            } else if (!first) {
                afterComma(c, ']', EXPECTED_LIST_COMMA_OR_END);
            }
            list.add(parseValue());
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (boolean first = true; ; first = false) {
            int c = parseWhitespace();
            if (c < 0) {
                throw peekError(EOF_OBJECT);
            } else if (c == '}') {
                return map;
            } else if (!first) {
                c = afterComma(c, '}', EXPECTED_OBJECT_COMMA_OR_END);
            }
            if (c != '"') {
                throw peekError(KEY_MUST_BE_A_STRING);
            }
            i++;
            String key = parseString();
            c = parseWhitespace();
            if (c != ':') {
                throw peekError(c < 0 ? EOF_OBJECT : EXPECTED_COLON);
            }
            i++;
            map.put(key, parseValue());
        }
    }

    // Strings (read.rs).

    private String parseString() {
        StringBuilder scratch = null;
        int start = i;
        while (true) {
            while (i < s.length && s[i] != '"' && s[i] != '\\' && (s[i] < 0 || s[i] >= 0x20)) {
                i++;
            }
            if (i == s.length) {
                throw error(EOF_STRING);
            }
            String run = new String(s, start, i - start, StandardCharsets.UTF_8);
            byte b = s[i++];
            if (b == '"') {
                return scratch == null ? run : scratch.append(run).toString();
            } else if (b != '\\') {
                throw error(CONTROL_CHARACTER);
            }
            scratch = (scratch == null ? new StringBuilder() : scratch).append(run);
            parseEscape(scratch);
            start = i;
        }
    }

    private void parseEscape(StringBuilder scratch) {
        if (i == s.length) {
            throw error(EOF_STRING);
        }
        switch (s[i++]) {
            case '"' -> scratch.append('"');
            case '\\' -> scratch.append('\\');
            case '/' -> scratch.append('/');
            case 'b' -> scratch.append('\b');
            case 'f' -> scratch.append('\f');
            case 'n' -> scratch.append('\n');
            case 'r' -> scratch.append('\r');
            case 't' -> scratch.append('\t');
            case 'u' -> parseUnicodeEscape(scratch);
            default -> throw error(INVALID_ESCAPE);
        }
    }

    private void parseUnicodeEscape(StringBuilder scratch) {
        int n = decodeHexEscape();
        if (n >= 0xDC00 && n <= 0xDFFF) {
            throw error(LONE_LEADING_SURROGATE);
        }
        scratch.append((char) n);
        if (n < 0xD800 || n > 0xDBFF) {
            return;
        }
        for (char expected : new char[] {'\\', 'u'}) {
            if (i == s.length) {
                throw error(EOF_STRING);
            }
            if (s[i++] != expected) {
                throw error(UNEXPECTED_END_OF_HEX_ESCAPE);
            }
        }
        int n2 = decodeHexEscape();
        if (n2 < 0xDC00 || n2 > 0xDFFF) {
            throw error(LONE_LEADING_SURROGATE);
        }
        scratch.append((char) n2);
    }

    private int decodeHexEscape() {
        if (s.length - i < 4) {
            i = s.length;
            throw error(EOF_STRING);
        }
        int n = 0;
        for (int end = i + 4; i < end; i++) {
            int digit = Character.digit(s[i], 16); // s[i] is ASCII or negative
            n = n < 0 || digit < 0 ? -1 : n << 4 | digit;
        }
        if (n < 0) {
            throw error(INVALID_ESCAPE);
        }
        return n;
    }

    // Numbers (de.rs without float_roundtrip: the significand is kept in a u64 and scaled by powers of ten).

    private static boolean overflowsU64(long significand, int digit) {
        int cmp = Long.compareUnsigned(significand, U64_MAX_DIV_10);
        return cmp > 0 || cmp == 0 && digit > 5;
    }

    private JsonNumber parseInteger(boolean positive) {
        if (i == s.length) {
            throw error(EOF_VALUE);
        }
        int c = s[i++];
        if (c == '0') {
            if (isDigit(peek())) {
                throw peekError(INVALID_NUMBER);
            }
            return parseNumber(positive, 0);
        } else if (c < '1' || c > '9') {
            throw error(INVALID_NUMBER);
        }
        long significand = c - '0';
        for (int p; isDigit(p = peek()); i++) {
            if (overflowsU64(significand, p - '0')) {
                return JsonNumber.ofF64(parseLongInteger(positive, significand));
            }
            significand = significand * 10 + p - '0';
        }
        return parseNumber(positive, significand);
    }

    private JsonNumber parseNumber(boolean positive, long significand) {
        int p = peek();
        if (p == '.') {
            return JsonNumber.ofF64(parseDecimal(positive, significand, 0));
        } else if (p == 'e' || p == 'E') {
            return JsonNumber.ofF64(parseExponent(positive, significand, 0));
        } else if (positive) {
            return JsonNumber.ofU64(significand);
        }
        // A negative integer below i64::MIN, and -0, become floats.
        long neg = -significand;
        return neg >= 0 ? JsonNumber.ofF64(-JsonNumber.u64ToF64(significand)) : JsonNumber.ofI64(neg);
    }

    private double parseDecimal(boolean positive, long significand, int exponentBeforePoint) {
        i++;
        int exponentAfterPoint = 0;
        for (int p; isDigit(p = peek()); i++, exponentAfterPoint--) {
            if (overflowsU64(significand, p - '0')) {
                // Ignore all further digits.
                while (isDigit(peek())) {
                    i++;
                }
                return parseExponentOrFinish(positive, significand, exponentBeforePoint + exponentAfterPoint);
            }
            significand = significand * 10 + p - '0';
        }
        if (exponentAfterPoint == 0) {
            throw peekError(i < s.length ? INVALID_NUMBER : EOF_VALUE);
        }
        return parseExponentOrFinish(positive, significand, exponentBeforePoint + exponentAfterPoint);
    }

    private double parseExponentOrFinish(boolean positive, long significand, int exponent) {
        int p = peek();
        return p == 'e' || p == 'E' ? parseExponent(positive, significand, exponent)
                : f64FromParts(positive, significand, exponent);
    }

    private double parseExponent(boolean positive, long significand, int startingExp) {
        i++;
        int sign = peek();
        if (sign == '+' || sign == '-') {
            i++;
        }
        if (i == s.length) {
            throw error(EOF_VALUE);
        }
        int c = s[i++];
        if (!isDigit(c)) {
            throw error(INVALID_NUMBER);
        }
        int exp = c - '0';
        for (int p; isDigit(p = peek()); ) {
            i++;
            if (exp > Integer.MAX_VALUE / 10 || exp == Integer.MAX_VALUE / 10 && p - '0' > 7) {
                // Error instead of +/- infinity; zero for tiny numbers.
                if (significand != 0 && sign != '-') {
                    throw error(NUMBER_OUT_OF_RANGE);
                }
                while (isDigit(peek())) {
                    i++;
                }
                return positive ? 0.0 : -0.0;
            }
            exp = exp * 10 + p - '0';
        }
        long finalExp = sign == '-' ? (long) startingExp - exp : (long) startingExp + exp; // saturating in Rust
        return f64FromParts(positive, significand, Math.clamp(finalExp, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    private double f64FromParts(boolean positive, long significand, int exponent) {
        double f = JsonNumber.u64ToF64(significand);
        while (true) {
            int abs = Math.abs(exponent);
            if (abs >= 0 && abs < POW10.length) {
                if (exponent < 0) {
                    f /= POW10[abs];
                } else if (Double.isInfinite(f *= POW10[abs])) {
                    throw error(NUMBER_OUT_OF_RANGE);
                }
                break;
            } else if (f == 0) {
                break;
            } else if (exponent >= 0) {
                throw error(NUMBER_OUT_OF_RANGE);
            }
            f /= 1e308;
            exponent += 308;
        }
        return positive ? f : -f;
    }

    /** The integer part overflowed a u64: the remaining digits only scale the significand. */
    private double parseLongInteger(boolean positive, long significand) {
        int exponent = 0;
        while (isDigit(peek())) {
            i++;
            exponent++;
        }
        return peek() == '.' ? parseDecimal(positive, significand, exponent)
                : parseExponentOrFinish(positive, significand, exponent);
    }
}
