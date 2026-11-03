// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.Usize;
import io.github.jugglerchris.html2text.render.TaggedLineElement.Str;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dividing line between table rows which tracks intersections with vertical lines; cells spanning several rows
 * punch holes of text through it.
 *
 * @param <T> the tag type
 */
final class BorderHoriz<T> {
    /** A space on a horizontal border. */
    enum Seg {
        HORIZ('─'), JOIN_ABOVE('┴'), JOIN_BELOW('┬'), JOIN_CROSS('┼'), VERT('│'), HORIZ_VERT('/'), JOIN_LEFT('┤'),
        JOIN_RIGHT('├'), CORNER_TL('┌'), CORNER_TR('┐'), CORNER_BL('└'), CORNER_BR('┘');

        final char ch;

        Seg(char ch) {
            this.ch = ch;
        }

        /** Chops any join to the left of this cell. */
        Seg chopLeft() {
            return switch (this) {
                case JOIN_BELOW -> CORNER_TL;
                case JOIN_ABOVE -> CORNER_BL;
                case JOIN_CROSS -> JOIN_RIGHT;
                case JOIN_LEFT, CORNER_TR, CORNER_BR -> VERT;
                default -> this;
            };
        }

        /** Chops any join to the right of this cell. */
        Seg chopRight() {
            return switch (this) {
                case JOIN_BELOW -> CORNER_TR;
                case JOIN_ABOVE -> CORNER_BR;
                case JOIN_CROSS -> JOIN_LEFT;
                case JOIN_RIGHT, CORNER_TL, CORNER_BL -> VERT;
                default -> this;
            };
        }
    }

    /** A piece of text on top of the line, at a position. */
    record Hole<T>(long pos, TaggedLineElement<T> element) {}

    final List<Seg> segments;
    final T tag;
    final List<Hole<T>> holes = new ArrayList<>();

    BorderHoriz(long width, T tag) {
        this(width, Seg.HORIZ, tag);
    }

    BorderHoriz(long width, Seg type, T tag) {
        segments = new ArrayList<>(Collections.nCopies(Usize.toInt(width), type));
        this.tag = tag;
    }

    /** Stretches the line to at least {@code width}. */
    void stretchTo(long width) {
        while (Usize.gt(width, segments.size())) {
            segments.add(Seg.HORIZ);
        }
    }

    void joinAbove(long x) {
        stretchTo(x + 1);
        int i = (int) x;
        segments.set(i, switch (segments.get(i)) {
            case HORIZ, JOIN_ABOVE -> Seg.JOIN_ABOVE;
            case JOIN_BELOW, JOIN_CROSS -> Seg.JOIN_CROSS;
            case CORNER_TL -> Seg.JOIN_RIGHT;
            case CORNER_TR -> Seg.JOIN_LEFT;
            default -> segments.get(i);
        });
    }

    void joinBelow(long x) {
        stretchTo(x + 1);
        int i = (int) x;
        segments.set(i, switch (segments.get(i)) {
            case HORIZ, JOIN_BELOW -> Seg.JOIN_BELOW;
            case JOIN_ABOVE, JOIN_CROSS -> Seg.JOIN_CROSS;
            case CORNER_BL -> Seg.JOIN_RIGHT;
            case CORNER_BR -> Seg.JOIN_LEFT;
            default -> segments.get(i);
        });
    }

    /** Merges a (possibly partial) border line from below into this one. */
    void mergeFromBelow(BorderHoriz<T> other, long pos) {
        for (int idx = 0; idx < other.segments.size(); idx++) {
            switch (other.segments.get(idx)) {
                case JOIN_ABOVE, JOIN_BELOW, JOIN_CROSS, CORNER_BL, CORNER_BR -> joinBelow(idx + pos);
                default -> {}
            }
        }
    }

    /** Merges a (possibly partial) border line from above into this one. */
    void mergeFromAbove(BorderHoriz<T> other, long pos) {
        for (int idx = 0; idx < other.segments.size(); idx++) {
            switch (other.segments.get(idx)) {
                case JOIN_ABOVE, JOIN_BELOW, JOIN_CROSS, CORNER_TL, CORNER_TR -> joinAbove(idx + pos);
                default -> {}
            }
        }
    }

    /** Spaces and vertical lines matching the joins of this line from above. */
    String verticalLinesAbove() {
        StringBuilder out = new StringBuilder();
        for (Seg seg : segments) {
            out.append(switch (seg) {
                case JOIN_ABOVE, JOIN_CROSS, CORNER_BL, CORNER_BR -> '│';
                default -> ' ';
            });
        }
        return out.toString();
    }

    /** Adds a chunk of text on top of the line. */
    void addTextSpan(long pos, TaggedLineElement<T> t) {
        long width = t.width();
        stretchTo(pos + width);
        if (pos > 0 && pos - 1 < segments.size()) {
            segments.set((int) (pos - 1), segments.get((int) (pos - 1)).chopRight());
        }
        long rpos = pos + width;
        if (rpos < segments.size()) {
            segments.set((int) rpos, segments.get((int) rpos).chopLeft());
        }
        holes.add(new Hole<>(pos, t));
    }

    private String segmentText(long from, long to) {
        StringBuilder out = new StringBuilder();
        for (Seg seg : segments.subList((int) from, (int) to)) {
            out.append(seg.ch);
        }
        return out.toString();
    }

    /** The line as text with drawing characters. */
    String text() {
        StringBuilder out = new StringBuilder();
        long pos = 0;
        for (Hole<T> hole : holes) {
            out.append(segmentText(pos, hole.pos()));
            pos = hole.pos();
            out.append(hole.element().asStr());
            pos += hole.element().width();
        }
        if (pos < segments.size()) {
            out.append(segmentText(pos, segments.size()));
        }
        return out.toString();
    }

    /** The line as a tagged line: border runs with this line's tag, and the holes' own elements. */
    TaggedLine<T> intoTaggedLine() {
        TaggedLine<T> result = new TaggedLine<>();
        long pos = 0;
        for (Hole<T> hole : holes) {
            if (hole.pos() > pos) {
                result.push(new Str<>(new TaggedString<>(segmentText(pos, hole.pos()), tag)));
                pos = hole.pos();
            }
            pos += hole.element().width();
            result.push(hole.element());
        }
        if (pos < segments.size()) {
            result.push(new Str<>(new TaggedString<>(segmentText(pos, segments.size()), tag)));
        }
        return result;
    }
}
