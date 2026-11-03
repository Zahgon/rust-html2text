// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.css;

import io.github.jugglerchris.html2text.Colour;
import io.github.jugglerchris.html2text.WhiteSpace;
import io.github.jugglerchris.html2text.css.WithSpec.Origin;
import io.github.jugglerchris.html2text.dom.Attribute;
import io.github.jugglerchris.html2text.dom.Namespace;
import io.github.jugglerchris.html2text.dom.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Stylesheet data (agent, user and author rules) used while building the render tree. */
public final class StyleData {
    /** A style property html2text supports. */
    public sealed interface Style {}

    /** {@code color}. */
    public record Foreground(Colour colour) implements Style {}

    /** {@code background-color}. */
    public record Background(Colour colour) implements Style {}

    /** {@code display: none}. */
    public record DisplayNone() implements Style {}

    /** {@code white-space}. */
    public record WhiteSpaceStyle(WhiteSpace value) implements Style {}

    /** {@code content: "text"}. */
    public record Content(String text) implements Style {}

    /**
     * A style declaration.
     *
     * @param style the property and value
     * @param important whether it is {@code !important}
     */
    public record StyleDecl(Style style, boolean important) {
        @Override
        public String toString() {
            String s = switch (style) {
                case Foreground f -> "color: " + f.colour();
                case Background b -> "background-color: " + b.colour();
                case DisplayNone d -> "display: none";
                case WhiteSpaceStyle w -> "white-space: " + w.value().css();
                case Content c -> "content: \"" + c.text() + "\"";
            };
            return important ? s + " !important" : s;
        }
    }

    /**
     * A selector with its declarations.
     *
     * @param selector the selector
     * @param styles the declarations
     */
    public record Ruleset(Selector selector, List<StyleDecl> styles) {
        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("  ").append(selector).append(" {\n");
            for (StyleDecl decl : styles) {
                out.append("    ").append(decl).append('\n');
            }
            return out.append("  }\n").toString();
        }
    }

    private final List<Ruleset> agentRules = new ArrayList<>();
    private final List<Ruleset> userRules = new ArrayList<>();
    private final List<Ruleset> authorRules = new ArrayList<>();

    /** Creates empty style data. */
    public StyleData() {}

    /** A copy of this style data. */
    public StyleData copy() {
        StyleData out = new StyleData();
        out.merge(this);
        return out;
    }

    private static void addCss(String css, List<Ruleset> rules) {
        for (CssParser.Rule rule : new CssParser(css).stylesheet()) {
            List<StyleDecl> styles = CssParser.stylesFromProperties(rule.declarations());
            if (!styles.isEmpty()) {
                for (Selector selector : rule.selectors()) {
                    rules.add(new Ruleset(selector, styles));
                }
            }
        }
    }

    /** Adds rules to the user agent rules. */
    public void addAgentRules(List<Ruleset> rules) {
        agentRules.addAll(rules);
    }

    /** Adds CSS source to the user agent ("browser") rules. */
    public void addAgentCss(String css) {
        addCss(css, agentRules);
    }

    /** Adds CSS source to the user rules. */
    public void addUserCss(String css) {
        addCss(css, userRules);
    }

    /** Adds CSS source to the document (author) rules. */
    public void addAuthorCss(String css) {
        addCss(css, authorRules);
    }

    /** Appends the rules of {@code other} to these. */
    public void merge(StyleData other) {
        agentRules.addAll(other.agentRules);
        userRules.addAll(other.userRules);
        authorRules.addAll(other.authorRules);
    }

    /** The style of an element: the parent's style updated by every matching rule and the element's attributes. */
    public ComputedStyle computedStyle(ComputedStyle parentStyle, Node node, boolean useDocCss) {
        ComputedStyle result = parentStyle.copy();
        mergeRules(result, node, Origin.AGENT, agentRules);
        mergeRules(result, node, Origin.USER, userRules);
        mergeRules(result, node, Origin.AUTHOR, authorRules);
        if (useDocCss && node.kind() == Node.Kind.ELEMENT) {
            for (Attribute attr : node.attrs()) {
                String name = attr.name().local();
                if (name.equals("style")) {
                    for (StyleDecl style : CssParser.parseStyleAttribute(attr.value())) {
                        merge(result, false, Origin.AUTHOR, Specificity.INLINE, null, style);
                    }
                } else if (name.equals("color") || name.equals("bgcolor")) {
                    Colour colour = CssParser.parseColorAttribute(attr.value());
                    if (colour != null) {
                        Style style = name.equals("color") ? new Foreground(colour) : new Background(colour);
                        merge(result, false, Origin.AUTHOR, Specificity.INLINE, null, new StyleDecl(style, false));
                    }
                }
            }
        }
        return result;
    }

    private static void mergeRules(ComputedStyle result, Node node, Origin origin, List<Ruleset> rules) {
        for (Ruleset rule : rules) {
            if (rule.selector().matches(node)) {
                for (StyleDecl style : rule.styles()) {
                    merge(result, style.important(), origin, rule.selector().specificity(),
                            rule.selector().pseudoElement(), style);
                }
            }
        }
    }

    private static void merge(ComputedStyle result, boolean important, Origin origin, Specificity specificity,
            String pseudoElement, StyleDecl decl) {
        ComputedStyle target = result;
        if ("before".equals(pseudoElement)) {
            if (result.contentBefore == null) {
                result.contentBefore = new ComputedStyle();
            }
            target = result.contentBefore;
        } else if ("after".equals(pseudoElement)) {
            if (result.contentAfter == null) {
                result.contentAfter = new ComputedStyle();
            }
            target = result.contentAfter;
        }
        switch (decl.style()) {
            case Foreground f -> target.colour.maybeUpdate(important, origin, specificity, f.colour());
            case Background b -> target.bgColour.maybeUpdate(important, origin, specificity, b.colour());
            case DisplayNone d -> target.displayNone.maybeUpdate(important, origin, specificity, Boolean.TRUE);
            case WhiteSpaceStyle w -> target.whiteSpace.maybeUpdate(important, origin, specificity, w.value());
            case Content c -> target.content.maybeUpdate(important, origin, specificity, c.text());
        }
    }

    /** The rules as the original's {@code Display} writes them. */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        appendRules(out, "Agent rules:\n", agentRules);
        appendRules(out, "User rules:\n", userRules);
        appendRules(out, "Author rules:\n", authorRules);
        return out.toString();
    }

    private static void appendRules(StringBuilder out, String title, List<Ruleset> rules) {
        if (!rules.isEmpty()) {
            out.append(title);
            for (Ruleset rule : rules) {
                out.append(rule);
            }
        }
    }

    /** The author rules of every HTML {@code <style>} element of a document (unparsable CSS is skipped). */
    public static StyleData fromDocument(Node document) {
        StyleData result = new StyleData();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(document);
        List<String> sheets = new ArrayList<>();
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node.kind() == Node.Kind.ELEMENT && node.isElement(Namespace.HTML, "style")) {
                StringBuilder css = new StringBuilder();
                for (Node child : node.children()) {
                    if (child.kind() == Node.Kind.TEXT) {
                        css.append(child.text());
                    }
                }
                sheets.add(css.toString());
            } else if (node.kind() == Node.Kind.DOCUMENT || node.kind() == Node.Kind.ELEMENT) {
                for (int i = node.children().size() - 1; i >= 0; i--) {
                    stack.push(node.children().get(i));
                }
            }
        }
        for (String css : sheets) {
            result.addAuthorCss(css);
        }
        return result;
    }
}
