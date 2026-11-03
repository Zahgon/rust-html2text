// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.cli;

/** UTF-8 decoding as Rust's {@code String::from_utf8_lossy} does it. */
final class Utf8 {
    private Utf8() {}

    /** Decodes UTF-8; each maximal ill-formed subsequence becomes one U+FFFD. */
    static String decodeLossy(byte[] b) {
        StringBuilder out = new StringBuilder(b.length);
        for (int i = 0; i < b.length; ) {
            int c = b[i++] & 0xff;
            int need = c < 0x80 ? 0 : c < 0xc2 ? -1 : c <= 0xdf ? 1 : c <= 0xef ? 2 : c <= 0xf4 ? 3 : -1;
            int lo = c == 0xe0 ? 0xa0 : c == 0xf0 ? 0x90 : 0x80;
            int hi = c == 0xed ? 0x9f : c == 0xf4 ? 0x8f : 0xbf;
            int cp = need <= 0 ? c : c & (0x3f >> need);
            int k = 0;
            for (; k < need && i < b.length; k++, i++) {
                int d = b[i] & 0xff;
                if (d < (k == 0 ? lo : 0x80) || d > (k == 0 ? hi : 0xbf)) {
                    break;
                }
                cp = cp << 6 | (d & 0x3f);
            }
            if (need < 0 || k < need) {
                out.append('�');
            } else {
                out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }
}
