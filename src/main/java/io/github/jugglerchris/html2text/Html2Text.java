// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)
//
// Java port of html2text (https://github.com/jugglerchris/rust-html2text), version 0.17.4.

package io.github.jugglerchris.html2text;

import io.github.jugglerchris.html2text.css.StyleData;
import io.github.jugglerchris.html2text.dom.Node;
import io.github.jugglerchris.html2text.render.RichAnnotation;
import io.github.jugglerchris.html2text.render.TaggedLine;
import io.github.jugglerchris.html2text.render.TextDecorator;
import io.github.jugglerchris.html2text.render.TrivialDecorator;
import java.util.List;

/**
 * Converts HTML to text formats: plain text wrapped to a width, or lines of text with annotations (such as colours or
 * links) for terminal output. The input is the HTML document as UTF-8 bytes.
 */
public final class Html2Text {
    private Html2Text() {}

    /** Reads HTML and returns plain text wrapped to {@code width} columns. */
    public static String fromRead(byte[] input, long width) throws Html2TextException {
        return Config.plain().stringFromRead(input, width);
    }

    /** Reads HTML and returns text decorated by {@code decorator}, wrapped to {@code width} columns. */
    public static <A> String fromReadWithDecorator(byte[] input, long width, TextDecorator<A> decorator)
            throws Html2TextException {
        return Config.withDecorator(decorator).stringFromRead(input, width);
    }

    /** Reads HTML and returns lines of text annotated with {@link RichAnnotation}s (the outer one first). */
    public static List<TaggedLine<List<RichAnnotation>>> fromReadRich(byte[] input, long width)
            throws Html2TextException {
        return Config.rich().linesFromRead(input, width);
    }

    /** Reads HTML and returns text with {@code colourMap} applied to every annotated span. */
    public static String fromReadColoured(byte[] input, long width, Config.ColourMap<RichAnnotation> colourMap)
            throws Html2TextException {
        return Config.rich().coloured(input, width, colourMap);
    }

    /** Reads and parses HTML into a render tree (with the default configuration). */
    public static RenderTree parse(byte[] input) throws Html2TextException {
        Config<?> config = Config.withDecorator(new TrivialDecorator());
        return config.doParse(config.makeContext(), input);
    }

    /** The CSS rules of the document's {@code <style>} elements, as text. */
    public static String domToParsedStyle(Node dom) {
        return StyleData.fromDocument(dom).toString();
    }
}
