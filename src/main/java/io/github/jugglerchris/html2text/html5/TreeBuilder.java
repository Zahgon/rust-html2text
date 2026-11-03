// Copyright 2014-2017 The html5ever Project Developers; ported in jugglerchris/rust-html2text. See the
// COPYRIGHT file at the top-level directory of this distribution.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
//
// Java port of html5ever's HTML5 tree builder (tree_builder/mod.rs, rules.rs, tag_sets.rs, data.rs) building
// html2text's RcDom (markup5ever_rcdom.rs), for documents with scripting disabled.

package io.github.jugglerchris.html2text.html5;

import io.github.jugglerchris.html2text.dom.Attribute;
import io.github.jugglerchris.html2text.dom.Namespace;
import io.github.jugglerchris.html2text.dom.Node;
import io.github.jugglerchris.html2text.dom.QualName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** The tree builder: consumes tokens and builds the document. */
final class TreeBuilder {
    /** Results of processing a token, for the tokenizer (non-negative values are raw text kinds). */
    static final int CONTINUE = -1, PLAINTEXT = -2, SCRIPT = -3, ENCODING = -4;
    private static final int REPROCESS = -5, SPLIT = -6;

    sealed interface Token permits Tag, Chars, Comment, Doctype, Simple {}

    record Tag(boolean end, String name, boolean selfClosing, List<Attribute> attrs) implements Token {
        Tag withName(String newName) {
            return new Tag(end, newName, selfClosing, attrs);
        }
    }

    record Chars(int split, String text) implements Token {
        static final int NOT_SPLIT = 0, WHITESPACE = 1, NOT_WHITESPACE = 2;
    }

    record Comment(String text) implements Token {}

    record Doctype(String name, String publicId, String systemId, boolean forceQuirks) implements Token {}

    enum Simple implements Token { NULL, EOF, ERROR }

    private enum Mode {
        INITIAL, BEFORE_HTML, BEFORE_HEAD, IN_HEAD, IN_HEAD_NOSCRIPT, AFTER_HEAD, IN_BODY, TEXT, IN_TABLE,
        IN_TABLE_TEXT, IN_CAPTION, IN_COLUMN_GROUP, IN_TABLE_BODY, IN_ROW, IN_CELL, IN_TEMPLATE, AFTER_BODY,
        IN_FRAMESET, AFTER_FRAMESET, AFTER_AFTER_BODY, AFTER_AFTER_FRAMESET
    }

    private record Result(int code, Mode mode, Token token) {}

    private static final Result DONE = new Result(CONTINUE, null, null);

    /** An entry of the list of active formatting elements; the marker has no element. */
    private record FormatEntry(Node elem, Tag tag) {}

    private static final FormatEntry MARKER = new FormatEntry(null, null);

    private static Set<String> names(String s) {
        return Set.of(s.split(" "));
    }

    private static final String CURSORY = "dd dt li option optgroup p rb rp rt rtc";
    private static final Set<String> DEFAULT_SCOPE =
        names("applet caption html table td th marquee object select template");
    private static final Set<String> TABLE_SCOPE = names("html table template");
    private static final Set<String> TABLE_BODY_CONTEXT = names("tbody tfoot thead template html");
    private static final Set<String> TABLE_ROW_CONTEXT = names("tr template html");
    private static final Set<String> TD_TH = names("td th");
    private static final Set<String> CURSORY_IMPLIED_END = names(CURSORY);
    private static final Set<String> THOROUGH_IMPLIED_END =
        names(CURSORY + " caption colgroup tbody td tfoot th thead tr");
    private static final Set<String> HEADING = names("h1 h2 h3 h4 h5 h6");
    private static final Set<String> SPECIAL = names("address applet area article aside base basefont bgsound "
        + "blockquote body br button caption center col colgroup dd details dir div dl dt embed fieldset figcaption "
        + "figure footer form frame frameset h1 h2 h3 h4 h5 h6 head header hgroup hr html iframe img input isindex "
        + "li link listing main marquee menu meta nav noembed noframes noscript object ol p param plaintext pre "
        + "script section select source style summary table tbody td template textarea tfoot th thead title tr "
        + "track ul wbr xmp");
    private static final Set<String> FOSTER_TARGET = names("table tbody tfoot thead tr");
    private static final Set<String> MATHML_TEXT_IP = names("mi mo mn ms mtext");
    private static final Set<String> SVG_HTML_IP = names("foreignObject desc title");
    private static final Set<String> HEAD_BODY_HTML_BR = names("head body html br");
    private static final Set<String> TABLE_SECTIONS = names("tbody tfoot thead");
    private static final Set<String> FOREIGN_BREAKOUT = names("b big blockquote body br center code dd div dl dt "
        + "em embed h1 h2 h3 h4 h5 h6 head hr i img li listing menu meta nobr ol p pre ruby s small span strong "
        + "strike sub sup table tt u ul var");
    private static final Map<String, String> SVG_TAG_NAMES = camelCaseMap("altGlyph altGlyphDef altGlyphItem "
        + "animateColor animateMotion animateTransform clipPath feBlend feColorMatrix feComponentTransfer "
        + "feComposite feConvolveMatrix feDiffuseLighting feDisplacementMap feDistantLight feDropShadow feFlood "
        + "feFuncA feFuncB feFuncG feFuncR feGaussianBlur feImage feMerge feMergeNode feMorphology feOffset "
        + "fePointLight feSpecularLighting feSpotLight feTile feTurbulence foreignObject glyphRef linearGradient "
        + "radialGradient textPath");
    private static final Map<String, String> SVG_ATTRIBUTES = camelCaseMap("attributeName attributeType "
        + "baseFrequency baseProfile calcMode clipPathUnits diffuseConstant edgeMode filterUnits glyphRef "
        + "gradientTransform gradientUnits kernelMatrix kernelUnitLength keyPoints keySplines keyTimes lengthAdjust "
        + "limitingConeAngle markerHeight markerUnits markerWidth maskContentUnits maskUnits numOctaves pathLength "
        + "patternContentUnits patternTransform patternUnits pointsAtX pointsAtY pointsAtZ preserveAlpha "
        + "preserveAspectRatio primitiveUnits refX refY repeatCount repeatDur requiredExtensions requiredFeatures "
        + "specularConstant specularExponent spreadMethod startOffset stdDeviation stitchTiles surfaceScale "
        + "systemLanguage tableValues targetX targetY textLength viewBox viewTarget xChannelSelector "
        + "yChannelSelector zoomAndPan");
    private static final Map<String, QualName> FOREIGN_ATTRIBUTES = Map.ofEntries(
        Map.entry("xlink:actuate", new QualName("xlink", Namespace.XLINK, "actuate")),
        Map.entry("xlink:arcrole", new QualName("xlink", Namespace.XLINK, "arcrole")),
        Map.entry("xlink:href", new QualName("xlink", Namespace.XLINK, "href")),
        Map.entry("xlink:role", new QualName("xlink", Namespace.XLINK, "role")),
        Map.entry("xlink:show", new QualName("xlink", Namespace.XLINK, "show")),
        Map.entry("xlink:title", new QualName("xlink", Namespace.XLINK, "title")),
        Map.entry("xlink:type", new QualName("xlink", Namespace.XLINK, "type")),
        Map.entry("xml:lang", new QualName("xml", Namespace.XML, "lang")),
        Map.entry("xml:space", new QualName("xml", Namespace.XML, "space")),
        Map.entry("xmlns", new QualName("", Namespace.XMLNS, "xmlns")),
        Map.entry("xmlns:xlink", new QualName("xmlns", Namespace.XMLNS, "xlink")));
    private static final List<String> QUIRKY_PUBLIC_PREFIXES = List.of(
        "-//advasoft ltd//dtd html 3.0 aswedit + extensions//", "-//as//dtd html 3.0 aswedit + extensions//",
        "-//ietf//dtd html 2.0 level 1//", "-//ietf//dtd html 2.0 level 2//", "-//ietf//dtd html 2.0 strict level 1//",
        "-//ietf//dtd html 2.0 strict level 2//", "-//ietf//dtd html 2.0 strict//", "-//ietf//dtd html 2.0//",
        "-//ietf//dtd html 2.1e//", "-//ietf//dtd html 3.0//", "-//ietf//dtd html 3.2 final//",
        "-//ietf//dtd html 3.2//", "-//ietf//dtd html 3//", "-//ietf//dtd html level 0//",
        "-//ietf//dtd html level 1//", "-//ietf//dtd html level 2//", "-//ietf//dtd html level 3//",
        "-//ietf//dtd html strict level 0//", "-//ietf//dtd html strict level 1//",
        "-//ietf//dtd html strict level 2//", "-//ietf//dtd html strict level 3//", "-//ietf//dtd html strict//",
        "-//ietf//dtd html//", "-//metrius//dtd metrius presentational//",
        "-//microsoft//dtd internet explorer 2.0 html strict//", "-//microsoft//dtd internet explorer 2.0 html//",
        "-//microsoft//dtd internet explorer 2.0 tables//", "-//microsoft//dtd internet explorer 3.0 html strict//",
        "-//microsoft//dtd internet explorer 3.0 html//", "-//microsoft//dtd internet explorer 3.0 tables//",
        "-//netscape comm. corp.//dtd html//", "-//netscape comm. corp.//dtd strict html//",
        "-//o'reilly and associates//dtd html 2.0//", "-//o'reilly and associates//dtd html extended 1.0//",
        "-//o'reilly and associates//dtd html extended relaxed 1.0//",
        "-//softquad software//dtd hotmetal pro 6.0::19990601::extensions to html 4.0//",
        "-//softquad//dtd hotmetal pro 4.0::19971010::extensions to html 4.0//",
        "-//spyglass//dtd html 2.0 extended//", "-//sq//dtd html 2.0 hotmetal + extensions//",
        "-//sun microsystems corp.//dtd hotjava html//", "-//sun microsystems corp.//dtd hotjava strict html//",
        "-//w3c//dtd html 3 1995-03-24//", "-//w3c//dtd html 3.2 draft//", "-//w3c//dtd html 3.2 final//",
        "-//w3c//dtd html 3.2//", "-//w3c//dtd html 3.2s draft//", "-//w3c//dtd html 4.0 frameset//",
        "-//w3c//dtd html 4.0 transitional//", "-//w3c//dtd html experimental 19960712//",
        "-//w3c//dtd html experimental 970421//", "-//w3c//dtd w3 html//", "-//w3o//dtd w3 html 3.0//",
        "-//webtechs//dtd mozilla html 2.0//", "-//webtechs//dtd mozilla html//");
    private static final List<String> QUIRKY_PUBLIC_MATCHES =
        List.of("-//w3o//dtd w3 html strict 3.0//en//", "-/w3c/dtd html 4.0 transitional/en", "html");
    private static final List<String> HTML4_PUBLIC_PREFIXES =
        List.of("-//w3c//dtd html 4.01 frameset//", "-//w3c//dtd html 4.01 transitional//");

