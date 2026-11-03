package io.github.jugglerchris.html2text.dom;

/** The namespace URLs the HTML parser uses. */
public final class Namespace {
    /** No namespace. */
    public static final String NONE = "";
    /** HTML. */
    public static final String HTML = "http://www.w3.org/1999/xhtml";
    /** SVG. */
    public static final String SVG = "http://www.w3.org/2000/svg";
    /** MathML. */
    public static final String MATHML = "http://www.w3.org/1998/Math/MathML";
    /** XLink (foreign attributes). */
    public static final String XLINK = "http://www.w3.org/1999/xlink";
    /** XML (foreign attributes). */
    public static final String XML = "http://www.w3.org/XML/1998/namespace";
    /** XMLNS (foreign attributes). */
    public static final String XMLNS = "http://www.w3.org/2000/xmlns/";

    private Namespace() {}
}
