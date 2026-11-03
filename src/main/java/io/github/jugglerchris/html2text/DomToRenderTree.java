// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

import io.github.jugglerchris.html2text.RenderNode.Cell;
import io.github.jugglerchris.html2text.RenderNode.Kind;
import io.github.jugglerchris.html2text.RenderNode.Row;
import io.github.jugglerchris.html2text.TreeMapReduce.Finished;
import io.github.jugglerchris.html2text.TreeMapReduce.Nothing;
import io.github.jugglerchris.html2text.TreeMapReduce.Pending;
import io.github.jugglerchris.html2text.TreeMapReduce.Reducer;
import io.github.jugglerchris.html2text.TreeMapReduce.Step;
import io.github.jugglerchris.html2text.css.ComputedStyle;
import io.github.jugglerchris.html2text.css.Specificity;
import io.github.jugglerchris.html2text.css.StyleData;
import io.github.jugglerchris.html2text.css.WithSpec;
import io.github.jugglerchris.html2text.dom.Attribute;
import io.github.jugglerchris.html2text.dom.Namespace;
import io.github.jugglerchris.html2text.dom.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Converts a DOM into a render tree. */
final class DomToRenderTree {
    private static final Step<HtmlContext, Node, RenderNode> NOTHING = new Nothing<>();

    private DomToRenderTree() {}

    /** The render tree of a document, or null; with document CSS on, the document's styles join the context's. */
    static RenderNode convert(Node document, HtmlContext context) throws Html2TextException {
        if (context.useDocCss) {
            StyleData docStyle = StyleData.fromDocument(document);
            docStyle.merge(context.styleData);
            context.styleData = docStyle;
        }
        return TreeMapReduce.run(context, document, DomToRenderTree::process);
    }

    private static Step<HtmlContext, Node, RenderNode> pending(Node node, Reducer<HtmlContext, RenderNode> f) {
        return new Pending<>(node.children(), f);
    }

    private static Step<HtmlContext, Node, RenderNode> pendingNoEmpty(Node node,
            Reducer<HtmlContext, RenderNode> f) {
        return new Pending<>(node.children(), (ctx, cs) -> cs.isEmpty() ? null : f.reduce(ctx, cs));
    }

    private static Step<HtmlContext, Node, RenderNode> containerOf(Node node, Kind kind, ComputedStyle style,
            boolean noEmpty) {
        Reducer<HtmlContext, RenderNode> f = (ctx, cs) -> RenderNode.container(kind, cs, style);
        return noEmpty ? pendingNoEmpty(node, f) : pending(node, f);
    }

    private static Step<HtmlContext, Node, RenderNode> process(HtmlContext context, Node node) {
        return switch (node.kind()) {
            case DOCUMENT -> pending(node, (ctx, cs) -> RenderNode.container(Kind.CONTAINER, cs,
                    new ComputedStyle()));
            case ELEMENT -> element(context, node);
            case TEXT -> new Finished<>(RenderNode.text(node.text(), new ComputedStyle()));
            default -> NOTHING;
        };
    }

