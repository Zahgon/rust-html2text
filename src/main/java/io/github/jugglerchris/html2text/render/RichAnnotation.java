// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.Colour;

/** Annotation type for "rich" text: text is associated with a list of these. */
public sealed interface RichAnnotation {
    /** Normal text. */
    record Default() implements RichAnnotation {}

    /**
     * A link with the target.
     *
     * @param url the target
     */
    record Link(String url) implements RichAnnotation {}

    /**
     * An image with its src (this tag is attached to the title text).
     *
     * @param src the image source
     */
    record Image(String src) implements RichAnnotation {}

    /** Emphasised text. */
    record Emphasis() implements RichAnnotation {}

    /** Strong text. */
    record Strong() implements RichAnnotation {}

    /** Struck-out text. */
    record Strikeout() implements RichAnnotation {}

    /** Code. */
    record Code() implements RichAnnotation {}

    /**
     * Preformatted text.
     *
     * @param continuation true for a continuation line of an overly-long line
     */
    record Preformat(boolean continuation) implements RichAnnotation {}

    /**
     * Colour information.
     *
     * @param colour the foreground colour
     */
    record Fg(Colour colour) implements RichAnnotation {}

    /**
     * Background colour information.
     *
     * @param colour the background colour
     */
    record Bg(Colour colour) implements RichAnnotation {}
}
