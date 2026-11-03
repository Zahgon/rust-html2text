// Copyright 2014-2017 The html5ever Project Developers; ported in jugglerchris/rust-html2text. See the
// COPYRIGHT file at the top-level directory of this distribution.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
//
// Java port of html5ever's HTML5 tokenizer (tokenizer/mod.rs), with exact_errors off and the BOM discarded.

package io.github.jugglerchris.html2text.html5;

import static io.github.jugglerchris.html2text.html5.BufferQueue.inSet;
import static io.github.jugglerchris.html2text.html5.BufferQueue.set;

import io.github.jugglerchris.html2text.dom.Attribute;
import io.github.jugglerchris.html2text.dom.Namespace;
import io.github.jugglerchris.html2text.dom.QualName;
import io.github.jugglerchris.html2text.html5.TreeBuilder.Chars;
import io.github.jugglerchris.html2text.html5.TreeBuilder.Comment;
import io.github.jugglerchris.html2text.html5.TreeBuilder.Doctype;
import io.github.jugglerchris.html2text.html5.TreeBuilder.Simple;
import io.github.jugglerchris.html2text.html5.TreeBuilder.Tag;
import java.util.ArrayList;
import java.util.List;

/** The HTML tokenizer, feeding tokens to a tree builder. */
final class Tokenizer {
    /** Raw text kinds (the parameter of the raw data states); also the escape kinds of the script states. */
    static final int RCDATA = 0, RAWTEXT = 1, SCRIPT_DATA = 2, ESCAPED = 3, DOUBLE_ESCAPED = 4;
    private static final int UNQUOTED = 0, SINGLE_QUOTED = 1, DOUBLE_QUOTED = 2, PUBLIC = 0, SYSTEM = 1;
    private static final int CONTINUE = 0, SUSPEND = 1, BREAK = 2;
    private static final long DATA_SET = set('\r', '\0', '&', '<', '\n');
    private static final long DATA_FAST_SET = set('\r', '\0', '&', '<');
    private static final long RAWTEXT_SET = set('\r', '\0', '<', '\n');
    private static final long PLAINTEXT_SET = set('\r', '\0', '\n');
    private static final long ESCAPED_SET = set('\r', '\0', '-', '<', '\n');
    private static final long DQ_SET = set('\r', '"', '&', '\0', '\n');
    private static final long SQ_SET = set('\r', '\'', '&', '\0', '\n');
    private static final long UNQUOTED_SET = set('\r', '\t', '\n', '\f', ' ', '&', '>', '\0');

    private enum State {
        DATA, PLAINTEXT, TAG_OPEN, END_TAG_OPEN, TAG_NAME, RAW_DATA, RAW_LESS_THAN, RAW_END_TAG_OPEN, RAW_END_TAG_NAME,
        SCRIPT_ESCAPE_START, SCRIPT_ESCAPE_START_DASH, SCRIPT_ESCAPED_DASH, SCRIPT_ESCAPED_DASH_DASH,
        SCRIPT_DOUBLE_ESCAPE_END, BEFORE_ATTR_NAME, ATTR_NAME, AFTER_ATTR_NAME, BEFORE_ATTR_VALUE, ATTR_VALUE,
        AFTER_ATTR_VALUE_QUOTED, SELF_CLOSING_START_TAG, BOGUS_COMMENT, MARKUP_DECLARATION_OPEN, COMMENT_START,
        COMMENT_START_DASH, COMMENT, COMMENT_LT, COMMENT_LT_BANG, COMMENT_LT_BANG_DASH, COMMENT_LT_BANG_DASH_DASH,
        COMMENT_END_DASH, COMMENT_END, COMMENT_END_BANG, DOCTYPE, BEFORE_DOCTYPE_NAME, DOCTYPE_NAME,
        AFTER_DOCTYPE_NAME, AFTER_DOCTYPE_KEYWORD, BEFORE_DOCTYPE_ID, DOCTYPE_ID_DOUBLE_QUOTED,
        DOCTYPE_ID_SINGLE_QUOTED, AFTER_DOCTYPE_ID, BETWEEN_DOCTYPE_IDS, BOGUS_DOCTYPE, CDATA, CDATA_BRACKET,
        CDATA_END
    }

    final BufferQueue input;
    private final TreeBuilder sink;
    private State state = State.DATA;
    /** The parameter of the current state (a raw kind, escape kind, attribute value kind or doctype id kind). */
    private int kind;
    private boolean atEof;
    private CharRef charRef;
    private int currentChar;
    private boolean reconsume;
    private boolean ignoreLf;
    private boolean tagIsEnd;
    private final StringBuilder tagName = new StringBuilder();
    private boolean selfClosing;
    private List<Attribute> attrs = new ArrayList<>();
    private final StringBuilder attrName = new StringBuilder();
    private final StringBuilder attrValue = new StringBuilder();
    private final StringBuilder comment = new StringBuilder();
    private StringBuilder doctypeName;
    private StringBuilder publicId;
    private StringBuilder systemId;
    private boolean forceQuirks;
    private String lastStartTag;
    private final StringBuilder tempBuf = new StringBuilder();

