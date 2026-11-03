package io.github.jugglerchris.html2text.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Expected values are the output of serde_json 1.0.154 (tools/jsonoracle, see tools/jsonoracle/cases.py). */
class JsonTest {
    private static String error(String text) {
        return assertThrows(JsonException.class, () -> Json.parse(text), text).getMessage();
    }

    @Test
    void syntaxErrors() {
        String[][] cases = {
            {"", "EOF while parsing a value at line 1 column 0"},
            {"  ", "EOF while parsing a value at line 1 column 2"},
            {"nul", "EOF while parsing a value at line 1 column 3"},
            {"nulx", "expected ident at line 1 column 4"},
            {"[1,]", "trailing comma at line 1 column 4"},
            {"[1 2]", "expected `,` or `]` at line 1 column 4"},
            {"[,1]", "expected value at line 1 column 2"},
            {"{\"a\":1,}", "trailing comma at line 1 column 8"},
            {"{\"a\" 1}", "expected `:` at line 1 column 6"},
            {"{\"a\":1 \"b\":2}", "expected `,` or `}` at line 1 column 8"},
            {"{1:2}", "key must be a string at line 1 column 2"},
            {"{\"a\":1,\"b\"", "EOF while parsing an object at line 1 column 10"},
            {"]", "expected value at line 1 column 1"},
            {"{\"a\":1}x", "trailing characters at line 1 column 8"},
            {"1 2", "trailing characters at line 1 column 3"},
            {"-", "EOF while parsing a value at line 1 column 1"},
            {"01", "invalid number at line 1 column 2"},
            {"1.", "EOF while parsing a value at line 1 column 2"},
            {"1.e5", "invalid number at line 1 column 3"},
            {"1e+", "EOF while parsing a value at line 1 column 3"},
            {".5", "expected value at line 1 column 1"},
            {"+1", "expected value at line 1 column 1"},
            {"1e400", "number out of range at line 1 column 5"},
            {"-1e400", "number out of range at line 1 column 6"},
            {"1e2147483648", "number out of range at line 1 column 12"},
            {"\"abc", "EOF while parsing a string at line 1 column 4"},
            {"\"\\", "EOF while parsing a string at line 1 column 2"},
            {"\"\\u12", "EOF while parsing a string at line 1 column 5"},
            {"\"\\u12G4\"", "invalid escape at line 1 column 7"},
            {"\"\\x\"", "invalid escape at line 1 column 3"},
            {"\"\\ud800\"", "unexpected end of hex escape at line 1 column 8"},
            {"\"\\ud800x\"", "unexpected end of hex escape at line 1 column 8"},
            {"\"\\udc00\"", "lone leading surrogate in hex escape at line 1 column 7"},
            {"\"\\ud800\\u0041\"", "lone leading surrogate in hex escape at line 1 column 13"},
            {"\"a\u0001\"", "control character (\\u0000-\\u001F) found while parsing a string at line 1 column 3"},
            // Columns count UTF-8 bytes.
            {"\"\u00e9\" x", "trailing characters at line 1 column 6"},
            {"[\"\u4e2d\u6587\", nul]", "expected ident at line 1 column 15"},
            {"{\"\ud83d\ude00\":1,}", "trailing comma at line 1 column 11"},
            // Only space, tab, CR and LF are whitespace.
            {"\ufeff{}", "expected value at line 1 column 1"},
            {"\u00a01", "expected value at line 1 column 1"},
            {"\u000b1", "expected value at line 1 column 1"},
            {"[".repeat(127), "EOF while parsing a list at line 1 column 127"},
            {"[".repeat(128) + "]".repeat(128), "recursion limit exceeded at line 1 column 128"},
            {"{\"a\":".repeat(128) + "0" + "}".repeat(128), "recursion limit exceeded at line 1 column 636"},
            // Lines and columns after newlines.
            {"{\n\"a\":\n  nul}", "expected ident at line 3 column 6"},
            {"[1,\n2,\n]", "trailing comma at line 3 column 1"},
            {"\n\n", "EOF while parsing a value at line 3 column 0"},
            {"\"a\nb\"", "control character (\\u0000-\\u001F) found while parsing a string at line 2 column 0"},
            {"[\n", "EOF while parsing a list at line 2 column 0"},
        };
        for (String[] c : cases) {
            assertEquals(c[1], error(c[0]), c[0]);
        }
        JsonException e = assertThrows(JsonException.class, () -> Json.parse("[\n  x]"));
        assertEquals(2, e.line());
        assertEquals(3, e.column());
    }

