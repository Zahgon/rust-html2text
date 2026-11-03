// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

/**
 * An RGB colour value.
 *
 * @param r red value, 0 to 255
 * @param g green value, 0 to 255
 * @param b blue value, 0 to 255
 */
public record Colour(int r, int g, int b) {
    /** The colour as {@code #rrggbb}. */
    @Override
    public String toString() {
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
