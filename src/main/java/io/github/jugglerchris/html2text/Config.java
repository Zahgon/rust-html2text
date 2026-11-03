// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

import io.github.jugglerchris.html2text.css.Selector;
import io.github.jugglerchris.html2text.css.StyleData;
import io.github.jugglerchris.html2text.dom.Node;
import io.github.jugglerchris.html2text.html5.HtmlParser;
import io.github.jugglerchris.html2text.render.PlainDecorator;
import io.github.jugglerchris.html2text.render.RichAnnotation;
import io.github.jugglerchris.html2text.render.RichDecorator;
import io.github.jugglerchris.html2text.render.TaggedLine;
import io.github.jugglerchris.html2text.render.TaggedString;
import io.github.jugglerchris.html2text.render.TextDecorator;
import io.github.jugglerchris.html2text.render.Unit;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of the HTML to text translation. Build one with {@link #plain()}, {@link #rich()},
 * {@link #plainNoDecorate()} or {@link #withDecorator}, adjust it with the builder methods (which return this
 * configuration), then convert with {@link #stringFromRead} and friends.
 *
 * @param <A> the annotation type of the decorator
 */
public final class Config<A> {
    /** Turns the annotations of a span and its text into the text to output (for example with terminal colours). */
    @FunctionalInterface
    public interface ColourMap<A> {
        /** The output for {@code text} carrying {@code annotations} (outermost first). */
        String apply(List<A> annotations, String text);
    }

    /** The minimum text wrapping width by default. */
    static final long MIN_WIDTH = 3;

    private final TextDecorator<A> decorator;
    private Long maxWrapWidth;
    private final StyleData style = new StyleData();
    private boolean useDocCss;
    private boolean padBlockWidth;
    private boolean allowWidthOverflow;
    private long minWrapWidth = MIN_WIDTH;
    private boolean raw;
    private boolean drawBorders = true;
    private boolean fenceTables;
    private boolean wrapLinks = true;
    private boolean includeLinkFootnotes;
    private boolean useUnicodeStrikeout = true;
    private ImageRenderMode imageMode = new ImageRenderMode.IgnoreEmpty();

    private Config(TextDecorator<A> decorator) {
        this.decorator = decorator;
    }

    /** A configuration with a custom decorator. */
    public static <A> Config<A> withDecorator(TextDecorator<A> decorator) {
        return new Config<>(decorator);
    }

    /** A configuration with the rich decorator (annotations instead of markup). */
    public static Config<RichAnnotation> rich() {
        return withDecorator(new RichDecorator());
    }

    /** A configuration with the plain decorator, markdown-like decorations and link footnotes. */
    public static Config<Unit> plain() {
        return withDecorator(new PlainDecorator()).doDecorate().linkFootnotes(true);
    }

    /** A configuration with the plain decorator only. */
    public static Config<Unit> plainNoDecorate() {
        return withDecorator(new PlainDecorator());
    }

    HtmlContext makeContext() {
        HtmlContext c = new HtmlContext();
        c.styleData = style.copy();
        c.useDocCss = useDocCss;
        c.maxWrapWidth = maxWrapWidth;
        c.padBlockWidth = padBlockWidth;
        c.allowWidthOverflow = allowWidthOverflow;
        c.minWrapWidth = minWrapWidth;
        c.raw = raw;
        c.drawBorders = drawBorders;
        c.fenceTables = fenceTables;
        c.wrapLinks = wrapLinks;
        c.includeLinkFootnotes = includeLinkFootnotes;
        c.useUnicodeStrikeout = useUnicodeStrikeout;
        c.imageMode = imageMode;
        return c;
    }

    RenderTree doParse(HtmlContext context, byte[] input) throws Html2TextException {
        return toRenderTree(parseHtml(input), context);
    }

    private static RenderTree toRenderTree(Node dom, HtmlContext context) throws Html2TextException {
        RenderNode root = DomToRenderTree.convert(dom, context);
        if (root == null) {
            throw new Html2TextException(Html2TextException.Kind.FAIL);
        }
        return new RenderTree(root);
    }

    /** Parses HTML (UTF-8 bytes; ill-formed sequences become U+FFFD) into a DOM; returns the document node. */
    public Node parseHtml(byte[] input) {
        return HtmlParser.parseDocument(input);
    }

    /** Converts a DOM into a render tree. */
    public RenderTree domToRenderTree(Node dom) throws Html2TextException {
        return toRenderTree(dom, makeContext());
    }

    /** Renders a render tree into a string wrapped to {@code width} columns. */
    public String renderToString(RenderTree tree, long width) throws Html2TextException {
        return tree.render(makeContext(), width, decorator.makeSubblockDecorator()).intoString();
    }

    /** Renders a render tree into lines of annotated text (the outer annotation first in each list). */
    public List<TaggedLine<List<A>>> renderToLines(RenderTree tree, long width) throws Html2TextException {
        return tree.render(makeContext(), width, decorator.makeSubblockDecorator()).intoLines();
    }

    /** Reads HTML and returns text wrapped to {@code width} columns. */
    public String stringFromRead(byte[] input, long width) throws Html2TextException {
        HtmlContext context = makeContext();
        return doParse(context, input).render(context, width, decorator).intoString();
    }

    /** Reads HTML and returns lines of annotated text wrapped to {@code width} columns. */
    public List<TaggedLine<List<A>>> linesFromRead(byte[] input, long width) throws Html2TextException {
        HtmlContext context = makeContext();
        return doParse(context, input).render(context, width, decorator).intoLines();
    }

    /** Reads HTML and returns text with the colour map applied to every span; one {@code \n} per line. */
    public String coloured(byte[] input, long width, ColourMap<A> colourMap) throws Html2TextException {
        HtmlContext context = makeContext();
        return renderColoured(doParse(context, input), width, colourMap);
    }

    /** Renders a render tree with the colour map applied to every span; one {@code \n} per line. */
    public String renderColoured(RenderTree tree, long width, ColourMap<A> colourMap) throws Html2TextException {
        StringBuilder out = new StringBuilder();
        for (TaggedLine<List<A>> line : renderToLines(tree, width)) {
            for (TaggedString<List<A>> ts : line.taggedStrings()) {
                out.append(colourMap.apply(ts.tag(), ts.s()));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Adds CSS rules (as user rules) used with any HTML processed. */
    public Config<A> addCss(String css) throws Html2TextException {
        style.addUserCss(css);
        return this;
    }

    /** Adds CSS rules as user agent rules. */
    public Config<A> addAgentCss(String css) throws Html2TextException {
        style.addAgentCss(css);
        return this;
    }

    /** Uses the CSS of {@code <style>} elements and style attributes of the document. */
    public Config<A> useDocCss() {
        useDocCss = true;
        return this;
    }

    /** Pads lines out to the full render width. */
    public Config<A> padBlockWidth() {
        padBlockWidth = true;
        return this;
    }

    /** Sets the maximum text wrap width. */
    public Config<A> maxWrapWidth(long wrapWidth) {
        maxWrapWidth = wrapWidth;
        return this;
    }

    /** Allows output wider than the width instead of failing with TooNarrow. */
    public Config<A> allowWidthOverflow() {
        allowWidthOverflow = true;
        return this;
    }

    /** Sets the minimum width for text wrapping (3 by default). */
    public Config<A> minWrapWidth(long width) {
        minWrapWidth = width;
        return this;
    }

    /** Raw extraction: tables as a single column with every cell its own row; also turns table borders off. */
    public Config<A> rawMode(boolean raw) {
        this.raw = raw;
        drawBorders = false;
        return this;
    }

    /** Draws no table borders. */
    public Config<A> noTableBorders() {
        drawBorders = false;
        return this;
    }

    /** Wraps tables in code fences (triple backticks). */
    public Config<A> fenceTables() {
        fenceTables = true;
        return this;
    }

    /** Does not wrap link footnotes. */
    public Config<A> noLinkWrapping() {
        wrapLinks = false;
        return this;
    }

    /** Selects whether to strike text out with Unicode combining characters. */
    public Config<A> unicodeStrikeout(boolean useUnicode) {
        useUnicodeStrikeout = useUnicode;
        return this;
    }

    private static StyleData.Ruleset surround(String element, boolean after, String content) {
        return new StyleData.Ruleset(Selector.element(element, after ? "after" : "before"),
                List.of(new StyleData.StyleDecl(new StyleData.Content(content), false)));
    }

    /** Decorates emphasis, strong text and code similarly to markdown. */
    public Config<A> doDecorate() {
        List<StyleData.Ruleset> rules = new ArrayList<>();
        String[][] pairs = {{"em", "*"}, {"dt", "*"}, {"strong", "**"}, {"b", "**"}, {"code", "`"}};
        for (String[] pair : pairs) {
            rules.add(surround(pair[0], false, pair[1]));
            rules.add(surround(pair[0], true, pair[1]));
        }
        style.addAgentRules(rules);
        return this;
    }

    /** Adds footnotes for hyperlinks. */
    public Config<A> linkFootnotes(boolean includeFootnotes) {
        includeLinkFootnotes = includeFootnotes;
        return this;
    }

    /** Configures how images with no alt text are handled. */
    public Config<A> emptyImgMode(ImageRenderMode mode) {
        imageMode = mode;
        return this;
    }
}
