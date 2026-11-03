// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

import io.github.jugglerchris.html2text.css.ComputedStyle;
import io.github.jugglerchris.html2text.render.TextDecorator;
import io.github.jugglerchris.html2text.unicode.Chars;
import io.github.jugglerchris.html2text.unicode.UnicodeWidth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** A node of the render tree: the information distilled from the DOM, with its computed style. */
final class RenderNode {
    /** The kinds of render node. */
    enum Kind {
        TEXT, CONTAINER, LINK, EM, STRONG, STRIKEOUT, CODE, IMG, SVG, BLOCK, HEADER, DIV, BLOCK_QUOTE, UL, OL, DL, DT,
        DD, BREAK, TABLE, TABLE_BODY, TABLE_ROW, TABLE_CELL, FRAG_START, LIST_ITEM, SUP
    }

    /**
     * Size information: a rough overall size, the narrowest possible width, and a prefix size specific to the kind.
     *
     * @param size the rough overall size
     * @param minWidth the narrowest possible width
     * @param prefixSize the width of the node's prefix
     */
    record SizeEstimate(long size, long minWidth, long prefixSize) {
        static final SizeEstimate ZERO = new SizeEstimate(0, 0, 0);

        /** Two estimates one after the other: the sizes add, the larger minimum width is kept. */
        SizeEstimate add(SizeEstimate o) {
            return new SizeEstimate(size + o.size, Usize.max(minWidth, o.minWidth), 0);
        }

        /** Two estimates side by side: the minimum widths add too. */
        SizeEstimate addHor(SizeEstimate o) {
            return new SizeEstimate(size + o.size, minWidth + o.minWidth, 0);
        }

        SizeEstimate max(SizeEstimate o) {
            return new SizeEstimate(Usize.max(size, o.size), Usize.max(minWidth, o.minWidth), 0);
        }
    }

    /** A table cell. */
    static final class Cell {
        long colspan;
        final long rowspan;
        final List<RenderNode> content;
        SizeEstimate sizeEstimate;
        Long colWidth;
        Long xPos;
        final ComputedStyle style;
        final boolean isDummy;

        Cell(long colspan, long rowspan, List<RenderNode> content, ComputedStyle style, boolean isDummy) {
            this.colspan = colspan;
            this.rowspan = rowspan;
            this.content = content;
            this.style = style;
            this.isDummy = isDummy;
        }

        /** A placeholder cell covering for a cell above with a larger rowspan. */
        static Cell dummy(long colspan) {
            Cell cell = new Cell(colspan, 1, new ArrayList<>(), new ComputedStyle(), true);
            cell.sizeEstimate = SizeEstimate.ZERO;
            return cell;
        }

        SizeEstimate sizeEstimate() {
            if (sizeEstimate == null) {
                SizeEstimate size = SizeEstimate.ZERO;
                for (RenderNode node : content) {
                    size = size.add(node.sizeEstimate());
                }
                sizeEstimate = size;
            }
            return sizeEstimate;
        }
    }

    /** A table row. */
    static final class Row {
        List<Cell> cells;
        List<Long> colSizes;
        final ComputedStyle style;

        Row(List<Cell> cells, ComputedStyle style) {
            this.cells = cells;
            this.style = style;
        }

        long numCells() {
            long n = 0;
            for (Cell cell : cells) {
                n += Usize.max(cell.colspan, 1);
            }
            return n;
        }

        /** The cells as render nodes with their widths; cells whose columns have no width are skipped. */
        List<RenderNode> intoCells(boolean vertical) {
            List<RenderNode> result = new ArrayList<>();
            int colno = 0;
            long xPos = 0;
            for (Cell cell : cells) {
                long colWidth = 0;
                if (vertical) {
                    colWidth = colSizes.get(colno);
                } else {
                    for (long i = 0; Usize.lt(i, cell.colspan); i++) {
                        colWidth += colSizes.get(Usize.toInt(colno + i));
                    }
                }
                if (colWidth != 0) {
                    long thisColWidth = colWidth + cell.colspan - 1;
                    cell.colWidth = thisColWidth;
                    cell.xPos = xPos;
                    xPos += thisColWidth + 1;
                    RenderNode node = new RenderNode(Kind.TABLE_CELL, cell.style);
                    node.cell = cell;
                    result.add(node);
                }
                colno += Usize.toInt(cell.colspan);
            }
            return result;
        }
    }

    /** A table: its rows, with the column ids remapped to the smallest set the colspans need. */
    static final class Table {
        final List<Row> rows;
        final long numColumns;
        SizeEstimate sizeEstimate;

