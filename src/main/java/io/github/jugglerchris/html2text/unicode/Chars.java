// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)
//
// Java port of html2text: the character classes html2text takes from Rust's char and str (White_Space trimming and
// splitting) and its own WhitespaceExt and StrExt helpers.

package io.github.jugglerchris.html2text.unicode;

import java.util.ArrayList;
import java.util.List;

/** Rust's whitespace semantics (the Unicode White_Space property) and html2text's wrapping classes. */
public final class Chars {
    private Chars() {}

    /** {@code char::is_whitespace}: the Unicode White_Space property. */
    public static boolean isWhitespace(int c) {
        return (c >= 0x9 && c <= 0xD) || c == 0x20 || c == 0x85 || c == 0xA0 || c == 0x1680
                || (c >= 0x2000 && c <= 0x200A) || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F
                || c == 0x3000;
    }

    /** Whether the character always takes space: non-whitespace and the non-breaking space. */
    public static boolean alwaysTakesSpace(int c) {
        return c == 0xA0 || !isWhitespace(c);
    }

    /** Whether a word may end before this character: whitespace (but not NBSP) and the zero-width space. */
    public static boolean isWordbreakPoint(int c) {
        if (c == 0xA0) {
            return false;
        }
        return c == 0x200B || isWhitespace(c);
    }

    /** {@code str::trim}. */
    public static String trim(String s) {
        return s.substring(startOfContent(s), endOfContent(s));
    }

    /** {@code str::trim_start}. */
    public static String trimStart(String s) {
        return s.substring(startOfContent(s));
    }

    /** Trims leading and trailing whitespace except for hard spaces (html2text's trim_collapsible_ws). */
    public static String trimCollapsibleWs(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && !alwaysTakesSpace(s.codePointAt(start))) {
            start += Character.charCount(s.codePointAt(start));
        }
        while (end > start && !alwaysTakesSpace(s.codePointBefore(end))) {
            end -= Character.charCount(s.codePointBefore(end));
        }
        return s.substring(start, end);
    }

    /** Whether every character is whitespace (true for the empty string). */
    public static boolean allWhitespace(String s) {
        return s.codePoints().allMatch(Chars::isWhitespace);
    }

    /** {@code str::split_whitespace}: the non-empty runs between whitespace. */
    public static List<String> splitWhitespace(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && isWhitespace(s.codePointAt(i))) {
                i += Character.charCount(s.codePointAt(i));
            }
            int start = i;
            while (i < s.length() && !isWhitespace(s.codePointAt(i))) {
                i += Character.charCount(s.codePointAt(i));
            }
            if (i > start) {
                out.add(s.substring(start, i));
            }
        }
        return out;
    }

    private static int startOfContent(String s) {
        int i = 0;
        while (i < s.length() && isWhitespace(s.codePointAt(i))) {
            i += Character.charCount(s.codePointAt(i));
        }
        return i;
    }

    private static int endOfContent(String s) {
        int start = startOfContent(s);
        int end = s.length();
        while (end > start && isWhitespace(s.codePointBefore(end))) {
            end -= Character.charCount(s.codePointBefore(end));
        }
        return end;
    }

    /** The number of bytes of the UTF-8 encoding of a string ({@code str::len}). */
    public static int utf8Length(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            n += c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4;
            i += Character.charCount(c);
        }
        return n;
    }
}
