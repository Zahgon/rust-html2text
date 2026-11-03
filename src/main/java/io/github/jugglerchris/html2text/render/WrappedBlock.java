// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.Html2TextException;
import io.github.jugglerchris.html2text.RustPanic;
import io.github.jugglerchris.html2text.Usize;
import io.github.jugglerchris.html2text.WhiteSpace;
import io.github.jugglerchris.html2text.render.TaggedLineElement.Str;
import io.github.jugglerchris.html2text.unicode.Chars;
import io.github.jugglerchris.html2text.unicode.UnicodeWidth;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds up wrapped text, word by word, keeping the tag of each span.
 *
 * @param <T> the tag type
 */
final class WrappedBlock<T> {
    /** A tag extended with the white-space mode of its text. */
    record Ws<T>(T tag, WhiteSpace ws) {}

    private final long width;
    private final List<TaggedLine<T>> text = new ArrayList<>();
    private TaggedLine<Ws<T>> line = new TaggedLine<>();
    private Ws<T> spacetag;
    private TaggedLine<Ws<T>> word = new TaggedLine<>();
    private long wordlen;
    private long wslen;
    private boolean preWrapped;
    private final boolean padBlocks;
    private final boolean allowOverflow;
    private final T defaultTag;

    WrappedBlock(long width, boolean padBlocks, boolean allowOverflow, T defaultTag) {
        this.width = width;
        this.padBlocks = padBlocks;
        this.allowOverflow = allowOverflow;
        this.defaultTag = defaultTag;
    }

    long wordlen() {
        return wordlen;
    }

    long lineLen() {
        return line.len;
    }

    private void flushWord() throws Html2TextException {
        if (!word.isEmpty()) {
            WhiteSpace wsMode = WhiteSpace.NORMAL;
            for (TaggedLineElement<Ws<T>> e : word.v) {
                if (e instanceof Str<Ws<T>> str) {
                    wsMode = str.ts().tag().ws();
                    break;
                }
            }
            preWrapped = false;
            long spaceInLine = width - line.len;
            long spaceNeeded = wslen + wordlen;
            if (Usize.le(spaceNeeded, spaceInLine)) {
                if (wslen > 0) {
                    line.pushWs(wslen, spacetag);
                    spacetag = null;
                    wslen = 0;
                }
                line.consume(word);
            } else {
                if (spacetag != null) {
                    switch (spacetag.ws()) {
                        case PRE -> {
                            if (Usize.ge(wslen, spaceInLine)) {
                                wslen -= spaceInLine;
                            } else if (wslen > 0) {
                                line.pushWs(wslen, spacetag);
                                spacetag = null;
                                wslen = 0;
                            }
                        }
                        case NORMAL -> {
                            spacetag = null;
                            wslen = 0;
                        }
                        case PRE_WRAP -> {
                            spacetag = null;
                            wslen = 0;
                            word.removeLeadingWhitespace();
                            line.removeTrailingSpaces();
                        }
                    }
                }
                flushLine();
                if (wsMode == WhiteSpace.PRE) {
                    preWrapped = true;
                }
                while (wslen > 0) {
                    long toCopy = Usize.min(wslen, width);
                    if (spacetag == null) {
                        throw new RustPanic(RustPanic.UNWRAP_NONE);
                    }
                    line.pushWs(toCopy, spacetag);
                    if (toCopy == width) {
                        flushLine();
                    }
                    wslen -= toCopy;
                }
                spacetag = null;
                flushWordHardWrap();
            }
        }
        wordlen = 0;
    }

