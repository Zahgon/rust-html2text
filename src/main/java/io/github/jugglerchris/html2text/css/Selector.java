// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.css;

import io.github.jugglerchris.html2text.RustPanic;
import io.github.jugglerchris.html2text.dom.Attribute;
import io.github.jugglerchris.html2text.dom.Node;
import io.github.jugglerchris.html2text.unicode.Chars;
import java.util.List;

/**
 * A CSS selector: its components from the right (the element itself first), and an optional pseudo-element.
 *
 * @param components the components, rightmost first
 * @param pseudoElement {@code "before"}, {@code "after"} or {@code null}
 */
public record Selector(List<Component> components, String pseudoElement) {
    /** One component of a selector. */
    public sealed interface Component {}

    /** {@code .name}. */
    public record ClassName(String name) implements Component {}

    /** An element type. */
    public record Element(String name) implements Component {}

    /** {@code #id}. */
    public record Hash(String id) implements Component {}

    /** {@code *}. */
    public record Star() implements Component {}

    /** The child combinator {@code >}. */
    public record CombChild() implements Component {}

    /** The descendant combinator. */
    public record CombDescendant() implements Component {}

    /** {@code :nth-child(An+B of sel)}. */
    public record NthChild(int a, int b, Selector sel) implements Component {}

    /** {@code [name]} (value {@code null}) or {@code [name="value"]}. */
    public record Attr(String name, String value) implements Component {}

    /** A selector made of one element type. */
    public static Selector element(String name, String pseudoElement) {
        return new Selector(List.of(new Element(name)), pseudoElement);
    }

    /** Whether the selector matches the node. */
    public boolean matches(Node node) {
        return matches(0, node);
    }

    private boolean matches(int from, Node node) {
        if (from == components.size()) {
            return true;
        }
        int next = from + 1;
        switch (components.get(from)) {
            case ClassName c -> {
                if (node.kind() != Node.Kind.ELEMENT) {
                    return false;
                }
                for (Attribute attr : node.attrs()) {
                    if (attr.name().local().equals("class")) {
                        for (String cls : Chars.splitWhitespace(attr.value())) {
                            if (cls.equals(c.name())) {
                                return matches(next, node);
                            }
                        }
                    }
                }
                return false;
            }
            case Attr a -> {
                if (node.kind() != Node.Kind.ELEMENT) {
                    return false;
                }
                for (Attribute attr : node.attrs()) {
                    if (attr.name().local().equals(a.name())) {
                        return (a.value() == null || attr.value().equals(a.value())) && matches(next, node);
                    }
                }
                return false;
            }
            case Hash h -> {
                if (node.kind() == Node.Kind.ELEMENT) {
                    for (Attribute attr : node.attrs()) {
                        if (attr.name().local().equals("id") && attr.value().equals(h.id())) {
                            return matches(next, node);
                        }
                    }
                }
                return false;
            }
            case Element e -> {
                return node.kind() == Node.Kind.ELEMENT && node.name().local().equals(e.name())
                        && matches(next, node);
            }
            case Star s -> {
                return matches(next, node);
            }
            case CombChild c -> {
                return node.parent() != null && matches(next, node.parent());
            }
            case CombDescendant d -> {
                Node parent = node.parent();
                return parent != null && (matches(next, parent) || matches(from, parent));
            }
            case NthChild n -> {
                return nthChildMatches(n, next, node);
            }
        }
    }

    private boolean nthChildMatches(NthChild n, int next, Node node) {
        Node parent = node.parent();
        if (parent == null) {
            return false;
        }
        int idx = 0;
        for (Node child : parent.children()) {
            if (child.kind() == Node.Kind.ELEMENT) {
                if (n.sel().matches(child)) {
                    idx += 1;
                    if (child == node) {
                        break;
                    }
                } else if (child == node) {
                    return false;
                }
            }
        }
        if (idx == 0) {
            return false;
        }
        int idxOffset = idx - n.b();
        if (n.a() == 0) {
            return idxOffset == 0 && matches(next, node);
        }
        if (idxOffset == Integer.MIN_VALUE && n.a() == -1) {
            throw new RustPanic("attempt to calculate the remainder with overflow");
        }
        if (idxOffset % n.a() != 0) {
            return false;
        }
        return idxOffset / n.a() >= 0 && matches(next, node);
    }

    /** The specificity of the selector. */
    public Specificity specificity() {
        Specificity result = Specificity.NONE;
        for (Component c : components) {
            switch (c) {
                case ClassName x -> result = result.plus(new Specificity(false, 0, 1, 0));
                case Attr x -> result = result.plus(new Specificity(false, 0, 1, 0));
                case Element x -> result = result.plus(new Specificity(false, 0, 0, 1));
                case Hash x -> result = result.plus(new Specificity(false, 1, 0, 0));
                case NthChild x -> result = result.plus(new Specificity(false, 0, 1, 0)).plus(x.sel().specificity());
                default -> {}
            }
        }
        return result;
    }

    /** The selector as the original's {@code Display} writes it. */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (int i = components.size() - 1; i >= 0; i--) {
            out.append(switch (components.get(i)) {
                case ClassName c -> "." + c.name();
                case Element e -> e.name();
                case Hash h -> "#" + h.id();
                case Star s -> " * ";
                case CombChild c -> " > ";
                case CombDescendant d -> " ";
                case NthChild n -> ":nth-child(" + n.a() + "n+" + n.b() + ")";
                case Attr a -> a.value() == null ? "[" + a.name() + "]" : "[" + a.name() + " = \"" + a.value() + "\"]";
            });
        }
        if (pseudoElement != null) {
            out.append("::").append(pseudoElement);
        }
        return out.toString();
    }
}