    private static Map<String, String> camelCaseMap(String s) {
        Map<String, String> map = new HashMap<>();
        for (String name : s.split(" ")) {
            map.put(name.toLowerCase(Locale.ROOT), name);
        }
        return map;
    }

    /** Tag-set memberships cached for the elements on the stack, and the scopes as masks of them. */
    private static final int F_DEFAULT = 1, F_BUTTON = 2, F_LIST = 4, F_TABLE = 8, F_SPECIAL = 16, F_BITS = 5;
    private static final int DEFAULT = F_DEFAULT, BUTTON = F_DEFAULT | F_BUTTON, LIST_ITEM = F_DEFAULT | F_LIST,
        TABLE = F_TABLE;

    /** The stack of open elements, with each element's tag-set flags and HTML name id kept in a parallel array. */
    private static final class ElementStack {
        private final Map<String, Integer> ids = new HashMap<>();
        private Node[] nodes = new Node[64];
        private int[] info = new int[64];
        private int size;

        int size() {
            return size;
        }

        boolean isEmpty() {
            return size == 0;
        }

        Node get(int i) {
            return nodes[Objects.checkIndex(i, size)];
        }

        /** The flags of element i, or'ed with its name id shifted left by F_BITS (0 if not an HTML element). */
        int info(int i) {
            return info[i];
        }

        /** The id of an HTML element name, or -1 if no element of that name was ever on the stack. */
        int id(String name) {
            return ids.getOrDefault(name, -1);
        }

        void set(int i, Node n) {
            nodes[Objects.checkIndex(i, size)] = n;
            QualName q = n.name();
            int f = htmlIn(n, DEFAULT_SCOPE) || mathmlTextIp(q) || svgHtmlIp(q) ? F_DEFAULT : 0;
            f |= (n.isHtml("button") ? F_BUTTON : 0) | (n.isHtml("ol") || n.isHtml("ul") ? F_LIST : 0)
                | (htmlIn(n, TABLE_SCOPE) ? F_TABLE : 0) | (htmlIn(n, SPECIAL) ? F_SPECIAL : 0);
            if (q.ns().equals(Namespace.HTML)) {
                f |= ids.computeIfAbsent(q.local(), k -> ids.size() + 1) << F_BITS;
            }
            info[i] = f;
        }

        void add(Node n) {
            add(size, n);
        }

        void add(int i, Node n) {
            if (size == nodes.length) {
                nodes = Arrays.copyOf(nodes, size * 2);
                info = Arrays.copyOf(info, size * 2);
            }
            System.arraycopy(nodes, i, nodes, i + 1, size - i);
            System.arraycopy(info, i, info, i + 1, size - i);
            size++;
            set(i, n);
        }

        Node remove(int i) {
            Node n = get(i);
            System.arraycopy(nodes, i + 1, nodes, i, size - i - 1);
            System.arraycopy(info, i + 1, info, i, size - i - 1);
            nodes[--size] = null;
            return n;
        }

        void truncate(int n) {
            Arrays.fill(nodes, n, size, null);
            size = n;
        }

        int indexOf(Node n) {
            for (int i = 0; i < size; i++) {
                if (nodes[i] == n) {
                    return i;
                }
            }
            return -1;
        }

        int lastIndexOf(Node n) {
            for (int i = size - 1; i >= 0; i--) {
                if (nodes[i] == n) {
                    return i;
                }
            }
            return -1;
        }
    }

    private final Node doc = Node.document();
    private Mode mode = Mode.INITIAL;
    private Mode origMode;
    private final List<Mode> templateModes = new ArrayList<>();
    private final List<Chars> pendingTableText = new ArrayList<>();
    private boolean quirks;
    private final ElementStack openElems = new ElementStack();
    private final List<FormatEntry> activeFormatting = new ArrayList<>();
    private Node headElem;
    private Node formElem;
    private boolean framesetOk = true;
    private boolean ignoreLf;
    private boolean fosterParenting;

    Node document() {
        return doc;
    }

    // ---- The sink: html2text's RcDom.

    private static void append(Node parent, Object child) {
        if (child instanceof String text) {
            List<Node> children = parent.children();
            if (!children.isEmpty() && children.get(children.size() - 1).kind() == Node.Kind.TEXT) {
                children.get(children.size() - 1).appendText(text);
                return;
            }
            child = Node.text(text);
        }
        parent.append((Node) child);
    }

    private static void appendBeforeSibling(Node sibling, Object child) {
        Node parent = sibling.parent();
        int i = sibling.indexInParent();
        if (child instanceof String text) {
            Node prev = i > 0 ? parent.children().get(i - 1) : null;
            if (prev != null && prev.kind() == Node.Kind.TEXT) {
                prev.appendText(text);
                return;
            }
            child = Node.text(text);
        }
        Node node = (Node) child;
        node.remove();
        parent.insert(i, node);
    }

    private static void addAttrsIfMissing(Node target, List<Attribute> attrs) {
        Set<QualName> existing = new HashSet<>();
        for (Attribute a : target.attrs()) {
            existing.add(a.name());
        }
        for (Attribute a : attrs) {
            if (!existing.contains(a.name())) {
                target.attrs().add(a);
            }
        }
    }

    private static Node createElement(QualName name, List<Attribute> attrs) {
        boolean aip = name.ns().equals(Namespace.MATHML) && name.local().equals("annotation-xml")
            && attrs.stream().anyMatch(a -> a.name().ns().isEmpty() && a.name().local().equals("encoding")
                && (eqIgnoreAsciiCase(a.value(), "text/html") || eqIgnoreAsciiCase(a.value(), "application/xhtml+xml")));
        return Node.element(name, attrs, name.ns().equals(Namespace.HTML) && name.local().equals("template"), aip);
    }

    // ---- Helpers.

    private static char lowerAscii(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
    }

