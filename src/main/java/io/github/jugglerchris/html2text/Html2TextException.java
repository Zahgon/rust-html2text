// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

/** Errors from reading or rendering HTML. */
public final class Html2TextException extends Exception {
    private static final long serialVersionUID = 1L;

    /** The kinds of error. */
    public enum Kind {
        /** The output width was too narrow to render to. */
        TOO_NARROW("TooNarrow", "Output width not wide enough."),
        /** CSS parse error. */
        CSS_PARSE_ERROR("CssParseError", "Invalid CSS"),
        /** A general error was encountered. */
        FAIL("Fail", "Unknown failure");

        private final String variant;
        private final String message;

        Kind(String variant, String message) {
            this.variant = variant;
            this.message = message;
        }

        /** The name of the error variant (as the original's {@code Debug} output shows it). */
        public String variant() {
            return variant;
        }
    }

    private final Kind kind;

    /** Creates an error of the given kind. */
    public Html2TextException(Kind kind) {
        super(kind.message);
        this.kind = kind;
    }

    /** The kind of error. */
    public Kind kind() {
        return kind;
    }

    /** The output width was too narrow to render to. */
    public static Html2TextException tooNarrow() {
        return new Html2TextException(Kind.TOO_NARROW);
    }
}
