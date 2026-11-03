// Copyright 2014-2017 The html5ever Project Developers; ported in jugglerchris/rust-html2text. See the
// COPYRIGHT file at the top-level directory of this distribution.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
//
// Java port of html5ever's driver (driver.rs) and of tendril's lossy UTF-8 decoding (stream.rs, utf8_decode.rs) as
// html2text's Config::parse_html uses them: read_from in 4096-byte chunks, from_utf8, parse_document.

package io.github.jugglerchris.html2text.html5;

import io.github.jugglerchris.html2text.dom.Node;
import java.nio.charset.StandardCharsets;

/** The HTML parser html2text uses: html5ever's tokenizer and tree builder, building an RcDom-like tree. */
public final class HtmlParser {
    private static final int READ_SIZE = 4096;
    private static final String REPLACEMENT = "�";

    private final BufferQueue input = new BufferQueue();
    private final TreeBuilder builder = new TreeBuilder();
    private final Tokenizer tokenizer = new Tokenizer(builder, input);
    /** The bytes of an incomplete UTF-8 sequence at the end of the last chunk, or null. */
    private byte[] incomplete;
    private int incompleteLen;

    private HtmlParser() {}

    /** Parses a whole document from raw bytes exactly as html2text's Config::parse_html does; returns the Document node. */
    public static Node parseDocument(byte[] input) {
        HtmlParser p = new HtmlParser();
        for (int off = 0; off < input.length; off += READ_SIZE) {
            p.decode(input, off, Math.min(input.length, off + READ_SIZE));
        }
        if (p.incomplete != null) {
            p.process(REPLACEMENT);
        }
        p.tokenizer.end();
        return p.builder.document();
    }

    /** Hands decoded text to the tokenizer and runs it until it has consumed all input. */
    private void process(String s) {
        input.pushBack(s);
        while (!tokenizer.feed()) {
            continue;
        }
    }

    private static String utf8(byte[] b, int from, int to) {
        return new String(b, from, to - from, StandardCharsets.UTF_8);
    }

    /** Lossily decodes one chunk; an incomplete sequence at its end waits for the next chunk. */
    private void decode(byte[] b, int pos, int end) {
        if (incomplete != null) {
            int initial = incompleteLen;
            int copied = Math.min(4 - initial, end - pos);
            System.arraycopy(b, pos, incomplete, initial, copied);
            int check = checkUtf8(incomplete, 0, initial + copied);
            int validUpTo = check >> 3;
            int errorLen = check & 7;
            int consumed;
            String result;
            if (errorLen == 0) {
                consumed = copied;
                result = utf8(incomplete, 0, initial + copied);
            } else if (validUpTo > 0) {
                consumed = validUpTo - initial;
                result = utf8(incomplete, 0, validUpTo);
            } else if (errorLen < 4) {
                consumed = errorLen - initial;
                result = REPLACEMENT;
            } else {
                incompleteLen = initial + copied;
                return;
            }
            incomplete = null;
            process(result);
            pos += consumed;
        }
        while (pos < end) {
            int check = checkUtf8(b, pos, end);
            int validUpTo = check >> 3;
            int errorLen = check & 7;
            if (errorLen == 0) {
                process(utf8(b, pos, end));
                return;
            }
            if (validUpTo > 0) {
                process(utf8(b, pos, pos + validUpTo));
            }
            if (errorLen == 4) {
                incomplete = new byte[4];
                incompleteLen = end - pos - validUpTo;
                System.arraycopy(b, pos + validUpTo, incomplete, 0, incompleteLen);
                return;
            }
            process(REPLACEMENT);
            pos += validUpTo + errorLen;
        }
    }

    /**
     * UTF-8 validation as Rust's str::from_utf8 does it: the length of the valid prefix shifted left by 3, or'ed with
     * 0 if everything is valid, the length (1 to 3) of the invalid sequence, or 4 if the input ends inside a sequence.
     */
    static int checkUtf8(byte[] b, int from, int to) {
        int i = from;
        while (i < to) {
            int c = b[i] & 0xFF;
            if (c < 0x80) {
                i++;
                continue;
            }
            int width = c >= 0xC2 && c <= 0xDF ? 2 : c >= 0xE0 && c <= 0xEF ? 3 : c >= 0xF0 && c <= 0xF4 ? 4 : 0;
            int error = width == 0 ? 1 : 0;
            for (int k = 1; k < width && error == 0; k++) {
                if (i + k >= to) {
                    return (i - from) << 3 | 4;
                }
                int d = b[i + k] & 0xFF;
                int lo = 0x80;
                int hi = 0xBF;
                if (k == 1) {
                    lo = c == 0xE0 ? 0xA0 : c == 0xF0 ? 0x90 : 0x80;
                    hi = c == 0xED ? 0x9F : c == 0xF4 ? 0x8F : 0xBF;
                }
                if (d < lo || d > hi) {
                    error = k;
                }
            }
            if (error != 0) {
                return (i - from) << 3 | error;
            }
            i += width;
        }
        return (to - from) << 3;
    }
}
