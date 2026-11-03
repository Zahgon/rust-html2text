// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.css;

import io.github.jugglerchris.html2text.Colour;
import io.github.jugglerchris.html2text.WhiteSpace;

/** The style computed for an element from the style rules that match it. */
public final class ComputedStyle {
    /** The foreground colour. */
    public final WithSpec<Colour> colour;
    /** The background colour. */
    public final WithSpec<Colour> bgColour;
    /** Set (to {@code true}) when {@code display: none} or an equivalent applies. */
    public final WithSpec<Boolean> displayNone;
    /** The CSS white-space property. */
    public final WithSpec<WhiteSpace> whiteSpace;
    /** The CSS content property. */
    public final WithSpec<String> content;
    /** The style of the ::before pseudo-element, if any rule set one. */
    public ComputedStyle contentBefore;
    /** The style of the ::after pseudo-element, if any rule set one. */
    public ComputedStyle contentAfter;
    /** A non-CSS flag indicating we're inside a {@code <pre>}. */
    public boolean internalPre;

    /** An empty style. */
    public ComputedStyle() {
        colour = new WithSpec<>();
        bgColour = new WithSpec<>();
        displayNone = new WithSpec<>();
        whiteSpace = new WithSpec<>();
        content = new WithSpec<>();
    }

    private ComputedStyle(ComputedStyle o) {
        colour = o.colour.copy();
        bgColour = o.bgColour.copy();
        displayNone = o.displayNone.copy();
        whiteSpace = o.whiteSpace.copy();
        content = o.content.copy();
        contentBefore = o.contentBefore == null ? null : o.contentBefore.copy();
        contentAfter = o.contentAfter == null ? null : o.contentAfter.copy();
        internalPre = o.internalPre;
    }

    /** A deep copy of this style. */
    public ComputedStyle copy() {
        return new ComputedStyle(this);
    }
}
