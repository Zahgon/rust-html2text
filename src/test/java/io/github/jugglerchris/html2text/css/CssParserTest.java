// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)
//
// Port of the unit tests of the original's css/parser.rs and css.rs.

package io.github.jugglerchris.html2text.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jugglerchris.html2text.Colour;
import io.github.jugglerchris.html2text.css.CssParser.Declaration;
import io.github.jugglerchris.html2text.css.CssParser.Prop;
import io.github.jugglerchris.html2text.css.CssParser.Rule;
import java.util.List;
import org.junit.jupiter.api.Test;

class CssParserTest {
    private static List<Declaration> rules(String css) {
        return new CssParser(css).rules(0);
    }

    private static List<Rule> stylesheet(String css) {
        return new CssParser(css).stylesheet();
    }

    private static Selector selector(String css) {
        return new CssParser(css).selector(0);
    }

    private static Declaration decl(Prop prop, Object value) {
        return new Declaration(prop, value, false);
    }

    @Test
    void parseDecl() {
        assertEquals(List.of(decl(Prop.UNKNOWN, null)), rules("foo:bar;"));
    }

    @Test
    void parseOverflow() {
        assertEquals(List.of(decl(Prop.OVERFLOW, "hidden"), decl(Prop.OVERFLOW_Y, "scroll")),
                rules("overflow: hidden; overflow-y: scroll"));
    }

    @Test
    void parseColor() {
        assertEquals(List.of(decl(Prop.COLOR, new Colour(0x11, 0x22, 0x33)), decl(Prop.COLOR, new Colour(0xab, 0xcd, 0xef))),
                rules("color: #123; color: #abcdef"));
        assertEquals(List.of(decl(Prop.UNKNOWN, null)), rules("color: inherit"));
    }

    @Test
    void parseHeight() {
        assertEquals(List.of(decl(Prop.HEIGHT, 0.0f), decl(Prop.MAX_HEIGHT, 100.0f)),
                rules("height: 0; max-height: 100cm"));
    }

    @Test
    void parseEmptyStylesheet() {
        assertEquals(List.of(), stylesheet(""));
    }

    @Test
    void parseClassAndImportant() {
        List<Rule> ss = stylesheet("\n  .foo {\n    color: #112233;\n    background-color: #332211 !important;\n  }\n");
        assertEquals(1, ss.size());
        assertEquals(".foo", ss.get(0).selectors().get(0).toString());
        assertEquals(List.of(decl(Prop.COLOR, new Colour(0x11, 0x22, 0x33)),
                new Declaration(Prop.BACKGROUND_COLOR, new Colour(0x33, 0x22, 0x11), true)), ss.get(0).declarations());
    }

    @Test
    void parseAtRules() {
        List<Rule> ss = stylesheet("@media paper {\n}\n@blah asldfkjasfda;\n"
                + "@nested { lkasjfd alkdsjfa sldkfjas ( alksjdasfd ) [ alskdjfalskdf] }\n"
                + "@keyframes foo {\n 0% { transform: translateY(0); }\n 50% { opacity:0.8; }\n 100% { }\n}\n"
                + ".foo { color: #112233; }");
        assertEquals(1, ss.size());
        assertEquals(".foo", ss.get(0).selectors().get(0).toString());
    }

    @Test
    void parseNamedColourAndFunction() {
        assertEquals(List.of(decl(Prop.COLOR, new Colour(0xff, 0xff, 0xff))), rules("color: white"));
        assertEquals(List.of(decl(Prop.COLOR, new Colour(1, 2, 3))), rules("color: rgb(1, 2, 3)"));
    }

    @Test
    void parseSelectors() {
        List<Rule> ss = stylesheet(".foo a .foo-bar2 { color: inherit; }\n.foo a.foo-bar .foo-bar2 { color: #112233; }\n"
                + ".foo a, p { color: #112233; }\n.foo a::before, p::after { content: \"blah*#\"; }");
        assertEquals(".foo a .foo-bar2", ss.get(0).selectors().get(0).toString());
        assertEquals(".foo a.foo-bar .foo-bar2", ss.get(1).selectors().get(0).toString());
        assertEquals("p", ss.get(2).selectors().get(1).toString());
        assertEquals(".foo a::before", ss.get(3).selectors().get(0).toString());
        assertEquals("p::after", ss.get(3).selectors().get(1).toString());
        assertEquals(List.of(decl(Prop.CONTENT, "blah*#")), ss.get(3).declarations());
    }

    @Test
    void background() {
        assertEquals(List.of(decl(Prop.BACKGROUND_COLOR, new Colour(0xff, 0xff, 0xff))), rules("background: white"));
        assertEquals(List.of(decl(Prop.BACKGROUND_COLOR, new Colour(0xff, 0xff, 0xff))),
                rules("background: url('blah'), white"));
        assertEquals(List.of(decl(Prop.UNKNOWN, null)), rules("background: url('blah'), foo"));
    }

    @Test
    void nthChild() {
        String[][] cases = {{"even", "2n+0"}, {"odd", "2n+1"}, {"17", "0n+17"}, {"17n", "17n+0"}, {"10n-1", "10n+-1"},
            {"10n+9", "10n+9"}, {"-n+3", "-1n+3"}, {"n", "1n+0"}, {"+n", "1n+0"}, {"-n", "-1n+0"}};
        for (String[] c : cases) {
            assertEquals(":nth-child(" + c[1] + ")", selector(":nth-child(" + c[0] + ")").toString());
        }
    }

    @Test
    void attributes() {
        assertEquals("[foo]", selector("[foo]").toString());
        assertEquals("[foo = \"bar\"]", selector("[foo=bar]").toString());
        assertEquals("[foo = \"some string\"]", selector("[foo='some string']").toString());
        assertEquals("x.y[foo = \"some string\"]", selector("x.y[foo='some string']").toString());
    }

    @Test
    void tokenHash() {
        Declaration unknownColour = decl(Prop.UNKNOWN, null);
        assertEquals(List.of(unknownColour), rules("color: #"));
        assertEquals(List.of(unknownColour), rules("color: #;"));
        assertEquals(1, stylesheet("p { color: #;}").size());
        assertEquals(1, stylesheet("p { color: #}").size());
    }

    @Test
    void specificity() {
        Specificity id = selector("#foo").specificity();
        Specificity classes = selector(".foo .bar .baz").specificity();
        assertEquals(new Specificity(false, 1, 0, 0), id);
        assertEquals(new Specificity(false, 0, 3, 0), classes);
        assertTrue(id.compareTo(classes) > 0);
    }
}
