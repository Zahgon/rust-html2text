// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

import io.github.jugglerchris.html2text.css.StyleData;
import io.github.jugglerchris.html2text.render.RenderOptions;

/** The settings of one conversion, taken from a {@link Config}. */
final class HtmlContext {
    StyleData styleData;
    boolean useDocCss;
    Long maxWrapWidth;
    boolean padBlockWidth;
    boolean allowWidthOverflow;
    long minWrapWidth;
    boolean raw;
    boolean drawBorders;
    boolean fenceTables;
    boolean wrapLinks;
    boolean includeLinkFootnotes;
    boolean useUnicodeStrikeout;
    ImageRenderMode imageMode;

    RenderOptions renderOptions() {
        return new RenderOptions(maxWrapWidth, allowWidthOverflow, padBlockWidth, raw, drawBorders, fenceTables,
                wrapLinks, includeLinkFootnotes, useUnicodeStrikeout, imageMode);
    }
}
