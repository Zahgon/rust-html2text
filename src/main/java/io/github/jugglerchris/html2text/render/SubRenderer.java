// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.Colour;
import io.github.jugglerchris.html2text.Html2TextException;
import io.github.jugglerchris.html2text.ImageRenderMode;
import io.github.jugglerchris.html2text.RustPanic;
import io.github.jugglerchris.html2text.Usize;
import io.github.jugglerchris.html2text.WhiteSpace;
import io.github.jugglerchris.html2text.render.TaggedLineElement.FragmentStart;
import io.github.jugglerchris.html2text.render.TaggedLineElement.Str;
import io.github.jugglerchris.html2text.unicode.Chars;
import io.github.jugglerchris.html2text.unicode.UnicodeWidth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A renderer of one block of plain text with annotations from a decorator; nested blocks (list items, table cells)
 * get their own sub renderers, appended to their parent when finished.
 *
 * @param <A> the annotation type
 */
public final class SubRenderer<A> {
    /** The lines of one table cell being laid out in a row. */
    private static final class LineSet<A> {
        final long pos;
        final long width;
        final long rowspan;
        final List<RenderLine<List<A>>> lines;

        LineSet(long pos, long width, long rowspan, List<RenderLine<List<A>>> lines) {
            this.pos = pos;
            this.width = width;
            this.rowspan = rowspan;
            this.lines = lines;
        }

        long cellHeight() {
            return rowspan == 1 ? lines.size() : Usize.div(lines.size(), rowspan);
        }
    }

    private final long width;
    private final RenderOptions options;
    private final List<RenderLine<List<A>>> lines = new ArrayList<>();
    private final List<TaggedLineElement<List<A>>> pendingFrags = new ArrayList<>();
    private boolean atBlockEnd;
    private WrappedBlock<List<A>> wrapping;
    private final TextDecorator<A> decorator;
    private List<A> annStack = new ArrayList<>();
    private int strikeoutFilters;
    private long preDepth;
    private final List<WhiteSpace> wsStack = new ArrayList<>();
    private List<LineSet<A>> overhangCells = new ArrayList<>();

    /** Creates an empty renderer of the given width. */
    public SubRenderer(long width, RenderOptions options, TextDecorator<A> decorator) {
        this.width = width;
        this.options = options;
        this.decorator = decorator;
    }

    /** The rendering options. */
    public RenderOptions options() {
        return options;
    }

    /** The width in character cells. */
    public long width() {
        return width;
    }

    private List<A> tag() {
        return List.copyOf(annStack);
    }

    /** The footnote lines for the links (none unless link footnotes are on). */
    public List<TaggedLine<A>> finalise(List<String> links) {
        return decorator.finalise(options.includeLinkFootnotes() ? links : List.of());
    }

    /** Appends a line; pending fragments are prepended to a text line. */
    private void addLine(RenderLine<List<A>> line) {
        if (!pendingFrags.isEmpty() && line instanceof RenderLine.Text<List<A>> text) {
            TaggedLine<List<A>> tl = new TaggedLine<>();
            for (TaggedLineElement<List<A>> frag : pendingFrags) {
                tl.push(frag);
            }
            pendingFrags.clear();
            for (TaggedLineElement<List<A>> part : text.line().v) {
                tl.push(part);
            }
            line = new RenderLine.Text<>(tl);
        }
        if (options.padBlockWidth()) {
            TaggedLine<List<A>> tl = line.intoTaggedLine();
            tl.padTo(width, tag());
            line = new RenderLine.Text<>(tl);
        }
        lines.add(line);
    }

    private void flushWrapping() throws Html2TextException {
        if (wrapping != null) {
            WrappedBlock<List<A>> w = wrapping;
            wrapping = null;
            List<TaggedLineElement<List<A>>> frags = w.takeTrailingFragments();
            for (TaggedLine<List<A>> line : w.intoLines()) {
                addLine(new RenderLine.Text<>(line));
            }
            pendingFrags.addAll(frags);
        }
    }

