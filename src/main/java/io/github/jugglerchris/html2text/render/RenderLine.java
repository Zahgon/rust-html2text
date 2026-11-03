// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

/**
 * A rendered line: text, or a table border line.
 *
 * @param <T> the tag type
 */
sealed interface RenderLine<T> {
    /** Some rendered text. */
    record Text<T>(TaggedLine<T> line) implements RenderLine<T> {}

    /** A table border line. */
    record Border<T>(BorderHoriz<T> border) implements RenderLine<T> {}

    /** The line as plain text. */
    default String text() {
        return switch (this) {
            case Text<T> t -> t.line().text();
            case Border<T> b -> b.border().text();
        };
    }

    /** The line as a tagged line (a border squashed into strings). */
    default TaggedLine<T> intoTaggedLine() {
        return switch (this) {
            case Text<T> t -> t.line();
            case Border<T> b -> b.border().intoTaggedLine();
        };
    }

    /** Whether the line has content: text that is not empty, or a border without text holes. */
    default boolean hasContent() {
        return switch (this) {
            case Text<T> t -> !t.line().isEmpty();
            case Border<T> b -> b.border().holes.isEmpty();
        };
    }
}
