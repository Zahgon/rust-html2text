// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.Colour;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorates or styles text: extra text around some elements (such as {@code *} around emphasis) and annotations of
 * type {@code A} attached to spans of text.
 *
 * @param <A> the annotation type
 */
public interface TextDecorator<A> {
    /**
     * Text to add, with the annotation of the text it starts.
     *
     * @param text the text
     * @param annotation the annotation
     * @param <A> the annotation type
     */
    record Decoration<A>(String text, A annotation) {}

    /** The rendering prefix and annotation for a link. */
    Decoration<A> decorateLinkStart(String url);

    /** The suffix after a link. */
    String decorateLinkEnd();

    /** The prefix and annotation for emphasis. */
    Decoration<A> decorateEmStart();

    /** The suffix after emphasis. */
    String decorateEmEnd();

    /** The prefix and annotation for strong text. */
    Decoration<A> decorateStrongStart();

    /** The suffix after strong text. */
    String decorateStrongEnd();

    /** The prefix and annotation for struck-out text. */
    Decoration<A> decorateStrikeoutStart();

    /** The suffix after struck-out text. */
    String decorateStrikeoutEnd();

    /** The prefix and annotation for code. */
    Decoration<A> decorateCodeStart();

    /** The suffix after code. */
    String decorateCodeEnd();

    /** The annotation for the first part of a preformatted line. */
    A decoratePreformatFirst();

    /** The annotation for a continuation of a preformatted line that does not fit. */
    A decoratePreformatCont();

    /** The text and annotation for an image. */
    Decoration<A> decorateImage(String src, String title);

    /** The prefix of a header of the given level. */
    String headerPrefix(long level);

    /** The prefix of a quoted block. */
    String quotePrefix();

    /** The prefix of an unordered list item. */
    String unorderedItemPrefix();

    /** The prefix of the ordered list item numbered {@code i}. */
    String orderedItemPrefix(long i);

    /** A decorator of the same type for sub blocks. */
    TextDecorator<A> makeSubblockDecorator();

    /** The default annotation (Rust's {@code Default::default()} of the annotation type). */
    A defaultAnnotation();

    /** The annotation for a foreground colour, or null. */
    default A pushColour(Colour colour) {
        return null;
    }

    /** Whether popping a colour removes an annotation. */
    default boolean popColour() {
        return false;
    }

    /** The annotation for a background colour, or null. */
    default A pushBgColour(Colour colour) {
        return null;
    }

    /** Whether popping a background colour removes an annotation. */
    default boolean popBgColour() {
        return false;
    }

    /** The prefix and annotation for superscript text. */
    default Decoration<A> decorateSuperscriptStart() {
        return new Decoration<>("^{", defaultAnnotation());
    }

    /** The suffix after superscript text. */
    default String decorateSuperscriptEnd() {
        return "}";
    }

    /** The lines added at the end of a document: the link footnotes for {@code urls}, in order. */
    default List<TaggedLine<A>> finalise(List<String> urls) {
        List<TaggedLine<A>> out = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            out.add(TaggedLine.fromString("[" + (i + 1) + "]: " + urls.get(i), defaultAnnotation()));
        }
        return out;
    }
}
