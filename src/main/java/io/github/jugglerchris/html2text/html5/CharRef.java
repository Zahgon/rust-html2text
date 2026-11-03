// Copyright 2014-2017 The html5ever Project Developers; ported in jugglerchris/rust-html2text. See the
// COPYRIGHT file at the top-level directory of this distribution.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
//
// Java port of html5ever's character reference sub-tokenizer (tokenizer/char_ref) and web_atoms' entity tables.

package io.github.jugglerchris.html2text.html5;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The tokenizer for one character reference, started after a '&'. */
final class CharRef {
    private static final int[] PREFIX = {0, 0};
    /** Entity names (without '&', with or without ';') and every prefix of one ({0, 0}) to their code points. */
    private static final Map<String, int[]> ENTITIES = loadEntities();
    /** The C1 control replacements of numeric references 0x80 to 0x9F (0: none). */
    private static final int[] C1_REPLACEMENTS = {0x20ac, 0, 0x201a, 0x0192, 0x201e, 0x2026, 0x2020, 0x2021, 0x02c6,
        0x2030, 0x0160, 0x2039, 0x0152, 0, 0x017d, 0, 0, 0x2018, 0x2019, 0x201c, 0x201d, 0x2022, 0x2013, 0x2014,
        0x02dc, 0x2122, 0x0161, 0x203a, 0x0153, 0, 0x017e, 0x0178};
    /** Step results besides the finished reference (an array of 0 to 2 code points; empty means a plain '&'). */
    static final int[] PROGRESS = {};
    static final int[] EMPTY = {};

    private enum State { BEGIN, OCTOTHORPE, NUMERIC, NUMERIC_SEMICOLON, NAMED, BOGUS_NAME }

    private final boolean inAttribute;
    private State state = State.BEGIN;
    private int base;
    private long num;
    private boolean numTooBig;
    private boolean seenDigit;
    private int hexMarker = -1;
    private StringBuilder nameBuf;
    private int[] nameMatch;
    private int nameLen;

    CharRef(boolean inAttribute) {
        this.inAttribute = inAttribute;
    }