    @Test
    void valuesRoundTrip() {
        String[][] cases = {
            {" [ 1 , \"x\" , true , false , null , {} , [] ] ", "[1,\"x\",true,false,null,{},[]]"},
            {"{\"a\":1,\"b\":2,\"a\":3}", "{\"a\":3,\"b\":2}"},
            {"{\"b\":1,\"a\":2,\"b\":null}", "{\"b\":null,\"a\":2}"},
            {"\"\\u0000\\u0001\\b\\t\\n\\u000b\\f\\r\\u001f \\\" \\\\ \\/ \\u007f \\u2028\\u2029 \u00e9 "
                + "\\ud83d\\ude00\"",
                "\"\\u0000\\u0001\\b\\t\\n\\u000b\\f\\r\\u001f \\\" \\\\ / \u007f \u2028\u2029 \u00e9 \ud83d\ude00\""},
            {"\"\u007f\u2028\u00e9\ud83d\ude00\"", "\"\u007f\u2028\u00e9\ud83d\ude00\""},
            {"[".repeat(127) + "]".repeat(127), "[".repeat(127) + "]".repeat(127)},
            {"\r\n\t1\t\r\n", "1"},
        };
        for (String[] c : cases) {
            assertEquals(c[1], Json.write(Json.parse(c[0])), c[0]);
        }
    }

    @Test
    void parsedTypes() {
        Object v = Json.parse("{\"n\":null,\"t\":true,\"s\":\"x\",\"a\":[1],\"o\":{}}");
        Map<?, ?> map = (Map<?, ?>) v;
        assertEquals(List.of("n", "t", "s", "a", "o"), new ArrayList<>(map.keySet()));
        assertSame(Json.NULL, map.get("n"));
        assertEquals(Boolean.TRUE, map.get("t"));
        assertEquals("x", map.get("s"));
        assertEquals(List.of(JsonNumber.ofU64(1)), map.get("a"));
        assertEquals(Map.of(), map.get("o"));
    }