    /** Rust's {@code str::parse::<usize>}: digits with an optional '+', or null. */
    private static Long parseUsize(String v) {
        int start = v.startsWith("+") ? 1 : 0;
        if (start == v.length()) {
            return null;
        }
        for (int i = start; i < v.length(); i++) {
            if (v.charAt(i) < '0' || v.charAt(i) > '9') {
                return null;
            }
        }
        try {
            return Long.parseUnsignedLong(v.substring(start));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Rust's {@code str::parse::<i64>}, or null. */
    private static Long parseI64(String v) {
        if (v.isEmpty() || v.equals("+") || v.equals("-")) {
            return null;
        }
        int start = v.charAt(0) == '+' || v.charAt(0) == '-' ? 1 : 0;
        for (int i = start; i < v.length(); i++) {
            if (v.charAt(i) < '0' || v.charAt(i) > '9') {
                return null;
            }
        }
        try {
            return Long.parseLong(v.charAt(0) == '+' ? v.substring(1) : v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Step<HtmlContext, Node, RenderNode> element(HtmlContext context, Node node) {
        ComputedStyle computed = context.styleData.computedStyle(new ComputedStyle(), node, context.useDocCss);
        if (Boolean.TRUE.equals(computed.displayNone.val())) {
            return NOTHING;
        }
        ComputedStyle before = computed.contentBefore;
        ComputedStyle after = computed.contentAfter;
        boolean fragFromNameAttr = false;
        String ns = node.name().ns();
        String local = node.name().local();
        Step<HtmlContext, Node, RenderNode> result;
        if (ns.equals(Namespace.SVG) && local.equals("svg")) {
            result = new Finished<>(svg(node, computed));
        } else if (!ns.equals(Namespace.HTML)) {
            result = containerOf(node, Kind.CONTAINER, computed, true);
        } else {
            switch (local) {
                case "html", "body" -> result = containerOf(node, Kind.CONTAINER, computed, false);
                case "link", "meta", "hr", "script", "style", "head" -> result = NOTHING;
                case "span" -> result = containerOf(node, Kind.CONTAINER, computed, true);
                case "a" -> {
                    fragFromNameAttr = true;
                    String href = node.attr("href");
                    if (href != null) {
                        result = pending(node, (ctx, cs) -> {
                            for (RenderNode c : cs) {
                                if (!c.isShallowEmpty()) {
                                    RenderNode link = RenderNode.container(Kind.LINK, cs, computed);
                                    link.text = href;
                                    return link;
                                }
                            }
                            return null;
                        });
                    } else {
                        result = containerOf(node, Kind.CONTAINER, computed, false);
                    }
                }
                case "em", "i", "ins" -> result = containerOf(node, Kind.EM, computed, false);
                case "strong", "b" -> result = containerOf(node, Kind.STRONG, computed, false);
                case "s", "del" -> result = containerOf(node, Kind.STRIKEOUT, computed, false);
                case "code" -> result = containerOf(node, Kind.CODE, computed, false);
                case "img" -> result = img(node, computed);
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    long level = local.charAt(1) - '0';
                    result = pending(node, (ctx, cs) -> {
                        RenderNode header = RenderNode.container(Kind.HEADER, cs, computed);
                        header.number = level;
                        return header;
                    });
                }
                case "p" -> result = containerOf(node, Kind.BLOCK, computed, true);
                case "li" -> result = containerOf(node, Kind.LIST_ITEM, computed, false);
                case "sup" -> result = containerOf(node, Kind.SUP, computed, false);
                case "div" -> result = containerOf(node, Kind.DIV, computed, true);
                case "pre" -> result = pending(node, (ctx, cs) -> {
                    computed.whiteSpace.maybeUpdate(false, WithSpec.Origin.AGENT, Specificity.NONE, WhiteSpace.PRE);
                    computed.internalPre = true;
                    return RenderNode.container(Kind.BLOCK, cs, computed);
                });
                case "br" -> result = new Finished<>(new RenderNode(Kind.BREAK, computed));
                case "wbr" -> result = new Finished<>(RenderNode.text("​", computed));
                case "table" -> result = table(node, computed);
                case "thead", "tbody" -> result = tbody(node, computed);
                case "tr" -> result = pending(node, (ctx, cs) -> {
                    List<Cell> cells = new ArrayList<>();
                    for (RenderNode c : cs) {
                        if (c.kind == Kind.TABLE_CELL) {
                            cells.add(c.cell);
                        }
                    }
                    RenderNode row = new RenderNode(Kind.TABLE_ROW, computed);
                    row.row = new Row(cells, computed);
                    return row;
                });
                case "th", "td" -> result = td(node, computed);
                case "blockquote" -> result = containerOf(node, Kind.BLOCK_QUOTE, computed, true);
                case "ul" -> result = containerOf(node, Kind.UL, computed, true);
                case "ol" -> {
                    String startAttr = node.attr("start");
                    Long parsed = startAttr == null ? null : parseI64(startAttr);
                    long start = parsed == null ? 1 : parsed;
                    result = pendingNoEmpty(node, (ctx, cs) -> {
                        RenderNode ol = RenderNode.container(Kind.OL, filter(cs, Kind.LIST_ITEM, null), computed);
                        ol.number = start;
                        return ol;
                    });
                }
                case "dl" -> result = pendingNoEmpty(node, (ctx, cs) -> RenderNode.container(Kind.DL,
                        filter(cs, Kind.DT, Kind.DD), computed));
                case "dt" -> result = containerOf(node, Kind.DT, computed, false);
                case "dd" -> result = containerOf(node, Kind.DD, computed, false);
                default -> result = containerOf(node, Kind.CONTAINER, computed, true);
            }
        }
        String fragment = null;
        for (Attribute attr : node.attrs()) {
            String name = attr.name().local();
            if (name.equals("id") || (fragFromNameAttr && name.equals("name"))) {
                fragment = attr.value();
                break;
            }
        }
        if (before != null || after != null) {
            result = wrap(result, rendered -> {
                if (before != null && before.content.val() != null) {
                    rendered = insertChild(RenderNode.text(before.content.val(), new ComputedStyle()), rendered, true);
                }
                if (after != null && after.content.val() != null) {
                    rendered = insertChild(RenderNode.text(after.content.val(), new ComputedStyle()), rendered, false);
                }
                return rendered;
            });
        }
        if (fragment == null) {
            return result;
        }
        String fragName = fragment;
        if (result instanceof Nothing) {
            return new Finished<>(fragStart(fragName));
        }
        if (result instanceof Pending<HtmlContext, Node, RenderNode> p) {
            Reducer<HtmlContext, RenderNode> cons = p.cons();
            return new Pending<>(p.children(), (ctx, cs) -> {
                RenderNode inner = cons.reduce(ctx, cs);
                return inner == null ? fragStart(fragName) : insertChild(fragStart(fragName), inner, true);
            }, p.before(), p.after());
        }
        return wrap(result, n -> insertChild(fragStart(fragName), n, true));
    }

    private static RenderNode fragStart(String name) {
        RenderNode node = new RenderNode(Kind.FRAG_START, new ComputedStyle());
        node.text = name;
        return node;
    }

    /** Applies {@code f} to the node a step produces, now or when its children are done. */
    private static Step<HtmlContext, Node, RenderNode> wrap(Step<HtmlContext, Node, RenderNode> step,
            UnaryOperator<RenderNode> f) {
        return switch (step) {
            case Finished<HtmlContext, Node, RenderNode> fin -> new Finished<>(f.apply(fin.result()));
            case Nothing<HtmlContext, Node, RenderNode> n -> n;
            case Pending<HtmlContext, Node, RenderNode> p -> new Pending<>(p.children(), (ctx, cs) -> {
                RenderNode inner = p.cons().reduce(ctx, cs);
                return inner == null ? null : f.apply(inner);
            }, p.before(), p.after());
        };
    }

    private static List<RenderNode> filter(List<RenderNode> cs, Kind kind, Kind otherKind) {
        List<RenderNode> out = new ArrayList<>();
        for (RenderNode c : cs) {
            if (c.kind == kind || c.kind == otherKind) {
                out.add(c);
            }
        }
        return out;
    }

    private static RenderNode svg(Node node, ComputedStyle computed) {
        String title = null;
        for (Node child : node.children()) {
            if (child.kind() == Node.Kind.ELEMENT) {
                if (!child.isElement(Namespace.SVG, "title")) {
                    break;
                }
                StringBuilder t = new StringBuilder();
                for (Node sub : child.children()) {
                    if (sub.kind() == Node.Kind.TEXT) {
                        t.append(sub.text());
                    }
                }
                title = t.toString();
            }
        }
        RenderNode out = new RenderNode(Kind.SVG, computed);
        out.title = title == null ? "" : title;
        return out;
    }

    private static Step<HtmlContext, Node, RenderNode> img(Node node, ComputedStyle computed) {
        String title = null;
        String src = null;
        for (Attribute attr : node.attrs()) {
            String name = attr.name().local();
            if (name.equals("alt") && !attr.value().isEmpty()) {
                title = attr.value();
            }
            if (name.equals("src") && !attr.value().isEmpty()) {
                src = attr.value();
            }
            if (title != null && src != null) {
                break;
            }
        }
        if (src == null) {
            return NOTHING;
        }
        RenderNode out = new RenderNode(Kind.IMG, computed);
        out.text = src;
        out.title = title == null ? "" : title;
        return new Finished<>(out);
    }

    private static Step<HtmlContext, Node, RenderNode> table(Node node, ComputedStyle computed) {
        return pending(node, (ctx, cs) -> {
            List<Row> rows = new ArrayList<>();
            for (RenderNode body : cs) {
                if (body.kind == Kind.TABLE_BODY) {
                    rows.addAll(body.rows);
                }
            }
            if (rows.isEmpty()) {
                return null;
            }
            RenderNode out = new RenderNode(Kind.TABLE, computed);
            out.table = new RenderNode.Table(rows);
            return out;
        });
    }

    private static Step<HtmlContext, Node, RenderNode> tbody(Node node, ComputedStyle computed) {
        return pendingNoEmpty(node, (ctx, cs) -> {
            List<Row> rows = new ArrayList<>();
            for (RenderNode c : cs) {
                if (c.kind == Kind.TABLE_ROW) {
                    rows.add(c.row);
                }
            }
            long maxColumns = 0;
            boolean anyRow = false;
            long[] spans = new long[rows.size()];
            boolean[] hasZero = new boolean[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                for (Cell cell : rows.get(i).cells) {
                    hasZero[i] |= cell.colspan == 0;
                    spans[i] += Usize.max(cell.colspan, 1);
                }
                maxColumns = anyRow ? Usize.max(maxColumns, spans[i]) : spans[i];
                anyRow = true;
            }
            if (!anyRow) {
                maxColumns = 1;
            }
            for (int i = 0; i < rows.size(); i++) {
                if (hasZero[i]) {
                    for (Cell cell : rows.get(i).cells) {
                        if (cell.colspan == 0) {
                            cell.colspan = maxColumns - spans[i] + 1;
                        }
                    }
                }
            }
            RenderNode out = new RenderNode(Kind.TABLE_BODY, computed);
            out.rows = rows;
            return out;
        });
    }

    private static Step<HtmlContext, Node, RenderNode> td(Node node, ComputedStyle computed) {
        long colspan = 1;
        long rowspan = 1;
        for (Attribute attr : node.attrs()) {
            if (attr.name().local().equals("colspan")) {
                Long v = parseUsize(attr.value());
                colspan = v == null ? 1 : v;
            }
            if (attr.name().local().equals("rowspan")) {
                Long v = parseUsize(attr.value());
                rowspan = Usize.max(v == null ? 1 : v, 1);
            }
        }
        long finalColspan = colspan;
        long finalRowspan = rowspan;
        return pending(node, (ctx, cs) -> {
            RenderNode out = new RenderNode(Kind.TABLE_CELL, computed);
            out.cell = new Cell(finalColspan, finalRowspan, cs, computed, false);
            return out;
        });
    }

    /** Puts a node (a fragment start or pseudo-element text) at the start or end of a node's content. */
    private static RenderNode insertChild(RenderNode newChild, RenderNode orig, boolean atStart) {
        List<RenderNode> target;
        switch (orig.kind) {
            case BLOCK, LIST_ITEM, DD, DT, DL, DIV, BLOCK_QUOTE, CONTAINER -> target = orig.children;
            case TABLE_CELL -> target = orig.cell.content;
            case TABLE_ROW -> target = orig.row.cells.isEmpty() ? null : orig.row.cells.get(0).content;
            case TABLE_BODY, TABLE -> {
                List<Row> rows = orig.kind == Kind.TABLE ? orig.table.rows : orig.rows;
                target = rows.isEmpty() || rows.get(0).cells.isEmpty() ? null : rows.get(0).cells.get(0).content;
            }
            default -> {
                List<RenderNode> pair = new ArrayList<>(atStart ? List.of(newChild, orig) : List.of(orig, newChild));
                return RenderNode.container(Kind.CONTAINER, pair, new ComputedStyle());
            }
        }
        if (target != null) {
            if (atStart) {
                target.add(0, newChild);
            } else {
                target.add(newChild);
            }
        }
        return orig;
    }
}