    static boolean eqIgnoreAsciiCase(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (lowerAscii(a.charAt(i)) != lowerAscii(b.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String asciiLower(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            b.append(lowerAscii(s.charAt(i)));
        }
        return b.toString();
    }

    private static boolean isAsciiWs(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r';
    }

    private static boolean anyNotWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isAsciiWs(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean htmlIn(Node n, Set<String> set) {
        return n.name().ns().equals(Namespace.HTML) && set.contains(n.name().local());
    }

    private static boolean mathmlTextIp(QualName q) {
        return q.ns().equals(Namespace.MATHML) && MATHML_TEXT_IP.contains(q.local());
    }

    private static boolean svgHtmlIp(QualName q) {
        return q.ns().equals(Namespace.SVG) && SVG_HTML_IP.contains(q.local());
    }

    private static boolean tableScope(Node n) {
        return htmlIn(n, TABLE_SCOPE);
    }

    private static boolean cursoryImpliedEnd(Node n) {
        return htmlIn(n, CURSORY_IMPLIED_END);
    }

    private static Result reprocess(Mode m, Token t) {
        return new Result(REPROCESS, m, t);
    }

    private static Result split(String text) {
        return new Result(SPLIT, null, new Chars(Chars.NOT_SPLIT, text));
    }

    private static Result signal(int code) {
        return new Result(code, null, null);
    }

    private Node current() {
        return openElems.get(openElems.size() - 1);
    }

    private Node pop() {
        return openElems.remove(openElems.size() - 1);
    }

    private boolean currentIs(String name) {
        return current().isHtml(name);
    }

    private boolean inHtmlElemNamed(String name) {
        int id = openElems.id(name);
        for (int i = 0; i < openElems.size() && id > 0; i++) {
            if (openElems.info(i) >>> F_BITS == id) {
                return true;
            }
        }
        return false;
    }

    /** Whether an element matching {@code pred} is in the scope given as a mask of stack flags. */
    private boolean inScope(int scope, Predicate<Node> pred) {
        for (int i = openElems.size() - 1; i >= 0; i--) {
            if (pred.test(openElems.get(i))) {
                return true;
            }
            if ((openElems.info(i) & scope) != 0) {
                return false;
            }
        }
        return false;
    }

    private boolean inScopeNamed(int scope, String name) {
        int id = openElems.id(name);
        for (int i = openElems.size() - 1; i >= 0 && id > 0; i--) {
            int info = openElems.info(i);
            if (info >>> F_BITS == id) {
                return true;
            }
            if ((info & scope) != 0) {
                return false;
            }
        }
        return false;
    }

    private void generateImpliedEndTags(Predicate<Node> set) {
        while (!openElems.isEmpty() && set.test(current())) {
            pop();
        }
    }

    private void generateImpliedEndExcept(String except) {
        generateImpliedEndTags(n -> !n.isHtml(except) && cursoryImpliedEnd(n));
    }

    private void popUntilCurrent(Predicate<Node> set) {
        while (!set.test(current())) {
            pop();
        }
    }

    private void popUntil(Predicate<Node> pred) {
        while (!openElems.isEmpty()) {
            if (pred.test(pop())) {
                return;
            }
        }
    }

    private void popUntilNamed(String name) {
        popUntil(n -> n.isHtml(name));
    }

    private void closePElement() {
        generateImpliedEndTags(n -> !n.isHtml("p") && cursoryImpliedEnd(n));
        popUntilNamed("p");
    }

    private void closePInButtonScope() {
        if (inScopeNamed(BUTTON, "p")) {
            closePElement();
        }
    }

    private void removeFromStack(Node elem) {
        int i = openElems.lastIndexOf(elem);
        if (i >= 0) {
            openElems.remove(i);
        }
    }

    private int positionInActiveFormatting(Node elem) {
        for (int i = 0; i < activeFormatting.size(); i++) {
            if (activeFormatting.get(i).elem() == elem && elem != null) {
                return i;
            }
        }
        return -1;
    }

    private void clearActiveFormattingToMarker() {
        while (!activeFormatting.isEmpty() && activeFormatting.remove(activeFormatting.size() - 1) != MARKER) {
            continue;
        }
    }

    private Node bodyElem() {
        return openElems.size() > 1 && openElems.get(1).isHtml("body") ? openElems.get(1) : null;
    }

    private Mode resetInsertionMode() {
        for (int i = openElems.size() - 1; i >= 0; i--) {
            Node node = openElems.get(i);
            boolean last = i == 0;
            if (!node.name().ns().equals(Namespace.HTML)) {
                continue;
            }
            switch (node.name().local()) {
                case "td", "th" -> {
                    if (!last) {
                        return Mode.IN_CELL;
                    }
                }
                case "tr" -> {
                    return Mode.IN_ROW;
                }
                case "tbody", "thead", "tfoot" -> {
                    return Mode.IN_TABLE_BODY;
                }
                case "caption" -> {
                    return Mode.IN_CAPTION;
                }
                case "colgroup" -> {
                    return Mode.IN_COLUMN_GROUP;
                }
                case "table" -> {
                    return Mode.IN_TABLE;
                }
                case "template" -> {
                    return templateModes.get(templateModes.size() - 1);
                }
                case "head" -> {
                    if (!last) {
                        return Mode.IN_HEAD;
                    }
                }
                case "body" -> {
                    return Mode.IN_BODY;
                }
                case "frameset" -> {
                    return Mode.IN_FRAMESET;
                }
                case "html" -> {
                    return headElem == null ? Mode.BEFORE_HEAD : Mode.AFTER_HEAD;
                }
                default -> {
                }
            }
        }
        return Mode.IN_BODY;
    }

    // ---- Inserting nodes.

    /** The appropriate place for inserting a node: a parent, or a table and its previous element on the stack. */
    private record Place(Node parent, Node table, Node prev) {}

    private Place place(Node override) {
        Node target = override != null ? override : current();
        if (!(fosterParenting && htmlIn(target, FOSTER_TARGET))) {
            return new Place(target.isHtml("template") ? target.templateContents() : target, null, null);
        }
        for (int i = openElems.size() - 1; i >= 0; i--) {
            Node elem = openElems.get(i);
            if (elem.isHtml("template")) {
                return new Place(elem.templateContents(), null, null);
            } else if (elem.isHtml("table")) {
                return new Place(null, elem, openElems.get(i - 1));
            }
        }
        return new Place(openElems.get(0), null, null);
    }

    private static void insertAt(Place p, Object child) {
        if (p.table() == null) {
            append(p.parent(), child);
        } else if (p.table().parent() != null) {
            appendBeforeSibling(p.table(), child);
        } else {
            append(p.prev(), child);
        }
    }

    private Node insertElement(boolean push, String ns, String name, List<Attribute> attrs) {
        Place p = place(null);
        Node elem = createElement(new QualName(null, ns, name), attrs);
        insertAt(p, elem);
        if (push) {
            openElems.add(elem);
        }
        return elem;
    }

    private Node insertElementFor(Tag tag) {
        return insertElement(true, Namespace.HTML, tag.name(), tag.attrs());
    }

    private Node insertAndPopElementFor(Tag tag) {
        return insertElement(false, Namespace.HTML, tag.name(), tag.attrs());
    }

    private Node insertPhantom(String name) {
        return insertElement(true, Namespace.HTML, name, List.of());
    }

    private Result appendText(String text) {
        insertAt(place(null), text);
        return DONE;
    }

    private Result appendComment(String text) {
        insertAt(place(null), Node.comment(text));
        return DONE;
    }

    private void createRoot(List<Attribute> attrs) {
        Node elem = createElement(QualName.html("html"), attrs);
        openElems.add(elem);
        doc.append(elem);
    }

    private Result parseRawData(Tag tag, int rawKind) {
        insertElementFor(tag);
        return toRawTextMode(rawKind);
    }

    private Result toRawTextMode(int rawKind) {
        origMode = mode;
        mode = Mode.TEXT;
        return signal(rawKind);
    }

    // ---- Active formatting elements.

    private boolean isMarkerOrOpen(FormatEntry e) {
        return e == MARKER || openElems.lastIndexOf(e.elem()) >= 0;
    }

    private void reconstructActiveFormattingElements() {
        if (activeFormatting.isEmpty() || isMarkerOrOpen(activeFormatting.get(activeFormatting.size() - 1))) {
            return;
        }
        int i = activeFormatting.size() - 1;
        while (i != 0) {
            i--;
            if (isMarkerOrOpen(activeFormatting.get(i))) {
                i++;
                break;
            }
        }
        while (true) {
            Tag tag = activeFormatting.get(i).tag();
            Node elem = insertElement(true, Namespace.HTML, tag.name(), tag.attrs());
            activeFormatting.set(i, new FormatEntry(elem, tag));
            if (i == activeFormatting.size() - 1) {
                return;
            }
            i++;
        }
    }

    private void createFormattingElementFor(Tag tag) {
        int firstMatch = -1;
        int matches = 0;
        for (int i = activeFormatting.size() - 1; i >= 0 && activeFormatting.get(i) != MARKER; i--) {
            Tag old = activeFormatting.get(i).tag();
            if (old.name().equals(tag.name()) && old.attrs().size() == tag.attrs().size()
                    && new HashSet<>(old.attrs()).equals(new HashSet<>(tag.attrs()))) {
                firstMatch = i;
                matches++;
            }
        }
        if (matches >= 3) {
            activeFormatting.remove(firstMatch);
        }
        Node elem = insertElement(true, Namespace.HTML, tag.name(), tag.attrs());
        activeFormatting.add(new FormatEntry(elem, tag));
    }

    /** The last entry after the last marker whose element is an HTML element named {@code name}, or -1. */
    private int lastFormattingNamed(String name) {
        for (int i = activeFormatting.size() - 1; i >= 0 && activeFormatting.get(i) != MARKER; i--) {
            if (activeFormatting.get(i).tag().name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private void adoptionAgency(String subject) {
        if (currentIs(subject) && positionInActiveFormatting(current()) < 0) {
            pop();
            return;
        }
        for (int outer = 0; outer < 8; outer++) {
            int fmtIndex = lastFormattingNamed(subject);
            if (fmtIndex < 0) {
                processEndTagInBody(new Tag(true, subject, false, List.of()));
                return;
            }
            FormatEntry fmtEntry = activeFormatting.get(fmtIndex);
            Node fmtElem = fmtEntry.elem();
            int fmtStackIndex = openElems.lastIndexOf(fmtElem);
            if (fmtStackIndex < 0) {
                activeFormatting.remove(fmtIndex);
                return;
            }
            if (!inScope(DEFAULT, n -> n == fmtElem)) {
                return;
            }
            int furthestIndex = -1;
            for (int i = fmtStackIndex; i < openElems.size(); i++) {
                if ((openElems.info(i) & F_SPECIAL) != 0) {
                    furthestIndex = i;
                    break;
                }
            }
            if (furthestIndex < 0) {
                openElems.truncate(fmtStackIndex);
                activeFormatting.remove(fmtIndex);
                return;
            }
            Node furthestBlock = openElems.get(furthestIndex);
            Node commonAncestor = openElems.get(fmtStackIndex - 1);
            Node bookmarkAfter = null;
            int nodeIndex = furthestIndex;
            Node lastNode = furthestBlock;
            for (int inner = 1; ; inner++) {
                nodeIndex--;
                Node node = openElems.get(nodeIndex);
                if (node == fmtElem) {
                    break;
                }
                int nodeFmtIndex = positionInActiveFormatting(node);
                if (inner > 3 && nodeFmtIndex >= 0) {
                    activeFormatting.remove(nodeFmtIndex);
                }
                if (inner > 3 || nodeFmtIndex < 0) {
                    openElems.remove(nodeIndex);
                    continue;
                }
                Tag tag = activeFormatting.get(nodeFmtIndex).tag();
                Node newElem = createElement(QualName.html(tag.name()), tag.attrs());
                openElems.set(nodeIndex, newElem);
                activeFormatting.set(nodeFmtIndex, new FormatEntry(newElem, tag));
                if (lastNode == furthestBlock) {
                    bookmarkAfter = newElem;
                }
                lastNode.remove();
                newElem.append(lastNode);
                lastNode = newElem;
            }
            lastNode.remove();
            insertAt(place(commonAncestor), lastNode);
            Node newElem = createElement(QualName.html(fmtEntry.tag().name()), fmtEntry.tag().attrs());
            FormatEntry newEntry = new FormatEntry(newElem, fmtEntry.tag());
            furthestBlock.reparentChildrenTo(newElem);
            furthestBlock.append(newElem);
            if (bookmarkAfter == null) {
                activeFormatting.set(positionInActiveFormatting(fmtElem), newEntry);
            } else {
                activeFormatting.add(positionInActiveFormatting(bookmarkAfter) + 1, newEntry);
                activeFormatting.remove(positionInActiveFormatting(fmtElem));
            }
            removeFromStack(fmtElem);
            openElems.add(openElems.indexOf(furthestBlock) + 1, newElem);
        }
    }

    private void processEndTagInBody(Tag tag) {
        int id = openElems.id(tag.name());
        int matchIndex = -1;
        for (int i = openElems.size() - 1; i >= 0 && id > 0; i--) {
            if (openElems.info(i) >>> F_BITS == id) {
                matchIndex = i;
                break;
            }
            if ((openElems.info(i) & F_SPECIAL) != 0) {
                return;
            }
        }
        if (matchIndex < 0) {
            return;
        }
        generateImpliedEndExcept(tag.name());
        openElems.truncate(matchIndex);
    }

    private void handleMisnestedATags() {
        Node node = null;
        for (int i = activeFormatting.size() - 1; i >= 0 && activeFormatting.get(i) != MARKER; i--) {
            if (activeFormatting.get(i).elem().isHtml("a")) {
                node = activeFormatting.get(i).elem();
                break;
            }
        }
        if (node == null) {
            return;
        }
        adoptionAgency("a");
        int i = positionInActiveFormatting(node);
        if (i >= 0) {
            activeFormatting.remove(i);
        }
        removeFromStack(node);
    }

    // ---- Token processing.

    /** Processes one token from the tokenizer. */
    int process(Token token) {
        boolean wasIgnoreLf = ignoreLf;
        ignoreLf = false;
        if (token == Simple.ERROR) {
            return CONTINUE;
        } else if (token instanceof Doctype d) {
            if (mode == Mode.INITIAL) {
                doc.append(Node.doctype(orEmpty(d.name()), orEmpty(d.publicId()), orEmpty(d.systemId())));
                quirks = doctypeQuirks(d);
                mode = Mode.BEFORE_HTML;
            }
            return CONTINUE;
        } else if (token instanceof Chars c) {
            String text = wasIgnoreLf && c.text().startsWith("\n") ? c.text().substring(1) : c.text();
            if (text.isEmpty()) {
                return CONTINUE;
            }
            token = new Chars(Chars.NOT_SPLIT, text);
        }
        return processToCompletion(token);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean doctypeQuirks(Doctype d) {
        if (d.forceQuirks() || !"html".equals(d.name())) {
            return true;
        }
        String p = d.publicId() == null ? null : asciiLower(d.publicId());
        String s = d.systemId() == null ? null : asciiLower(d.systemId());
        return p != null && (QUIRKY_PUBLIC_MATCHES.contains(p) || QUIRKY_PUBLIC_PREFIXES.stream().anyMatch(p::startsWith)
                || s == null && HTML4_PUBLIC_PREFIXES.stream().anyMatch(p::startsWith))
            || "http://www.ibm.com/data/dtd/v11/ibmxhtml1-transitional.dtd".equals(s);
    }

    /** Whether the tokenizer may see a CDATA section: the current node is not an HTML element. */
    boolean adjustedCurrentNodeNotHtml() {
        return !openElems.isEmpty() && !current().name().ns().equals(Namespace.HTML);
    }

    private int processToCompletion(Token token) {
        Chars more = null;
        while (true) {
            Result r = isForeign(token) ? stepForeign(token) : step(mode, token);
            switch (r.code()) {
                case CONTINUE -> {
                    if (more == null) {
                        return CONTINUE;
                    }
                    token = more;
                    more = null;
                }
                case REPROCESS -> {
                    mode = r.mode();
                    token = r.token();
                }
                case SPLIT -> {
                    String buf = ((Chars) r.token()).text();
                    boolean ws = isAsciiWs(buf.charAt(0));
                    int i = 1;
                    while (i < buf.length() && isAsciiWs(buf.charAt(i)) == ws) {
                        i++;
                    }
                    token = new Chars(ws ? Chars.WHITESPACE : Chars.NOT_WHITESPACE, buf.substring(0, i));
                    if (i < buf.length()) {
                        more = new Chars(Chars.NOT_SPLIT, buf.substring(i));
                    }
                }
                default -> {
                    return r.code();
                }
            }
        }
    }

    private boolean isForeign(Token token) {
        if (token == Simple.EOF || openElems.isEmpty()) {
            return false;
        }
        Node cur = current();
        QualName name = cur.name();
        if (name.ns().equals(Namespace.HTML)) {
            return false;
        }
        boolean text = token instanceof Chars || token == Simple.NULL;
        Tag start = token instanceof Tag t && !t.end() ? t : null;
        if (mathmlTextIp(name)
                && (text || start != null && !start.name().equals("mglyph") && !start.name().equals("malignmark"))) {
            return false;
        }
        if (svgHtmlIp(name) && (text || start != null)) {
            return false;
        }
        if (name.ns().equals(Namespace.MATHML) && name.local().equals("annotation-xml")) {
            if (start != null && start.name().equals("svg")) {
                return false;
            }
            if (text || start != null) {
                return !cur.isMathmlAnnotationXmlIntegrationPoint();
            }
        }
        return true;
    }

    private static boolean isStart(Token t, String name) {
        return t instanceof Tag tag && !tag.end() && tag.name().equals(name);
    }

    private static boolean isEnd(Token t, String name) {
        return t instanceof Tag tag && tag.end() && tag.name().equals(name);
    }

    private static int split(Token t) {
        return t instanceof Chars c ? c.split() : -1;
    }

    private Result step(Mode m, Token token) {
        return switch (m) {
            case INITIAL -> initial(token);
            case BEFORE_HTML -> beforeHtml(token);
            case BEFORE_HEAD -> beforeHead(token);
            case IN_HEAD -> inHead(token);
            case IN_HEAD_NOSCRIPT -> inHeadNoscript(token);
            case AFTER_HEAD -> afterHead(token);
            case IN_BODY -> inBody(token);
            case TEXT -> text(token);
            case IN_TABLE -> inTable(token);
            case IN_TABLE_TEXT -> inTableText(token);
            case IN_CAPTION -> inCaption(token);
            case IN_COLUMN_GROUP -> inColumnGroup(token);
            case IN_TABLE_BODY -> inTableBody(token);
            case IN_ROW -> inRow(token);
            case IN_CELL -> inCell(token);
            case IN_TEMPLATE -> inTemplate(token);
            case AFTER_BODY, AFTER_AFTER_BODY -> afterBody(m, token);
            case IN_FRAMESET, AFTER_FRAMESET, AFTER_AFTER_FRAMESET -> frameset(m, token);
        };
    }

    private Result initial(Token token) {
        if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE) {
            return DONE;
        } else if (token instanceof Comment c) {
            doc.append(Node.comment(c.text()));
            return DONE;
        }
        quirks = true;
        return reprocess(Mode.BEFORE_HTML, token);
    }

    private Result beforeHtml(Token token) {
        if (token instanceof Comment c) {
            doc.append(Node.comment(c.text()));
            return DONE;
        } else if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE) {
            return DONE;
        } else if (token instanceof Tag tag) {
            if (isStart(tag, "html")) {
                createRoot(tag.attrs());
                mode = Mode.BEFORE_HEAD;
                return DONE;
            } else if (tag.end() && !HEAD_BODY_HTML_BR.contains(tag.name())) {
                return DONE;
            }
        }
        createRoot(List.of());
        return reprocess(Mode.BEFORE_HEAD, token);
    }

    private Result beforeHead(Token token) {
        if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE) {
            return DONE;
        } else if (token instanceof Comment c) {
            return appendComment(c.text());
        } else if (token instanceof Tag tag) {
            if (isStart(tag, "html")) {
                return inBody(token);
            } else if (isStart(tag, "head")) {
                headElem = insertElementFor(tag);
                mode = Mode.IN_HEAD;
                return DONE;
            } else if (tag.end() && !HEAD_BODY_HTML_BR.contains(tag.name())) {
                return DONE;
            }
        }
        headElem = insertPhantom("head");
        return reprocess(Mode.IN_HEAD, token);
    }

    private Result inHead(Token token) {
        if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE) {
            return appendText(((Chars) token).text());
        } else if (token instanceof Comment c) {
            return appendComment(c.text());
        } else if (token instanceof Tag tag && !tag.end()) {
            switch (tag.name()) {
                case "html" -> {
                    return inBody(token);
                }
                case "base", "basefont", "bgsound", "link", "meta" -> {
                    insertAndPopElementFor(tag);
                    if (attr(tag, "charset") != null) {
                        return signal(ENCODING);
                    }
                    String httpEquiv = attr(tag, "http-equiv");
                    String content = attr(tag, "content");
                    if (httpEquiv != null && eqIgnoreAsciiCase(httpEquiv, "content-type") && content != null
                            && hasMetaCharset(content)) {
                        return signal(ENCODING);
                    }
                    return DONE;
                }
                case "title" -> {
                    return parseRawData(tag, Tokenizer.RCDATA);
                }
                case "noframes", "style" -> {
                    return parseRawData(tag, Tokenizer.RAWTEXT);
                }
                case "noscript" -> {
                    insertElementFor(tag);
                    mode = Mode.IN_HEAD_NOSCRIPT;
                    return DONE;
                }
                case "script" -> {
                    Node elem = createElement(QualName.html("script"), tag.attrs());
                    insertAt(place(null), elem);
                    openElems.add(elem);
                    return toRawTextMode(Tokenizer.SCRIPT_DATA);
                }
                case "template" -> {
                    activeFormatting.add(MARKER);
                    framesetOk = false;
                    mode = Mode.IN_TEMPLATE;
                    templateModes.add(Mode.IN_TEMPLATE);
                    insertElementFor(tag);
                    return DONE;
                }
                case "head" -> {
                    return DONE;
                }
                default -> {
                }
            }
        } else if (token instanceof Tag tag) {
            switch (tag.name()) {
                case "head" -> {
                    pop();
                    mode = Mode.AFTER_HEAD;
                    return DONE;
                }
                case "body", "html", "br" -> {
                }
                case "template" -> {
                    if (inHtmlElemNamed("template")) {
                        generateImpliedEndTags(n -> htmlIn(n, THOROUGH_IMPLIED_END));
                        popUntilNamed("template");
                        clearActiveFormattingToMarker();
                        if (!templateModes.isEmpty()) {
                            templateModes.remove(templateModes.size() - 1);
                        }
                        mode = resetInsertionMode();
                    }
                    return DONE;
                }
                default -> {
                    return DONE;
                }
            }
        }
        pop();
        return reprocess(Mode.AFTER_HEAD, token);
    }

    /** The value of the attribute with no namespace and this local name, or null. */
    private static String attr(Tag tag, String local) {
        for (Attribute a : tag.attrs()) {
            if (a.name().ns().isEmpty() && a.name().local().equals(local)) {
                return a.value();
            }
        }
        return null;
    }

    /** Whether the algorithm for extracting a character encoding from a meta element finds one. */
    private static boolean hasMetaCharset(String s) {
        int pos = 0;
        while (true) {
            while (true) {
                if (pos + 7 > s.length()) {
                    return false;
                }
                if (eqIgnoreAsciiCase(s.substring(pos, pos + 7), "charset")) {
                    break;
                }
                pos++;
            }
            pos += 7;
            while (pos < s.length() && isAsciiWs(s.charAt(pos))) {
                pos++;
            }
            if (pos >= s.length()) {
                return false;
            }
            if (s.charAt(pos) == '=') {
                break;
            }
        }
        pos++;
        while (pos < s.length() && isAsciiWs(s.charAt(pos))) {
            pos++;
        }
        if (pos >= s.length()) {
            return false;
        }
        char q = s.charAt(pos);
        return q != '"' && q != '\'' || s.indexOf(q, pos + 1) >= 0;
    }

    private Result inHeadNoscript(Token token) {
        if (isStart(token, "html")) {
            return inBody(token);
        } else if (isEnd(token, "noscript")) {
            pop();
            mode = Mode.IN_HEAD;
            return DONE;
        } else if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE || token instanceof Comment) {
            return inHead(token);
        } else if (token instanceof Tag tag) {
            if (!tag.end() && Set.of("basefont", "bgsound", "link", "meta", "noframes", "style").contains(tag.name())) {
                return inHead(token);
            } else if (tag.end() ? !tag.name().equals("br") : tag.name().equals("head")
                    || tag.name().equals("noscript")) {
                return DONE;
            }
        }
        pop();
        return reprocess(Mode.IN_HEAD, token);
    }

    private Result afterHead(Token token) {
        if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE) {
            return appendText(((Chars) token).text());
        } else if (token instanceof Comment c) {
            return appendComment(c.text());
        } else if (token instanceof Tag tag && !tag.end()) {
            switch (tag.name()) {
                case "html" -> {
                    return inBody(token);
                }
                case "body" -> {
                    insertElementFor(tag);
                    framesetOk = false;
                    mode = Mode.IN_BODY;
                    return DONE;
                }
                case "frameset" -> {
                    insertElementFor(tag);
                    mode = Mode.IN_FRAMESET;
                    return DONE;
                }
                case "base", "basefont", "bgsound", "link", "meta", "noframes", "script", "style", "template",
                        "title" -> {
                    Node head = headElem;
                    openElems.add(head);
                    Result result = inHead(token);
                    removeFromStack(head);
                    return result;
                }
                case "head" -> {
                    return DONE;
                }
                default -> {
                }
            }
        } else if (token instanceof Tag tag) {
            if (tag.name().equals("template")) {
                return inHead(token);
            } else if (!Set.of("body", "html", "br").contains(tag.name())) {
                return DONE;
            }
        }
        insertPhantom("body");
        return reprocess(Mode.IN_BODY, token);
    }

    private Result inBody(Token token) {
        if (token == Simple.NULL) {
            return DONE;
        } else if (token instanceof Chars c) {
            reconstructActiveFormattingElements();
            if (anyNotWhitespace(c.text())) {
                framesetOk = false;
            }
            return appendText(c.text());
        } else if (token instanceof Comment c) {
            return appendComment(c.text());
        } else if (token == Simple.EOF) {
            return templateModes.isEmpty() ? DONE : inTemplate(token);
        }
        Tag tag = (Tag) token;
        String name = tag.name();
        return tag.end() ? inBodyEndTag(tag, name) : inBodyStartTag(tag, name);
    }

    private Result inBodyStartTag(Tag tag, String name) {
        switch (name) {
            case "html" -> {
                if (!inHtmlElemNamed("template")) {
                    addAttrsIfMissing(openElems.get(0), tag.attrs());
                }
                return DONE;
            }
            case "base", "basefont", "bgsound", "link", "meta", "noframes", "script", "style", "template", "title" -> {
                return inHead(tag);
            }
            case "body" -> {
                Node body = bodyElem();
                if (body != null && openElems.size() != 1 && !inHtmlElemNamed("template")) {
                    framesetOk = false;
                    addAttrsIfMissing(body, tag.attrs());
                }
                return DONE;
            }
            case "frameset" -> {
                Node body = bodyElem();
                if (!framesetOk || body == null) {
                    return DONE;
                }
                body.remove();
                openElems.truncate(1);
                insertElementFor(tag);
                mode = Mode.IN_FRAMESET;
                return DONE;
            }
            case "address", "article", "aside", "blockquote", "center", "details", "dialog", "dir", "div", "dl",
                    "fieldset", "figcaption", "figure", "footer", "header", "hgroup", "main", "nav", "ol", "p",
                    "search", "section", "summary", "ul", "menu" -> {
                closePInButtonScope();
                insertElementFor(tag);
                return DONE;
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                closePInButtonScope();
                if (htmlIn(current(), HEADING)) {
                    pop();
                }
                insertElementFor(tag);
                return DONE;
            }
            case "pre", "listing" -> {
                closePInButtonScope();
                insertElementFor(tag);
                ignoreLf = true;
                framesetOk = false;
                return DONE;
            }
            case "form" -> {
                if (formElem == null || inHtmlElemNamed("template")) {
                    closePInButtonScope();
                    Node elem = insertElementFor(tag);
                    if (!inHtmlElemNamed("template")) {
                        formElem = elem;
                    }
                }
                return DONE;
            }
            case "li", "dd", "dt" -> {
                framesetOk = false;
                for (int i = openElems.size() - 1; i >= 0; i--) {
                    Node node = openElems.get(i);
                    if (name.equals("li") ? node.isHtml("li") : node.isHtml("dd") || node.isHtml("dt")) {
                        String toClose = node.name().local();
                        generateImpliedEndExcept(toClose);
                        popUntilNamed(toClose);
                        break;
                    }
                    if ((openElems.info(i) & F_SPECIAL) != 0 && !node.isHtml("address") && !node.isHtml("div")
                            && !node.isHtml("p")) {
                        break;
                    }
                }
                closePInButtonScope();
                insertElementFor(tag);
                return DONE;
            }
            case "plaintext" -> {
                closePInButtonScope();
                insertElementFor(tag);
                return signal(PLAINTEXT);
            }
            case "button" -> {
                if (inScopeNamed(DEFAULT, "button")) {
                    generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    popUntilNamed("button");
                }
                reconstructActiveFormattingElements();
                insertElementFor(tag);
                framesetOk = false;
                return DONE;
            }
            case "a" -> {
                handleMisnestedATags();
                reconstructActiveFormattingElements();
                createFormattingElementFor(tag);
                return DONE;
            }
            case "b", "big", "code", "em", "font", "i", "s", "small", "strike", "strong", "tt", "u" -> {
                reconstructActiveFormattingElements();
                createFormattingElementFor(tag);
                return DONE;
            }
            case "nobr" -> {
                reconstructActiveFormattingElements();
                if (inScopeNamed(DEFAULT, "nobr")) {
                    adoptionAgency("nobr");
                    reconstructActiveFormattingElements();
                }
                createFormattingElementFor(tag);
                return DONE;
            }
            case "applet", "marquee", "object" -> {
                reconstructActiveFormattingElements();
                insertElementFor(tag);
                activeFormatting.add(MARKER);
                framesetOk = false;
                return DONE;
            }
            case "table" -> {
                if (!quirks) {
                    closePInButtonScope();
                }
                insertElementFor(tag);
                framesetOk = false;
                mode = Mode.IN_TABLE;
                return DONE;
            }
            case "area", "br", "embed", "img", "keygen", "wbr" -> {
                reconstructActiveFormattingElements();
                insertAndPopElementFor(tag);
                framesetOk = false;
                return DONE;
            }
            case "input" -> {
                if (inScopeNamed(DEFAULT, "select")) {
                    popUntilNamed("select");
                }
                String type = attr(tag, "type");
                reconstructActiveFormattingElements();
                insertAndPopElementFor(tag);
                if (type == null || !eqIgnoreAsciiCase(type, "hidden")) {
                    framesetOk = false;
                }
                return DONE;
            }
            case "param", "source", "track" -> {
                insertAndPopElementFor(tag);
                return DONE;
            }
            case "hr" -> {
                closePInButtonScope();
                if (inScopeNamed(DEFAULT, "select")) {
                    generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                }
                insertAndPopElementFor(tag);
                framesetOk = false;
                return DONE;
            }
            case "image" -> {
                return inBody(tag.withName("img"));
            }
            case "textarea" -> {
                ignoreLf = true;
                framesetOk = false;
                return parseRawData(tag, Tokenizer.RCDATA);
            }
            case "xmp" -> {
                closePInButtonScope();
                reconstructActiveFormattingElements();
                framesetOk = false;
                return parseRawData(tag, Tokenizer.RAWTEXT);
            }
            case "iframe" -> {
                framesetOk = false;
                return parseRawData(tag, Tokenizer.RAWTEXT);
            }
            case "noembed" -> {
                return parseRawData(tag, Tokenizer.RAWTEXT);
            }
            case "select" -> {
                if (inScopeNamed(DEFAULT, "select")) {
                    popUntilNamed("select");
                } else {
                    reconstructActiveFormattingElements();
                    insertElementFor(tag);
                    framesetOk = false;
                }
                return DONE;
            }
            case "option", "optgroup" -> {
                if (inScopeNamed(DEFAULT, "select")) {
                    if (name.equals("option")) {
                        generateImpliedEndExcept("optgroup");
                    } else {
                        generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    }
                } else if (currentIs("option")) {
                    pop();
                }
                reconstructActiveFormattingElements();
                insertElementFor(tag);
                return DONE;
            }
            case "rb", "rtc", "rp", "rt" -> {
                if (inScopeNamed(DEFAULT, "ruby")) {
                    if (name.equals("rb") || name.equals("rtc")) {
                        generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    } else {
                        generateImpliedEndExcept("rtc");
                    }
                }
                insertElementFor(tag);
                return DONE;
            }
            case "math", "svg" -> {
                reconstructActiveFormattingElements();
                return enterForeign(tag, name.equals("math") ? Namespace.MATHML : Namespace.SVG);
            }
            case "caption", "col", "colgroup", "frame", "head", "tbody", "td", "tfoot", "th", "thead", "tr" -> {
                return DONE;
            }
            default -> {
                reconstructActiveFormattingElements();
                insertElementFor(tag);
                return DONE;
            }
        }
    }

    private Result inBodyEndTag(Tag tag, String name) {
        switch (name) {
            case "template" -> {
                return inHead(tag);
            }
            case "body" -> {
                if (inScopeNamed(DEFAULT, "body")) {
                    mode = Mode.AFTER_BODY;
                }
                return DONE;
            }
            case "html" -> {
                return inScopeNamed(DEFAULT, "body") ? reprocess(Mode.AFTER_BODY, tag) : DONE;
            }
            case "address", "article", "aside", "blockquote", "button", "center", "details", "dialog", "dir", "div",
                    "dl", "fieldset", "figcaption", "figure", "footer", "header", "hgroup", "listing", "main", "menu",
                    "nav", "ol", "pre", "search", "section", "select", "summary", "ul" -> {
                if (inScopeNamed(DEFAULT, name)) {
                    generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    popUntilNamed(name);
                }
                return DONE;
            }
            case "form" -> {
                if (!inHtmlElemNamed("template")) {
                    Node node = formElem;
                    formElem = null;
                    if (node == null || !inScope(DEFAULT, n -> n == node)) {
                        return DONE;
                    }
                    generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    removeFromStack(node);
                } else if (inScopeNamed(DEFAULT, "form")) {
                    generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    popUntilNamed("form");
                }
                return DONE;
            }
            case "p" -> {
                if (!inScopeNamed(BUTTON, "p")) {
                    insertPhantom("p");
                }
                closePElement();
                return DONE;
            }
            case "li", "dd", "dt" -> {
                if (inScopeNamed(name.equals("li") ? LIST_ITEM : DEFAULT, name)) {
                    generateImpliedEndExcept(name);
                    popUntilNamed(name);
                }
                return DONE;
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                if (inScope(DEFAULT, n -> htmlIn(n, HEADING))) {
                    generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    popUntil(n -> htmlIn(n, HEADING));
                }
                return DONE;
            }
            case "a", "b", "big", "code", "em", "font", "i", "nobr", "s", "small", "strike", "strong", "tt", "u" -> {
                adoptionAgency(name);
                return DONE;
            }
            case "applet", "marquee", "object" -> {
                if (inScopeNamed(DEFAULT, name)) {
                    generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    popUntilNamed(name);
                    clearActiveFormattingToMarker();
                }
                return DONE;
            }
            case "br" -> {
                return inBody(new Tag(false, "br", tag.selfClosing(), List.of()));
            }
            default -> {
                processEndTagInBody(tag);
                return DONE;
            }
        }
    }

    private Result text(Token token) {
        if (token instanceof Chars c) {
            return appendText(c.text());
        } else if (token == Simple.EOF) {
            pop();
            Mode m = origMode;
            origMode = null;
            return reprocess(m, token);
        } else if (token instanceof Tag tag && tag.end()) {
            pop();
            mode = origMode;
            origMode = null;
            return tag.name().equals("script") ? signal(SCRIPT) : DONE;
        }
        throw new IllegalStateException("impossible case in Text mode");
    }

    private Result fosterParentInBody(Token token) {
        fosterParenting = true;
        Result r = inBody(token);
        fosterParenting = false;
        return r;
    }

    private Result inTable(Token token) {
        if (token == Simple.NULL || token instanceof Chars) {
            if (htmlIn(current(), FOSTER_TARGET)) {
                origMode = mode;
                return reprocess(Mode.IN_TABLE_TEXT, token);
            }
            return fosterParentInBody(token);
        } else if (token instanceof Comment c) {
            return appendComment(c.text());
        } else if (token == Simple.EOF) {
            return inBody(token);
        }
        Tag tag = (Tag) token;
        if (!tag.end()) {
            switch (tag.name()) {
                case "caption" -> {
                    popUntilCurrent(TreeBuilder::tableScope);
                    activeFormatting.add(MARKER);
                    insertElementFor(tag);
                    mode = Mode.IN_CAPTION;
                    return DONE;
                }
                case "colgroup" -> {
                    popUntilCurrent(TreeBuilder::tableScope);
                    insertElementFor(tag);
                    mode = Mode.IN_COLUMN_GROUP;
                    return DONE;
                }
                case "col" -> {
                    popUntilCurrent(TreeBuilder::tableScope);
                    insertPhantom("colgroup");
                    return reprocess(Mode.IN_COLUMN_GROUP, token);
                }
                case "tbody", "tfoot", "thead" -> {
                    popUntilCurrent(TreeBuilder::tableScope);
                    insertElementFor(tag);
                    mode = Mode.IN_TABLE_BODY;
                    return DONE;
                }
                case "td", "th", "tr" -> {
                    popUntilCurrent(TreeBuilder::tableScope);
                    insertPhantom("tbody");
                    return reprocess(Mode.IN_TABLE_BODY, token);
                }
                case "table" -> {
                    if (inScopeNamed(TABLE, "table")) {
                        popUntilNamed("table");
                        return reprocess(resetInsertionMode(), token);
                    }
                    return DONE;
                }
                case "style", "script", "template" -> {
                    return inHead(token);
                }
                case "input" -> {
                    String type = attr(tag, "type");
                    if (type != null && eqIgnoreAsciiCase(type, "hidden")) {
                        insertAndPopElementFor(tag);
                        return DONE;
                    }
                    return fosterParentInBody(token);
                }
                case "form" -> {
                    if (!inHtmlElemNamed("template") && formElem == null) {
                        formElem = insertAndPopElementFor(tag);
                    }
                    return DONE;
                }
                default -> {
                    return fosterParentInBody(token);
                }
            }
        }
        switch (tag.name()) {
            case "table" -> {
                if (inScopeNamed(TABLE, "table")) {
                    popUntilNamed("table");
                    mode = resetInsertionMode();
                }
                return DONE;
            }
            case "body", "caption", "col", "colgroup", "html", "tbody", "td", "tfoot", "th", "thead", "tr" -> {
                return DONE;
            }
            case "template" -> {
                return inHead(token);
            }
            default -> {
                return fosterParentInBody(token);
            }
        }
    }

    private Result inTableText(Token token) {
        if (token == Simple.NULL) {
            return DONE;
        } else if (token instanceof Chars c) {
            pendingTableText.add(c);
            return DONE;
        }
        List<Chars> pending = new ArrayList<>(pendingTableText);
        pendingTableText.clear();
        boolean nonSpace = pending.stream().anyMatch(c -> c.split() == Chars.NOT_WHITESPACE
            || c.split() == Chars.NOT_SPLIT && anyNotWhitespace(c.text()));
        for (Chars c : pending) {
            if (nonSpace) {
                if (fosterParentInBody(c) != DONE) {
                    throw new IllegalStateException("not prepared to handle this!");
                }
            } else {
                appendText(c.text());
            }
        }
        Mode m = origMode;
        origMode = null;
        return reprocess(m, token);
    }

    private Result inCaption(Token token) {
        if (token instanceof Tag tag) {
            String n = tag.name();
            if (!tag.end() && Set.of("caption", "col", "colgroup", "tbody", "td", "tfoot", "th", "thead", "tr")
                    .contains(n) || tag.end() && (n.equals("table") || n.equals("caption"))) {
                if (!inScopeNamed(TABLE, "caption")) {
                    return DONE;
                }
                generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                popUntilNamed("caption");
                clearActiveFormattingToMarker();
                if (isEnd(tag, "caption")) {
                    mode = Mode.IN_TABLE;
                    return DONE;
                }
                return reprocess(Mode.IN_TABLE, tag);
            } else if (tag.end() && Set.of("body", "col", "colgroup", "html", "tbody", "td", "tfoot", "th", "thead",
                    "tr").contains(n)) {
                return DONE;
            }
        }
        return inBody(token);
    }

    private Result inColumnGroup(Token token) {
        if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE) {
            return appendText(((Chars) token).text());
        } else if (token instanceof Comment c) {
            return appendComment(c.text());
        } else if (isStart(token, "html") || token == Simple.EOF) {
            return inBody(token);
        } else if (isStart(token, "col")) {
            insertAndPopElementFor((Tag) token);
            return DONE;
        } else if (isEnd(token, "colgroup")) {
            if (currentIs("colgroup")) {
                pop();
                mode = Mode.IN_TABLE;
            }
            return DONE;
        } else if (isEnd(token, "col")) {
            return DONE;
        } else if (isStart(token, "template") || isEnd(token, "template")) {
            return inHead(token);
        } else if (currentIs("colgroup")) {
            pop();
            return reprocess(Mode.IN_TABLE, token);
        }
        return DONE;
    }

