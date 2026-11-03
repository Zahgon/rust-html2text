// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

import io.github.jugglerchris.html2text.RenderNode.Cell;
import io.github.jugglerchris.html2text.RenderNode.Kind;
import io.github.jugglerchris.html2text.RenderNode.Row;
import io.github.jugglerchris.html2text.RenderNode.SizeEstimate;
import io.github.jugglerchris.html2text.TreeMapReduce.Finished;
import io.github.jugglerchris.html2text.TreeMapReduce.Hook;
import io.github.jugglerchris.html2text.TreeMapReduce.Nothing;
import io.github.jugglerchris.html2text.TreeMapReduce.Pending;
import io.github.jugglerchris.html2text.TreeMapReduce.Step;
import io.github.jugglerchris.html2text.css.ComputedStyle;
import io.github.jugglerchris.html2text.render.SubRenderer;
import io.github.jugglerchris.html2text.render.TaggedLine;
import io.github.jugglerchris.html2text.render.TextDecorator;
import io.github.jugglerchris.html2text.render.TextRenderer;
import io.github.jugglerchris.html2text.unicode.Chars;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The structure of an HTML document that can be rendered with a {@link TextDecorator}. A render tree is consumed by
 * rendering it: render it once.
 */
public final class RenderTree {
    private static final String SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    private final RenderNode root;

    RenderTree(RenderNode root) {
        this.root = root;
    }

    /** Renders the tree with the context's options, the decorator and the width, into a finished renderer. */
    <A> SubRenderer<A> render(HtmlContext context, long width, TextDecorator<A> decorator)
            throws Html2TextException {
        if (width == 0) {
            throw Html2TextException.tooNarrow();
        }
        TextDecorator<A> testDecorator = decorator.makeSubblockDecorator();
        SubRenderer<A> builder = new SubRenderer<>(width, context.renderOptions(), decorator);
        TreeMapReduce.run(context, root, (ctx, node) -> precalcSizeEstimate(node, testDecorator, ctx.minWrapWidth));
        TextRenderer<A> renderer = new TextRenderer<>(builder);
        TreeMapReduce.run(renderer, root, RenderTree::renderNode);
        SubRenderer<A> outer = renderer.outer();
        List<TaggedLine<A>> lines = outer.finalise(renderer.links());
        if (!lines.isEmpty()) {
            outer.startBlock();
            outer.fmtLinks(lines);
        }
        return outer;
    }

    private static Step<HtmlContext, RenderNode, Object> precalcSizeEstimate(RenderNode node,
            TextDecorator<?> decorator, long minWrapWidth) {
        if (node.sizeEstimate != null) {
            return new Nothing<>();
        }
        switch (node.kind) {
            case TEXT, IMG, SVG, BREAK, FRAG_START -> {
                node.calcSizeEstimate(decorator, minWrapWidth);
                return new Nothing<>();
            }
            case TABLE_ROW, TABLE_BODY, TABLE_CELL -> throw new RustPanic("not implemented");
            default -> {
                List<RenderNode> children = node.children;
                if (node.kind == Kind.TABLE) {
                    children = new ArrayList<>();
                    for (Row row : node.table.rows) {
                        for (Cell cell : row.cells) {
                            children.addAll(cell.content);
                        }
                    }
                }
                return new Pending<>(children, (ctx, cs) -> {
                    node.calcSizeEstimate(decorator, minWrapWidth);
                    return null;
                });
            }
        }
    }

    /** The style state applied to a renderer for a node, so that it can be undone. */
    private record PushedStyle(boolean colour, boolean bgColour, boolean whiteSpace, boolean preformat) {
        static <A> PushedStyle apply(TextRenderer<A> r, ComputedStyle style) {
            SubRenderer<A> top = r.top();
            boolean colour = style.colour.val() != null;
            if (colour) {
                top.pushColour(style.colour.val());
            }
            boolean bgColour = style.bgColour.val() != null;
            if (bgColour) {
                top.pushBgColour(style.bgColour.val());
            }
            WhiteSpace ws = style.whiteSpace.val();
            boolean whiteSpace = ws == WhiteSpace.PRE || ws == WhiteSpace.PRE_WRAP;
            if (whiteSpace) {
                top.pushWs(ws);
            }
            if (style.internalPre) {
                top.pushPreformat();
            }
            return new PushedStyle(colour, bgColour, whiteSpace, style.internalPre);
        }