    Tokenizer(TreeBuilder sink, BufferQueue input) {
        this.sink = sink;
        this.input = input;
    }

    /** Runs over the queued input; true when done (all input consumed), false to be fed again. */
    boolean feed() {
        if (input.isEmpty()) {
            return true;
        }
        if (input.peek() == 0xFEFF) {
            input.next();
        }
        return run();
    }

    private boolean run() {
        while (true) {
            int r = step();
            if (r != CONTINUE) {
                return r == SUSPEND;
            }
        }
    }

    // Preprocessing the input stream: CR and CR LF become LF.
    private int preprocess(int c) {
        if (ignoreLf) {
            ignoreLf = false;
            if (c == '\n') {
                c = input.next();
                if (c < 0) {
                    return -1;
                }
            }
        }
        if (c == '\r') {
            ignoreLf = true;
            c = '\n';
        }
        currentChar = c;
        return c;
    }

    private int getChar() {
        if (reconsume) {
            reconsume = false;
            return currentChar;
        }
        int c = input.next();
        return c < 0 ? -1 : preprocess(c);
    }

    /** A character (Integer) or a run of characters not in the set (String), or null when there is no input. */
    private Object popExceptFrom(long set) {
        if (reconsume || ignoreLf) {
            int c = getChar();
            return c < 0 ? null : (Object) c;
        }
        if (input.isEmpty()) {
            return null;
        }
        String run = input.popRun(set);
        return run != null ? run : (Object) preprocess(input.next());
    }

    /** Matches the next characters against an ASCII pattern; null when more input is needed. */
    private Boolean eat(String pat, boolean ignoreCase) {
        if (ignoreLf) {
            ignoreLf = false;
            if (peek() == '\n') {
                discardChar();
            }
        }
        input.pushFront(tempBuf.toString());
        tempBuf.setLength(0);
        Boolean matched = input.eat(pat, ignoreCase);
        if (matched == null) {
            if (atEof) {
                return false;
            }
            for (int c = input.next(); c >= 0; c = input.next()) {
                tempBuf.appendCodePoint(c);
            }
        }
        return matched;
    }

    int peek() {
        return reconsume ? currentChar : input.peek();
    }

    void discardChar() {
        if (reconsume) {
            reconsume = false;
        } else {
            input.next();
        }
    }

    void clearIgnoreLf() {
        ignoreLf = false;
    }

    void error() {
        sink.process(Simple.ERROR);
    }

    private void emitChar(int c) {
        sink.process(c == 0 ? Simple.NULL : new Chars(Chars.NOT_SPLIT, Character.toString(c)));
    }

    private void emitChars(String s) {
        sink.process(new Chars(Chars.NOT_SPLIT, s));
    }

    private void emitTempBuf() {
        String s = tempBuf.toString();
        tempBuf.setLength(0);
        emitChars(s);
    }

    private void emitComment() {
        String s = comment.toString();
        comment.setLength(0);
        sink.process(new Comment(s));
    }

    private void emitDoctype() {
        sink.process(new Doctype(str(doctypeName), str(publicId), str(systemId), forceQuirks));
        createDoctype();
    }

    private static String str(StringBuilder b) {
        return b == null ? null : b.toString();
    }

    private void createDoctype() {
        doctypeName = publicId = systemId = null;
        forceQuirks = false;
    }

    private void pushDoctypeName(int c) {
        if (doctypeName == null) {
            doctypeName = new StringBuilder();
        }
        doctypeName.appendCodePoint(c);
    }

    private StringBuilder clearDoctypeId(int idKind) {
        StringBuilder b = new StringBuilder();
        if (idKind == PUBLIC) {
            publicId = b;
        } else {
            systemId = b;
        }
        return b;
    }

    private void pushDoctypeId(int idKind, int c) {
        StringBuilder b = idKind == PUBLIC ? publicId : systemId;
        (b == null ? clearDoctypeId(idKind) : b).appendCodePoint(c);
    }

    private void createTag(boolean end, int c) {
        tagName.setLength(0);
        selfClosing = false;
        attrs = new ArrayList<>();
        tagName.appendCodePoint(c);
        tagIsEnd = end;
    }

    private boolean haveAppropriateEndTag() {
        return lastStartTag != null && tagIsEnd && lastStartTag.contentEquals(tagName);
    }