    private Result inTableBody(Token token) {
        if (token instanceof Tag tag) {
            String n = tag.name();
            if (isStart(tag, "tr")) {
                popUntilCurrent(t -> htmlIn(t, TABLE_BODY_CONTEXT));
                insertElementFor(tag);
                mode = Mode.IN_ROW;
                return DONE;
            } else if (isStart(tag, "th") || isStart(tag, "td")) {
                popUntilCurrent(t -> htmlIn(t, TABLE_BODY_CONTEXT));
                insertPhantom("tr");
                return reprocess(Mode.IN_ROW, token);
            } else if (tag.end() && TABLE_SECTIONS.contains(n)) {
                if (inScopeNamed(TABLE, n)) {
                    popUntilCurrent(t -> htmlIn(t, TABLE_BODY_CONTEXT));
                    pop();
                    mode = Mode.IN_TABLE;
                }
                return DONE;
            } else if (!tag.end() && Set.of("caption", "col", "colgroup", "tbody", "tfoot", "thead").contains(n)
                    || isEnd(tag, "table")) {
                if (inScope(TABLE, t -> t.isHtml("table") || t.isHtml("tbody") || t.isHtml("tfoot"))) {
                    popUntilCurrent(t -> htmlIn(t, TABLE_BODY_CONTEXT));
                    pop();
                    return reprocess(Mode.IN_TABLE, token);
                }
                return DONE;
            } else if (tag.end() && Set.of("body", "caption", "col", "colgroup", "html", "td", "th", "tr")
                    .contains(n)) {
                return DONE;
            }
        }
        return inTable(token);
    }

