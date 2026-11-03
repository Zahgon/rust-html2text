// Copyright 2014-2017 The html5ever Project Developers; ported in jugglerchris/rust-html2text. See the
// COPYRIGHT file at the top-level directory of this distribution.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
//
// Java port of html2text's markup5ever_rcdom module (a simple DOM).

package io.github.jugglerchris.html2text.dom;

import java.util.ArrayList;
import java.util.List;

/**
 * A node of the document tree built by the HTML parser: the document itself, a doctype, a text node, a comment, an
 * element or a processing instruction. Children are owned by their parent; every child knows its parent.
 */
public final class Node {
    /** The kinds of node. */
    public enum Kind {
        /** The document, root of the tree. */
        DOCUMENT,
        /** A {@code <!DOCTYPE>}. */
        DOCTYPE,
        /** A run of text. */
        TEXT,
        /** A comment. */
        COMMENT,
        /** An element with attributes. */
        ELEMENT,
        /** A processing instruction (never produced by the HTML parser). */
        PROCESSING_INSTRUCTION
    }

    private final Kind kind;
    private Node parent;
    private final List<Node> children = new ArrayList<>();
    private final QualName name;
    private final List<Attribute> attrs;
    private final Node templateContents;
    private final boolean mathmlAnnotationXmlIntegrationPoint;
    private final StringBuilder text;
    private final String[] doctype;

    private Node(Kind kind, QualName name, List<Attribute> attrs, Node templateContents, boolean integrationPoint,
            StringBuilder text, String[] doctype) {
        this.kind = kind;
        this.name = name;
        this.attrs = attrs;
        this.templateContents = templateContents;
        this.mathmlAnnotationXmlIntegrationPoint = integrationPoint;
        this.text = text;
        this.doctype = doctype;
    }

    /** Creates an empty document node. */
    public static Node document() {
        return new Node(Kind.DOCUMENT, null, null, null, false, null, null);
    }

    /** Creates a doctype node. */
    public static Node doctype(String name, String publicId, String systemId) {
        return new Node(Kind.DOCTYPE, null, null, null, false, null, new String[] {name, publicId, systemId});
    }

    /** Creates a text node. */
    public static Node text(String contents) {
        return new Node(Kind.TEXT, null, null, null, false, new StringBuilder(contents), null);
    }

    /** Creates a comment node. */
    public static Node comment(String contents) {
        return new Node(Kind.COMMENT, null, null, null, false, new StringBuilder(contents), null);
    }

    /** Creates a processing instruction node. */
    public static Node processingInstruction(String target, String contents) {
        return new Node(Kind.PROCESSING_INSTRUCTION, null, null, null, false, new StringBuilder(contents),
                new String[] {target, "", ""});
    }

    /**
     * Creates an element; a template element gets an empty document fragment (a {@link Kind#DOCUMENT} node) as its
     * template contents.
     */
    public static Node element(QualName name, List<Attribute> attrs, boolean template, boolean integrationPoint) {
        return new Node(Kind.ELEMENT, name, new ArrayList<>(attrs), template ? document() : null, integrationPoint,
                null, null);
    }

    /** The kind of this node. */
    public Kind kind() {
        return kind;
    }

    /** The parent node, or {@code null} for a root. */
    public Node parent() {
        return parent;
    }

    /** The children, in document order (live list: change it only through the methods of this class). */
    public List<Node> children() {
        return children;
    }

    /** The element name; {@code null} unless this is an element. */
    public QualName name() {
        return name;
    }

    /** Whether this is an element with the given namespace and local name. */
    public boolean isElement(String ns, String local) {
        return kind == Kind.ELEMENT && name.ns().equals(ns) && name.local().equals(local);
    }

    /** Whether this is an HTML element with the given local name. */
    public boolean isHtml(String local) {
        return isElement(Namespace.HTML, local);
    }

    /** The attributes of an element (live list), in source order. */
    public List<Attribute> attrs() {
        return attrs;
    }

    /** The value of the first attribute with this local name, or {@code null}. */
    public String attr(String local) {
        if (attrs != null) {
            for (Attribute a : attrs) {
                if (a.name().local().equals(local)) {
                    return a.value();
                }
            }
        }
        return null;
    }

    /** The template contents of a template element, or {@code null}. */
    public Node templateContents() {
        return templateContents;
    }

    /** Whether this element is a MathML annotation-xml HTML integration point. */
    public boolean isMathmlAnnotationXmlIntegrationPoint() {
        return mathmlAnnotationXmlIntegrationPoint;
    }

    /** The contents of a text or comment node (or of a processing instruction). */
    public String text() {
        return text == null ? null : text.toString();
    }

    /** Appends to the contents of a text node. */
    public void appendText(String more) {
        text.append(more);
    }

    /** The name of a doctype (or the target of a processing instruction). */
    public String doctypeName() {
        return doctype[0];
    }

    /** The public id of a doctype. */
    public String publicId() {
        return doctype[1];
    }

    /** The system id of a doctype. */
    public String systemId() {
        return doctype[2];
    }

    /** Appends a parentless node as the last child of this node. */
    public void append(Node child) {
        if (child.parent != null) {
            throw new IllegalStateException("child already has a parent");
        }
        child.parent = this;
        children.add(child);
    }

    /** Inserts a parentless node as the child at {@code index}. */
    public void insert(int index, Node child) {
        if (child.parent != null) {
            throw new IllegalStateException("child already has a parent");
        }
        child.parent = this;
        children.add(index, child);
    }

    /** The position of this node among its parent's children, or -1 without a parent. */
    public int indexInParent() {
        if (parent == null) {
            return -1;
        }
        List<Node> siblings = parent.children;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i) == this) {
                return i;
            }
        }
        throw new IllegalStateException("have parent but couldn't find in parent's children!");
    }

    /** Detaches this node from its parent, if any. */
    public void remove() {
        int i = indexInParent();
        if (i >= 0) {
            parent.children.remove(i);
            parent = null;
        }
    }

    /** Moves all children of this node to the end of {@code newParent}'s children. */
    public void reparentChildrenTo(Node newParent) {
        for (Node child : children) {
            child.parent = newParent;
        }
        newParent.children.addAll(children);
        children.clear();
    }
}
