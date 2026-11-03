// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

import io.github.jugglerchris.html2text.Colour;

/** A decorator generating rich (styled) text: annotations instead of markup characters. */
public final class RichDecorator implements TextDecorator<RichAnnotation> {
    /** Creates a rich decorator. */
    public RichDecorator() {}

    private static Decoration<RichAnnotation> annotate(RichAnnotation annotation) {
        return new Decoration<>("", annotation);
    }

    @Override
    public Decoration<RichAnnotation> decorateLinkStart(String url) {
        return annotate(new RichAnnotation.Link(url));
    }

    @Override
    public String decorateLinkEnd() {
        return "";
    }

    @Override
    public Decoration<RichAnnotation> decorateEmStart() {
        return annotate(new RichAnnotation.Emphasis());
    }

    @Override
    public String decorateEmEnd() {
        return "";
    }

    @Override
    public Decoration<RichAnnotation> decorateStrongStart() {
        return annotate(new RichAnnotation.Strong());
    }

    @Override
    public String decorateStrongEnd() {
        return "";
    }

    @Override
    public Decoration<RichAnnotation> decorateStrikeoutStart() {
        return annotate(new RichAnnotation.Strikeout());
    }

    @Override
    public String decorateStrikeoutEnd() {
        return "";
    }

    @Override
    public Decoration<RichAnnotation> decorateCodeStart() {
        return annotate(new RichAnnotation.Code());
    }

    @Override
    public String decorateCodeEnd() {
        return "";
    }

    @Override
    public RichAnnotation decoratePreformatFirst() {
        return new RichAnnotation.Preformat(false);
    }

    @Override
    public RichAnnotation decoratePreformatCont() {
        return new RichAnnotation.Preformat(true);
    }

    @Override
    public Decoration<RichAnnotation> decorateImage(String src, String title) {
        return new Decoration<>(title, new RichAnnotation.Image(src));
    }

    @Override
    public String headerPrefix(long level) {
        return "#".repeat((int) level) + " ";
    }

    @Override
    public String quotePrefix() {
        return "> ";
    }

    @Override
    public String unorderedItemPrefix() {
        return "* ";
    }

    @Override
    public String orderedItemPrefix(long i) {
        return i + ". ";
    }

    @Override
    public TextDecorator<RichAnnotation> makeSubblockDecorator() {
        return new RichDecorator();
    }

    @Override
    public RichAnnotation defaultAnnotation() {
        return new RichAnnotation.Default();
    }

    @Override
    public RichAnnotation pushColour(Colour colour) {
        return new RichAnnotation.Fg(colour);
    }

    @Override
    public boolean popColour() {
        return true;
    }

    @Override
    public RichAnnotation pushBgColour(Colour colour) {
        return new RichAnnotation.Bg(colour);
    }

    @Override
    public boolean popBgColour() {
        return true;
    }
}
