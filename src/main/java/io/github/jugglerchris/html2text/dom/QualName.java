package io.github.jugglerchris.html2text.dom;

/**
 * A qualified name: an optional prefix, a namespace URL (empty for none) and a local name.
 *
 * @param prefix the prefix, or {@code null}
 * @param ns the namespace URL, or {@code ""} for no namespace
 * @param local the local name
 */
public record QualName(String prefix, String ns, String local) {
    /** An HTML element name. */
    public static QualName html(String local) {
        return new QualName(null, Namespace.HTML, local);
    }

    /** A name in no namespace (as most attribute names are). */
    public static QualName local(String local) {
        return new QualName(null, Namespace.NONE, local);
    }
}
