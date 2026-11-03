// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

/** The CSS white-space values html2text supports. */
public enum WhiteSpace {
    /** Collapse whitespace and wrap. */
    NORMAL("normal"),
    /** Preserve whitespace, do not wrap. */
    PRE("pre"),
    /** Preserve whitespace, wrap. */
    PRE_WRAP("pre-wrap");

    private final String css;

    WhiteSpace(String css) {
        this.css = css;
    }

    /** Whether whitespace is kept as it is. */
    public boolean preserveWhitespace() {
        return this != NORMAL;
    }

    /** The CSS keyword. */
    public String css() {
        return css;
    }
}
