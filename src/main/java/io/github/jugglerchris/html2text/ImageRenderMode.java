// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

/** How images with missing or empty alt text are handled. */
public sealed interface ImageRenderMode {
    /** Ignore {@code <img>} without alt, or {@code <svg>} without {@code <title>} (the default). */
    record IgnoreEmpty() implements ImageRenderMode {}

    /** Always process images (handled by the decorator). */
    record ShowAlways() implements ImageRenderMode {}

    /**
     * Use a fixed replacement text.
     *
     * @param text the replacement text
     */
    record Replace(String text) implements ImageRenderMode {}

    /** Use the last component of the image source, if any. */
    record Filename() implements ImageRenderMode {}
}
