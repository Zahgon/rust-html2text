// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** A non-recursive map/reduce over a tree, so that deep documents do not exhaust the stack. */
final class TreeMapReduce {
    private TreeMapReduce() {}

    /** Combines the results of a node's children into the node's result; null means no result. */
    interface Reducer<C, R> {
        R reduce(C context, List<R> children) throws Html2TextException;
    }

    /** Called before processing each child node, or after each child result. */
    interface Hook<C, X> {
        void apply(C context, X x) throws Html2TextException;
    }

    /** What processing one node gives. */
    sealed interface Step<C, N, R> {}

    /** A completed result. */
    record Finished<C, N, R>(R result) implements Step<C, N, R> {}

    /** A result to make from the children's results once they are processed. */
    record Pending<C, N, R>(List<N> children, Reducer<C, R> cons, Hook<C, N> before, Hook<C, R> after)
            implements Step<C, N, R> {
        Pending(List<N> children, Reducer<C, R> cons) {
            this(children, cons, null, null);
        }
    }

    /** No result (for example a comment). */
    record Nothing<C, N, R>() implements Step<C, N, R> {}

    /** Processes one node. */
    interface Processor<C, N, R> {
        Step<C, N, R> process(C context, N node) throws Html2TextException;
    }

    private static final class Frame<C, N, R> {
        final Reducer<C, R> cons;
        final Hook<C, N> before;
        final Hook<C, R> after;
        final List<R> results = new ArrayList<>();
        final Iterator<N> toProcess;

        Frame(Reducer<C, R> cons, Hook<C, N> before, Hook<C, R> after, List<N> children) {
            this.cons = cons;
            this.before = before;
            this.after = after;
            this.toProcess = children.iterator();
        }
    }

    /** Maps and reduces the tree under {@code top}; the result of {@code top}, or null. */
    static <C, N, R> R run(C context, N top, Processor<C, N, R> process) throws Html2TextException {
        Frame<C, N, R> last = new Frame<>((c, cs) -> cs.isEmpty() ? null : cs.get(cs.size() - 1), null, null,
                List.of(top));
        Deque<Frame<C, N, R>> stack = new ArrayDeque<>();
        while (true) {
            while (last.toProcess.hasNext()) {
                N node = last.toProcess.next();
                if (last.before != null) {
                    last.before.apply(context, node);
                }
                switch (process.process(context, node)) {
                    case Finished<C, N, R> f -> {
                        if (last.after != null) {
                            last.after.apply(context, f.result());
                        }
                        last.results.add(f.result());
                    }
                    case Pending<C, N, R> p -> {
                        stack.push(last);
                        last = new Frame<>(p.cons(), p.before(), p.after(), p.children());
                    }
                    case Nothing<C, N, R> n -> {}
                }
            }
            if (stack.isEmpty()) {
                return last.cons.reduce(context, last.results);
            }
            Frame<C, N, R> parent = stack.pop();
            R result = last.cons.reduce(context, last.results);
            if (result != null) {
                if (parent.after != null) {
                    parent.after.apply(context, result);
                }
                parent.results.add(result);
            }
            last = parent;
        }
    }
}
