package io.github.jugglerchris.html2text.html5;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jugglerchris.html2text.dom.Node;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Malformed-markup cases; the expected dumps are the html5ever oracle's (tools/domdump). */
class HtmlParserTest {
    private static void check(String expected, byte[] input) {
        assertEquals(expected, DomDump.dump(HtmlParser.parseDocument(input)));
    }

    private static void check(String expected, String input) {
        check(expected, input.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void adoptionAgency() {
        check("""
            0 #document
            1 E - "http://www.w3.org/1999/xhtml" "html"
            2 E - "http://www.w3.org/1999/xhtml" "head"
            2 E - "http://www.w3.org/1999/xhtml" "body"
            3 E - "http://www.w3.org/1999/xhtml" "b"
            4 T "1"
            3 E - "http://www.w3.org/1999/xhtml" "p"
            4 E - "http://www.w3.org/1999/xhtml" "b"
            5 T "2"
            4 T "3"
            """, "<b>1<p>2</b>3</p>");
    }

    @Test
    void fosterParentedTextIsMerged() {
        check("""
            0 #document
            1 E - "http://www.w3.org/1999/xhtml" "html"
            2 E - "http://www.w3.org/1999/xhtml" "head"
            2 E - "http://www.w3.org/1999/xhtml" "body"
            3 T "xz"
            3 E - "http://www.w3.org/1999/xhtml" "table"
            4 E - "http://www.w3.org/1999/xhtml" "tbody"
            5 E - "http://www.w3.org/1999/xhtml" "tr"
            6 E - "http://www.w3.org/1999/xhtml" "td"
            7 T "y"
            """, "<table>x<tr><td>y</td></tr>z</table>");
    }

    @Test
    void quirksDoctypeKeepsTableInParagraph() {
        check("""
            0 #document
            1 <!DOCTYPE "html" "-//W3C//DTD HTML 4.01 Transitional//EN" "">
            1 E - "http://www.w3.org/1999/xhtml" "html"
            2 E - "http://www.w3.org/1999/xhtml" "head"
            2 E - "http://www.w3.org/1999/xhtml" "body"
            3 E - "http://www.w3.org/1999/xhtml" "p"
            4 E - "http://www.w3.org/1999/xhtml" "table"
            """, "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"><p><table>");
    }

    @Test
    void foreignContent() {
        check("""
            0 #document
            1 E - "http://www.w3.org/1999/xhtml" "html"
            2 E - "http://www.w3.org/1999/xhtml" "head"
            2 E - "http://www.w3.org/1999/xhtml" "body"
            3 E - "http://www.w3.org/2000/svg" "svg"
            4 @ - "" "viewBox"="0 0 1 1"
            4 @ "xlink" "http://www.w3.org/1999/xlink" "href"="a"
            4 @ "" "http://www.w3.org/2000/xmlns/" "xmlns"="x"
            4 E - "http://www.w3.org/2000/svg" "clipPath"
            3 E - "http://www.w3.org/1998/Math/MathML" "math"
            4 E - "http://www.w3.org/1998/Math/MathML" "annotation-xml" aip
            5 @ - "" "encoding"="text/html"
            5 E - "http://www.w3.org/1999/xhtml" "p"
            6 T "x"
            """, "<svg viewbox=\"0 0 1 1\" xlink:href=a xmlns=x><clippath/></svg>"
                + "<math><annotation-xml encoding=\"text/html\"><p>x</p></annotation-xml></math>");
    }

    @Test
    void templateContents() {
        check("""
            0 #document
            1 E - "http://www.w3.org/1999/xhtml" "html"
            2 E - "http://www.w3.org/1999/xhtml" "head"
            3 E - "http://www.w3.org/1999/xhtml" "template"
            4 #contents
            5 E - "http://www.w3.org/1999/xhtml" "td"
            6 T "x"
            2 E - "http://www.w3.org/1999/xhtml" "body"
            """, "<template><td>x</td></template>");
    }

    @Test
    void characterReferencesAndNewlines() {
        check("""
            0 #document
            1 E - "http://www.w3.org/1999/xhtml" "html"
            2 E - "http://www.w3.org/1999/xhtml" "head"
            2 E - "http://www.w3.org/1999/xhtml" "body"
            3 E - "http://www.w3.org/1999/xhtml" "pre"
            4 T "\\u{a}&\\u{ac}it; \\u{20ac}\\u{a}"
            """, "<pre>\n&#x0A&amp&notit; &#128;\r\n\0</pre>");
    }

    @Test
    void illFormedUtf8AndByteOrderMarks() {
        check("""
            0 #document
            1 E - "http://www.w3.org/1999/xhtml" "html"
            2 E - "http://www.w3.org/1999/xhtml" "head"
            2 E - "http://www.w3.org/1999/xhtml" "body"
            3 E - "http://www.w3.org/1999/xhtml" "p"
            4 T "a\\u{fffd}b\\u{fffd}"
            3 E - "http://www.w3.org/1999/xhtml" "meta"
            4 @ - "" "charset"="x"
            3 T "z"
            """, HexFormat.of().parseHex("efbbbf3c703e61ff62e2823c2f703e3c6d65746120636861727365743d783eefbbbf7a"));
    }

    @Test
    void selectAndNestedLinks() {
        check("""
            0 #document
            1 E - "http://www.w3.org/1999/xhtml" "html"
            2 E - "http://www.w3.org/1999/xhtml" "head"
            2 E - "http://www.w3.org/1999/xhtml" "body"
            3 E - "http://www.w3.org/1999/xhtml" "select"
            4 E - "http://www.w3.org/1999/xhtml" "option"
            5 T "a"
            4 E - "http://www.w3.org/1999/xhtml" "optgroup"
            5 E - "http://www.w3.org/1999/xhtml" "option"
            6 T "b"
            3 E - "http://www.w3.org/1999/xhtml" "a"
            4 @ - "" "href"="1"
            4 T "c"
            3 E - "http://www.w3.org/1999/xhtml" "div"
            4 E - "http://www.w3.org/1999/xhtml" "a"
            5 @ - "" "href"="1"
            4 E - "http://www.w3.org/1999/xhtml" "a"
            5 @ - "" "href"="2"
            5 T "d"
            """, "<select><option>a<optgroup><option>b</select><a href=1>c<div><a href=2>d</a></div>");
    }

    @Test
    void deepNestingDoesNotRecurse() {
        Node node = HtmlParser.parseDocument("<div>".repeat(100_000).getBytes(StandardCharsets.UTF_8));
        int depth = 0;
        while (!node.children().isEmpty()) {
            node = node.children().get(node.children().size() - 1);
            depth++;
        }
        assertEquals(100_002, depth);
    }
}