        <A> void unwind(TextRenderer<A> r) {
            SubRenderer<A> top = r.top();
            if (bgColour) {
                top.popBgColour();
            }
            if (colour) {
                top.popColour();
            }
            if (whiteSpace) {
                top.popWs();
            }
            if (preformat) {
                top.popPreformat();
            }
        }
    }

    /** What rendering a node gives: a sub renderer for table cells, else nothing. */
    private interface Action<A> {
        void run(TextRenderer<A> r) throws Html2TextException;
    }

    private static <A> Step<TextRenderer<A>, RenderNode, Optional<SubRenderer<A>>> done() {
        return new Finished<>(Optional.empty());
    }

    private static <A> Step<TextRenderer<A>, RenderNode, Optional<SubRenderer<A>>> then(List<RenderNode> children,
            PushedStyle pushed, Action<A> after) {
        return new Pending<>(children, (r, cs) -> {
            after.run(r);
            pushed.unwind(r);
            return Optional.empty();
        });
    }

    private static <A> Step<TextRenderer<A>, RenderNode, Optional<SubRenderer<A>>> renderNode(TextRenderer<A> r,
            RenderNode tree) throws Html2TextException {
        SizeEstimate est = tree.sizeEstimate != null ? tree.sizeEstimate : SizeEstimate.ZERO;
        PushedStyle pushed = PushedStyle.apply(r, tree.style);
        switch (tree.kind) {
            case TEXT -> {
                r.top().addInlineText(tree.text);
                pushed.unwind(r);
                return done();
            }
            case CONTAINER -> {
                return then(tree.children, pushed, x -> {});
            }
            case LINK -> {
                r.startLink(tree.text);
                return then(tree.children, pushed, TextRenderer::endLink);
            }
            case EM -> {
                r.top().startEmphasis();
                return then(tree.children, pushed, x -> x.top().endEmphasis());
            }
            case STRONG -> {
                r.top().startStrong();
                return then(tree.children, pushed, x -> x.top().endStrong());
            }
            case STRIKEOUT -> {
                r.top().startStrikeout();
                return then(tree.children, pushed, x -> x.top().endStrikeout());
            }
            case CODE -> {
                r.top().startCode();
                return then(tree.children, pushed, x -> x.top().endCode());
            }
            case IMG, SVG -> {
                r.top().addImage(tree.kind == Kind.IMG ? tree.text : "", tree.title);
                pushed.unwind(r);
                return done();
            }
            case BLOCK, LIST_ITEM -> {
                r.top().startBlock();
                return then(tree.children, pushed, x -> x.top().endBlock());
            }
            case HEADER -> {
                String prefix = r.top().headerPrefix(tree.number);
                long innerWidth = Usize.satSub(est.minWidth(), est.prefixSize());
                pushSub(r, est.prefixSize(), innerWidth);
                return then(tree.children, pushed, x -> {
                    SubRenderer<A> sub = x.pop();
                    x.top().startBlock();
                    x.top().appendSubrender(sub, prefix, prefix);
                    x.top().endBlock();
                });
            }
            case DIV -> {
                r.top().newLine();
                return then(tree.children, pushed, x -> x.top().newLine());
            }
            case BLOCK_QUOTE -> {
                String prefix = r.top().quotePrefix();
                long len = Chars.utf8Length(prefix);
                pushSub(r, len, est.minWidth() - len);
                return then(tree.children, pushed, x -> {
                    SubRenderer<A> sub = x.pop();
                    x.top().startBlock();
                    x.top().appendSubrender(sub, prefix, prefix);
                    x.top().endBlock();
                });
            }
            case UL -> {
                String prefix = r.top().unorderedItemPrefix();
                long len = Chars.utf8Length(prefix);
                return list(tree.children, pushed, x -> pushSub(x, len, est.minWidth() - len), x -> {
                    SubRenderer<A> sub = x.pop();
                    x.top().appendSubrender(sub, prefix, " ".repeat((int) len));
                });
            }
            case OL -> {
                long start = tree.number;
                long prefixWidth = Math.max(Chars.utf8Length(r.top().orderedItemPrefix(start)),
                        Chars.utf8Length(r.top().orderedItemPrefix(start + tree.children.size() - 1)));
                String prefixn = " ".repeat((int) prefixWidth);
                long[] i = {start};
                return list(tree.children, pushed,
                        x -> pushSub(x, prefixWidth, est.minWidth() - est.prefixSize()),
                        x -> {
                            SubRenderer<A> sub = x.pop();
                            String prefix1 = x.top().orderedItemPrefix(i[0]);
                            if (prefix1.length() < prefixWidth) {
                                prefix1 += " ".repeat((int) (prefixWidth - prefix1.length()));
                            }
                            x.top().appendSubrender(sub, prefix1, prefixn);
                            i[0] += 1;
                        });
            }
            case DL -> {
                r.top().startBlock();
                return then(tree.children, pushed, x -> {});
            }
            case DT -> {
                r.top().newLine();
                r.top().startEmphasis();
                return then(tree.children, pushed, x -> x.top().endEmphasis());
            }
            case DD -> {
                pushSub(r, 2, est.minWidth() - 2);
                return then(tree.children, pushed, x -> {
                    SubRenderer<A> sub = x.pop();
                    x.top().appendSubrender(sub, "  ", "  ");
                });
            }
            case BREAK -> {
                r.top().newLineHard();
                pushed.unwind(r);
                return done();
            }
            case TABLE -> {
                return renderTable(r, tree.table);
            }
            case TABLE_ROW -> {
                return tree.vertical ? renderRowVert(tree.row, pushed) : renderRow(tree.row, pushed);
            }
            case TABLE_BODY -> throw new RustPanic("not implemented: Unexpected TableBody while rendering");
            case TABLE_CELL -> {
                return new Pending<>(tree.cell.content, (x, cs) -> {
                    pushed.unwind(x);
                    return Optional.of(x.pop());
                });
            }
            case FRAG_START -> {
                r.top().recordFragStart(tree.text);
                pushed.unwind(r);
                return done();
            }
            case SUP -> {
                String digits = supDigits(tree.children);
                if (digits != null) {
                    r.top().addInlineText(digits);
                    pushed.unwind(r);
                    return done();
                }
                r.top().startSuperscript();
                return then(tree.children, pushed, x -> x.top().endSuperscript());
            }
            default -> throw new IllegalStateException(tree.kind.toString());
        }
    }