    private Result inRow(Token token) {
        if (token instanceof Tag tag) {
            String n = tag.name();
            if (isStart(tag, "th") || isStart(tag, "td")) {
                popUntilCurrent(t -> htmlIn(t, TABLE_ROW_CONTEXT));
                insertElementFor(tag);
                mode = Mode.IN_CELL;
                activeFormatting.add(MARKER);
                return DONE;
            } else if (isEnd(tag, "tr")) {
                if (inScopeNamed(TABLE, "tr")) {
                    popUntilCurrent(t -> htmlIn(t, TABLE_ROW_CONTEXT));
                    pop();
                    mode = Mode.IN_TABLE_BODY;
                }
                return DONE;
            } else if (!tag.end() && Set.of("caption", "col", "colgroup", "tbody", "tfoot", "thead", "tr").contains(n)
                    || isEnd(tag, "table")) {
                if (inScopeNamed(TABLE, "tr")) {
                    popUntilCurrent(t -> htmlIn(t, TABLE_ROW_CONTEXT));
                    pop();
                    return reprocess(Mode.IN_TABLE_BODY, token);
                }
                return DONE;
            } else if (tag.end() && TABLE_SECTIONS.contains(n)) {
                if (inScopeNamed(TABLE, n) && inScopeNamed(TABLE, "tr")) {
                    popUntilCurrent(t -> htmlIn(t, TABLE_ROW_CONTEXT));
                    pop();
                    return reprocess(Mode.IN_TABLE_BODY, token);
                }
                return DONE;
            } else if (tag.end() && Set.of("body", "caption", "col", "colgroup", "html", "td", "th").contains(n)) {
                return DONE;
            }
        }
        return inTable(token);
    }

