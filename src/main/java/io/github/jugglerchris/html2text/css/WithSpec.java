// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.css;

/**
 * A computed style value with the origin, specificity and importance of the rule that set it.
 *
 * @param <T> the value type
 */
public final class WithSpec<T> {
    /** Where a style rule comes from, in increasing order. */
    public enum Origin {
        /** No rule. */
        NONE,
        /** The user agent (html2text's own rules). */
        AGENT,
        /** The user (rules added by the caller). */
        USER,
        /** The document. */
        AUTHOR
    }

    private T val;
    private Origin origin = Origin.NONE;
    private Specificity specificity = Specificity.NONE;
    private boolean important;

    /** Creates an unset value. */
    public WithSpec() {}

    private WithSpec(WithSpec<T> other) {
        val = other.val;
        origin = other.origin;
        specificity = other.specificity;
        important = other.important;
    }

    /** A copy of this value. */
    public WithSpec<T> copy() {
        return new WithSpec<>(this);
    }

    /** Sets the value unless the current one has priority over the new rule. */
    public void maybeUpdate(boolean important, Origin origin, Specificity specificity, T val) {
        if (this.val != null) {
            if (this.important && !important) {
                return;
            }
            if (this.origin != origin) {
                int cmp = origin.compareTo(this.origin);
                if ((important && cmp > 0) || (!important && cmp < 0)) {
                    return;
                }
            }
            if (specificity.compareTo(this.specificity) < 0) {
                return;
            }
        }
        this.val = val;
        this.origin = origin;
        this.specificity = specificity;
        this.important = important;
    }

    /** The value, or {@code null} if unset. */
    public T val() {
        return val;
    }
}