    private static Map<String, int[]> loadEntities() {
        Map<String, int[]> map = new HashMap<>();
        try (var in = CharRef.class.getResourceAsStream("entities.txt");
                var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                for (String entry : line.split(" ")) {
                    int eq = entry.indexOf('=');
                    String name = entry.substring(0, eq);
                    String[] cps = entry.substring(eq + 1).split(",");
                    map.put(name, new int[] {Integer.parseInt(cps[0], 16),
                        cps.length > 1 ? Integer.parseInt(cps[1], 16) : 0});
                    for (int n = 1; n < name.length(); n++) {
                        map.putIfAbsent(name.substring(0, n), PREFIX);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        map.put("", PREFIX);
        return map;
    }

    static boolean isAsciiAlnum(int c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    private static int digit(int c, int base) {
        int d = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'z' ? c - 'a' + 10 : c >= 'A' && c <= 'Z'
            ? c - 'A' + 10 : 99;
        return d < base ? d : -1;
    }

    /** One step: null when stuck (needs more input), PROGRESS, or the finished reference. */
    int[] step(Tokenizer t) {
        int c = t.peek();
        if (c < 0) {
            return null;
        }
        switch (state) {
            case BEGIN -> {
                if (isAsciiAlnum(c)) {
                    state = State.NAMED;
                    nameBuf = new StringBuilder();
                    return PROGRESS;
                }
                if (c == '#') {
                    t.discardChar();
                    state = State.OCTOTHORPE;
                    return PROGRESS;
                }
                return EMPTY;
            }
            case OCTOTHORPE -> {
                if (c == 'x' || c == 'X') {
                    t.discardChar();
                    hexMarker = c;
                    base = 16;
                } else {
                    hexMarker = -1;
                    base = 10;
                }
                state = State.NUMERIC;
                return PROGRESS;
            }
            case NUMERIC -> {
                int d = digit(c, base);
                if (d >= 0) {
                    t.discardChar();
                    num = num * base & 0xFFFFFFFFL;
                    if (num > 0x10FFFF) {
                        numTooBig = true;
                    }
                    num = num + d & 0xFFFFFFFFL;
                    seenDigit = true;
                    return PROGRESS;
                }
                if (!seenDigit) {
                    return unconsumeNumeric(t);
                }
                state = State.NUMERIC_SEMICOLON;
                return PROGRESS;
            }
            case NUMERIC_SEMICOLON -> {
                if (c == ';') {
                    t.discardChar();
                } else {
                    t.error();
                }
                return finishNumeric(t);
            }
            case NAMED -> {
                t.discardChar();
                nameBuf.appendCodePoint(c);
                int[] m = ENTITIES.get(nameBuf.toString());
                if (m == null) {
                    return finishNamed(t, c);
                }
                if (m[0] != 0) {
                    nameMatch = m;
                    nameLen = nameBuf.length();
                }
                return PROGRESS;
            }
            default -> {
                t.discardChar();
                nameBuf.appendCodePoint(c);
                if (isAsciiAlnum(c)) {
                    return PROGRESS;
                }
                if (c == ';') {
                    t.error();
                }
                t.input.pushFront(nameBuf.toString());
                return EMPTY;
            }
        }
    }

    private int[] unconsumeNumeric(Tokenizer t) {
        t.input.pushFront(hexMarker < 0 ? "#" : "#" + (char) hexMarker);
        t.error();
        return EMPTY;
    }

    private int[] finishNumeric(Tokenizer t) {
        long n = num;
        int c = (int) n;
        boolean error = true;
        if (n > 0x10FFFF || numTooBig || n == 0 || n >= 0xD800 && n <= 0xDFFF) {
            c = 0xFFFD;
        } else if (n >= 0x80 && n <= 0x9F) {
            if (C1_REPLACEMENTS[c - 0x80] != 0) {
                c = C1_REPLACEMENTS[c - 0x80];
            }
        } else if (!(n >= 0x01 && n <= 0x08 || n == 0x0B || n >= 0x0D && n <= 0x1F || n == 0x7F
                || n >= 0xFDD0 && n <= 0xFDEF || (n & 0xFFFE) == 0xFFFE)) {
            error = false;
        }
        if (error) {
            t.error();
        }
        return new int[] {c};
    }

    /** The end of a named reference, after the character {@code end} (-1 at the end of the input). */
    private int[] finishNamed(Tokenizer t, int end) {
        if (nameMatch == null) {
            if (end >= 0 && isAsciiAlnum(end)) {
                state = State.BOGUS_NAME;
                return PROGRESS;
            }
            if (end == ';' && nameBuf.length() > 1) {
                t.error();
            }
            t.input.pushFront(nameBuf.toString());
            return EMPTY;
        }
        char lastMatched = nameBuf.charAt(nameLen - 1);
        int nextAfter = nameLen == nameBuf.length() ? -1 : nameBuf.codePointAt(nameLen);
        if (lastMatched != ';' && inAttribute && (nextAfter == '=' || nextAfter >= 0 && isAsciiAlnum(nextAfter))) {
            t.input.pushFront(nameBuf.toString());
            return EMPTY;
        }
        if (lastMatched != ';') {
            t.error();
        }
        t.input.pushFront(nameBuf.substring(nameLen));
        t.clearIgnoreLf();
        return nameMatch[1] == 0 ? new int[] {nameMatch[0]} : nameMatch.clone();
    }

    /** Finishes the reference at the end of the input. */
    int[] endOfFile(Tokenizer t) {
        while (true) {
            int[] r = switch (state) {
                case BEGIN -> EMPTY;
                case NUMERIC, NUMERIC_SEMICOLON -> {
                    if (state == State.NUMERIC && !seenDigit) {
                        yield unconsumeNumeric(t);
                    }
                    t.error();
                    yield finishNumeric(t);
                }
                case NAMED -> finishNamed(t, -1);
                case BOGUS_NAME -> {
                    t.input.pushFront(nameBuf.toString());
                    yield EMPTY;
                }
                case OCTOTHORPE -> {
                    t.input.pushFront("#");
                    t.error();
                    yield EMPTY;
                }
            };
            if (r != PROGRESS) {
                return r;
            }
        }
    }
}