    private Result inCell(Token token) {
        if (token instanceof Tag tag) {
            String n = tag.name();
            if (tag.end() && TD_TH.contains(n)) {
                if (inScopeNamed(TABLE, n)) {
                    generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
                    popUntilNamed(n);
                    clearActiveFormattingToMarker();
                    mode = Mode.IN_ROW;
                }
                return DONE;
            } else if (!tag.end() && Set.of("caption", "col", "colgroup", "tbody", "td", "tfoot", "th", "thead", "tr")
                    .contains(n)) {
                if (inScope(TABLE, t -> htmlIn(t, TD_TH))) {
                    closeTheCell();
                    return reprocess(Mode.IN_ROW, token);
                }
                return DONE;
            } else if (tag.end() && Set.of("body", "caption", "col", "colgroup", "html").contains(n)) {
                return DONE;
            } else if (tag.end() && Set.of("table", "tbody", "tfoot", "thead", "tr").contains(n)) {
                if (inScopeNamed(TABLE, n)) {
                    closeTheCell();
                    return reprocess(Mode.IN_ROW, token);
                }
                return DONE;
            }
        }
        return inBody(token);
    }

    private void closeTheCell() {
        generateImpliedEndTags(TreeBuilder::cursoryImpliedEnd);
        popUntil(n -> htmlIn(n, TD_TH));
        clearActiveFormattingToMarker();
    }