    private void createAttribute(int c) {
        finishAttribute();
        attrName.appendCodePoint(c);
    }

    private void finishAttribute() {
        if (attrName.isEmpty()) {
            return;
        }
        String name = attrName.toString();
        attrName.setLength(0);
        if (attrs.stream().anyMatch(a -> a.name().local().equals(name))) {
            error();
        } else {
            attrs.add(new Attribute(new QualName(null, Namespace.NONE, name), attrValue.toString()));
        }
        attrValue.setLength(0);
    }

    private int emitTag(State next) {
        to(next, 0);
        finishAttribute();
        String name = tagName.toString();
        tagName.setLength(0);
        if (!tagIsEnd) {
            lastStartTag = name;
        } else {
            if (!attrs.isEmpty()) {
                error();
            }
            if (selfClosing) {
                error();
            }
        }
        Tag tag = new Tag(tagIsEnd, name, selfClosing, attrs);
        attrs = new ArrayList<>();
        int r = sink.process(tag);
        switch (r) {
            case TreeBuilder.CONTINUE -> {
                return CONTINUE;
            }
            case TreeBuilder.PLAINTEXT -> to(State.PLAINTEXT, 0);
            case TreeBuilder.SCRIPT -> {
                to(State.DATA, 0);
                return BREAK;
            }
            case TreeBuilder.ENCODING -> {
                return BREAK;
            }
            default -> to(State.RAW_DATA, r);
        }
        return CONTINUE;
    }

    private int to(State s, int k) {
        state = s;
        kind = k;
        return CONTINUE;
    }

    private int reconsumeIn(State s, int k) {
        reconsume = true;
        return to(s, k);
    }

