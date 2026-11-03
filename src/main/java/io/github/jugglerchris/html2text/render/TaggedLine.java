// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.Usize;
import io.github.jugglerchris.html2text.render.TaggedLineElement.Str;
import io.github.jugglerchris.html2text.unicode.Chars;
import io.github.jugglerchris.html2text.unicode.UnicodeWidth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A line of tagged text: tagged strings and fragment markers. Adjacent strings with equal tags are merged. The line
 * keeps a running width ({@code len}) updated as the original updates it, which can differ from {@link #width()}.
 *
 * @param <T> the tag type
 */
public final class TaggedLine<T> implements Iterable<TaggedLineElement<T>> {
    final List<TaggedLineElement<T>> v = new ArrayList<>();
    long len;

    /** Creates an empty line. */
    public TaggedLine() {}

    /** A line holding one string. */
    public static <T> TaggedLine<T> fromString(String s, T tag) {
        TaggedLine<T> line = new TaggedLine<>();
        line.v.add(new Str<>(new TaggedString<>(s, tag)));
        line.len = UnicodeWidth.width(s);
        return line;
    }

    /** The text of the line, without tags and markers. */
    public String text() {
        StringBuilder out = new StringBuilder();
        for (TaggedLineElement<T> e : v) {
            if (e instanceof Str<T> str) {
                out.append(str.ts().s);
            }
        }
        return out.toString();
    }

    /** Whether the line has no string (fragment markers do not count). */
    boolean isEmpty() {
        for (TaggedLineElement<T> e : v) {
            if (e instanceof Str) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private TaggedString<T> lastStr() {
        return !v.isEmpty() && v.get(v.size() - 1) instanceof Str<?> str ? (TaggedString<T>) str.ts() : null;
    }

    void pushStr(TaggedString<T> ts) {
        if (ts.s.isEmpty()) {
            return;
        }
        len += ts.width();
        TaggedString<T> prev = lastStr();
        if (prev != null && Objects.equals(prev.tag(), ts.tag())) {
            prev.s.append(ts.s);
        } else {
            v.add(new Str<>(ts.copy()));
        }
    }

    void push(TaggedLineElement<T> e) {
        if (e instanceof Str<T> str) {
            pushStr(str.ts());
        } else {
            v.add(e);
        }
    }

    void pushWs(long n, T tag) {
        pushStr(new TaggedString<>(" ".repeat(Usize.toInt(n)), tag));
    }

    void insertFront(TaggedString<T> ts) {
        len += ts.width();
        if (!v.isEmpty() && v.get(0) instanceof Str<T> first && Objects.equals(first.ts().tag(), ts.tag())) {
            first.ts().s.insert(0, ts.s);
        } else {
            v.add(0, new Str<>(ts.copy()));
        }
    }

    void pushChar(int c, T tag) {
        len += UnicodeWidth.charWidthOrZero(c);
        TaggedString<T> prev = lastStr();
        if (prev != null && Objects.equals(prev.tag(), tag)) {
            prev.s.appendCodePoint(c);
        } else {
            v.add(new Str<>(new TaggedString<>(new String(Character.toChars(c)), tag)));
        }
    }

    /** Moves the elements of {@code other} to the end of this line (the other line's width is left as it was). */
    void consume(TaggedLine<T> other) {
        for (TaggedLineElement<T> e : other.v) {
            push(e);
        }
        other.v.clear();
    }

    /** The same line with every tag transformed. */
    public <U> TaggedLine<U> mapTag(Function<T, U> f) {
        TaggedLine<U> out = new TaggedLine<>();
        for (TaggedLineElement<T> e : v) {
            out.v.add(e.mapTag(f));
        }
        out.len = len;
        return out;
    }

    List<TaggedLineElement<T>> removeItems() {
        len = 0;
        List<TaggedLineElement<T>> items = new ArrayList<>(v);
        v.clear();
        return items;
    }

    /** The elements of the line. */
    @Override
    public Iterator<TaggedLineElement<T>> iterator() {
        return Collections.unmodifiableList(v).iterator();
    }

    /** The tagged strings of the line, ignoring fragment markers. */
    public List<TaggedString<T>> taggedStrings() {
        List<TaggedString<T>> out = new ArrayList<>();
        for (TaggedLineElement<T> e : v) {
            if (e instanceof Str<T> str) {
                out.add(str.ts());
            }
        }
        return out;
    }

    /** The width of the line in cells, recomputed from its strings. */
    long width() {
        long w = 0;
        for (TaggedLineElement<T> e : v) {
            if (e instanceof Str<T> str) {
                w += str.ts().width();
            }
        }
        return w;
    }

    void padTo(long width, T tag) {
        long mine = width();
        if (Usize.gt(width, mine)) {
            pushWs(width - mine, tag);
        }
    }

    void removeLeadingWhitespace() {
        int remove = 0;
        long removed = 0;
        for (TaggedLineElement<T> e : v) {
            if (!(e instanceof Str<T> str)) {
                break;
            }
            TaggedString<T> piece = str.ts();
            String s = piece.s();
            String trimmed = Chars.trimStart(s);
            int offset = s.length() - trimmed.length();
            if (offset == 0) {
                break;
            }
            if (trimmed.isEmpty()) {
                remove++;
                removed += piece.width();
            } else {
                long orig = piece.width();
                piece.s.delete(0, offset);
                removed += orig - piece.width();
                break;
            }
        }
        v.subList(0, remove).clear();
        len -= removed;
    }

    void removeTrailingSpaces() {
        TaggedString<T> piece;
        while ((piece = lastStr()) != null) {
            int end = piece.s.length();
            while (end > 0 && piece.s.charAt(end - 1) == ' ') {
                end--;
            }
            if (end == 0) {
                len -= piece.width();
                v.remove(v.size() - 1);
            } else if (end == piece.s.length()) {
                break;
            } else {
                len -= piece.width() - UnicodeWidth.width(piece.s.subSequence(0, end));
                piece.s.setLength(end);
                break;
            }
        }
    }
}