    private static <A> void pushSub(TextRenderer<A> r, long prefixLen, long minWidth) throws Html2TextException {
        r.push(r.top().newSubRenderer(r.top().widthMinus(prefixLen, minWidth)));
    }

    /** A list: each item is rendered into its own sub renderer, then appended with its prefix. */
    private static <A> Step<TextRenderer<A>, RenderNode, Optional<SubRenderer<A>>> list(List<RenderNode> items,
            PushedStyle pushed, Action<A> before, Action<A> after) {
        Hook<TextRenderer<A>, RenderNode> pre = (x, node) -> before.run(x);
        Hook<TextRenderer<A>, Optional<SubRenderer<A>>> post = (x, result) -> after.run(x);
        return new Pending<>(items, (x, cs) -> {
            pushed.unwind(x);
            return Optional.empty();
        }, pre, post);
    }

    /** The superscript characters for a {@code <sup>} holding only one text of ASCII digits, or null. */
    private static String supDigits(List<RenderNode> children) {
        if (children.size() != 1 || children.get(0).kind != Kind.TEXT) {
            return null;
        }
        String s = children.get(0).text;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            out.append(SUPERSCRIPTS.charAt(c - '0'));
        }
        return out.toString();
    }

    private static <A> Step<TextRenderer<A>, RenderNode, Optional<SubRenderer<A>>> renderTable(TextRenderer<A> r,
            RenderNode.Table table) throws Html2TextException {
        int numColumns = Usize.toInt(table.numColumns);
        SizeEstimate[] colSizes = new SizeEstimate[numColumns];
        java.util.Arrays.fill(colSizes, SizeEstimate.ZERO);
        for (Row row : table.rows) {
            int colno = 0;
            for (Cell cell : row.cells) {
                SizeEstimate e = cell.sizeEstimate();
                SizeEstimate est = new SizeEstimate(Usize.div(e.size(), cell.colspan),
                        Usize.div(e.minWidth(), cell.colspan), e.prefixSize());
                for (int i = 0; i < cell.colspan; i++) {
                    colSizes[colno + i] = colSizes[colno + i].max(est);
                }
                colno += Usize.toInt(cell.colspan);
            }
        }
        long totSize = 0;
        long minSize = Usize.satSub(numColumns, 1);
        for (SizeEstimate s : colSizes) {
            totSize += s.size();
            minSize += s.minWidth();
        }
        long width = r.top().width();
        boolean vertRow = r.top().options().raw() || Usize.gt(minSize, width) || width == 0;
        List<Long> colWidths = new ArrayList<>();
        for (SizeEstimate sz : colSizes) {
            if (vertRow) {
                colWidths.add(width);
            } else if (sz.size() == 0) {
                colWidths.add(0L);
            } else if (Usize.le(Usize.div(Usize.MAX, width), sz.size())) {
                colWidths.add(Usize.min(sz.size(), Usize.max(Usize.div(width, totSize) * sz.size(), sz.minWidth())));
            } else {
                colWidths.add(Usize.min(sz.size(), Usize.max(Usize.div(sz.size() * width, totSize), sz.minWidth())));
            }
        }
        if (!vertRow && numColumns > 0) {
            while (true) {
                long cur = numColumns - 1;
                for (long w : colWidths) {
                    cur += w;
                }
                if (Usize.le(cur, width)) {
                    break;
                }
                int best = 0;
                for (int i = 1; i < numColumns; i++) {
                    long w = colWidths.get(i);
                    long bw = colWidths.get(best);
                    long slack = Usize.satSub(w, colSizes[i].minWidth());
                    long bestSlack = Usize.satSub(bw, colSizes[best].minWidth());
                    if (Usize.gt(slack, bestSlack) || (slack == bestSlack && Usize.gt(w, bw))) {
                        best = i;
                    }
                }
                colWidths.set(best, colWidths.get(best) - 1);
            }
        }
        long tableWidth;
        if (vertRow) {
            tableWidth = width;
        } else {
            long sum = 0;
            long nonZero = 0;
            for (long w : colWidths) {
                sum += w;
                nonZero += w != 0 ? 1 : 0;
            }
            tableWidth = sum + Usize.satSub(nonZero, 1);
        }
        r.top().startTable();
        r.tableDepth += 1;
        if (r.top().options().fenceTables() && r.tableDepth == 1) {
            r.top().addInlineText("```");
        }
        if (tableWidth != 0 && r.top().options().drawBorders()) {
            r.top().addHorizontalBorderWidth(tableWidth);
        }
        return new Pending<>(table.intoRows(colWidths, vertRow), (x, cs) -> {
            if (x.top().options().fenceTables() && x.tableDepth == 1) {
                x.top().addInlineText("```");
            }
            x.tableDepth -= 1;
            return Optional.empty();
        });
    }

    private static <A> List<SubRenderer<A>> unwrapAll(List<Optional<SubRenderer<A>>> results) {
        List<SubRenderer<A>> subs = new ArrayList<>();
        for (Optional<SubRenderer<A>> s : results) {
            subs.add(s.orElseThrow(() -> new RustPanic(RustPanic.UNWRAP_NONE)));
        }
        return subs;
    }

    private static <A> Hook<TextRenderer<A>, RenderNode> cellRenderer(boolean panicOnOther) {
        return (x, node) -> {
            if (node.kind != Kind.TABLE_CELL) {
                if (panicOnOther) {
                    throw new RustPanic("explicit panic");
                }
                throw new Html2TextException(Html2TextException.Kind.FAIL);
            }
            if (node.cell.colWidth == null) {
                throw new RustPanic(RustPanic.UNWRAP_NONE);
            }
            x.push(x.top().newSubRenderer(node.cell.colWidth));
        };
    }

    private static <A> Step<TextRenderer<A>, RenderNode, Optional<SubRenderer<A>>> renderRow(Row row,
            PushedStyle pushed) {
        List<Long> rowspans = new ArrayList<>();
        boolean haveOverhang = false;
        for (Cell cell : row.cells) {
            rowspans.add(cell.rowspan);
            haveOverhang |= cell.isDummy;
        }
        boolean overhang = haveOverhang;
        return new Pending<>(row.intoCells(false), (x, cs) -> {
            List<SubRenderer<A>> subs = unwrapAll(cs);
            if (overhang || subs.stream().anyMatch(s -> !s.empty())) {
                x.top().appendColumnsWithBorders(subs, rowspans, true);
            }
            pushed.unwind(x);
            return Optional.empty();
        }, cellRenderer(true), (x, result) -> {});
    }

    private static <A> Step<TextRenderer<A>, RenderNode, Optional<SubRenderer<A>>> renderRowVert(Row row,
            PushedStyle pushed) {
        return new Pending<>(row.intoCells(true), (x, cs) -> {
            x.top().appendVertRow(unwrapAll(cs));
            pushed.unwind(x);
            return Optional.empty();
        }, cellRenderer(false), (x, result) -> {});
    }
}
