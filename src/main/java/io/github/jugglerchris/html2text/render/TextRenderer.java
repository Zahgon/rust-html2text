// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.Html2TextException;
import io.github.jugglerchris.html2text.RustPanic;
import java.util.ArrayList;
import java.util.List;

/**
 * The renderer context while rendering a tree: a stack of sub renderers (nested blocks push their own) and the links
 * seen so far, for footnotes.
 *
 * @param <A> the annotation type
 */
public final class TextRenderer<A> {
    private final List<SubRenderer<A>> stack = new ArrayList<>();
    private final List<String> links = new ArrayList<>();
    /** The current depth of nested tables. */
    public long tableDepth;

    /** Creates the stack with its outermost renderer. */
    public TextRenderer(SubRenderer<A> outer) {
        stack.add(outer);
    }

    /** The innermost renderer. */
    public SubRenderer<A> top() {
        if (stack.isEmpty()) {
            throw new RustPanic("Underflow in renderer stack");
        }
        return stack.get(stack.size() - 1);
    }

    /** Starts a link and records its target for footnotes. */
    public void startLink(String target) throws Html2TextException {
        links.add(target);
        top().startLink(target);
    }

    /** Ends a link, adding its footnote number when footnotes are on. */
    public void endLink() throws Html2TextException {
        top().endLink();
        if (top().options().includeLinkFootnotes()) {
            top().addInlineText("[" + links.size() + "]");
        }
    }

    /** Pushes a renderer for a nested block. */
    public void push(SubRenderer<A> sub) {
        stack.add(sub);
    }

    /** Pops the innermost renderer. */
    public SubRenderer<A> pop() {
        if (stack.isEmpty()) {
            throw new RustPanic("Attempt to pop a subrender from empty stack");
        }
        return stack.remove(stack.size() - 1);
    }

    /** The only renderer left at the end, and the links seen. */
    public SubRenderer<A> outer() {
        if (stack.size() != 1) {
            throw new RustPanic("assertion `left == right` failed\n  left: " + stack.size() + "\n right: 1");
        }
        return stack.get(0);
    }

    /** The link targets seen, in order. */
    public List<String> links() {
        return links;
    }
}