    @Test
    void numbers() {
        // input, kind, as_u64, as_i64, as_f64 bits, Display
        String[][] cases = {
            {"0", "POS_INT", "0", "0", "0000000000000000", "0"},
            {"-0", "FLOAT", "-", "-", "8000000000000000", "-0.0"},
            {"-0.0", "FLOAT", "-", "-", "8000000000000000", "-0.0"},
            {"-1", "NEG_INT", "-", "-1", "bff0000000000000", "-1"},
            {"1.5", "FLOAT", "-", "-", "3ff8000000000000", "1.5"},
            {"1e20", "FLOAT", "-", "-", "4415af1d78b58c40", "1e+20"},
            {"100000000000000000000", "FLOAT", "-", "-", "4415af1d78b58c40", "1e+20"},
            {"1e15", "FLOAT", "-", "-", "430c6bf526340000", "1000000000000000.0"},
            {"1e16", "FLOAT", "-", "-", "4341c37937e08000", "1e+16"},
            {"1e-5", "FLOAT", "-", "-", "3ee4f8b588e368f1", "0.00001"},
            {"1e-6", "FLOAT", "-", "-", "3eb0c6f7a0b5ed8d", "1e-6"},
            {"1E+2", "FLOAT", "-", "-", "4059000000000000", "100.0"},
            {"18446744073709551615", "POS_INT", "18446744073709551615", "-", "43f0000000000000",
                "18446744073709551615"},
            {"18446744073709551616", "FLOAT", "-", "-", "43f0000000000000", "1.8446744073709552e+19"},
            {"9223372036854775808", "POS_INT", "9223372036854775808", "-", "43e0000000000000", "9223372036854775808"},
            {"-9223372036854775808", "NEG_INT", "-", "-9223372036854775808", "c3e0000000000000",
                "-9223372036854775808"},
            {"-9223372036854775809", "FLOAT", "-", "-", "c3e0000000000000", "-9.223372036854776e+18"},
            {"9007199254740993", "POS_INT", "9007199254740993", "9007199254740993", "4340000000000000",
                "9007199254740993"},
            {"1.7976931348623157e308", "FLOAT", "-", "-", "7fefffffffffffff", "1.7976931348623157e+308"},
            {"5e-324", "FLOAT", "-", "-", "0000000000000001", "5e-324"},
            {"2.2250738585072014e-308", "FLOAT", "-", "-", "0010000000000000", "2.2250738585072014e-308"},
            {"0.30000000000000004", "FLOAT", "-", "-", "3fd3333333333334", "0.30000000000000004"},
            {"0e999999999999", "FLOAT", "-", "-", "0000000000000000", "0.0"},
            {"-1e-400", "FLOAT", "-", "-", "8000000000000000", "-0.0"},
            {"123.456e-2", "FLOAT", "-", "-", "3ff3c0c1fc8f3238", "1.23456"},
            {"1e23", "FLOAT", "-", "-", "44b52d02c7e14af6", "1e+23"},
        };
        for (String[] c : cases) {
            JsonNumber n = (JsonNumber) Json.parse(c[0]);
            String u = n.asU64().isPresent() ? Long.toUnsignedString(n.asU64().getAsLong()) : "-";
            String i = n.asI64().isPresent() ? Long.toString(n.asI64().getAsLong()) : "-";
            String f = String.format("%016x", Double.doubleToRawLongBits(n.asF64()));
            assertEquals(Arrays.asList(c).subList(1, 6), List.of(n.kind().name(), u, i, f, n.toString()), c[0]);
            assertEquals(c[5], Json.write(n));
        }
        assertEquals(JsonNumber.ofF64(0.0), JsonNumber.ofF64(-0.0));
        assertEquals(JsonNumber.ofU64(5), JsonNumber.ofI64(5));
        assertEquals(JsonNumber.Kind.NEG_INT, JsonNumber.ofI64(-5).kind());
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.ofF64(Double.NaN));
    }

    @Test
    void floatFormatting() {
        // f64 bits and serde_json::to_string (zmij)
        String[][] cases = {
            {"0000000000000001", "5e-324"},
            {"0000000000000123", "1.44e-321"},
            {"000fffffffffffff", "2.225073858507201e-308"},
            {"7fefffffffffffff", "1.7976931348623157e+308"},
            {"3ff0000000000000", "1.0"},
            {"3c00000000000000", "1.0842021724855044e-19"},
            {"4340000000000000", "9007199254740992.0"},
            {"432fffffffffffff", "4503599627370495.5"},
            {"3f1a36e2eb1c432d", "0.0001"},
            {"c00921fb54442d18", "-3.141592653589793"},
            {"8000000000000000", "-0.0"},
            {"7ff0000000000000", "null"},
            {"7ff8000000000000", "null"},
        };
        for (String[] c : cases) {
            assertEquals(c[1], Json.write(Double.longBitsToDouble(Long.parseUnsignedLong(c[0], 16))), c[0]);
        }
    }

    @Test
    void writePlainJavaValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("z", 1);
        map.put("a", -2L);
        map.put("n", null);
        map.put("N", Json.NULL);
        map.put("b", false);
        map.put("l", Arrays.asList("q\"\\\u0000\u001f\u007f", List.of(), Map.of()));
        assertEquals("{\"z\":1,\"a\":-2,\"n\":null,\"N\":null,\"b\":false,"
                + "\"l\":[\"q\\\"\\\\\\u0000\\u001f\u007f\",[],{}]}", Json.write(map));
        assertEquals("null", Json.write(null));
        assertEquals("\"unknown op\"", Json.write("unknown op"));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
    }
}
