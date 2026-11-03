// Copyright 2014-2017 The html5ever Project Developers; ported in jugglerchris/rust-html2text. See the
// COPYRIGHT file at the top-level directory of this distribution.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
//
// Java port of markup5ever's BufferQueue and SmallCharSet.

package io.github.jugglerchris.html2text.html5;

import java.util.ArrayDeque;
import java.util.Iterator;

/** A queue of string buffers which supports incrementally consuming characters (code points). */
final class BufferQueue {
    private static final class Buf {
        final String s;
        int pos;

        Buf(String s) {
            this.s = s;
        }
    }

    private final ArrayDeque<Buf> buffers = new ArrayDeque<>();

    /** A set of characters below 64, as a bit mask. */
    static long set(char... chars) {
        long bits = 0;
        for (char c : chars) {
            bits |= 1L << c;
        }
        return bits;
    }

    static boolean inSet(long set, int c) {
        return c < 64 && (set & (1L << c)) != 0;
    }

    boolean isEmpty() {
        return buffers.isEmpty();
    }

    void pushFront(String s) {
        if (!s.isEmpty()) {
            buffers.addFirst(new Buf(s));
        }
    }

    void pushBack(String s) {
        if (!s.isEmpty()) {
            buffers.addLast(new Buf(s));
        }
    }

    /** The next character without removing it, or -1. */
    int peek() {
        Buf b = buffers.peekFirst();
        return b == null ? -1 : b.s.codePointAt(b.pos);
    }

    /** Removes and returns the next character, or -1. */
    int next() {
        Buf b = buffers.peekFirst();
        if (b == null) {
            return -1;
        }
        int c = b.s.codePointAt(b.pos);
        b.pos += Character.charCount(c);
        if (b.pos == b.s.length()) {
            buffers.removeFirst();
        }
        return c;
    }

    /**
     * Pops the run of characters not in {@code set} at the start of the first buffer, or returns null (consuming
     * nothing) when the next character is in the set. The queue must not be empty.
     */
    String popRun(long set) {
        Buf b = buffers.getFirst();
        int n = b.pos;
        while (n < b.s.length() && !inSet(set, b.s.charAt(n))) {
            n++;
        }
        if (n == b.pos) {
            return null;
        }
        String out = b.s.substring(b.pos, n);
        b.pos = n;
        if (n == b.s.length()) {
            buffers.removeFirst();
        }
        return out;
    }

    /**
     * Consumes the ASCII pattern {@code pat} if the queue starts with it: true if so, false if not, null when more
     * input is needed to know.
     */
    Boolean eat(String pat, boolean ignoreCase) {
        if (buffers.isEmpty()) {
            return null;
        }
        Iterator<Buf> it = buffers.iterator();
        Buf buf = it.next();
        int exhausted = 0;
        int i = buf.pos;
        for (int k = 0; k < pat.length(); k++) {
            if (buf == null) {
                return null;
            }
            char c = buf.s.charAt(i);
            char p = pat.charAt(k);
            if (c != p && !(ignoreCase && c >= 'A' && c <= 'Z' && c + 32 == p)) {
                return false;
            }
            if (++i == buf.s.length()) {
                exhausted++;
                buf = it.hasNext() ? it.next() : null;
                i = buf == null ? 0 : buf.pos;
            }
        }
        for (int k = 0; k < exhausted; k++) {
            buffers.removeFirst();
        }
        if (buf != null) {
            buf.pos = i;
        }
        return true;
    }
}