        Table(List<Row> rows) {
            TreeSet<Long> colPositions = new TreeSet<>(Long::compareUnsigned);
            List<long[]> overhang = new ArrayList<>();
            List<long[]> nextOverhang = new ArrayList<>();
            colPositions.add(0L);
            for (Row row : rows) {
                long col = 0;
                List<Cell> newCells = new ArrayList<>();
                for (Cell cell : row.cells) {
                    while (!overhang.isEmpty() && Usize.le(overhang.get(overhang.size() - 1)[1], col)) {
                        long[] used = overhang.remove(overhang.size() - 1);
                        newCells.add(Cell.dummy(used[2]));
                        col += used[2];
                        colPositions.add(col);
                        if (Usize.gt(used[0], 1)) {
                            used[0] -= 1;
                            nextOverhang.add(used);
                        }
                    }
                    if (Usize.gt(cell.rowspan, 1)) {
                        nextOverhang.add(new long[] {cell.rowspan - 1, col, cell.colspan});
                    }
                    col += cell.colspan;
                    colPositions.add(col);
                    newCells.add(cell);
                }
                while (!overhang.isEmpty()) {
                    long[] hanging = overhang.remove(overhang.size() - 1);
                    newCells.add(Cell.dummy(hanging[2]));
                    col += hanging[2];
                    colPositions.add(col);
                    if (Usize.gt(hanging[0], 1)) {
                        hanging[0] -= 1;
                        nextOverhang.add(hanging);
                    }
                }
                row.cells = newCells;
                overhang = nextOverhang;
                nextOverhang = new ArrayList<>();
                Collections.reverse(overhang);
            }
            while (!overhang.isEmpty()) {
                List<Cell> newCells = new ArrayList<>();
                long rowIncrement = Usize.MAX;
                for (long[] cell : overhang) {
                    rowIncrement = Usize.min(rowIncrement, cell[0]);
                }
                while (!overhang.isEmpty()) {
                    long[] hanging = overhang.remove(overhang.size() - 1);
                    newCells.add(Cell.dummy(hanging[2]));
                    if (Usize.gt(hanging[0], rowIncrement)) {
                        hanging[0] -= rowIncrement;
                        nextOverhang.add(hanging);
                    }
                }
                rows.add(new Row(newCells, new ComputedStyle()));
                overhang = nextOverhang;
                nextOverhang = new ArrayList<>();
                Collections.reverse(overhang);
            }
            Map<Long, Long> colmap = new HashMap<>();
            long index = 0;
            for (Long pos : colPositions) {
                colmap.put(pos, index++);
            }
            long columns = 0;
            for (Row row : rows) {
                long pos = 0;
                long mappedPos = 0;
                for (Cell cell : row.cells) {
                    long nextPos = pos + Usize.max(cell.colspan, 1);
                    Long nextMapped = colmap.get(nextPos);
                    if (nextMapped == null) {
                        throw new RustPanic(RustPanic.UNWRAP_NONE);
                    }
                    cell.colspan = nextMapped - mappedPos;
                    pos = nextPos;
                    mappedPos = nextMapped;
                }
                columns = Usize.max(columns, row.numCells());
            }
            this.rows = rows;
            this.numColumns = columns;
        }

        /** The rows as render nodes which know the column sizes. */
        List<RenderNode> intoRows(List<Long> colSizes, boolean vertical) {
            List<RenderNode> out = new ArrayList<>();
            for (Row row : rows) {
                row.colSizes = colSizes;
                RenderNode node = new RenderNode(Kind.TABLE_ROW, row.style);
                node.row = row;
                node.vertical = vertical;
                out.add(node);
            }
            return out;
        }

        SizeEstimate calcSizeEstimate() {
            if (numColumns == 0) {
                sizeEstimate = SizeEstimate.ZERO;
                return sizeEstimate;
            }
            SizeEstimate[] sizes = new SizeEstimate[Usize.toInt(numColumns)];
            java.util.Arrays.fill(sizes, SizeEstimate.ZERO);
            for (Row row : rows) {
                int colno = 0;
                for (Cell cell : row.cells) {
                    SizeEstimate cellSize = cell.sizeEstimate();
                    for (int c = 0; c < cell.colspan; c++) {
                        SizeEstimate old = sizes[colno + c];
                        sizes[colno + c] = new SizeEstimate(old.size() + Usize.div(cellSize.size(), cell.colspan),
                                Usize.max(old.minWidth(), Usize.div(cellSize.minWidth(), cell.colspan)), 0);
                    }
                    colno += Usize.toInt(cell.colspan);
                }
            }
            long size = 0;
            long minWidth = 0;
            for (SizeEstimate s : sizes) {
                size += s.size();
                minWidth += s.minWidth();
            }
            sizeEstimate = new SizeEstimate(size + Usize.satSub(numColumns, 1), minWidth + numColumns - 1, 0);
            return sizeEstimate;
        }
    }

    final Kind kind;
    final ComputedStyle style;
    /** The text of a Text node, the target of a Link, the src of an Img, the name of a FragStart. */
    String text;
    /** The title of an Img or Svg. */
    String title;
    /** The level of a Header, the start of an Ol. */
    long number;
    List<RenderNode> children;
    Table table;
    List<Row> rows;
    Row row;
    boolean vertical;
    Cell cell;
    SizeEstimate sizeEstimate;