    /** Writes the current word out, hard-wrapped (a preformatted word, or one longer than the line). */
    private void flushWordHardWrap() throws Html2TextException {
        long lineleft = width - line.len;
        for (TaggedLineElement<Ws<T>> element : word.removeItems()) {
            if (!(element instanceof Str<Ws<T>> str)) {
                continue;
            }
            TaggedString<Ws<T>> piece = str.ts();
            String s = piece.s();
            long w = piece.width();
            long wpos = 0;
            int bpos = 0;
            while (Usize.gt(w - wpos, lineleft)) {
                int split = 0;
                for (int idx = bpos; idx < s.length(); ) {
                    int c = s.codePointAt(idx);
                    int cw = UnicodeWidth.charWidth(c);
                    if (cw < 0) {
                        throw new RustPanic(RustPanic.UNWRAP_NONE);
                    }
                    if (Usize.le(cw, lineleft)) {
                        lineleft -= cw;
                        wpos += cw;
                    } else {
                        if (idx == bpos && line.width() == 0) {
                            if (!allowOverflow) {
                                throw Html2TextException.tooNarrow();
                            }
                            split = Character.charCount(c);
                            wpos += cw;
                            break;
                        }
                        split = idx - bpos;
                        break;
                    }
                    idx += Character.charCount(c);
                }
                line.push(new Str<>(new TaggedString<>(s.substring(bpos, bpos + split), piece.tag())));
                bpos += split;
                forceFlushLine();
                lineleft = width;
            }
            if (bpos == 0) {
                line.push(element);
                lineleft -= w;
            } else if (bpos < s.length()) {
                line.push(new Str<>(new TaggedString<>(s.substring(bpos), piece.tag())));
                lineleft -= Usize.satSub(w, wpos);
            }
        }
    }

    private void flushLine() {
        if (!line.isEmpty()) {
            forceFlushLine();
        }
    }

    private void forceFlushLine() {
        TaggedLine<Ws<T>> done = line;
        line = new TaggedLine<>();
        if (padBlocks) {
            done.padTo(width, new Ws<>(defaultTag, WhiteSpace.NORMAL));
        }
        text.add(done.mapTag(Ws::tag));
    }

    private void flush() throws Html2TextException {
        flushWord();
        flushLine();
    }

    /** If the pending word holds only fragment starts, takes them. */
    List<TaggedLineElement<T>> takeTrailingFragments() {
        List<TaggedLineElement<T>> out = new ArrayList<>();
        if (word.isEmpty()) {
            for (TaggedLineElement<Ws<T>> e : word.v) {
                out.add(e.mapTag(Ws::tag));
            }
            word = new TaggedLine<>();
        }
        return out;
    }

    /** Finishes the block and returns its lines. */
    List<TaggedLine<T>> intoLines() throws Html2TextException {
        flush();
        return text;
    }

    void addText(String s, WhiteSpace wsMode, T mainTag, T wrapTag) throws Html2TextException {
        T tag = preWrapped ? wrapTag : mainTag;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            if (Chars.isWordbreakPoint(c) && wordlen > 0 && wsMode != WhiteSpace.PRE) {
                flushWord();
            }
            if (c == 0x200B) {
                continue;
            } else if (!Chars.alwaysTakesSpace(c)) {
                if (wsMode.preserveWhitespace()) {
                    if (c == '\n') {
                        flushWord();
                        forceFlushLine();
                        wslen = 0;
                        spacetag = null;
                        preWrapped = false;
                        tag = mainTag;
                    } else if (c == '\t') {
                        flushWord();
                        long p = line.len + wordlen + wslen;
                        boolean atLeastOneSpace = false;
                        while (Long.remainderUnsigned(p, 8) != 0 || !atLeastOneSpace) {
                            if (Usize.ge(p, width)) {
                                flushLine();
                                p = 0;
                            } else {
                                line.pushChar(' ', new Ws<>(tag, wsMode));
                                p += 1;
                                atLeastOneSpace = true;
                            }
                        }
                    } else {
                        int cw = UnicodeWidth.charWidth(c);
                        if (cw >= 0) {
                            if (word.isEmpty() && Chars.isWhitespace(c)) {
                                wslen += cw;
                                spacetag = new Ws<>(tag, wsMode);
                            } else {
                                word.pushChar(c, new Ws<>(tag, wsMode));
                                wordlen += cw;
                            }
                        }
                    }
                } else if (line.len > 0 && wslen == 0) {
                    spacetag = new Ws<>(tag, wsMode);
                    wslen = 1;
                }
            } else {
                int cw = UnicodeWidth.charWidth(c);
                if (cw >= 0) {
                    wordlen += cw;
                    if (wsMode == WhiteSpace.PRE && Usize.gt(line.len + wslen + wordlen, width)) {
                        preWrapped = true;
                        tag = wrapTag;
                    }
                    word.pushChar(c, new Ws<>(tag, wsMode));
                }
            }
        }
    }

    void addElement(TaggedLineElement<T> element) {
        word.push(element.mapTag(t -> new Ws<>(t, WhiteSpace.NORMAL)));
    }

    boolean isEmpty() {
        return text.size() + line.len + wordlen == 0;
    }
}
