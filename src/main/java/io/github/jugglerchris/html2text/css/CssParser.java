// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)
//
// Java port of html2text's css/parser.rs: parsing for the subset of CSS used in html2text. The original is written
// with nom parser combinators; this port keeps their exact semantics (which alternative wins, what each parser
// consumes, where a failed parse leaves the input).

package io.github.jugglerchris.html2text.css;

import io.github.jugglerchris.html2text.Colour;
import io.github.jugglerchris.html2text.RustPanic;
import io.github.jugglerchris.html2text.WhiteSpace;
import io.github.jugglerchris.html2text.css.Selector.Component;
import io.github.jugglerchris.html2text.css.StyleData.StyleDecl;
import io.github.jugglerchris.html2text.unicode.Chars;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** A parser over one CSS source text. Each parse method returns null on failure, else sets {@link #pos}. */
final class CssParser {
    /** Token types of the CSS Syntax Module Level 3 that the tokenizer produces. */
    enum T {
        IDENT, FUNCTION, HASH, STRING, BAD_STRING, DELIM, NUMBER, DIMENSION, PERCENTAGE, CDO, CDC, COLON,
        SEMICOLON, COMMA, OPEN_SQUARE, CLOSE_SQUARE, OPEN_ROUND, CLOSE_ROUND, OPEN_BRACE, CLOSE_BRACE
    }

    /** A token: its type, its text (name, string, number or delimiter) and the unit of a dimension. */
    record Token(T type, String text, String unit) {}

    /** The properties html2text understands. */
    enum Prop { COLOR, BACKGROUND_COLOR, HEIGHT, MAX_HEIGHT, OVERFLOW, OVERFLOW_Y, DISPLAY, WHITE_SPACE, CONTENT, UNKNOWN }

    /**
     * A parsed declaration: the property and its value (a Colour, a Float length, an overflow keyword, a Boolean
     * "display: none", a WhiteSpace or a content string).
     */
    record Declaration(Prop prop, Object value, boolean important) {}

    /** A ruleset: its selectors and declarations. */
    record Rule(List<Selector> selectors, List<Declaration> declarations) {}

    private record RawValue(List<Token> tokens, boolean important) {}

    private static final Map<String, Colour> NAMED_COLOURS = Map.ofEntries(
            Map.entry("aqua", new Colour(0, 0xff, 0xff)), Map.entry("black", new Colour(0, 0, 0)),
            Map.entry("blue", new Colour(0, 0, 0xff)), Map.entry("fuchsia", new Colour(0xff, 0, 0xff)),
            Map.entry("gray", new Colour(0x80, 0x80, 0x80)), Map.entry("green", new Colour(0, 0x80, 0)),
            Map.entry("lime", new Colour(0, 0xff, 0)), Map.entry("maroon", new Colour(0x80, 0, 0)),
            Map.entry("navy", new Colour(0, 0, 0x80)), Map.entry("olive", new Colour(0x80, 0x80, 0)),
            Map.entry("orange", new Colour(0xff, 0xa5, 0)), Map.entry("purple", new Colour(0x80, 0, 0x80)),
            Map.entry("red", new Colour(0xff, 0, 0)), Map.entry("silver", new Colour(0xc0, 0xc0, 0xc0)),
            Map.entry("teal", new Colour(0, 0x80, 0x80)), Map.entry("white", new Colour(0xff, 0xff, 0xff)),
            Map.entry("yellow", new Colour(0xff, 0xff, 0)));

    private final String s;
    private int pos;
    private float number;

    CssParser(String s) {
        this.s = s;
    }

    private boolean at(int i, String t) {
        return s.startsWith(t, i);
    }

    private static boolean isHex(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isAlpha(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static int lower(int c) {
        return c >= 'A' && c <= 'Z' ? c + 32 : c;
    }

    private int digits(int i) {
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
        }
        return i;
    }

    private int comment(int i) {
        if (!at(i, "/*")) {
            return -1;
        }
        int end = s.indexOf("*/", i + 2);
        return end < 0 ? -1 : end + 2;
    }

    private int whitespaceItem(int i) {
        if (i < s.length() && " \t\r\n\f".indexOf(s.charAt(i)) >= 0) {
            return i + 1;
        }
        return comment(i);
    }

    private int skipWs(int i) {
        for (int j = whitespaceItem(i); j >= 0; j = whitespaceItem(i)) {
            i = j;
        }
        return i;
    }

    /** An identifier escape: the code point, or -1. */
    private int identEscape(int i) {
        if (!at(i, "\\")) {
            return -1;
        }
        int rest = i + 1;
        if (rest >= s.length()) {
            pos = rest;
            return 0xFFFD;
        }
        int c = s.codePointAt(rest);
        if (isHex(c)) {
            int end = rest + 1;
            for (int j = rest + 1; j < s.length(); ) {
                int next = s.codePointAt(j);
                if (isHex(next) && j - rest < 6) {
                    j += Character.charCount(next);
                } else {
                    end = j;
                    break;
                }
            }
            int val = Integer.parseInt(s, rest, end, 16);
            pos = end;
            return val > 0x10FFFF || (val >= 0xD800 && val <= 0xDFFF) ? 0xFFFD : val;
        }
        pos = rest + Character.charCount(c);
        return c;
    }

    private int nmstart(int i) {
        if (i < s.length() && (s.charAt(i) == '_' || isAlpha(s.charAt(i)))) {
            pos = i + 1;
            return lower(s.charAt(i));
        }
        return identEscape(i);
    }

    private int nmchar(int i) {
        if (i < s.length()) {
            char c = s.charAt(i);
            if (c == '_' || c == '-' || isAlpha(c) || (c >= '0' && c <= '9')) {
                pos = i + 1;
                return lower(c);
            }
        }
        return identEscape(i);
    }

    private String ident(int i) {
        i = skipWs(i);
        StringBuilder name = new StringBuilder();
        if (at(i, "-")) {
            name.append('-');
            i++;
        }
        int c = nmstart(i);
        if (c < 0) {
            return null;
        }
        name.appendCodePoint(c);
        i = pos;
        for (c = nmchar(i); c >= 0; c = nmchar(i)) {
            name.appendCodePoint(c);
            i = pos;
        }
        pos = i;
        return name.toString();
    }

    private String identString(int i) {
        i = skipWs(i);
        StringBuilder name = new StringBuilder();
        for (int c = nmchar(i); c >= 0; c = nmchar(i)) {
            name.appendCodePoint(c);
            i = pos;
        }
        pos = i;
        return name.isEmpty() ? null : name.toString();
    }

    private Token simple(T type, int end, String text) {
        pos = end;
        return new Token(type, text, null);
    }

    private Token delim(int i) {
        int c = s.codePointAt(i);
        return simple(T.DELIM, i + Character.charCount(c), new String(Character.toChars(c)));
    }

    private Token token(int i) {
        i = skipWs(i);
        if (i >= s.length()) {
            return null;
        }
        Token t;
        switch (s.charAt(i)) {
            case '"', '\'' -> {
                return stringToken(i);
            }
            case '#' -> {
                String id = identString(i + 1);
                return id != null ? new Token(T.HASH, id, null) : delim(i);
            }
            case ';' -> {
                return simple(T.SEMICOLON, i + 1, null);
            }
            case '(' -> {
                return simple(T.OPEN_ROUND, i + 1, null);
            }
            case ')' -> {
                return simple(T.CLOSE_ROUND, i + 1, null);
            }
            case '+' -> {
                t = numericToken(i + 1);
                return t != null ? t : delim(i);
            }
            case ',' -> {
                return simple(T.COMMA, i + 1, null);
            }
            case '-' -> {
                if ((t = numericToken(i)) != null) {
                    return t;
                }
                if (at(i, "-->")) {
                    return simple(T.CDC, i + 3, null);
                }
                t = identLike(i);
                return t != null ? t : delim(i);
            }
            case '.' -> {
                t = numericToken(i);
                return t != null ? t : delim(i);
            }
            case ':' -> {
                return simple(T.COLON, i + 1, null);
            }
            case '<' -> {
                return at(i, "<!--") ? simple(T.CDO, i + 4, null) : delim(i);
            }
            case '[' -> {
                return simple(T.OPEN_SQUARE, i + 1, null);
            }
            case '\\' -> {
                t = identLike(i);
                return t != null ? t : delim(i);
            }
            case ']' -> {
                return simple(T.CLOSE_SQUARE, i + 1, null);
            }
            case '{' -> {
                return simple(T.OPEN_BRACE, i + 1, null);
            }
            case '}' -> {
                return simple(T.CLOSE_BRACE, i + 1, null);
            }
            default -> {
                int c = s.codePointAt(i);
                if (isAlpha(c) || c == '_' || c >= 0x81) {
                    return identLike(i);
                }
                if (c >= '0' && c <= '9') {
                    return numericToken(i);
                }
                return delim(i);
            }
        }
    }

    /** parse_number: the end of the number, or -1; its value is left in {@link #number}. */
    private int number(int i) {
        i = skipWs(i);
        boolean negative = at(i, "-");
        if (negative || at(i, "+")) {
            i++;
        }
        int end = digits(i);
        if (end == i) {
            if (!at(i, ".")) {
                return -1;
            }
            end = digits(i + 1);
            if (end == i + 1) {
                return -1;
            }
        }
        float value = Float.parseFloat(s.substring(i, end));
        number = negative ? -value : value;
        return end;
    }

    private Token numericToken(int i) {
        int end = number(i);
        if (end < 0) {
            return null;
        }
        String num = s.substring(i, end);
        if (at(end, "%")) {
            return simple(T.PERCENTAGE, end + 1, num);
        }
        String dim = ident(end);
        if (dim != null) {
            return new Token(T.DIMENSION, num, dim);
        }
        return simple(T.NUMBER, end, num);
    }

    private Token identLike(int i) {
        String id = ident(i);
        if (id == null) {
            return null;
        }
        return at(pos, "(") ? simple(T.FUNCTION, pos + 1, id) : new Token(T.IDENT, id, null);
    }

    private Token stringToken(int i) {
        int end = s.charAt(i);
        StringBuilder b = new StringBuilder();
        for (int j = i + 1; ; ) {
            if (j >= s.length()) {
                return simple(T.STRING, j, b.toString());
            }
            int c = s.codePointAt(j);
            if (c == end) {
                return simple(T.STRING, j + 1, b.toString());
            } else if (c == '\n') {
                return simple(T.BAD_STRING, j, b.toString());
            } else if (c == '\\') {
                if (j + 1 >= s.length()) {
                    return simple(T.STRING, j + 1, b.toString());
                }
                int escaped = s.codePointAt(j + 1);
                if (escaped != '\n') {
                    b.appendCodePoint(escaped);
                }
                j += 1 + Character.charCount(escaped);
            } else {
                b.appendCodePoint(c);
                j += Character.charCount(c);
            }
        }
    }

    /** parse_quoted_string: a string token that is not a bad string; panics (as the original) at end of input. */
    private String quotedString(int i) {
        if (i >= s.length()) {
            throw new RustPanic(RustPanic.UNWRAP_NONE);
        }
        if (s.charAt(i) != '"' && s.charAt(i) != '\'') {
            return null;
        }
        Token t = stringToken(i);
        return t.type() == T.STRING ? t.text() : null;
    }

    /** parse_to_end_of_statement: the end of the statement, or -1 for unbalanced brackets. */
    private int toEndOfStatement(int i, List<Token> out, boolean includeSemicolon) {
        Deque<T> brackets = new ArrayDeque<>();
        int rest = i;
        while (true) {
            Token tok = token(rest);
            if (tok == null) {
                return rest;
            }
            int remain = pos;
            switch (tok.type()) {
                case FUNCTION, OPEN_ROUND -> brackets.push(T.CLOSE_ROUND);
                case CDO -> brackets.push(T.CDC);
                case OPEN_SQUARE -> brackets.push(T.CLOSE_SQUARE);
                case OPEN_BRACE -> brackets.push(T.CLOSE_BRACE);
                case SEMICOLON -> {
                    if (brackets.isEmpty()) {
                        if (!includeSemicolon) {
                            return rest;
                        }
                        add(out, tok);
                        return remain;
                    }
                }
                case CDC, CLOSE_SQUARE, CLOSE_ROUND, CLOSE_BRACE -> {
                    if (tok.type() == T.CLOSE_BRACE && brackets.isEmpty()) {
                        return rest;
                    }
                    if (brackets.peek() != tok.type()) {
                        return -1;
                    }
                    brackets.pop();
                    if (tok.type() == T.CLOSE_BRACE && brackets.isEmpty()) {
                        add(out, tok);
                        return remain;
                    }
                }
                default -> {}
            }
            add(out, tok);
            rest = remain;
        }
    }

    private static void add(List<Token> out, Token tok) {
        if (out != null) {
            out.add(tok);
        }
    }

    private RawValue value(int i) {
        List<Token> tokens = new ArrayList<>();
        int end = toEndOfStatement(i, tokens, false);
        if (end < 0) {
            return null;
        }
        boolean important = false;
        int n = tokens.size();
        if (n >= 2 && tokens.get(n - 2).equals(new Token(T.DELIM, "!", null))
                && tokens.get(n - 1).type() == T.IDENT && tokens.get(n - 1).text().equals("important")) {
            tokens.subList(n - 2, n).clear();
            important = true;
        }
        pos = end;
        return new RawValue(tokens, important);
    }

    private Declaration declaration(int i) {
        String prop = ident(i);
        if (prop == null) {
            return null;
        }
        int j = skipWs(pos);
        if (!at(j, ":")) {
            return null;
        }
        RawValue v = value(skipWs(j + 1));
        if (v == null) {
            return null;
        }
        Object value = null;
        Prop kind = switch (prop) {
            case "background-color" -> (value = parseColor(v.tokens())) != null ? Prop.BACKGROUND_COLOR : Prop.UNKNOWN;
            case "background" -> (value = parseBackgroundColor(v.tokens())) != null
                    ? Prop.BACKGROUND_COLOR : Prop.UNKNOWN;
            case "color" -> (value = parseColor(v.tokens())) != null ? Prop.COLOR : Prop.UNKNOWN;
            case "height" -> (value = parseHeight(v.tokens())) != null ? Prop.HEIGHT : Prop.UNKNOWN;
            case "max-height" -> (value = parseHeight(v.tokens())) != null ? Prop.MAX_HEIGHT : Prop.UNKNOWN;
            case "overflow" -> (value = parseOverflow(v.tokens())) != null ? Prop.OVERFLOW : Prop.UNKNOWN;
            case "overflow-y" -> (value = parseOverflow(v.tokens())) != null ? Prop.OVERFLOW_Y : Prop.UNKNOWN;
            case "display" -> {
                value = v.tokens().stream().anyMatch(t -> t.type() == T.IDENT && t.text().equals("none"));
                yield Prop.DISPLAY;
            }
            case "white-space" -> {
                value = parseWhiteSpace(v.tokens());
                yield Prop.WHITE_SPACE;
            }
            case "content" -> (value = parseContent(v.tokens())) != null ? Prop.CONTENT : Prop.UNKNOWN;
            default -> Prop.UNKNOWN;
        };
        return new Declaration(kind, value, v.important());
    }

    private static Colour parseColor(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return null;
        }
        Token first = tokens.get(0);
        int n = tokens.size();
        if (n == 1 && first.type() == T.IDENT) {
            return NAMED_COLOURS.get(first.text());
        }
        if (n >= 2 && first.type() == T.FUNCTION && tokens.get(n - 1).type() == T.CLOSE_ROUND) {
            if (!first.text().equals("rgb") || n != 7) {
                return null;
            }
            T[] shape = {T.NUMBER, T.COMMA, T.NUMBER, T.COMMA, T.NUMBER};
            for (int i = 0; i < 5; i++) {
                if (tokens.get(i + 1).type() != shape[i]) {
                    return null;
                }
            }
            long r = parseUnsigned(tokens.get(1).text(), 10, 255);
            long g = parseUnsigned(tokens.get(3).text(), 10, 255);
            long b = parseUnsigned(tokens.get(5).text(), 10, 255);
            return r < 0 || g < 0 || b < 0 ? null : new Colour((int) r, (int) g, (int) b);
        }
        if (n == 1 && first.type() == T.HASH) {
            String h = first.text();
            int len = Chars.utf8Length(h);
            if (len != 3 && len != 6) {
                return null;
            }
            long v = parseUnsigned(h, 16, 0xFFFFFFFFL);
            if (v < 0) {
                return null;
            }
            if (len == 3) {
                return new Colour((int) ((v >> 8) & 0xf) * 0x11, (int) ((v >> 4) & 0xf) * 0x11,
                        (int) (v & 0xf) * 0x11);
            }
            return new Colour((int) ((v >> 16) & 0xff), (int) ((v >> 8) & 0xff), (int) (v & 0xff));
        }
        return null;
    }

    /** Rust's unsigned {@code from_str_radix}: an optional '+', then digits; -1 when invalid or above max. */
    private static long parseUnsigned(String text, int radix, long max) {
        int start = text.startsWith("+") ? 1 : 0;
        if (start == text.length()) {
            return -1;
        }
        long v = 0;
        for (int i = start; i < text.length(); i++) {
            int d = Character.digit(text.charAt(i), radix);
            if (d < 0 || text.charAt(i) > 'z') {
                return -1;
            }
            v = v * radix + d;
            if (v > max) {
                return -1;
            }
        }
        return v;
    }

    private static Colour parseBackgroundColor(List<Token> tokens) {
        int last = tokens.lastIndexOf(new Token(T.COMMA, null, null));
        return parseColor(tokens.subList(last + 1, tokens.size()));
    }

    /** parse_height: the length (any supported unit, or a unitless 0), or null. */
    private static Float parseHeight(List<Token> tokens) {
        if (tokens.size() != 1) {
            return null;
        }
        Token t = tokens.get(0);
        if (t.type() == T.DIMENSION) {
            CssParser p = new CssParser(t.text());
            if (p.number(0) < 0) {
                return null;
            }
            return switch (t.unit()) {
                case "in", "cm", "mm", "pt", "pc", "px", "em", "ex" -> p.number;
                default -> null;
            };
        } else if (t.type() == T.NUMBER) {
            CssParser p = new CssParser(t.text());
            return p.number(0) >= 0 && p.number == 0.0f ? 0.0f : null;
        }
        return null;
    }

    private static String parseOverflow(List<Token> tokens) {
        for (Token t : tokens) {
            if (t.type() == T.IDENT && List.of("visible", "hidden", "scroll", "auto").contains(t.text())) {
                return t.text();
            }
        }
        return null;
    }

    private static WhiteSpace parseWhiteSpace(List<Token> tokens) {
        for (Token t : tokens) {
            if (t.type() == T.IDENT) {
                switch (t.text()) {
                    case "normal" -> {
                        return WhiteSpace.NORMAL;
                    }
                    case "pre" -> {
                        return WhiteSpace.PRE;
                    }
                    case "pre-wrap" -> {
                        return WhiteSpace.PRE_WRAP;
                    }
                    default -> {}
                }
            }
        }
        return WhiteSpace.NORMAL;
    }

    private static String parseContent(List<Token> tokens) {
        StringBuilder out = new StringBuilder();
        for (Token t : tokens) {
            if (t.type() != T.STRING) {
                return null;
            }
            out.append(t.text());
        }
        return out.toString();
    }

    /** parse_rules: declarations separated by semicolons. */
    List<Declaration> rules(int i) {
        List<Declaration> out = new ArrayList<>();
        Declaration d = declaration(i);
        if (d == null) {
            pos = i;
            return out;
        }
        out.add(d);
        i = pos;
        while (at(i, ";")) {
            d = declaration(skipWs(i + 1));
            if (d == null) {
                break;
            }
            out.add(d);
            i = pos;
        }
        pos = i;
        return out;
    }

    private Selector.Attr attr(int i) {
        if (!at(i, "[")) {
            return null;
        }
        String name = ident(i + 1);
        if (name == null) {
            return null;
        }
        int j = pos;
        if (at(j, "]")) {
            pos = j + 1;
            return new Selector.Attr(name, null);
        }
        if (!at(j, "=")) {
            return null;
        }
        String value = quotedString(j + 1);
        if (value == null || !at(pos, "]")) {
            value = ident(j + 1);
            if (value == null || !at(pos, "]")) {
                return null;
            }
        }
        pos++;
        return new Selector.Attr(name, value);
    }

    private static int parseI32(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw RustPanic.unwrapErr("ParseIntError { kind: PosOverflow }");
        }
    }

    private Component nthChildArgs(int i) {
        if (!at(i, "(")) {
            return null;
        }
        i = skipWs(i + 1);
        int a;
        int b;
        int end;
        if (at(i, "even")) {
            a = 2;
            b = 0;
            end = i + 4;
        } else if (at(i, "odd")) {
            a = 2;
            b = 1;
            end = i + 3;
        } else {
            int k = i;
            int aSign = at(k, "-") ? -1 : 1;
            if (at(k, "-") || at(k, "+")) {
                k++;
            }
            int digitsEnd = digits(k);
            String aDigits = digitsEnd > k ? s.substring(k, digitsEnd) : "1";
            if (at(digitsEnd, "n")) {
                int m = skipWs(digitsEnd + 1);
                int bEnd = at(m, "-") || at(m, "+") ? digits(m + 1) : m + 1;
                a = parseI32(aDigits) * aSign;
                if (bEnd > m + 1) {
                    b = parseI32(s.substring(m + 1, bEnd)) * (at(m, "-") ? -1 : 1);
                    end = bEnd;
                } else {
                    b = 0;
                    end = digitsEnd + 1;
                }
            } else if (digitsEnd > k) {
                a = 0;
                b = parseI32(aDigits) * aSign;
                end = digitsEnd;
            } else {
                return null;
            }
        }
        end = skipWs(end);
        if (!at(end, ")")) {
            return null;
        }
        pos = end + 1;
        return new Selector.NthChild(a, b, new Selector(List.of(new Selector.Star()), null));
    }

    private Component simpleComponent(int i) {
        int j = skipWs(i);
        if (at(j, ">")) {
            pos = skipWs(j + 1);
            return new Selector.CombChild();
        }
        if (at(j, "*")) {
            pos = skipWs(j + 1);
            return new Selector.Star();
        }
        if (j > i) {
            pos = j;
            return new Selector.CombDescendant();
        }
        if (at(i, ".")) {
            String name = ident(i + 1);
            if (name != null) {
                return new Selector.ClassName(name);
            }
        }
        Component c = attr(i);
        if (c != null) {
            return c;
        }
        if (at(i, "#")) {
            String id = identString(i + 1);
            if (id != null) {
                return new Selector.Hash(id);
            }
        }
        String element = ident(i);
        if (element != null) {
            return new Selector.Element(element);
        }
        if (at(i, ":")) {
            String pseudo = ident(i + 1);
            if (pseudo != null && pseudo.equals("nth-child")) {
                return nthChildArgs(pos);
            }
        }
        return null;
    }

    Selector selector(int i) {
        List<Component> components = new ArrayList<>();
        String element = ident(i);
        int j = i;
        if (element != null) {
            components.add(new Selector.Element(element));
            j = pos;
        }
        for (Component c = simpleComponent(j); c != null; c = simpleComponent(j)) {
            components.add(c);
            j = pos;
        }
        if (components.isEmpty()) {
            return null;
        }
        if (components.get(components.size() - 1) instanceof Selector.CombDescendant) {
            components.remove(components.size() - 1);
        }
        Collections.reverse(components);
        if (!components.isEmpty() && components.get(components.size() - 1) instanceof Selector.CombDescendant) {
            components.remove(components.size() - 1);
        }
        String pseudo = null;
        if (at(j, "::before")) {
            pseudo = "before";
            j += 8;
        } else if (at(j, "::after")) {
            pseudo = "after";
            j += 7;
        }
        pos = j;
        return new Selector(List.copyOf(components), pseudo);
    }

    private Rule ruleset(int i) {
        i = skipWs(i);
        List<Selector> selectors = new ArrayList<>();
        Selector sel = selector(i);
        if (sel != null) {
            selectors.add(sel);
            i = pos;
            while (at(i, ",")) {
                sel = selector(skipWs(i + 1));
                if (sel == null) {
                    break;
                }
                selectors.add(sel);
                i = pos;
            }
        }
        i = skipWs(i);
        if (!at(i, "{")) {
            return null;
        }
        List<Declaration> declarations = rules(skipWs(i + 1));
        i = skipWs(pos);
        if (at(i, ";")) {
            i = skipWs(i + 1);
        }
        if (!at(i, "}")) {
            return null;
        }
        pos = skipWs(i + 1);
        return new Rule(selectors, declarations);
    }

    private boolean atRule(int i) {
        int j = skipWs(i);
        if (!at(j, "@") || ident(skipWs(j + 1)) == null) {
            return false;
        }
        int end = toEndOfStatement(pos, null, true);
        pos = end;
        return end >= 0;
    }

    /** parse_stylesheet: the rulesets of the statements that parse (at-rules are skipped). */
    List<Rule> stylesheet() {
        List<Rule> out = new ArrayList<>();
        int i = 0;
        while (true) {
            Rule rule = ruleset(i);
            if (rule != null) {
                out.add(rule);
                i = pos;
            } else if (atRule(i)) {
                i = pos;
            } else {
                return out;
            }
        }
    }

    /** The styles html2text uses from a list of declarations. */
    static List<StyleDecl> stylesFromProperties(List<Declaration> decls) {
        List<StyleDecl> styles = new ArrayList<>();
        boolean overflowHidden = false;
        boolean heightZero = false;
        for (Declaration d : decls) {
            switch (d.prop()) {
                case COLOR -> styles.add(new StyleDecl(new StyleData.Foreground((Colour) d.value()), d.important()));
                case BACKGROUND_COLOR -> styles.add(
                        new StyleDecl(new StyleData.Background((Colour) d.value()), d.important()));
                case HEIGHT, MAX_HEIGHT -> heightZero |= (Float) d.value() == 0.0f;
                case OVERFLOW, OVERFLOW_Y -> overflowHidden |= d.value().equals("hidden");
                case DISPLAY -> {
                    if ((Boolean) d.value()) {
                        styles.add(new StyleDecl(new StyleData.DisplayNone(), d.important()));
                    }
                }
                case WHITE_SPACE -> styles.add(
                        new StyleDecl(new StyleData.WhiteSpaceStyle((WhiteSpace) d.value()), d.important()));
                case CONTENT -> styles.add(new StyleDecl(new StyleData.Content((String) d.value()), d.important()));
                case UNKNOWN -> {}
            }
        }
        if (heightZero && overflowHidden) {
            styles.add(new StyleDecl(new StyleData.DisplayNone(), false));
        }
        return styles;
    }

    /** The styles of an inline {@code style} attribute. */
    static List<StyleDecl> parseStyleAttribute(String text) {
        return stylesFromProperties(new CssParser(text).rules(0));
    }

    /** A {@code color}/{@code bgcolor} attribute value, also accepting a bare "rrggbb"; null if invalid. */
    static Colour parseColorAttribute(String text) {
        RawValue v = new CssParser(text).value(0);
        if (v == null) {
            return null;
        }
        Colour colour = parseColor(v.tokens());
        if (colour != null) {
            return colour;
        }
        byte[] bytes = Chars.trim(text).getBytes(StandardCharsets.UTF_8);
        long r = colourPart(bytes, 0);
        long g = colourPart(bytes, 2);
        long b = colourPart(bytes, 4);
        return r < 0 || g < 0 || b < 0 ? null : new Colour((int) r, (int) g, (int) b);
    }

    private static long colourPart(byte[] bytes, int start) {
        if (start + 2 > bytes.length || bytes[start] < 0 || bytes[start + 1] < 0) {
            return -1;
        }
        return parseUnsigned(new String(bytes, start, 2, StandardCharsets.US_ASCII), 16, 255);
    }
}
