// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.unicode.UnicodeWidth;
import java.util.function.Function;

/**
 * A string with extra metadata (its tag). A tagged string belongs to one line: lines copy the strings they are given,
 * so appending to one never changes another.
 *
 * @param <T> the tag type
 */
public final class TaggedString<T> {
    final StringBuilder s;
    private final T tag;

    /** Creates a tagged string. */
    public TaggedString(CharSequence s, T tag) {
        this.s = new StringBuilder(s);
        this.tag = tag;
    }

    /** The text. */
    public String s() {
        return s.toString();
    }

    /** The metadata. */
    public T tag() {
        return tag;
    }

    /** The display width in columns ({@code UnicodeWidthStr::width}). */
    public long width() {
        return UnicodeWidth.width(s);
    }

    /** The same text with the tag transformed. */
    public <U> TaggedString<U> mapTag(Function<T, U> f) {
        return new TaggedString<>(s, f.apply(tag));
    }

    TaggedString<T> copy() {
        return new TaggedString<>(s, tag);
    }
}
