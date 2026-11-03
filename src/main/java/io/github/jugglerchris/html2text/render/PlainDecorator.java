// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.render;

/** Plain text with no annotations: markup is rendered as text characters or footnotes. */
public final class PlainDecorator implements TextDecorator<Unit> {
    private static final Decoration<Unit> NONE = new Decoration<>("", Unit.UNIT);

    /** Creates a plain decorator. */
    public PlainDecorator() {}

    @Override
    public Decoration<Unit> decorateLinkStart(String url) {
        return new Decoration<>("[", Unit.UNIT);
    }

    @Override
    public String decorateLinkEnd() {
        return "]";
    }

    @Override
    public Decoration<Unit> decorateEmStart() {
        return NONE;
    }

    @Override
    public String decorateEmEnd() {
        return "";
    }

    @Override
    public Decoration<Unit> decorateStrongStart() {
        return NONE;
    }

    @Override
    public String decorateStrongEnd() {
        return "";
    }

    @Override
    public Decoration<Unit> decorateStrikeoutStart() {
        return NONE;
    }

    @Override
    public String decorateStrikeoutEnd() {
        return "";
    }

    @Override
    public Decoration<Unit> decorateCodeStart() {
        return NONE;
    }

    @Override
    public String decorateCodeEnd() {
        return "";
    }

    @Override
    public Unit decoratePreformatFirst() {
        return Unit.UNIT;
    }

    @Override
    public Unit decoratePreformatCont() {
        return Unit.UNIT;
    }

    @Override
    public Decoration<Unit> decorateImage(String src, String title) {
        return new Decoration<>("[" + title + "]", Unit.UNIT);
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
    public TextDecorator<Unit> makeSubblockDecorator() {
        return new PlainDecorator();
    }

    @Override
    public Unit defaultAnnotation() {
        return Unit.UNIT;
    }
}