    /** Finishes the renderer and returns its text, one line per {@code \n}-terminated line. */
    public String intoString() throws Html2TextException {
        flushWrapping();
        StringBuilder out = new StringBuilder();
        for (RenderLine<List<A>> line : lines) {
            out.append(line.text()).append('\n');
        }
        return out.toString();
    }

    /** Finishes the renderer and returns its lines. */
    public List<TaggedLine<List<A>>> intoLines() throws Html2TextException {
        flushWrapping();
        List<TaggedLine<List<A>>> out = new ArrayList<>();
        for (RenderLine<List<A>> line : lines) {
            out.add(line.intoTaggedLine());
        }
        return out;
    }

    private List<RenderLine<List<A>>> intoRenderLines() throws Html2TextException {
        flushWrapping();
        return lines;
    }

    /** Adds the link footnote lines, hard-wrapped to the width unless link wrapping is off. */
    public void fmtLinks(List<TaggedLine<A>> links) {
        for (TaggedLine<A> line : links) {
            long pos = 0;
            TaggedLine<List<A>> wrapped = new TaggedLine<>();
            for (TaggedString<A> ts : line.taggedStrings()) {
                String s = ts.s().replace('\n', ' ');
                List<A> tag = Collections.singletonList(ts.tag());
                long w = UnicodeWidth.width(s);
                if (options.wrapLinks() && Usize.gt(pos + w, width)) {
                    StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < s.length(); ) {
                        int c = s.codePointAt(i);
                        i += Character.charCount(c);
                        int cw = UnicodeWidth.charWidthOrZero(c);
                        if (Usize.gt(pos + cw, width)) {
                            if (!buf.isEmpty()) {
                                wrapped.pushStr(new TaggedString<>(buf, tag));
                                buf.setLength(0);
                            }
                            addLine(new RenderLine.Text<>(wrapped));
                            wrapped = new TaggedLine<>();
                            pos = 0;
                        }
                        pos += cw;
                        buf.appendCodePoint(c);
                    }
                    wrapped.pushStr(new TaggedString<>(buf, tag));
                } else {
                    wrapped.pushStr(new TaggedString<>(s, tag));
                    pos += w;
                }
            }
            addLine(new RenderLine.Text<>(wrapped));
        }
    }

    private void addHorizontalLine(BorderHoriz<List<A>> line) throws Html2TextException {
        flushWrapping();
        addLine(new RenderLine.Border<>(line));
    }

    /** The width left after a prefix, failing with TooNarrow below {@code minWidth} unless overflow is allowed. */
    public long widthMinus(long prefixLen, long minWidth) throws Html2TextException {
        long newWidth = Usize.satSub(width, prefixLen);
        if (Usize.lt(newWidth, minWidth) && !options.allowWidthOverflow()) {
            throw Html2TextException.tooNarrow();
        }
        return Usize.max(newWidth, minWidth);
    }

    private WhiteSpace wsMode() {
        return wsStack.isEmpty() ? WhiteSpace.NORMAL : wsStack.get(wsStack.size() - 1);
    }

    private WrappedBlock<List<A>> wrapping() {
        if (wrapping == null) {
            long wwidth = options.wrapWidth() == null ? width : Usize.min(options.wrapWidth(), width);
            wrapping = new WrappedBlock<>(wwidth, options.padBlockWidth(), options.allowWidthOverflow(), tag());
        }
        return wrapping;
    }

    private void addEmptyLine() throws Html2TextException {
        flushWrapping();
        addLine(new RenderLine.Text<>(new TaggedLine<>()));
        atBlockEnd = false;
    }

    /** A renderer for a nested block of the given width, with the same annotations open. */
    public SubRenderer<A> newSubRenderer(long width) {
        SubRenderer<A> result = new SubRenderer<>(width, options, decorator.makeSubblockDecorator());
        result.annStack = new ArrayList<>(annStack);
        return result;
    }

    /** Starts a new block (after an empty line if there is content already). */
    public void startBlock() throws Html2TextException {
        flushWrapping();
        for (RenderLine<List<A>> line : lines) {
            if (line.hasContent()) {
                addEmptyLine();
                break;
            }
        }
        atBlockEnd = false;
    }

    /** Starts a new table. */
    public void startTable() throws Html2TextException {
        startBlock();
    }

    /** Marks the end of a block. */
    public void endBlock() {
        atBlockEnd = true;
    }

    /** Starts a new line if necessary. */
    public void newLine() throws Html2TextException {
        flushWrapping();
    }

    /** Starts a new line (a line break). */
    public void newLineHard() throws Html2TextException {
        if (wrapping == null || (wrapping.wordlen() == 0 && wrapping.lineLen() == 0)) {
            addEmptyLine();
        } else {
            flushWrapping();
        }
    }

    /** Adds a horizontal table border of the full width. */
    public void addHorizontalBorder() throws Html2TextException {
        addHorizontalBorderWidth(width);
    }

    /** Adds a horizontal table border of the given width. */
    public void addHorizontalBorderWidth(long borderWidth) throws Html2TextException {
        flushWrapping();
        addLine(new RenderLine.Border<>(new BorderHoriz<>(borderWidth, tag())));
    }

    /** Updates the white-space setting. */
    public void pushWs(WhiteSpace ws) {
        wsStack.add(ws);
    }

    /** Ends the current white-space setting. */
    public void popWs() {
        if (!wsStack.isEmpty()) {
            wsStack.remove(wsStack.size() - 1);
        }
    }

    /** Begins a preformatted block (inside a {@code <pre>}). */
    public void pushPreformat() {
        preDepth += 1;
    }

    /** Ends a preformatted block. */
    public void popPreformat() {
        preDepth -= 1;
    }

    private static String strikeout(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            out.appendCodePoint(c);
            if (UnicodeWidth.charWidthOrZero(c) > 0) {
                out.append('̶');
            }
        }
        return out.toString();
    }

    /** Adds inline text, wrapped to the width, to the current block. */
    public void addInlineText(String text) throws Html2TextException {
        if (!wsMode().preserveWhitespace() && atBlockEnd && Chars.allWhitespace(text)) {
            return;
        }
        if (atBlockEnd) {
            startBlock();
        }
        String filtered = text;
        for (int i = 0; i < strikeoutFilters; i++) {
            filtered = strikeout(filtered);
        }
        WhiteSpace ws = wsMode();
        WrappedBlock<List<A>> w = wrapping();
        List<A> mainTag = tag();
        List<A> contTag = mainTag;
        if (preDepth > 0) {
            List<A> first = new ArrayList<>(annStack);
            first.add(decorator.decoratePreformatFirst());
            List<A> cont = new ArrayList<>(annStack);
            cont.add(decorator.decoratePreformatCont());
            mainTag = List.copyOf(first);
            contTag = List.copyOf(cont);
        }
        w.addText(filtered, ws, mainTag, contTag);
    }

    /** Appends a finished sub renderer's lines, the first prefixed by {@code first} and the rest by {@code rest}. */
    public void appendSubrender(SubRenderer<A> other, String first, String rest) throws Html2TextException {
        flushWrapping();
        List<A> tag = tag();
        String prefix = first;
        for (RenderLine<List<A>> line : other.intoRenderLines()) {
            if (line instanceof RenderLine.Text<List<A>> text) {
                if (!prefix.isEmpty()) {
                    text.line().insertFront(new TaggedString<>(prefix, tag));
                }
                addLine(text);
            } else {
                TaggedLine<List<A>> tl = new TaggedLine<>();
                tl.push(new Str<>(new TaggedString<>(prefix, tag)));
                tl.push(new Str<>(new TaggedString<>(line.text(), tag)));
                addLine(new RenderLine.Text<>(tl));
            }
            prefix = rest;
        }
    }

    /**
     * Appends table cells side by side with vertical lines between them and a border below; cells with a rowspan
     * above 1 continue into the following rows.
     */
    public void appendColumnsWithBorders(List<SubRenderer<A>> cols, List<Long> rowspans, boolean collapse)
            throws Html2TextException {
        flushWrapping();
        long totWidth = 0;
        List<LineSet<A>> lineSets = new ArrayList<>();
        for (int i = 0; i < Math.min(cols.size(), rowspans.size()); i++) {
            SubRenderer<A> sub = cols.get(i);
            long w = sub.width;
            long pos = totWidth;
            totWidth += w + 1;
            List<RenderLine<List<A>>> subLines = new ArrayList<>(sub.intoRenderLines());
            for (RenderLine<List<A>> line : subLines) {
                switch (line) {
                    case RenderLine.Text<List<A>> t -> t.line().padTo(w, tag());
                    case RenderLine.Border<List<A>> b -> b.border().stretchTo(w);
                }
            }
            lineSets.add(new LineSet<>(pos, w, rowspans.get(i), subLines));
        }
        int lidx = 0;
        long lnextpos = 0;
        List<LineSet<A>> overhangs = overhangCells;
        overhangCells = new ArrayList<>();
        for (LineSet<A> ls : overhangs) {
            while (lidx < lineSets.size() && Usize.lt(lineSets.get(lidx).pos, ls.pos)) {
                lnextpos = lineSets.get(lidx).pos + lineSets.get(lidx).width + 1;
                lidx++;
            }
            if (lidx >= lineSets.size()) {
                if (Usize.lt(lnextpos, ls.pos)) {
                    lineSets.add(new LineSet<>(lnextpos, Usize.satSub(ls.pos, lnextpos + 1), 1, new ArrayList<>()));
                }
                if (Usize.gt(ls.pos + ls.width, totWidth)) {
                    totWidth = ls.pos + ls.width + 1;
                }
                lineSets.add(ls);
            } else {
                lineSets.set(lidx, ls);
            }
        }
        totWidth = Usize.satSub(totWidth, 1);
        BorderHoriz<List<A>> nextBorder = new BorderHoriz<>(totWidth, tag());
        if (!lines.isEmpty() && lines.get(lines.size() - 1) instanceof RenderLine.Border<List<A>> prev) {
            long pos = 0;
            for (LineSet<A> ls : lineSets.subList(0, Math.max(lineSets.size() - 1, 0))) {
                prev.border().joinBelow(pos + ls.width);
                nextBorder.joinAbove(pos + ls.width);
                pos += ls.width + 1;
            }
            if (!lineSets.isEmpty()) {
                prev.border().stretchTo(pos + lineSets.get(lineSets.size() - 1).width);
            }
        }
        String[] columnPadding = new String[lineSets.size()];
        if (collapse) {
            long pos = 0;
            for (LineSet<A> ls : lineSets) {
                if (!ls.lines.isEmpty() && ls.lines.get(0) instanceof RenderLine.Border<List<A>> top) {
                    if (lines.isEmpty()) {
                        throw new RustPanic("No previous line");
                    }
                    if (!(lines.get(lines.size() - 1) instanceof RenderLine.Border<List<A>> prev)) {
                        throw new RustPanic("internal error: entered unreachable code");
                    }
                    ls.lines.remove(0);
                    prev.border().mergeFromBelow(top.border(), pos);
                }
                pos += ls.width + 1;
            }
            pos = 0;
            for (int col = 0; col < lineSets.size(); col++) {
                LineSet<A> ls = lineSets.get(col);
                if (!ls.lines.isEmpty() && ls.lines.get(ls.lines.size() - 1) instanceof RenderLine.Border<List<A>> b) {
                    nextBorder.mergeFromAbove(b.border(), pos);
                    columnPadding[col] = b.border().verticalLinesAbove();
                    ls.lines.remove(ls.lines.size() - 1);
                }
                pos += ls.width + 1;
            }
        }
        long cellHeight = 0;
        for (LineSet<A> ls : lineSets) {
            cellHeight = Usize.max(cellHeight, ls.cellHeight());
        }
        String spaces = " ".repeat(Usize.toInt(totWidth));
        int lastCellno = Math.max(lineSets.size() - 1, 0);
        for (int i = 0; Usize.lt(i, cellHeight); i++) {
            TaggedLine<List<A>> line = new TaggedLine<>();
            for (int cellno = 0; cellno < lineSets.size(); cellno++) {
                LineSet<A> ls = lineSets.get(cellno);
                RenderLine<List<A>> cell = i < ls.lines.size() ? ls.lines.get(i) : null;
                switch (cell) {
                    case RenderLine.Text<List<A>> t -> line.consume(t.line());
                    case RenderLine.Border<List<A>> b -> line.push(new Str<>(new TaggedString<>(b.border().text(),
                            tag())));
                    case null -> line.push(new Str<>(new TaggedString<>(columnPadding[cellno] != null
                            ? columnPadding[cellno] : spaces.substring(0, Usize.toInt(ls.width)), tag())));
                }
                if (cellno != lastCellno) {
                    line.pushChar(options.drawBorders() ? '│' : ' ', tag());
                }
            }
            addLine(new RenderLine.Text<>(line));
        }
        long pos = 0;
        for (LineSet<A> ls : lineSets) {
            if (Usize.gt(ls.rowspan, 1)) {
                if (Usize.lt(cellHeight, ls.lines.size())) {
                    long tmppos = pos;
                    for (TaggedLineElement<List<A>> ts : ls.lines.get((int) cellHeight).intoTaggedLine().v) {
                        long w = ts.width();
                        nextBorder.addTextSpan(tmppos, ts);
                        tmppos += w;
                    }
                } else {
                    nextBorder.addTextSpan(pos, new Str<>(new TaggedString<>(" ".repeat(Usize.toInt(ls.width)),
                            nextBorder.tag)));
                }
                long newLen = Usize.satSub(ls.lines.size(), cellHeight + 1);
                while (ls.lines.size() > newLen) {
                    ls.lines.remove(0);
                }
                overhangCells.add(new LineSet<>(ls.pos, ls.width, ls.rowspan - 1, ls.lines));
            }
            pos += ls.width + 1;
        }
        if (options.drawBorders()) {
            addLine(new RenderLine.Border<>(nextBorder));
        }
    }

    /** Appends table cells one below the other, for tables too wide for the width. */
    public void appendVertRow(List<SubRenderer<A>> cols) throws Html2TextException {
        flushWrapping();
        boolean first = true;
        for (SubRenderer<A> col : cols) {
            if (first) {
                first = false;
            } else if (options.drawBorders()) {
                addHorizontalLine(new BorderHoriz<>(width, BorderHoriz.Seg.HORIZ_VERT, tag()));
            }
            appendSubrender(col, "", "");
        }
        if (options.drawBorders()) {
            addHorizontalBorder();
        }
    }

    /** Whether this renderer has no content. */
    public boolean empty() {
        return lines.isEmpty() && (wrapping == null || wrapping.isEmpty());
    }

    private void startDecoration(TextDecorator.Decoration<A> d) throws Html2TextException {
        annStack.add(d.annotation());
        addInlineText(d.text());
    }

    private void endDecoration(String s) throws Html2TextException {
        addInlineText(s);
        popAnnotation();
    }

    private void popAnnotation() {
        if (!annStack.isEmpty()) {
            annStack.remove(annStack.size() - 1);
        }
    }

    /** Starts a hyperlink. */
    public void startLink(String target) throws Html2TextException {
        startDecoration(decorator.decorateLinkStart(target));
    }

    /** Finishes a hyperlink. */
    public void endLink() throws Html2TextException {
        endDecoration(decorator.decorateLinkEnd());
    }

    /** Starts an emphasised region. */
    public void startEmphasis() throws Html2TextException {
        startDecoration(decorator.decorateEmStart());
    }

    /** Finishes an emphasised region. */
    public void endEmphasis() throws Html2TextException {
        endDecoration(decorator.decorateEmEnd());
    }

    /** Starts a strong region. */
    public void startStrong() throws Html2TextException {
        startDecoration(decorator.decorateStrongStart());
    }

    /** Finishes a strong region. */
    public void endStrong() throws Html2TextException {
        endDecoration(decorator.decorateStrongEnd());
    }

    /** Starts a struck-out region. */
    public void startStrikeout() throws Html2TextException {
        startDecoration(decorator.decorateStrikeoutStart());
        if (options.useUnicodeStrikeout()) {
            strikeoutFilters++;
        }
    }

    /** Finishes a struck-out region. */
    public void endStrikeout() throws Html2TextException {
        if (options.useUnicodeStrikeout()) {
            if (strikeoutFilters == 0) {
                throw new RustPanic("end_strikeout() called without a corresponding start_strokeout()");
            }
            strikeoutFilters--;
        }
        endDecoration(decorator.decorateStrikeoutEnd());
    }

    /** Starts a code region. */
    public void startCode() throws Html2TextException {
        startDecoration(decorator.decorateCodeStart());
    }

    /** Finishes a code region. */
    public void endCode() throws Html2TextException {
        endDecoration(decorator.decorateCodeEnd());
    }

    /** Starts superscript text. */
    public void startSuperscript() throws Html2TextException {
        startDecoration(decorator.decorateSuperscriptStart());
    }

    /** Finishes superscript text. */
    public void endSuperscript() throws Html2TextException {
        endDecoration(decorator.decorateSuperscriptEnd());
    }

    /** Adds an image (its title text, or what the image mode makes of an empty title). */
    public void addImage(String src, String title) throws Html2TextException {
        TextDecorator.Decoration<A> d;
        if (!title.isEmpty()) {
            d = decorator.decorateImage(src, title);
        } else {
            switch (options.imgMode()) {
                case ImageRenderMode.IgnoreEmpty m -> {
                    return;
                }
                case ImageRenderMode.Filename m -> d = decorator.decorateImage(src, src.substring(
                        src.lastIndexOf('/') + 1));
                case ImageRenderMode.ShowAlways m -> d = decorator.decorateImage(src, title);
                case ImageRenderMode.Replace m -> d = decorator.decorateImage(src, m.text());
            }
        }
        annStack.add(d.annotation());
        addInlineText(d.text());
        popAnnotation();
    }

    /** The prefix of a header. */
    public String headerPrefix(long level) {
        return decorator.headerPrefix(level);
    }

    /** The prefix of a quoted block. */
    public String quotePrefix() {
        return decorator.quotePrefix();
    }

    /** The prefix of an unordered list item. */
    public String unorderedItemPrefix() {
        return decorator.unorderedItemPrefix();
    }

    /** The prefix of an ordered list item. */
    public String orderedItemPrefix(long i) {
        return decorator.orderedItemPrefix(i);
    }

    /** Records the start of a named fragment. */
    public void recordFragStart(String name) {
        wrapping().addElement(new FragmentStart<>(name));
    }

    /** Pushes a foreground colour. */
    public void pushColour(Colour colour) {
        A ann = decorator.pushColour(colour);
        if (ann != null) {
            annStack.add(ann);
        }
    }

    /** Pops the last foreground colour. */
    public void popColour() {
        if (decorator.popColour()) {
            popAnnotation();
        }
    }

    /** Pushes a background colour. */
    public void pushBgColour(Colour colour) {
        A ann = decorator.pushBgColour(colour);
        if (ann != null) {
            annStack.add(ann);
        }
    }

    /** Pops the last background colour. */
    public void popBgColour() {
        if (decorator.popBgColour()) {
            popAnnotation();
        }
    }
}