    private static boolean isAsciiAlpha(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    private static int lower(int c) {
        return c >= 'A' && c <= 'Z' ? c + 32 : c;
    }

    private static boolean isSpace(int c) {
        return c == '\t' || c == '\n' || c == '\f' || c == ' ';
    }

    private void startCharRef() {
        charRef = new CharRef(state == State.ATTR_VALUE);
    }

    private int stepCharRef() {
        int[] r = charRef.step(this);
        if (r == null) {
            return SUSPEND;
        }
        if (r != CharRef.PROGRESS) {
            processCharRef(r);
            charRef = null;
        }
        return CONTINUE;
    }

    private void processCharRef(int[] chars) {
        for (int c : chars.length == 0 ? new int[] {'&'} : chars) {
            if (state == State.ATTR_VALUE) {
                attrValue.appendCodePoint(c);
            } else if (state == State.DATA || state == State.RAW_DATA && kind == RCDATA) {
                emitChar(c);
            } else {
                throw new IllegalStateException("state " + state + " should not be reachable in process_char_ref");
            }
        }
    }

    /** Runs the state machine for one step. */
    private int step() {
        if (charRef != null) {
            return stepCharRef();
        }
        switch (state) {
            case DATA -> {
                Object r;
                if (!reconsume && !ignoreLf) {
                    int first = input.peek();
                    if (first < 0) {
                        return SUSPEND;
                    }
                    r = inSet(DATA_SET, first) ? popExceptFrom(DATA_SET) : input.popRun(DATA_FAST_SET);
                } else {
                    r = popExceptFrom(DATA_SET);
                }
                if (r == null) {
                    return SUSPEND;
                }
                if (r instanceof String s) {
                    emitChars(s);
                    return CONTINUE;
                }
                int c = (Integer) r;
                switch (c) {
                    case '&' -> startCharRef();
                    case '<' -> to(State.TAG_OPEN, 0);
                    case 0 -> {
                        error();
                        emitChar(0);
                    }
                    default -> emitChar(c);
                }
                return CONTINUE;
            }
            case RAW_DATA -> {
                return rawData();
            }
            case PLAINTEXT -> {
                Object r = popExceptFrom(PLAINTEXT_SET);
                if (r == null) {
                    return SUSPEND;
                }
                textOrChar(r);
                return CONTINUE;
            }
            case TAG_OPEN -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (c == '!') {
                    return to(State.MARKUP_DECLARATION_OPEN, 0);
                } else if (c == '/') {
                    return to(State.END_TAG_OPEN, 0);
                } else if (isAsciiAlpha(c)) {
                    createTag(false, lower(c));
                    return to(State.TAG_NAME, 0);
                } else if (c == '?') {
                    error();
                    comment.setLength(0);
                    return reconsumeIn(State.BOGUS_COMMENT, 0);
                }
                error();
                emitChar('<');
                return reconsumeIn(State.DATA, 0);
            }
            case END_TAG_OPEN -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (isAsciiAlpha(c)) {
                    createTag(true, lower(c));
                    return to(State.TAG_NAME, 0);
                }
                error();
                if (c == '>') {
                    return to(State.DATA, 0);
                }
                comment.setLength(0);
                return reconsumeIn(State.BOGUS_COMMENT, 0);
            }
            case TAG_NAME -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (isSpace(c)) {
                    return to(State.BEFORE_ATTR_NAME, 0);
                } else if (c == '/') {
                    return to(State.SELF_CLOSING_START_TAG, 0);
                } else if (c == '>') {
                    return emitTag(State.DATA);
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                }
                tagName.appendCodePoint(lower(c));
                return CONTINUE;
            }
            case RAW_LESS_THAN -> {
                return rawLessThan();
            }
            case RAW_END_TAG_OPEN -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (isAsciiAlpha(c)) {
                    createTag(true, lower(c));
                    tempBuf.appendCodePoint(c);
                    return to(State.RAW_END_TAG_NAME, kind);
                }
                emitChar('<');
                emitChar('/');
                return reconsumeIn(State.RAW_DATA, kind);
            }
            case RAW_END_TAG_NAME -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (haveAppropriateEndTag()) {
                    if (isSpace(c)) {
                        tempBuf.setLength(0);
                        return to(State.BEFORE_ATTR_NAME, 0);
                    } else if (c == '/') {
                        tempBuf.setLength(0);
                        return to(State.SELF_CLOSING_START_TAG, 0);
                    } else if (c == '>') {
                        tempBuf.setLength(0);
                        return emitTag(State.DATA);
                    }
                }
                if (isAsciiAlpha(c)) {
                    tagName.appendCodePoint(lower(c));
                    tempBuf.appendCodePoint(c);
                    return CONTINUE;
                }
                tagName.setLength(0);
                selfClosing = false;
                attrs = new ArrayList<>();
                emitChar('<');
                emitChar('/');
                emitTempBuf();
                return reconsumeIn(State.RAW_DATA, kind);
            }
            case SCRIPT_ESCAPE_START -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (kind == ESCAPED) {
                    if (c == '-') {
                        emitChar('-');
                        return to(State.SCRIPT_ESCAPE_START_DASH, 0);
                    }
                    return reconsumeIn(State.RAW_DATA, SCRIPT_DATA);
                }
                if (isSpace(c) || c == '/' || c == '>') {
                    int k = tempBuf.toString().equals("script") ? DOUBLE_ESCAPED : ESCAPED;
                    emitChar(c);
                    return to(State.RAW_DATA, k);
                } else if (isAsciiAlpha(c)) {
                    tempBuf.appendCodePoint(lower(c));
                    emitChar(c);
                    return CONTINUE;
                }
                return reconsumeIn(State.RAW_DATA, ESCAPED);
            }
            case SCRIPT_ESCAPE_START_DASH -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (c == '-') {
                    emitChar('-');
                    return to(State.SCRIPT_ESCAPED_DASH_DASH, ESCAPED);
                }
                return reconsumeIn(State.RAW_DATA, SCRIPT_DATA);
            }
            case SCRIPT_ESCAPED_DASH, SCRIPT_ESCAPED_DASH_DASH -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                boolean dashDash = state == State.SCRIPT_ESCAPED_DASH_DASH;
                if (c == '-') {
                    emitChar('-');
                    return to(State.SCRIPT_ESCAPED_DASH_DASH, kind);
                } else if (c == '<') {
                    if (kind == DOUBLE_ESCAPED) {
                        emitChar('<');
                    }
                    return to(State.RAW_LESS_THAN, kind);
                } else if (c == '>' && dashDash) {
                    emitChar('>');
                    return to(State.RAW_DATA, SCRIPT_DATA);
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                }
                emitChar(c);
                return to(State.RAW_DATA, kind);
            }
            case SCRIPT_DOUBLE_ESCAPE_END -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (isSpace(c) || c == '/' || c == '>') {
                    int k = tempBuf.toString().equals("script") ? ESCAPED : DOUBLE_ESCAPED;
                    emitChar(c);
                    return to(State.RAW_DATA, k);
                } else if (isAsciiAlpha(c)) {
                    tempBuf.appendCodePoint(lower(c));
                    emitChar(c);
                    return CONTINUE;
                }
                return reconsumeIn(State.RAW_DATA, DOUBLE_ESCAPED);
            }
            case BEFORE_ATTR_NAME, AFTER_ATTR_NAME -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                boolean after = state == State.AFTER_ATTR_NAME;
                if (isSpace(c)) {
                    return CONTINUE;
                } else if (c == '/') {
                    return to(State.SELF_CLOSING_START_TAG, 0);
                } else if (c == '>') {
                    return emitTag(State.DATA);
                } else if (c == '=' && after) {
                    return to(State.BEFORE_ATTR_VALUE, 0);
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                } else if (c == '"' || c == '\'' || c == '<' || c == '=') {
                    error();
                }
                createAttribute(lower(c));
                return to(State.ATTR_NAME, 0);
            }
            case ATTR_NAME -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (isSpace(c)) {
                    return to(State.AFTER_ATTR_NAME, 0);
                } else if (c == '/') {
                    return to(State.SELF_CLOSING_START_TAG, 0);
                } else if (c == '>') {
                    return emitTag(State.DATA);
                } else if (c == '=') {
                    return to(State.BEFORE_ATTR_VALUE, 0);
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                } else if (c == '"' || c == '\'' || c == '<') {
                    error();
                }
                attrName.appendCodePoint(lower(c));
                return CONTINUE;
            }
            case BEFORE_ATTR_VALUE -> {
                int c = peek();
                if (c < 0) {
                    return SUSPEND;
                }
                if (isSpace(c) || c == '\r') {
                    discardChar();
                    return CONTINUE;
                } else if (c == '"') {
                    discardChar();
                    return to(State.ATTR_VALUE, DOUBLE_QUOTED);
                } else if (c == '\'') {
                    discardChar();
                    return to(State.ATTR_VALUE, SINGLE_QUOTED);
                } else if (c == '>') {
                    discardChar();
                    error();
                    return emitTag(State.DATA);
                }
                return to(State.ATTR_VALUE, UNQUOTED);
            }
            case ATTR_VALUE -> {
                return attrValueStep();
            }
            case AFTER_ATTR_VALUE_QUOTED, SELF_CLOSING_START_TAG -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                boolean afterValue = state == State.AFTER_ATTR_VALUE_QUOTED;
                if (afterValue && isSpace(c)) {
                    return to(State.BEFORE_ATTR_NAME, 0);
                } else if (afterValue && c == '/') {
                    return to(State.SELF_CLOSING_START_TAG, 0);
                } else if (c == '>') {
                    if (!afterValue) {
                        selfClosing = true;
                    }
                    return emitTag(State.DATA);
                }
                error();
                return reconsumeIn(State.BEFORE_ATTR_NAME, 0);
            }
            case BOGUS_COMMENT -> {
                int c = getChar();
                if (c < 0) {
                    return SUSPEND;
                }
                if (c == '>') {
                    emitComment();
                    return to(State.DATA, 0);
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                }
                comment.appendCodePoint(c);
                return CONTINUE;
            }
            case MARKUP_DECLARATION_OPEN -> {
                Boolean m = eat("--", false);
                if (m == null) {
                    return SUSPEND;
                } else if (m) {
                    comment.setLength(0);
                    return to(State.COMMENT_START, 0);
                }
                m = eat("doctype", true);
                if (m == null) {
                    return SUSPEND;
                } else if (m) {
                    return to(State.DOCTYPE, 0);
                }
                if (sink.adjustedCurrentNodeNotHtml()) {
                    m = eat("[CDATA[", false);
                    if (m == null) {
                        return SUSPEND;
                    } else if (m) {
                        tempBuf.setLength(0);
                        return to(State.CDATA, 0);
                    }
                }
                error();
                comment.setLength(0);
                return to(State.BOGUS_COMMENT, 0);
            }
            default -> {
                return commentOrDoctype();
            }
        }
    }

    private void textOrChar(Object r) {
        if (r instanceof String s) {
            emitChars(s);
            return;
        }
        int c = (Integer) r;
        if (c == 0) {
            error();
            c = 0xFFFD;
        }
        emitChar(c);
    }

    private int rawData() {
        boolean escaped = kind == ESCAPED || kind == DOUBLE_ESCAPED;
        Object r = popExceptFrom(kind == RCDATA ? DATA_SET : escaped ? ESCAPED_SET : RAWTEXT_SET);
        if (r == null) {
            return SUSPEND;
        }
        if (r instanceof Integer c) {
            if (c == '&' && kind == RCDATA) {
                startCharRef();
                return CONTINUE;
            } else if (c == '-' && escaped) {
                emitChar('-');
                return to(State.SCRIPT_ESCAPED_DASH, kind);
            } else if (c == '<') {
                if (kind == DOUBLE_ESCAPED) {
                    emitChar('<');
                }
                return to(State.RAW_LESS_THAN, kind);
            }
        }
        textOrChar(r);
        return CONTINUE;
    }

    private int rawLessThan() {
        int c = getChar();
        if (c < 0) {
            return SUSPEND;
        }
        if (kind == DOUBLE_ESCAPED) {
            if (c == '/') {
                tempBuf.setLength(0);
                emitChar('/');
                return to(State.SCRIPT_DOUBLE_ESCAPE_END, 0);
            }
            return reconsumeIn(State.RAW_DATA, DOUBLE_ESCAPED);
        }
        if (c == '/') {
            tempBuf.setLength(0);
            return to(State.RAW_END_TAG_OPEN, kind);
        }
        if (kind == ESCAPED && isAsciiAlpha(c)) {
            tempBuf.setLength(0);
            tempBuf.appendCodePoint(lower(c));
            emitChar('<');
            emitChar(c);
            return to(State.SCRIPT_ESCAPE_START, DOUBLE_ESCAPED);
        }
        if (kind == SCRIPT_DATA && c == '!') {
            emitChar('<');
            emitChar('!');
            return to(State.SCRIPT_ESCAPE_START, ESCAPED);
        }
        emitChar('<');
        return reconsumeIn(State.RAW_DATA, kind);
    }

    private int attrValueStep() {
        Object r = popExceptFrom(kind == DOUBLE_QUOTED ? DQ_SET : kind == SINGLE_QUOTED ? SQ_SET : UNQUOTED_SET);
        if (r == null) {
            return SUSPEND;
        }
        if (r instanceof String s) {
            attrValue.append(s);
            return CONTINUE;
        }
        int c = (Integer) r;
        if (kind == DOUBLE_QUOTED && c == '"' || kind == SINGLE_QUOTED && c == '\'') {
            return to(State.AFTER_ATTR_VALUE_QUOTED, 0);
        } else if (c == '&') {
            startCharRef();
            return CONTINUE;
        } else if (kind == UNQUOTED && isSpace(c)) {
            return to(State.BEFORE_ATTR_NAME, 0);
        } else if (kind == UNQUOTED && c == '>') {
            return emitTag(State.DATA);
        } else if (c == 0) {
            error();
            c = 0xFFFD;
        } else if (kind == UNQUOTED && (c == '"' || c == '\'' || c == '<' || c == '=' || c == '`')) {
            error();
        }
        attrValue.appendCodePoint(c);
        return CONTINUE;
    }

    private int commentOrDoctype() {
        if (state == State.AFTER_DOCTYPE_NAME) {
            Boolean m = eat("public", true);
            if (m == null) {
                return SUSPEND;
            } else if (m) {
                return to(State.AFTER_DOCTYPE_KEYWORD, PUBLIC);
            }
            m = eat("system", true);
            if (m == null) {
                return SUSPEND;
            } else if (m) {
                return to(State.AFTER_DOCTYPE_KEYWORD, SYSTEM);
            }
        }
        int c = getChar();
        if (c < 0) {
            return SUSPEND;
        }
        switch (state) {
            case COMMENT_START, COMMENT_START_DASH -> {
                if (c == '-') {
                    return to(state == State.COMMENT_START ? State.COMMENT_START_DASH : State.COMMENT_END, 0);
                } else if (c == '>') {
                    error();
                    emitComment();
                    return to(State.DATA, 0);
                }
                if (state == State.COMMENT_START_DASH) {
                    comment.append('-');
                }
                return commentChar(c);
            }
            case COMMENT -> {
                if (c == '<') {
                    comment.append('<');
                    return to(State.COMMENT_LT, 0);
                } else if (c == '-') {
                    return to(State.COMMENT_END_DASH, 0);
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                }
                comment.appendCodePoint(c);
                return CONTINUE;
            }
            case COMMENT_LT -> {
                if (c == '!') {
                    comment.append('!');
                    return to(State.COMMENT_LT_BANG, 0);
                } else if (c == '<') {
                    comment.append('<');
                    return CONTINUE;
                }
                return reconsumeIn(State.COMMENT, 0);
            }
            case COMMENT_LT_BANG -> {
                return c == '-' ? to(State.COMMENT_LT_BANG_DASH, 0) : reconsumeIn(State.COMMENT, 0);
            }
            case COMMENT_LT_BANG_DASH -> {
                return c == '-' ? to(State.COMMENT_LT_BANG_DASH_DASH, 0) : reconsumeIn(State.COMMENT_END_DASH, 0);
            }
            case COMMENT_LT_BANG_DASH_DASH -> {
                if (c != '>') {
                    error();
                }
                return reconsumeIn(State.COMMENT_END, 0);
            }
            case COMMENT_END_DASH -> {
                if (c == '-') {
                    return to(State.COMMENT_END, 0);
                }
                comment.append('-');
                return commentChar(c);
            }
            case COMMENT_END -> {
                if (c == '>') {
                    emitComment();
                    return to(State.DATA, 0);
                } else if (c == '!') {
                    return to(State.COMMENT_END_BANG, 0);
                } else if (c == '-') {
                    comment.append('-');
                    return CONTINUE;
                }
                comment.append("--");
                return reconsumeIn(State.COMMENT, 0);
            }
            case COMMENT_END_BANG -> {
                if (c == '-') {
                    comment.append("--!");
                    return to(State.COMMENT_END_DASH, 0);
                } else if (c == '>') {
                    error();
                    emitComment();
                    return to(State.DATA, 0);
                }
                comment.append("--!");
                return commentChar(c);
            }
            case CDATA -> {
                if (c == ']') {
                    return to(State.CDATA_BRACKET, 0);
                } else if (c == 0) {
                    emitTempBuf();
                    emitChar(0);
                } else {
                    tempBuf.appendCodePoint(c);
                }
                return CONTINUE;
            }
            case CDATA_BRACKET -> {
                if (c == ']') {
                    return to(State.CDATA_END, 0);
                }
                tempBuf.append(']');
                return reconsumeIn(State.CDATA, 0);
            }
            case CDATA_END -> {
                if (c == ']') {
                    tempBuf.append(']');
                    return CONTINUE;
                } else if (c == '>') {
                    emitTempBuf();
                    return to(State.DATA, 0);
                }
                tempBuf.append("]]");
                return reconsumeIn(State.CDATA, 0);
            }
            default -> {
                return doctypeStep(c);
            }
        }
    }

    private int commentChar(int c) {
        if (c == 0) {
            error();
            c = 0xFFFD;
        }
        comment.appendCodePoint(c);
        return to(State.COMMENT, 0);
    }

    private int emitDoctypeToData() {
        emitDoctype();
        return to(State.DATA, 0);
    }

    private int doctypeStep(int c) {
        switch (state) {
            case DOCTYPE -> {
                if (isSpace(c)) {
                    return to(State.BEFORE_DOCTYPE_NAME, 0);
                } else if (c != '>') {
                    error();
                }
                return reconsumeIn(State.BEFORE_DOCTYPE_NAME, 0);
            }
            case BEFORE_DOCTYPE_NAME -> {
                if (isSpace(c)) {
                    return CONTINUE;
                } else if (c == '>') {
                    error();
                    createDoctype();
                    forceQuirks = true;
                    return emitDoctypeToData();
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                }
                createDoctype();
                pushDoctypeName(lower(c));
                return to(State.DOCTYPE_NAME, 0);
            }
            case DOCTYPE_NAME -> {
                if (isSpace(c)) {
                    tempBuf.setLength(0);
                    return to(State.AFTER_DOCTYPE_NAME, 0);
                } else if (c == '>') {
                    return emitDoctypeToData();
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                }
                pushDoctypeName(lower(c));
                return CONTINUE;
            }
            case AFTER_DOCTYPE_NAME -> {
                if (isSpace(c)) {
                    return CONTINUE;
                } else if (c == '>') {
                    return emitDoctypeToData();
                }
                error();
                forceQuirks = true;
                return reconsumeIn(State.BOGUS_DOCTYPE, 0);
            }
            case AFTER_DOCTYPE_KEYWORD, BEFORE_DOCTYPE_ID -> {
                boolean keyword = state == State.AFTER_DOCTYPE_KEYWORD;
                if (isSpace(c)) {
                    return keyword ? to(State.BEFORE_DOCTYPE_ID, kind) : CONTINUE;
                } else if (c == '"' || c == '\'') {
                    if (keyword) {
                        error();
                    }
                    clearDoctypeId(kind);
                    return to(c == '"' ? State.DOCTYPE_ID_DOUBLE_QUOTED : State.DOCTYPE_ID_SINGLE_QUOTED, kind);
                } else if (c == '>') {
                    error();
                    forceQuirks = true;
                    return emitDoctypeToData();
                }
                error();
                forceQuirks = true;
                return reconsumeIn(State.BOGUS_DOCTYPE, 0);
            }
            case DOCTYPE_ID_DOUBLE_QUOTED, DOCTYPE_ID_SINGLE_QUOTED -> {
                if (c == (state == State.DOCTYPE_ID_DOUBLE_QUOTED ? '"' : '\'')) {
                    return to(State.AFTER_DOCTYPE_ID, kind);
                } else if (c == '>') {
                    error();
                    forceQuirks = true;
                    return emitDoctypeToData();
                } else if (c == 0) {
                    error();
                    c = 0xFFFD;
                }
                pushDoctypeId(kind, c);
                return CONTINUE;
            }
            case AFTER_DOCTYPE_ID, BETWEEN_DOCTYPE_IDS -> {
                boolean between = state == State.BETWEEN_DOCTYPE_IDS;
                if (kind == SYSTEM && !between) {
                    if (isSpace(c)) {
                        return CONTINUE;
                    } else if (c == '>') {
                        return emitDoctypeToData();
                    }
                    error();
                    return reconsumeIn(State.BOGUS_DOCTYPE, 0);
                }
                if (isSpace(c)) {
                    return between ? CONTINUE : to(State.BETWEEN_DOCTYPE_IDS, 0);
                } else if (c == '>') {
                    return emitDoctypeToData();
                } else if (c == '"' || c == '\'') {
                    if (!between) {
                        error();
                    }
                    clearDoctypeId(SYSTEM);
                    return to(c == '"' ? State.DOCTYPE_ID_DOUBLE_QUOTED : State.DOCTYPE_ID_SINGLE_QUOTED, SYSTEM);
                }
                error();
                forceQuirks = true;
                return reconsumeIn(State.BOGUS_DOCTYPE, 0);
            }
            case BOGUS_DOCTYPE -> {
                if (c == '>') {
                    return emitDoctypeToData();
                } else if (c == 0) {
                    error();
                }
                return CONTINUE;
            }
            default -> throw new IllegalStateException(state.toString());
        }
    }

    /** Signals the end of the input. */
    void end() {
        if (charRef != null) {
            CharRef r = charRef;
            charRef = null;
            processCharRef(r.endOfFile(this));
        }
        atEof = true;
        run();
        while (eofStep() == CONTINUE) {
            continue;
        }
    }

    private int eofStep() {
        switch (state) {
            case DATA, PLAINTEXT -> {
                sink.process(Simple.EOF);
                return SUSPEND;
            }
            case RAW_DATA -> {
                if (kind != ESCAPED && kind != DOUBLE_ESCAPED) {
                    sink.process(Simple.EOF);
                    return SUSPEND;
                }
                error();
                return to(State.DATA, 0);
            }
            case TAG_NAME, BEFORE_ATTR_NAME, ATTR_NAME, AFTER_ATTR_NAME, ATTR_VALUE, AFTER_ATTR_VALUE_QUOTED,
                    SELF_CLOSING_START_TAG, SCRIPT_ESCAPED_DASH, SCRIPT_ESCAPED_DASH_DASH -> {
                error();
                return to(State.DATA, 0);
            }
            case BEFORE_ATTR_VALUE -> {
                return reconsumeIn(State.ATTR_VALUE, UNQUOTED);
            }
            case TAG_OPEN, END_TAG_OPEN -> {
                error();
                emitChar('<');
                if (state == State.END_TAG_OPEN) {
                    emitChar('/');
                }
                return to(State.DATA, 0);
            }
            case RAW_LESS_THAN -> {
                if (kind != DOUBLE_ESCAPED) {
                    emitChar('<');
                }
                return to(State.RAW_DATA, kind);
            }
            case RAW_END_TAG_OPEN, RAW_END_TAG_NAME -> {
                emitChar('<');
                emitChar('/');
                if (state == State.RAW_END_TAG_NAME) {
                    emitTempBuf();
                }
                return to(State.RAW_DATA, kind);
            }
            case SCRIPT_ESCAPE_START -> {
                return to(State.RAW_DATA, kind);
            }
            case SCRIPT_ESCAPE_START_DASH -> {
                return to(State.RAW_DATA, SCRIPT_DATA);
            }
            case SCRIPT_DOUBLE_ESCAPE_END -> {
                return to(State.RAW_DATA, DOUBLE_ESCAPED);
            }
            case COMMENT_START, COMMENT_START_DASH, COMMENT, COMMENT_END_DASH, COMMENT_END, COMMENT_END_BANG -> {
                error();
                emitComment();
                return to(State.DATA, 0);
            }
            case COMMENT_LT, COMMENT_LT_BANG -> {
                return reconsumeIn(State.COMMENT, 0);
            }
            case COMMENT_LT_BANG_DASH -> {
                return reconsumeIn(State.COMMENT_END_DASH, 0);
            }
            case COMMENT_LT_BANG_DASH_DASH -> {
                return reconsumeIn(State.COMMENT_END, 0);
            }
            case DOCTYPE, BEFORE_DOCTYPE_NAME -> {
                error();
                createDoctype();
                forceQuirks = true;
                return emitDoctypeToData();
            }
            case BOGUS_DOCTYPE -> {
                return emitDoctypeToData();
            }
            case BOGUS_COMMENT -> {
                emitComment();
                return to(State.DATA, 0);
            }
            case MARKUP_DECLARATION_OPEN -> {
                error();
                return to(State.BOGUS_COMMENT, 0);
            }
            case CDATA -> {
                emitTempBuf();
                error();
                return to(State.DATA, 0);
            }
            case CDATA_BRACKET -> {
                tempBuf.append(']');
                return to(State.CDATA, 0);
            }
            case CDATA_END -> {
                tempBuf.append("]]");
                return to(State.CDATA, 0);
            }
            default -> {
                error();
                forceQuirks = true;
                return emitDoctypeToData();
            }
        }
    }
}
