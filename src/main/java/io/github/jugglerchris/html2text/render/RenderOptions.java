// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.ImageRenderMode;

/**
 * Rendering options.
 *
 * @param wrapWidth the maximum text wrap width, or null for none
 * @param allowWidthOverflow allow output wider than the width instead of failing with TooNarrow
 * @param padBlockWidth pad lines out to the full width
 * @param raw raw extraction: tables as a single column with every cell its own row
 * @param drawBorders draw table borders
 * @param fenceTables wrap tables in code fences
 * @param wrapLinks wrap link footnotes like normal text
 * @param includeLinkFootnotes add footnotes for hyperlinks
 * @param useUnicodeStrikeout strike text out with combining characters
 * @param imgMode the handling of images without alt text
 */
public record RenderOptions(Long wrapWidth, boolean allowWidthOverflow, boolean padBlockWidth, boolean raw,
        boolean drawBorders, boolean fenceTables, boolean wrapLinks, boolean includeLinkFootnotes,
        boolean useUnicodeStrikeout, ImageRenderMode imgMode) {}