    RenderNode(Kind kind, ComputedStyle style) {
        this.kind = kind;
        this.style = style;
    }

    static RenderNode container(Kind kind, List<RenderNode> children, ComputedStyle style) {
        RenderNode node = new RenderNode(kind, style);
        node.children = children;
        return node;
    }

    static RenderNode text(String text, ComputedStyle style) {
        RenderNode node = new RenderNode(Kind.TEXT, style);
        node.text = text;
        return node;
    }

    SizeEstimate sizeEstimate() {
        if (sizeEstimate == null) {
            throw new RustPanic(RustPanic.UNWRAP_NONE);
        }
        return sizeEstimate;
    }

    private SizeEstimate childrenSize(TextDecorator<?> decorator, long minWrapWidth) {
        SizeEstimate size = SizeEstimate.ZERO;
        for (RenderNode child : children) {
            size = size.add(child.calcSizeEstimate(decorator, minWrapWidth));
        }
        return size;
    }

    private SizeEstimate prefixed(long prefixSize, TextDecorator<?> decorator, long minWrapWidth) {
        SizeEstimate size = childrenSize(decorator, minWrapWidth).addHor(new SizeEstimate(prefixSize, prefixSize, 0));
        return new SizeEstimate(size.size(), size.minWidth(), prefixSize);
    }

    /** Calculates (or returns the already calculated) size estimate of this node. */
    SizeEstimate calcSizeEstimate(TextDecorator<?> decorator, long minWrapWidth) {
        if (sizeEstimate != null) {
            return sizeEstimate;
        }
        SizeEstimate estimate = switch (kind) {
            case TEXT, IMG, SVG -> {
                String t = kind == Kind.TEXT ? text : title;
                long len = 0;
                boolean inWhitespace = false;
                String trimmed = Chars.trimCollapsibleWs(t);
                for (int i = 0; i < trimmed.length(); ) {
                    int c = trimmed.codePointAt(i);
                    i += Character.charCount(c);
                    boolean collapsible = !Chars.alwaysTakesSpace(c);
                    if (!collapsible) {
                        len += UnicodeWidth.charWidthOrZero(c);
                        if (inWhitespace) {
                            len += 1;
                        }
                    }
                    inWhitespace = collapsible;
                }
                if (!t.isEmpty() && !Chars.alwaysTakesSpace(t.codePointAt(0)) && len > 0) {
                    len += 1;
                }
                if (kind == Kind.IMG) {
                    len += 2;
                }
                yield new SizeEstimate(len, Usize.min(len, minWrapWidth), 0);
            }
            case CONTAINER, EM, STRONG, STRIKEOUT, CODE, BLOCK, DIV, DL, DT, LIST_ITEM, SUP ->
                    childrenSize(decorator, minWrapWidth);
            case LINK -> childrenSize(decorator, minWrapWidth).add(new SizeEstimate(5, 5, 0));
            case DD -> prefixed(2, decorator, minWrapWidth);
            case BLOCK_QUOTE -> prefixed(UnicodeWidth.width(decorator.quotePrefix()), decorator, minWrapWidth);
            case UL -> prefixed(UnicodeWidth.width(decorator.unorderedItemPrefix()), decorator, minWrapWidth);
            case OL -> prefixed(olPrefixSize(number, children.size(), decorator), decorator, minWrapWidth);
            case HEADER -> prefixed(Chars.utf8Length(decorator.headerPrefix(number)), decorator, minWrapWidth);
            case BREAK -> new SizeEstimate(1, 1, 0);
            case TABLE -> table.calcSizeEstimate();
            case FRAG_START -> SizeEstimate.ZERO;
            case TABLE_ROW, TABLE_BODY, TABLE_CELL -> throw new RustPanic("not implemented");
        };
        sizeEstimate = estimate;
        return estimate;
    }

    /** The width of the widest ordered-list prefix (the numbers at either end of the list). */
    static long olPrefixSize(long start, long numItems, TextDecorator<?> decorator) {
        long maxNumber = start + numItems - 1;
        return Math.max(Chars.utf8Length(decorator.orderedItemPrefix(start)),
                Chars.utf8Length(decorator.orderedItemPrefix(maxNumber)));
    }

    /** Whether this node is definitely empty (without looking more deeply). */
    boolean isShallowEmpty() {
        return switch (kind) {
            case TEXT -> Chars.trim(text).isEmpty();
            case IMG, SVG -> Chars.trim(title).isEmpty();
            case BREAK, FRAG_START -> true;
            case TABLE, TABLE_ROW, TABLE_BODY, TABLE_CELL -> false;
            default -> children.isEmpty();
        };
    }
}