    private Result switchTemplateMode(Mode m, Token token) {
        if (!templateModes.isEmpty()) {
            templateModes.remove(templateModes.size() - 1);
        }
        templateModes.add(m);
        return reprocess(m, token);
    }

    private Result inTemplate(Token token) {
        if (token instanceof Chars || token instanceof Comment) {
            return inBody(token);
        } else if (token == Simple.EOF) {
            if (!inHtmlElemNamed("template")) {
                return DONE;
            }
            popUntilNamed("template");
            clearActiveFormattingToMarker();
            if (!templateModes.isEmpty()) {
                templateModes.remove(templateModes.size() - 1);
            }
            mode = resetInsertionMode();
            return reprocess(resetInsertionMode(), token);
        }
        if (!(token instanceof Tag tag) || tag.end()) {
            return isEnd(token, "template") ? inHead(token) : DONE;
        }
        return switch (tag.name()) {
            case "base", "basefont", "bgsound", "link", "meta", "noframes", "script", "style", "template", "title" ->
                inHead(token);
            case "caption", "colgroup", "tbody", "tfoot", "thead" -> switchTemplateMode(Mode.IN_TABLE, token);
            case "col" -> switchTemplateMode(Mode.IN_COLUMN_GROUP, token);
            case "tr" -> switchTemplateMode(Mode.IN_TABLE_BODY, token);
            case "td", "th" -> switchTemplateMode(Mode.IN_ROW, token);
            default -> switchTemplateMode(Mode.IN_BODY, token);
        };
    }

