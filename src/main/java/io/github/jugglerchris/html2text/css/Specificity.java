// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.css;

/**
 * The specificity of a selector (or of an inline style), compared in the order inline, id, class, type. The counts are
 * 16-bit and wrap as the original's do.
 *
 * @param inline whether this is an inline style
 * @param id number of id selectors
 * @param cls number of class, attribute and pseudo-class selectors
 * @param typ number of type selectors
 */
public record Specificity(boolean inline, int id, int cls, int typ) implements Comparable<Specificity> {
    /** No specificity. */
    public static final Specificity NONE = new Specificity(false, 0, 0, 0);
    /** The specificity of an inline style attribute. */
    public static final Specificity INLINE = new Specificity(true, 0, 0, 0);

    /** The sum of two specificities. */
    public Specificity plus(Specificity o) {
        return new Specificity(inline || o.inline, (id + o.id) & 0xFFFF, (cls + o.cls) & 0xFFFF,
                (typ + o.typ) & 0xFFFF);
    }

    @Override
    public int compareTo(Specificity o) {
        int c = Boolean.compare(inline, o.inline);
        if (c == 0) {
            c = Integer.compare(id, o.id);
        }
        if (c == 0) {
            c = Integer.compare(cls, o.cls);
        }
        return c != 0 ? c : Integer.compare(typ, o.typ);
    }
}
