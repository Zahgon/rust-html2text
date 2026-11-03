// Copyright (c) David Tolnay; ported in jugglerchris/rust-html2text. serde_json is licensed under the Apache
// License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <http://opensource.org/licenses/MIT>, at your option.
//
// Java port of serde_json 1.0.154's syntax errors (error.rs).

package io.github.jugglerchris.html2text.json;

/**
 * A syntax error found by {@link Json#parse}. Its message is serde_json's {@code Error} Display, for example
 * {@code "expected ident at line 1 column 2"}.
 */
public final class JsonException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;

    JsonException(String code, int line, int column) {
        super(code + " at line " + line + " column " + column);
        this.line = line;
        this.column = column;
    }

    /** The one-based line of the error. */
    public int line() {
        return line;
    }

    /** The column of the error: the number of UTF-8 bytes of its line up to and including the offending byte. */
    public int column() {
        return column;
    }
}
