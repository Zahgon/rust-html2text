// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

/**
 * A failure the original library does not report as an error but panics on (an {@code unwrap} or an arithmetic
 * check failing); the message is the original's panic message.
 */
public final class RustPanic extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The panic message of {@code Option::unwrap()} on {@code None}. */
    public static final String UNWRAP_NONE = "called `Option::unwrap()` on a `None` value";

    /** Creates a panic with the given message. */
    public RustPanic(String message) {
        super(message);
    }

    /** The panic message of {@code Result::unwrap()} on an error whose {@code Debug} text is {@code debug}. */
    public static RustPanic unwrapErr(String debug) {
        return new RustPanic("called `Result::unwrap()` on an `Err` value: " + debug);
    }
}
