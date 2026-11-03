// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import java.util.function.Function;

/**
 * An element of a line of tagged text: a tagged string, or a marker between document characters.
 *
 * @param <T> the tag type
 */
public sealed interface TaggedLineElement<T> {
    /**
     * A string with tag information attached.
     *
     * @param ts the string
     * @param <T> the tag type
     */
    record Str<T>(TaggedString<T> ts) implements TaggedLineElement<T> {}

    /**
     * A zero-width marker indicating the start of a named HTML fragment.
     *
     * @param name the fragment name
     * @param <T> the tag type
     */
    record FragmentStart<T>(String name) implements TaggedLineElement<T> {}

    /** The element with its tag transformed. */
    default <U> TaggedLineElement<U> mapTag(Function<T, U> f) {
        return switch (this) {
            case Str<T> str -> new Str<>(str.ts().mapTag(f));
            case FragmentStart<T> frag -> new FragmentStart<>(frag.name());
        };
    }

    /** The text width of this element (0 for a fragment start). */
    default long width() {
        return this instanceof Str<T> str ? str.ts().width() : 0;
    }

    /** The text of this element ("" for a fragment start). */
    default String asStr() {
        return this instanceof Str<T> str ? str.ts().s() : "";
    }
}