    /** The after body and after after body modes. */
    private Result afterBody(Mode m, Token token) {
        if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE || isStart(token, "html")) {
            return inBody(token);
        } else if (token instanceof Comment c) {
            (m == Mode.AFTER_BODY ? openElems.get(0) : doc).append(Node.comment(c.text()));
            return DONE;
        } else if (token == Simple.EOF) {
            return DONE;
        } else if (m == Mode.AFTER_BODY && isEnd(token, "html")) {
            mode = Mode.AFTER_AFTER_BODY;
            return DONE;
        }
        return reprocess(Mode.IN_BODY, token);
    }

    /** The in frameset, after frameset and after after frameset modes. */
    private Result frameset(Mode m, Token token) {
        if (split(token) == Chars.NOT_SPLIT) {
            return split(((Chars) token).text());
        } else if (split(token) == Chars.WHITESPACE) {
            return m == Mode.AFTER_AFTER_FRAMESET ? inBody(token) : appendText(((Chars) token).text());
        } else if (token instanceof Comment c) {
            if (m == Mode.AFTER_AFTER_FRAMESET) {
                doc.append(Node.comment(c.text()));
                return DONE;
            }
            return appendComment(c.text());
        } else if (isStart(token, "html")) {
            return inBody(token);
        } else if (isStart(token, "noframes")) {
            return inHead(token);
        } else if (m == Mode.IN_FRAMESET && isStart(token, "frameset")) {
            insertElementFor((Tag) token);
        } else if (m == Mode.IN_FRAMESET && isEnd(token, "frameset")) {
            if (openElems.size() != 1) {
                pop();
                if (!currentIs("frameset")) {
                    mode = Mode.AFTER_FRAMESET;
                }
            }
        } else if (m == Mode.IN_FRAMESET && isStart(token, "frame")) {
            insertAndPopElementFor((Tag) token);
        } else if (m == Mode.AFTER_FRAMESET && isEnd(token, "html")) {
            mode = Mode.AFTER_AFTER_FRAMESET;
        }
        return DONE;
    }

    // ---- Foreign content.

    private Result enterForeign(Tag tag, String ns) {
        if (ns.equals(Namespace.MATHML)) {
            adjustMathmlAttributes(tag);
        } else {
            adjustAttributes(tag, SVG_ATTRIBUTES);
        }
        adjustForeignAttributes(tag);
        insertElement(!tag.selfClosing(), ns, tag.name(), tag.attrs());
        return DONE;
    }

    private static void adjustAttributes(Tag tag, Map<String, String> map) {
        List<Attribute> attrs = tag.attrs();
        for (int i = 0; i < attrs.size(); i++) {
            String replacement = map.get(attrs.get(i).name().local());
            if (replacement != null) {
                attrs.set(i, new Attribute(QualName.local(replacement), attrs.get(i).value()));
            }
        }
    }

    private static void adjustMathmlAttributes(Tag tag) {
        adjustAttributes(tag, Map.of("definitionurl", "definitionURL"));
    }

    private static void adjustForeignAttributes(Tag tag) {
        List<Attribute> attrs = tag.attrs();
        for (int i = 0; i < attrs.size(); i++) {
            QualName replacement = FOREIGN_ATTRIBUTES.get(attrs.get(i).name().local());
            if (replacement != null) {
                attrs.set(i, new Attribute(replacement, attrs.get(i).value()));
            }
        }
    }

    private Result foreignStartTag(Tag tag) {
        String ns = current().name().ns();
        if (ns.equals(Namespace.MATHML)) {
            adjustMathmlAttributes(tag);
        } else if (ns.equals(Namespace.SVG)) {
            tag = tag.withName(SVG_TAG_NAMES.getOrDefault(tag.name(), tag.name()));
            adjustAttributes(tag, SVG_ATTRIBUTES);
        }
        adjustForeignAttributes(tag);
        insertElement(!tag.selfClosing(), ns, tag.name(), tag.attrs());
        return DONE;
    }

    private Result unexpectedStartTagInForeignContent(Tag tag) {
        while (true) {
            QualName n = current().name();
            if (n.ns().equals(Namespace.HTML) || mathmlTextIp(n) || svgHtmlIp(n)) {
                break;
            }
            pop();
        }
        return step(mode, tag);
    }

    private Result stepForeign(Token token) {
        if (token == Simple.NULL) {
            return appendText("�");
        } else if (token instanceof Chars c) {
            if (anyNotWhitespace(c.text())) {
                framesetOk = false;
            }
            return appendText(c.text());
        } else if (token instanceof Comment c) {
            return appendComment(c.text());
        } else if (token == Simple.EOF) {
            throw new IllegalStateException("impossible case in foreign content");
        }
        Tag tag = (Tag) token;
        if (!tag.end()) {
            if (FOREIGN_BREAKOUT.contains(tag.name()) || tag.name().equals("font")
                    && (attr(tag, "color") != null || attr(tag, "face") != null || attr(tag, "size") != null)) {
                return unexpectedStartTagInForeignContent(tag);
            }
            return foreignStartTag(tag);
        } else if (tag.name().equals("br") || tag.name().equals("p")) {
            return unexpectedStartTagInForeignContent(tag);
        }
        boolean first = true;
        for (int i = openElems.size() - 1; i != 0; i--) {
            QualName n = openElems.get(i).name();
            if (!first && n.ns().equals(Namespace.HTML)) {
                return step(mode, tag);
            }
            if (eqIgnoreAsciiCase(n.local(), tag.name())) {
                openElems.truncate(i);
                return DONE;
            }
            first = false;
        }
        return DONE;
    }
}
