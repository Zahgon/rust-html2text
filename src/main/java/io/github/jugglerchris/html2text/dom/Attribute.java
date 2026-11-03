package io.github.jugglerchris.html2text.dom;

/**
 * An attribute of an element.
 *
 * @param name the attribute name
 * @param value the attribute value
 */
public record Attribute(QualName name, String value) {}
