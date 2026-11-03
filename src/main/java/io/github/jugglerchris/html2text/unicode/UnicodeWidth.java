// Copyright 2012-2025 The Rust Project Developers; ported in jugglerchris/rust-html2text. See the COPYRIGHT
// file at the top-level directory of this distribution and at
// http://rust-lang.org/COPYRIGHT.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
//
// Java port of the unicode-width 0.2.2 crate (Unicode 17.0.0, non-CJK widths), which html2text uses for every
// column count. The lookup tables are generated from the crate's own tables (resource width-tables.txt).

package io.github.jugglerchris.html2text.unicode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Display widths of characters and strings as the unicode-width crate computes them (UAX #11, narrow ambiguous). */
public final class UnicodeWidth {
    private static final int DEFAULT = 0;
    private static final int LINE_FEED = 0b0000000000000001;
    private static final int EMOJI_MODIFIER = 0b0000000000000010;
    private static final int REGIONAL_INDICATOR = 0b0000000000000011;
    private static final int SEVERAL_REGIONAL_INDICATOR = 0b0000000000000100;
    private static final int EMOJI_PRESENTATION = 0b0000000000000101;
    private static final int ZWJ_EMOJI_PRESENTATION = 0b0001000000000110;
    private static final int VS16_ZWJ_EMOJI_PRESENTATION = 0b1001000000000110;
    private static final int KEYCAP_ZWJ_EMOJI_PRESENTATION = 0b0001000000000111;
    private static final int VS16_KEYCAP_ZWJ_EMOJI_PRESENTATION = 0b1001000000000111;
    private static final int REGIONAL_INDICATOR_ZWJ_PRESENTATION = 0b0000000000001001;
    private static final int EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION = 0b0000000000001010;
    private static final int ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION = 0b0000000000001011;
    private static final int TAG_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000010000;
    private static final int TAG_D1_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000010001;
    private static final int TAG_D2_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000010010;
    private static final int TAG_D3_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000010011;
    private static final int TAG_A1_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000011001;
    private static final int TAG_A2_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000011010;
    private static final int TAG_A3_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000011011;
    private static final int TAG_A4_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000011100;
    private static final int TAG_A5_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000011101;
    private static final int TAG_A6_END_ZWJ_EMOJI_PRESENTATION = 0b0000000000011110;
    private static final int KIRAT_RAI_VOWEL_SIGN_E = 0b0000000000100000;
    private static final int KIRAT_RAI_VOWEL_SIGN_AI = 0b0000000000100001;
    private static final int VARIATION_SELECTOR_1_2_OR_3 = 0b0000001000000000;
    private static final int VARIATION_SELECTOR_15 = 0b0100000000000000;
    private static final int VARIATION_SELECTOR_16 = 0b1000000000000000;
    private static final int JOINING_GROUP_ALEF = 0b0011000011111111;
    private static final int HEBREW_LETTER_LAMED = 0b0011100000000000;
    private static final int ZWJ_HEBREW_LETTER_LAMED = 0b0011110000000000;
    private static final int BUGINESE_LETTER_YA = 0b0011100000000001;
    private static final int ZWJ_BUGINESE_LETTER_YA = 0b0011110000000001;
    private static final int BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA = 0b0011110000000010;
    private static final int TIFINAGH_CONSONANT = 0b0011100000000011;
    private static final int ZWJ_TIFINAGH_CONSONANT = 0b0011110000000011;
    private static final int TIFINAGH_JOINER_CONSONANT = 0b0011110000000100;
    private static final int LISU_TONE_LETTER_MYA_NA_JEU = 0b0011110000000101;
    private static final int OLD_TURKIC_LETTER_ORKHON_I = 0b0011100000000110;
    private static final int ZWJ_OLD_TURKIC_LETTER_ORKHON_I = 0b0011110000000110;
    private static final int KHMER_COENG_ELIGIBLE_LETTER = 0b0011110000000111;
    private static final int LIGATURE_TRANSPARENT_MASK = 0b0010_0000_0000_0000;

    private static final int[] RUN_STARTS;
    private static final byte[] RUN_WIDTHS;
    private static final int[] TRANSPARENT_ZERO_WIDTH;
    private static final int[] EMOJI_PRESENTATION_SEQ;
    private static final int[] TEXT_PRESENTATION_SEQ;
    private static final int[] EMOJI_MODIFIER_BASE;

    static {
        Map<String, String> tables = new HashMap<>();
        try (InputStream in = UnicodeWidth.class.getResourceAsStream("width-tables.txt");
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                int space = line.indexOf(' ');
                tables.put(line.substring(0, space), line.substring(space + 1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String[] runs = tables.get("WIDTHS").split(",");
        RUN_STARTS = new int[runs.length];
        RUN_WIDTHS = new byte[runs.length];
        for (int i = 0; i < runs.length; i++) {
            int colon = runs[i].indexOf(':');
            RUN_STARTS[i] = Integer.parseInt(runs[i], 0, colon, 16);
            RUN_WIDTHS[i] = (byte) (runs[i].charAt(colon + 1) - '0');
        }
        TRANSPARENT_ZERO_WIDTH = ranges(tables.get("TRANSPARENT_ZERO_WIDTH"));
        EMOJI_PRESENTATION_SEQ = ranges(tables.get("EMOJI_PRESENTATION_SEQ"));
        TEXT_PRESENTATION_SEQ = ranges(tables.get("TEXT_PRESENTATION_SEQ"));
        EMOJI_MODIFIER_BASE = ranges(tables.get("EMOJI_MODIFIER_BASE"));
    }

    private UnicodeWidth() {}

    /** Parses "lo-hi,lo-hi,..." (hex) into a flat sorted array of inclusive bounds. */
    private static int[] ranges(String spec) {
        String[] parts = spec.split(",");
        int[] out = new int[parts.length * 2];
        for (int i = 0; i < parts.length; i++) {
            int dash = parts[i].indexOf('-');
            out[2 * i] = Integer.parseInt(parts[i], 0, dash, 16);
            out[2 * i + 1] = Integer.parseInt(parts[i], dash + 1, parts[i].length(), 16);
        }
        return out;
    }

    private static boolean inRanges(int[] bounds, int cp) {
        int i = Arrays.binarySearch(bounds, cp);
        return i >= 0 || ((-i - 1) & 1) == 1;
    }

    /** The raw table width (0 to 3) of a code point. */
    private static int rawWidth(int cp) {
        int i = Arrays.binarySearch(RUN_STARTS, cp);
        return RUN_WIDTHS[i >= 0 ? i : -i - 2];
    }

    /** lookup_width: the width of a code point in the low byte, its width info shifted left by 8. */
    private static int lookup(int cp) {
        int width = rawWidth(cp);
        if (width < 3) {
            return width;
        }
        int info;
        if (cp == 0xA) {
            width = 1;
            info = LINE_FEED;
        } else if (cp == 0x5DC) {
            width = 1;
            info = HEBREW_LETTER_LAMED;
        } else if (cp >= 0x622 && cp <= 0x882) {
            width = 1;
            info = JOINING_GROUP_ALEF;
        } else if (cp >= 0x1780 && cp <= 0x17AF) {
            width = 1;
            info = KHMER_COENG_ELIGIBLE_LETTER;
        } else if (cp == 0x17D8) {
            info = DEFAULT;
        } else if (cp == 0x1A10) {
            width = 1;
            info = BUGINESE_LETTER_YA;
        } else if (cp >= 0x2D31 && cp <= 0x2D6F) {
            width = 1;
            info = TIFINAGH_CONSONANT;
        } else if (cp == 0xA4FC || cp == 0xA4FD) {
            width = 1;
            info = LISU_TONE_LETTER_MYA_NA_JEU;
        } else if (cp == 0xFE01) {
            width = 0;
            info = VARIATION_SELECTOR_1_2_OR_3;
        } else if (cp == 0xFE0E) {
            width = 0;
            info = VARIATION_SELECTOR_15;
        } else if (cp == 0xFE0F) {
            width = 0;
            info = VARIATION_SELECTOR_16;
        } else if (cp == 0x10C03) {
            width = 1;
            info = OLD_TURKIC_LETTER_ORKHON_I;
        } else if (cp == 0x16D67) {
            width = 1;
            info = KIRAT_RAI_VOWEL_SIGN_E;
        } else if (cp == 0x16D68) {
            width = 1;
            info = KIRAT_RAI_VOWEL_SIGN_AI;
        } else if (cp >= 0x1F1E6 && cp <= 0x1F1FF) {
            width = 1;
            info = REGIONAL_INDICATOR;
        } else if (cp >= 0x1F3FB && cp <= 0x1F3FF) {
            width = 2;
            info = EMOJI_MODIFIER;
        } else {
            width = 2;
            info = EMOJI_PRESENTATION;
        }
        return width | info << 8;
    }

    /**
     * The width of one character ({@code UnicodeWidthChar::width}), or -1 for a control character (Rust's
     * {@code None}).
     */
    public static int charWidth(int cp) {
        if (cp < 0x7F) {
            return cp >= 0x20 ? 1 : -1;
        } else if (cp >= 0xA0) {
            return lookup(cp) & 0xFF;
        } else {
            return -1;
        }
    }

    /** The width of one character, with 0 for control characters ({@code width(c).unwrap_or(0)}). */
    public static int charWidthOrZero(int cp) {
        return Math.max(charWidth(cp), 0);
    }

    /** The width of a string ({@code UnicodeWidthStr::width}), which accounts for emoji and other sequences. */
    public static long width(CharSequence s) {
        long sum = 0;
        int next = DEFAULT;
        for (int i = s.length(); i > 0; ) {
            int cp = Character.codePointBefore(s, i);
            i -= Character.charCount(cp);
            int r = widthInStr(cp, next);
            sum += (byte) r;
            next = r >>> 8;
        }
        return sum;
    }

    private static int result(int add, int info) {
        return (add & 0xFF) | info << 8;
    }

    private static boolean isLigatureTransparent(int c) {
        return c == 0x34F || (c >= 0x17B4 && c <= 0x17B5) || (c >= 0x180B && c <= 0x180D) || c == 0x180F
                || c == 0x200D || (c >= 0xFE00 && c <= 0xFE0F) || (c >= 0xE0100 && c <= 0xE01EF);
    }

    private static boolean isTagLetter(int c) {
        return c >= 0xE0061 && c <= 0xE007A;
    }

    private static boolean isTagDigit(int c) {
        return c >= 0xE0030 && c <= 0xE0039;
    }

    private static boolean isRegionalIndicator(int c) {
        return c >= 0x1F1E6 && c <= 0x1F1FF;
    }

    /** width_in_str: the width added by {@code c} (low byte, signed) and the info for the character before it. */
    private static int widthInStr(int c, int nextInfo) {
        if ((nextInfo & VARIATION_SELECTOR_16) == VARIATION_SELECTOR_16) {
            if (inRanges(EMOJI_PRESENTATION_SEQ, c)) {
                int width = (nextInfo & 0b1011_0000_0000_0000) == 0b1001_0000_0000_0000 ? 0 : 2;
                return result(width, EMOJI_PRESENTATION);
            }
            nextInfo = (nextInfo & LIGATURE_TRANSPARENT_MASK) == LIGATURE_TRANSPARENT_MASK
                    ? nextInfo & ~VARIATION_SELECTOR_16 : DEFAULT;
        }
        if (c <= 0xA0) {
            if (c == '\n') {
                return result(1, LINE_FEED);
            }
            return c == '\r' && nextInfo == LINE_FEED ? result(0, DEFAULT) : result(1, DEFAULT);
        }
        if (nextInfo != DEFAULT) {
            if (c == 0xFE0F) {
                return result(0, setEmojiPresentation(nextInfo));
            }
            if (c == 0xFE01) {
                return result(0, setVariation(nextInfo, VARIATION_SELECTOR_1_2_OR_3));
            }
            if (c == 0xFE0E) {
                return result(0, setVariation(nextInfo, VARIATION_SELECTOR_15));
            }
            if ((nextInfo & VARIATION_SELECTOR_15) == VARIATION_SELECTOR_15) {
                if (inRanges(TEXT_PRESENTATION_SEQ, c)) {
                    return result(1, DEFAULT);
                }
                nextInfo &= ~VARIATION_SELECTOR_15;
            } else if ((nextInfo & VARIATION_SELECTOR_1_2_OR_3) == VARIATION_SELECTOR_1_2_OR_3) {
                if (c == 0x2018 || c == 0x2019 || c == 0x201C || c == 0x201D) {
                    return result(2, DEFAULT);
                }
                nextInfo &= ~VARIATION_SELECTOR_1_2_OR_3;
            }
            if ((nextInfo & 0b0000_1000_0000_0000) == 0b0000_1000_0000_0000) {
                if (c == 0x200D) {
                    return result(0, nextInfo | 0b0000_0100_0000_0000);
                } else if (isLigatureTransparent(c)) {
                    return result(0, nextInfo);
                }
            }
            Integer special = special(nextInfo, c);
            if (special != null) {
                return special;
            }
        }
        int ret = lookup(c);
        return result(ret & 0xFF, ret >>> 8);
    }

    /** The sequence rules of width_in_str that depend on the following character's info, or null. */
    private static Integer special(int next, int c) {
        switch (next) {
            case JOINING_GROUP_ALEF:
                if (c == 0x644 || (c >= 0x6B5 && c <= 0x6B8) || c == 0x76A || c == 0x8A6 || c == 0x8C7) {
                    return result(0, DEFAULT);
                }
                return inRanges(TRANSPARENT_ZERO_WIDTH, c) ? result(0, JOINING_GROUP_ALEF) : null;
            case ZWJ_HEBREW_LETTER_LAMED:
                return c == 0x5D0 ? result(0, DEFAULT) : null;
            case KHMER_COENG_ELIGIBLE_LETTER:
                return c == 0x17D2 ? result(-1, DEFAULT) : null;
            case ZWJ_BUGINESE_LETTER_YA:
                return c == 0x1A17 ? result(0, BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA) : null;
            case BUGINESE_VOWEL_SIGN_I_ZWJ_LETTER_YA:
                return c == 0x1A15 ? result(0, DEFAULT) : null;
            case TIFINAGH_CONSONANT:
                return c == 0x2D7F ? result(1, TIFINAGH_JOINER_CONSONANT) : null;
            case ZWJ_TIFINAGH_CONSONANT:
                if (c == 0x2D7F) {
                    return result(1, TIFINAGH_JOINER_CONSONANT);
                }
                return (c >= 0x2D31 && c <= 0x2D65) || c == 0x2D6F ? result(0, DEFAULT) : null;
            case TIFINAGH_JOINER_CONSONANT:
                return (c >= 0x2D31 && c <= 0x2D65) || c == 0x2D6F ? result(-1, DEFAULT) : null;
            case LISU_TONE_LETTER_MYA_NA_JEU:
                return c >= 0xA4F8 && c <= 0xA4FB ? result(0, DEFAULT) : null;
            case ZWJ_OLD_TURKIC_LETTER_ORKHON_I:
                return c == 0x10C32 ? result(0, DEFAULT) : null;
            case EMOJI_MODIFIER:
                if (inRanges(EMOJI_MODIFIER_BASE, c)) {
                    return result(0, EMOJI_PRESENTATION);
                }
                return c == 0x200D ? result(0, ZWJ_EMOJI_PRESENTATION) : null;
            case REGIONAL_INDICATOR:
                return isRegionalIndicator(c) ? result(1, SEVERAL_REGIONAL_INDICATOR) : null;
            case SEVERAL_REGIONAL_INDICATOR:
                if (isRegionalIndicator(c)) {
                    return result(1, SEVERAL_REGIONAL_INDICATOR);
                }
                return c == 0x200D ? result(0, ZWJ_EMOJI_PRESENTATION) : null;
            case EMOJI_PRESENTATION:
                return c == 0x200D ? result(0, ZWJ_EMOJI_PRESENTATION) : null;
            case EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION:
                if (c == 0x200D) {
                    return result(0, ZWJ_EMOJI_PRESENTATION);
                }
                return isRegionalIndicator(c) ? result(3, ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION) : null;
            case ODD_REGIONAL_INDICATOR_ZWJ_PRESENTATION:
                if (c == 0x200D) {
                    return result(0, ZWJ_EMOJI_PRESENTATION);
                }
                return isRegionalIndicator(c) ? result(-1, EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION) : null;
            case REGIONAL_INDICATOR_ZWJ_PRESENTATION:
                return isRegionalIndicator(c) ? result(-1, EVEN_REGIONAL_INDICATOR_ZWJ_PRESENTATION) : null;
            case ZWJ_EMOJI_PRESENTATION:
                if (c == 0x20E3) {
                    return result(0, KEYCAP_ZWJ_EMOJI_PRESENTATION);
                }
                if (isRegionalIndicator(c)) {
                    return result(1, REGIONAL_INDICATOR_ZWJ_PRESENTATION);
                }
                if (c >= 0x1F3FB && c <= 0x1F3FF) {
                    return result(0, EMOJI_MODIFIER);
                }
                if (c == 0xE007F) {
                    return result(0, TAG_END_ZWJ_EMOJI_PRESENTATION);
                }
                return lookup(c) >>> 8 == EMOJI_PRESENTATION ? result(0, EMOJI_PRESENTATION) : null;
            case VS16_ZWJ_EMOJI_PRESENTATION:
                return inRanges(EMOJI_PRESENTATION_SEQ, c) ? result(0, EMOJI_PRESENTATION) : null;
            case VS16_KEYCAP_ZWJ_EMOJI_PRESENTATION:
                return (c >= '0' && c <= '9') || c == '#' || c == '*' ? result(0, EMOJI_PRESENTATION) : null;
            case TAG_END_ZWJ_EMOJI_PRESENTATION:
                return tagStep(c, TAG_A1_END_ZWJ_EMOJI_PRESENTATION, true, false);
            case TAG_A1_END_ZWJ_EMOJI_PRESENTATION:
                return tagStep(c, TAG_A2_END_ZWJ_EMOJI_PRESENTATION, true, false);
            case TAG_A2_END_ZWJ_EMOJI_PRESENTATION:
                return tagStep(c, TAG_A3_END_ZWJ_EMOJI_PRESENTATION, true, false);
            case TAG_A3_END_ZWJ_EMOJI_PRESENTATION:
                return tagStep(c, TAG_A4_END_ZWJ_EMOJI_PRESENTATION, true, true);
            case TAG_A4_END_ZWJ_EMOJI_PRESENTATION:
                return tagStep(c, TAG_A5_END_ZWJ_EMOJI_PRESENTATION, true, true);
            case TAG_A5_END_ZWJ_EMOJI_PRESENTATION:
                if (isTagLetter(c)) {
                    return result(0, TAG_A6_END_ZWJ_EMOJI_PRESENTATION);
                }
                return c == 0x1F3F4 ? result(0, EMOJI_PRESENTATION) : null;
            case TAG_A6_END_ZWJ_EMOJI_PRESENTATION:
            case TAG_D3_END_ZWJ_EMOJI_PRESENTATION:
                return c == 0x1F3F4 ? result(0, EMOJI_PRESENTATION) : null;
            case TAG_D1_END_ZWJ_EMOJI_PRESENTATION:
                return isTagDigit(c) ? result(0, TAG_D2_END_ZWJ_EMOJI_PRESENTATION) : null;
            case TAG_D2_END_ZWJ_EMOJI_PRESENTATION:
                return isTagDigit(c) ? result(0, TAG_D3_END_ZWJ_EMOJI_PRESENTATION) : null;
            case KIRAT_RAI_VOWEL_SIGN_E:
                if (c == 0x16D63 || c == 0x16D69) {
                    return result(0, DEFAULT);
                }
                if (c == 0x16D67) {
                    return result(0, KIRAT_RAI_VOWEL_SIGN_AI);
                }
                return c == 0x16D68 ? result(1, KIRAT_RAI_VOWEL_SIGN_E) : null;
            case KIRAT_RAI_VOWEL_SIGN_AI:
                return c == 0x16D63 ? result(0, DEFAULT) : null;
            default:
                return null;
        }
    }

    /** The tag-sequence rules: a tag letter advances, a tag digit starts the digit run, a black flag may end. */
    private static Integer tagStep(int c, int nextLetter, boolean digits, boolean flagEnds) {
        if (isTagLetter(c)) {
            return result(0, nextLetter);
        }
        if (digits && isTagDigit(c)) {
            return result(0, TAG_D1_END_ZWJ_EMOJI_PRESENTATION);
        }
        return flagEnds && c == 0x1F3F4 ? result(0, EMOJI_PRESENTATION) : null;
    }

    private static int setEmojiPresentation(int info) {
        if ((info & LIGATURE_TRANSPARENT_MASK) == LIGATURE_TRANSPARENT_MASK
                || (info & 0b1001_0000_0000_0000) == 0b0001_0000_0000_0000) {
            return info | VARIATION_SELECTOR_16;
        }
        return VARIATION_SELECTOR_16;
    }

    /** set_text_presentation and set_vs1_2_3: a ligature-transparent info keeps its bits and gains the selector. */
    private static int setVariation(int info, int selector) {
        return (info & LIGATURE_TRANSPARENT_MASK) == LIGATURE_TRANSPARENT_MASK ? info | selector : selector;
    }
}
