// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)
//
// The conversions of the original's test suite (src/tests.rs): one test per upstream test, with the same inputs,
// configurations and widths, checked against the original's results. (Tests of the original that use custom Rust
// decorators, the XHTML parser or the css_ext features have no counterpart here.)

package io.github.jugglerchris.html2text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jugglerchris.html2text.render.RichAnnotation;
import io.github.jugglerchris.html2text.render.TaggedLine;
import io.github.jugglerchris.html2text.render.TaggedLineElement;
import io.github.jugglerchris.html2text.render.TrivialDecorator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class UpstreamTest {
    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** A text too long for a class constant, kept as a test resource. */
    private static String resource(String name) throws IOException {
        try (InputStream in = UpstreamTest.class.getResourceAsStream("upstream/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertFails(Html2TextException.Kind kind, Executable conversion) {
        assertEquals(kind, assertThrows(Html2TextException.class, conversion).kind());
    }

    /** The colour map of the original's tests: markers for some foreground and background colours. */
    static String testColourMap(List<RichAnnotation> annotations, String s) {
        String[] tags = {"", ""};
        String[] bgtags = {"", ""};
        for (RichAnnotation ann : annotations) {
            if (ann instanceof RichAnnotation.Fg fg) {
                String t = switch (fg.colour().toString()) {
                    case "#ff0000" -> "R";
                    case "#ffffff" -> "W";
                    case "#00ff00" -> "G";
                    case "#0000ff" -> "B";
                    default -> "?";
                };
                tags = new String[] {"<" + t + ">", "</" + t + ">"};
            } else if (ann instanceof RichAnnotation.Bg bg) {
                String t = switch (bg.colour().toString()) {
                    case "#ff0000" -> "r";
                    case "#00ff00" -> "g";
                    default -> ".";
                };
                bgtags = new String[] {"<" + t + ">", "</" + t + ">"};
            }
        }
        return bgtags[0] + tags[0] + s + tags[1] + bgtags[1];
    }

    /** Lines as text: each string as [annotations]text (_ for a unit annotation), each fragment as <#name>. */
    private static <A> String lines(List<TaggedLine<List<A>>> lines) {
        StringBuilder out = new StringBuilder();
        for (TaggedLine<List<A>> line : lines) {
            for (TaggedLineElement<List<A>> e : line) {
                if (e instanceof TaggedLineElement.Str<List<A>> str) {
                    out.append('[');
                    for (int i = 0; i < str.ts().tag().size(); i++) {
                        out.append(i > 0 ? "," : "").append(annotation(str.ts().tag().get(i)));
                    }
                    out.append(']').append(str.ts().s());
                } else {
                    out.append("<#").append(((TaggedLineElement.FragmentStart<List<A>>) e).name()).append('>');
                }
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String annotation(Object ann) {
        return switch (ann) {
            case RichAnnotation.Default d -> "Default";
            case RichAnnotation.Link l -> "Link=" + l.url();
            case RichAnnotation.Image i -> "Image=" + i.src();
            case RichAnnotation.Emphasis e -> "Emphasis";
            case RichAnnotation.Strong s -> "Strong";
            case RichAnnotation.Strikeout s -> "Strikeout";
            case RichAnnotation.Code c -> "Code";
            case RichAnnotation.Preformat p -> "Preformat=" + p.continuation();
            case RichAnnotation.Fg c -> "Colour=" + c.colour();
            case RichAnnotation.Bg c -> "BgColour=" + c.colour();
            default -> "_";
        };
    }

    @Test
    void testBeforeAfterInCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("Hello [world]!\n",
                c.coloured(b("\n        <style>\n          span.bracketed::before {\n              content: \"[\";\n          }\n          span.bracketed::after {\n              content: \"]\";\n          }\n        </style>\n        <body>\n        <p>Hello <span class=\"bracketed\">world</span>!</p>\n        </body>"), 80L, UpstreamTest::testColourMap));
    }

    @Test
    void testBgcolouredInCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("Test <g><R>red</R></g>\n",
                c.coloured(b("\n          <style>\n              .red {\n                  color:#FF0000;\n                  background-color:#00FF00;\n              }\n          </style>\n        <p>Test <span class=\"red\">red</span></p>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testBgcoloured2InCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("Test <g><R>red</R></g> and <G>green</G>\n",
                c.coloured(b("\n          <style>\n              .red {\n                  color:#FF0000;\n                  background-color:#00FF00;\n              }\n          </style>\n        <p>Test <span class=\"red\">red</span> and <span style=\"color: #00ff00\">green</span></p>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testBgcoloured3InCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("Test <g>Two words</g> bg\n",
                c.coloured(b("\n          <style>\n              .but {\n                  background-color:#00FF00;\n              }\n          </style>\n        <p>Test <span class=\"but\">Two words</span> bg</p>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testColorAttrInCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("Test <R>red</R>\n",
                c.coloured(b("\n        <p>Test <font color=\"red\">red</font></p>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testColourRowInCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("───┬─────\nRow│One  \n───┼─────\n<R>Row│Two  </R>\n<R>───┼─────</R>\nRow│Three\n───┴─────\n",
                c.coloured(b("<head><style>\n        tr.r {\n            color: #f00;\n        }\n        </style></head><body>\n        <table>\n         <tr>\n           <td>Row</td><td>One</td>\n         </tr>\n         <tr class=\"r\">\n           <td>Row</td><td>Two</td>\n         </tr>\n         <tr>\n           <td>Row</td><td>Three</td>\n         </tr>\n        </table>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testColouredAInCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("Test <R>red</R>\n",
                c.coloured(b("\n          <style>\n              .red {\n                  color:#FF0000;\n              }\n          </style>\n        <p>Test <a class=\"red\" href=\"foo\">red</a></p>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testColouredElementInCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("Test <R>red</R>\n",
                c.coloured(b("\n          <style>\n              .red {\n                  color:#FF0000;\n              }\n          </style>\n        <p>Test <blah class=\"red\" href=\"foo\">red</blah></p>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testColouredImportantInCssTests() throws Exception {
        {
            var c = Config.rich().useDocCss();
            assertEquals("Test\nparagraph\nwith <R>red</R>\ntext\n",
                    c.renderColoured(c.domToRenderTree(c.parseHtml(b("\n          <style>\n              .red {\n                  color:#FF0000 !important;\n              }\n          </style>\n        <p>Test paragraph with <span class=\"red\">red</span> text</p>\n        "))), 10L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Test paragraph with <R>red</R> text\n",
                    c.renderColoured(c.domToRenderTree(c.parseHtml(b("\n          <style>\n              .red {\n                  color:#FF0000 !important;\n              }\n          </style>\n        <p>Test paragraph with <span class=\"red\">red</span> text</p>\n        "))), 100L, UpstreamTest::testColourMap));
        }
    }

    @Test
    void testColouredMultiInCssTests() throws Exception {
        {
            var c = Config.rich().useDocCss();
            assertEquals("Test\nparagraph\nwith <R>red</R>\ntext\n",
                    c.renderColoured(c.domToRenderTree(c.parseHtml(b("\n          <style>\n              .red {\n                  color:#FF0000;\n              }\n          </style>\n        <p>Test paragraph with <span class=\"red\">red</span> text</p>\n        "))), 10L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Test paragraph with <R>red</R> text\n",
                    c.renderColoured(c.domToRenderTree(c.parseHtml(b("\n          <style>\n              .red {\n                  color:#FF0000;\n              }\n          </style>\n        <p>Test paragraph with <span class=\"red\">red</span> text</p>\n        "))), 100L, UpstreamTest::testColourMap));
        }
    }

    @Test
    void testCssLevelsInCssTests() throws Exception {
        var c = Config.rich().useDocCss().addAgentCss("\n                .agent_green { color: #0f0; }\n                .agent_green_imp { color: #0f0 !important; }\n                ").addCss("\n                .user_blue { color: #00f; }\n                .user_blue_imp { color: #00f !important; }\n                ");
        assertEquals("<R>Doc red</R>\n\n<G>Agent green</G>\n\n<B>User blue</B>\n\n<R>Doc vs agent</R>\n\n<B>Agent vs user</B>\n\n<R>User vs doc</R>\n\n<G>Doc vs agent!</G>\n\n<G>Agent! vs user</G>\n\n<B>User! vs doc</B>\n\n<G>Doc! vs agent!</G>\n\n<G>Agent! vs user!</G>\n\n<B>User! vs doc!</B>\n",
                c.coloured(b("\n        <head><style>\n            .doc_red { color: #f00; }\n            .doc_red_imp { color: #f00 !important; }\n        </style></head>\n        <body>\n            <p class=\"doc_red\">Doc red</p>\n            <p class=\"agent_green\">Agent green</p>\n            <p class=\"user_blue\">User blue</p>\n            <p class=\"doc_red agent_green\">Doc vs agent</p>\n            <p class=\"agent_green user_blue\">Agent vs user</p>\n            <p class=\"user_blue doc_red\">User vs doc</p>\n            <p class=\"doc_red agent_green_imp\">Doc vs agent!</p>\n            <p class=\"agent_green_imp user_blue\">Agent! vs user</p>\n            <p class=\"user_blue_imp doc_red\">User! vs doc</p>\n            <p class=\"doc_red_imp agent_green_imp\">Doc! vs agent!</p>\n            <p class=\"agent_green_imp user_blue_imp\">Agent! vs user!</p>\n            <p class=\"user_blue_imp doc_red_imp\">User! vs doc!</p>\n        </body>"), 80L, UpstreamTest::testColourMap));
    }

    @Test
    void testCssListsInCssTests() throws Exception {
        {
            var c = Config.rich().useDocCss();
            assertEquals("* <R>Line one</R>\n* Line <R>two</R>\n",
                    c.coloured(b("\n          <style>\n              .red {\n                  color:#FF0000;\n              }\n          </style>\n        <ul>\n          <li class=\"red\">Line one</li>\n          <li>Line <span class=\"red\">two</span></li>\n        </ul>\n        "), 20L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("1. <R>Line one</R>\n2. Line <R>two</R>\n",
                    c.coloured(b("\n          <style>\n              .red {\n                  color:#FF0000;\n              }\n          </style>\n        <ol>\n          <li class=\"red\">Line one</li>\n          <li>Line <span class=\"red\">two</span></li>\n        </ul>\n        "), 20L, UpstreamTest::testColourMap));
        }
    }

    @Test
    void testDispNoneInCssTests() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).useDocCss();
            assertEquals("Hello\n\nThere\n",
                    c.stringFromRead(b("\n          <style>\n              .hide { display: none; }\n          </style>\n        <p>Hello</p>\n        <p class=\"hide\">Ignore</p>\n        <p>There</p>"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).addCss(" .hide { display: none; }");
            assertEquals("Hello\n\nThere\n",
                    c.stringFromRead(b("\n        <p>Hello</p>\n        <p class=\"hide\">Ignore</p>\n        <p>There</p>"), 20L));
        }
    }

    @Test
    void testHeight0InCssTests() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).useDocCss();
        assertEquals("Hello\n",
                c.stringFromRead(b("\n        <div style=\"height: 0; overflow: hidden\">This should be hidden</div>\n        <p>Hello</p>"), 20L));
    }

    @Test
    void testIssue297InCssTests() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).useDocCss();
        assertEquals("x\n",
                c.stringFromRead(b("<html><head><style>@media all{p{color: #}}</style></head><body><p>x</p></body></html>"), 80L));
    }

    @Test
    void testMaxHeight0InCssTests() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).useDocCss();
        assertEquals("Hello\n",
                c.stringFromRead(b("\n        <div style=\"max-height: 0; overflow-y: hidden\">This should be hidden</div>\n        <p>Hello</p>"), 20L));
    }

    @Test
    void testNthChildInCssTests() throws Exception {
        {
            var c = Config.rich().useDocCss();
            assertEquals("* One\n* <R>Two</R>\n* Three\n* <R>Four</R>\n* Five\n",
                    c.coloured(b("\n          <style>\n              li:nth-child(even) {\n                  color: #f00;\n              }\n          </style>\n          <body><ul>\n              <li>One</li>\n              <li>Two</li>\n              <li>Three</li>\n              <li>Four</li>\n              <li>Five</li>\n          </ul>"), 20L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("* <R>One</R>\n* Two\n* <R>Three</R>\n* Four\n* <R>Five</R>\n",
                    c.coloured(b("\n          <style>\n              li:nth-child(odd) {\n                  color: #f00;\n              }\n          </style>\n          <body><ul>\n              <li>One</li>\n              <li>Two</li>\n              <li>Three</li>\n              <li>Four</li>\n              <li>Five</li>\n          </ul>"), 20L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("* <R>One</R>\n* <R>Two</R>\n* <R>Three</R>\n* Four\n* Five\n",
                    c.coloured(b("\n          <style>\n              li:nth-child(-n+3) {\n                  color: #f00;\n              }\n          </style>\n          <body><ul>\n              <li>One</li>\n              <li>Two</li>\n              <li>Three</li>\n              <li>Four</li>\n              <li>Five</li>\n          </ul>"), 20L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("* One\n* <R>Two</R>\n* Three\n* Four\n* Five\n",
                    c.coloured(b("\n          <style>\n              li:nth-child(2) {\n                  color: #f00;\n              }\n          </style>\n          <body><ul>\n              <li>One</li>\n              <li>Two</li>\n              <li>Three</li>\n              <li>Four</li>\n              <li>Five</li>\n          </ul>"), 20L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("* One\n* Two\n* <R>Three</R>\n* <R>Four</R>\n* <R>Five</R>\n* Six\n* Seven\n* Eight\n* Nine\n* Ten\n",
                    c.coloured(b("\n          <style>\n              li:nth-child(n+3):nth-child(-n+5) {\n                  color: #f00;\n              }\n          </style>\n          <body><ul>\n              <li>One</li>\n              <li>Two</li>\n              <li>Three</li>\n              <li>Four</li>\n              <li>Five</li>\n              <li>Six</li>\n              <li>Seven</li>\n              <li>Eight</li>\n              <li>Nine</li>\n              <li>Ten</li>\n          </ul>"), 20L, UpstreamTest::testColourMap));
        }
    }

    @Test
    void testPreWrapInCssTests() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).addCss(".prewrap { white-space: pre-wrap; }");
            assertEquals("Hi\n a\n  b\n   x\nlongword\nc  d  e\n",
                    c.stringFromRead(b("<p class=\"prewrap\">Hi\n a\n  b\n   x  longword\nc  d  e\n</p>"), 10L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).addCss(".prewrap { white-space: pre-wrap; }");
            assertEquals("Test wrapping of\nsome normal text\nin pre-wrap mode\n",
                    c.stringFromRead(b("<p class=\"prewrap\">Test wrapping of some normal text in pre-wrap mode</p>"), 17L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).addCss(".prewrap { white-space: pre-wrap; }");
            assertEquals("This  para  has  double  spacing\nwhich  should  survive  except\nat  line  breaks\n",
                    c.stringFromRead(b("<p class=\"prewrap\">This  para  has  double  spacing  which  should  survive  except  at  line  breaks</p>"), 33L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).addCss(".prewrap { white-space: pre-wrap; }");
            assertEquals("The last line shouldn't\nhave a space at the\nstart\n",
                    c.stringFromRead(b("<p class=\"prewrap\">The last line shouldn't have a space at the start</p>"), 23L));
        }
    }

    @Test
    void testSelectorAocInCssTests() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).useDocCss();
        assertEquals("Hello\n\nOk\n\nSpan1\n\nSpan1\n\nThere\n",
                c.stringFromRead(b("\n          <style>\n              .someclass > * > span > span {\n                  display: none;\n              }\n          </style>\n        <p>Hello</p>\n        <div class=\"someclass\">Ok\n        <p>\n         <span>Span1<span>Span2</span></span>\n        </p>\n        <div>\n         <span>Span1<span>Span2</span></span>\n        </div>\n        </div>\n        <p>There</p>"), 20L));
    }

    @Test
    void testSelectorChildDescInCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("X<R>CD</R>Y\n\n<G>C</G>D\n",
                c.coloured(b("<head><style>\n        p.d span { /* descendent */\n            color: #f00;\n        }\n        p.c > span { /* child */\n            color: #0f0;\n        }\n        </style></head><body>\n\n        <p class=\"d\">X<span>C</span><dummy><span>D</span></dummy>Y</p>\n        <p class=\"c\"><span>C</span><dummy><span>D</span></dummy></p>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testSelectorElementnameInCssTests() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).useDocCss();
        assertEquals("Hello\n\nThere\n",
                c.stringFromRead(b("\n          <style>\n              div { display: none; }\n          </style>\n        <p>Hello</p>\n        <div>Ignore</div>\n        <p>There</p>"), 20L));
    }

    @Test
    void testSelectorHashInCssTests() throws Exception {
        var c = Config.rich().useDocCss();
        assertEquals("<R>Foo</R>\n\n<G>Bar</G>\n\nBaz\n\n<W>Qux</W>\n",
                c.coloured(b("<head><style>\n        #foo {\n            color: #f00;\n        }\n        p#bar {\n            color: #0f0;\n        }\n        div#baz {\n            color: #00f;\n        }\n        *#qux {\n            color: #fff;\n        }\n        </style></head><body>\n\n        <p id=\"foo\">Foo</p>\n        <p id=\"bar\">Bar</p>\n        <p id=\"baz\">Baz</p>\n        <p id=\"qux\">Qux</p>\n        "), 20L, UpstreamTest::testColourMap));
    }

    @Test
    void testWrapNbspStyleInCssTests() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).useDocCss();
        assertEquals("Don't break a line between the words\nFoo and Bar\n",
                c.stringFromRead(b("<p>Don't break a line between the words <span style=\"white-space: pre\">Foo and Bar</span></p>"), 40L));
    }

    @Test
    void testWrapWordBoundariesInCssTests() throws Exception {
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello *<W>there</W>* boo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 20L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello *<W>there</W>*\nboo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 15L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello *<W>there</W>*\nboo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 14L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello *<W>there</W>*\nboo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 13L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello\n*<W>there</W>* boo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 12L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello\n*<W>there</W>* boo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 11L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello\n*<W>there</W>*\nboo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 10L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello\n*<W>there</W>*\nboo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 7L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello\n*<W>there</W>\n* boo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 6L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hello\n*<W>ther</W>\n<W>e</W>*\nboo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 5L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("Hell\no\n*<W>the</W>\n<W>re</W>*\nboo\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 4L, UpstreamTest::testColourMap));
        }
        {
            var c = Config.rich().useDocCss();
            assertEquals("H\ne\nl\nl\no\n*\n<W>t</W>\n<W>h</W>\n<W>e</W>\n<W>r</W>\n<W>e</W>\n*\nb\no\no\n",
                    c.coloured(b("<head><style>em { color: white; }</style></head>\n            <body>\n                Hello *<em>there</em>* boo"), 1L, UpstreamTest::testColourMap));
        }
    }

    @Test
    void fragList() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("<#my_id>[]* One\n[]* Two\n",
                lines(c.linesFromRead(b("<ul id=\"my_id\">\n            <li>One</li>\n            <li>Two</li>\n        </ul>"), 10L)));
    }

    @Test
    void fragSimple() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("<#my_id>[]Hi\n",
                lines(c.linesFromRead(b("<p id=\"my_id\">Hi</p>"), 10L)));
    }

    @Test
    void testBTag() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hi **bold**\n",
                c.stringFromRead(b("\n   <p>Hi <b>bold</b></p>\n"), 21L));
    }

    @Test
    void testBlockquote() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello\n\n> One, two,\n> three\n\nfoo\n",
                c.stringFromRead(b("<p>Hello</p>\n    <blockquote>One, two, three</blockquote>\n    <p>foo</p>\n"), 12L));
    }

    @Test
    void testBlockquoteOverflow() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("> > Foo\n",
                    c.stringFromRead(b("<blockquote><blockquote>Foo</blockquote></blockquote>"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertEquals("> > F\n> > o\n> > o\n",
                    c.stringFromRead(b("<blockquote><blockquote>Foo</blockquote></blockquote>"), 5L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<blockquote><blockquote>Foo</blockquote></blockquote>"), 3L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<blockquote><blockquote>Foo</blockquote></blockquote>"), 4L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L).allowWidthOverflow();
            assertEquals("> > F\n> > o\n> > o\n",
                    c.stringFromRead(b("<blockquote><blockquote>Foo</blockquote></blockquote>"), 3L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L).allowWidthOverflow();
            assertEquals("> > Foo\n",
                    c.stringFromRead(b("<blockquote><blockquote>Foo</blockquote></blockquote>"), 4L));
        }
    }

    @Test
    void testBr() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello\nWorld\n",
                c.stringFromRead(b("<p>Hello<br/>World</p>"), 20L));
    }

    @Test
    void testBr2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello\n\nWorld\n",
                c.stringFromRead(b("<p>Hello<br/><br/>World</p>"), 20L));
    }

    @Test
    void testBr3() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello\n\nWorld\n",
                c.stringFromRead(b("<p>Hello<br/> <br/>World</p>"), 20L));
    }

    @Test
    void testColspan() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬─\n1│2│3\n─┴─┼─\n12 │3\n─┬─┴─\n1│23 \n─┴───\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td>1</td>\n       <td>2</td>\n       <td>3</td>\n     </tr>\n     <tr>\n       <td colspan=\"2\">12</td>\n       <td>3</td>\n     </tr>\n     <tr>\n       <td>1</td>\n       <td colspan=\"2\">23</td>\n     </tr>\n   </table>\n"), 12L));
    }

    @Test
    void testColspanLarge() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬─\n1│2│3\n─┴─┼─\n12 │3\n─┬─┴──\n1│23  \n─┴────\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td>1</td>\n       <td>2</td>\n       <td>3</td>\n     </tr>\n     <tr>\n       <td colspan=\"2\">12</td>\n       <td>3</td>\n     </tr>\n     <tr>\n       <td>1</td>\n       <td colspan=\"99\">23</td>\n     </tr>\n   </table>\n"), 12L));
    }

    @Test
    void testColspanLarger() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬─\n1│2│3\n─┴─┼─\n12 │3\n─┬─┴─\n1│23 \n─┴───\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td colspan=\"50\">1</td>\n       <td colspan=\"50\">2</td>\n       <td colspan=\"50\">3</td>\n     </tr>\n     <tr>\n       <td colspan=\"100\">12</td>\n       <td colspan=\"50\">3</td>\n     </tr>\n     <tr>\n       <td colspan=\"50\">1</td>\n       <td colspan=\"100\">23</td>\n     </tr>\n   </table>\n"), 12L));
    }

    @Test
    void testColspanZero() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬─\n1│2│3\n─┴─┼─\n12 │3\n─┬─┴─\n1│23 \n─┴───\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td>1</td>\n       <td>2</td>\n       <td>3</td>\n     </tr>\n     <tr>\n       <td colspan=\"2\">12</td>\n       <td>3</td>\n     </tr>\n     <tr>\n       <td>1</td>\n       <td colspan=\"0\">23</td>\n     </tr>\n   </table>\n"), 12L));
    }

    @Test
    void testControlchar() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("FooBar\n",
                    c.stringFromRead(b("Foo\u0080Bar"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("FooB\nar\n",
                    c.stringFromRead(b("Foo\u0080Bar"), 4L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("FooB\nar\n",
                    c.stringFromRead(b("FooBa\u0080r"), 4L));
        }
    }

    @Test
    void testDdOverflow() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("> *Foo*\n>   Hello\n",
                    c.stringFromRead(b("<blockquote><dl><dt>Foo</dt><dd>Hello</dd></dl></blockquote>"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertEquals("> *Fo\n> o*\n>   H\n>   e\n>   l\n>   l\n>   o\n",
                    c.stringFromRead(b("<blockquote><dl><dt>Foo</dt><dd>Hello</dd></dl></blockquote>"), 5L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<blockquote><dl><dt>Foo</dt><dd>Hello</dd></dl></blockquote>"), 3L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<blockquote><dl><dt>Foo</dt><dd>Hello</dd></dl></blockquote>"), 4L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L).allowWidthOverflow();
            assertEquals("> *Foo*\n>   Hel\n>   lo\n",
                    c.stringFromRead(b("<blockquote><dl><dt>Foo</dt><dd>Hello</dd></dl></blockquote>"), 4L));
        }
    }

    @Test
    void testDeeplyNested() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("",
                c.stringFromRead(b(resource("text-000.txt")), 10L));
    }

    @Test
    void testDeeplyNestedTable() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals(resource("text-002.txt"),
                c.stringFromRead(b(resource("text-001.txt")), 10L));
    }

    @Test
    void testDiv() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n\nDiv\n",
                    c.stringFromRead(b("<p>Hello</p><div>Div</div>"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n\nDiv\nDiv2\n",
                    c.stringFromRead(b("<p>Hello</p><div>Div</div><div>Div2</div>"), 20L));
        }
    }

    @Test
    void testDl() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("*Foo*\n  Definition of foo\n",
                c.stringFromRead(b("<dl><dt>Foo</dt><dd>Definition of foo</dd></dl>"), 40L));
    }

    @Test
    void testDowrapUnicode() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Do break a line somewhere in foobarfoo\nbarfoobarfoobarfoobarfoobar\n",
                c.stringFromRead(b("<p>Do break a line somewhere in foo\u200bbar\u200bfoo\u200bbar\u200bfoo\u200bbar\u200bfoo\u200bbar\u200bfoo\u200bbar\u200bfoo\u200bbar</p>"), 40L));
    }

    @Test
    void testDowrapWbr() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Do break a line somewhere in foobarfoo\nbarfoobarfoobarfoobarfoobar\n",
                c.stringFromRead(b("<p>Do break a line somewhere in foo<wbr>bar<wbr>foo<wbr>bar<wbr>foo<wbr>bar<wbr>foo<wbr>bar<wbr>foo<wbr>bar<wbr>foo<wbr>bar</p>"), 40L));
    }

    @Test
    void testEmStrong() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hi *em* **strong**\n",
                c.stringFromRead(b("\n   <p>Hi <em>em</em> <strong>strong</strong></p>\n"), 21L));
    }

    @Test
    void testEmptyCols() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─\n1│2\n─┼─\n3│4\n─┼─\n5│6\n─┴─\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td></td>\n       <td>1</td>\n       <td></td>\n       <td>2</td>\n       <td></td>\n     </tr>\n     <tr>\n       <td></td>\n       <td>3</td>\n       <td></td>\n       <td>4</td>\n       <td></td>\n     </tr>\n     <tr>\n       <td></td>\n       <td>5</td>\n       <td></td>\n       <td>6</td>\n       <td></td>\n     </tr>\n   </table>\n"), 12L));
    }

    @Test
    void testEmptyRows() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬─\n1│2│3\n─┼─┼─\n4│5│6\n─┴─┴─\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td>1</td>\n       <td>2</td>\n       <td>3</td>\n     </tr>\n     <tr><td></td><td></td><td></td></tr>\n     <tr>\n       <td>4</td>\n       <td>5</td>\n       <td>6</td>\n     </tr>\n   </table>\n"), 12L));
    }

    @Test
    void testEmptyTable() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("",
                c.stringFromRead(b("\n   <table></table>\n"), 12L));
    }

    @Test
    void testEmptyTableInList() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("",
                c.stringFromRead(b("\n<ul>\n  <table>\n    <tr></tr>\n  </table>\n</ul>"), 80L));
    }

    @Test
    void testFenceTables() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).fenceTables();
        assertEquals("text\n\n```\n─┬─\n1│2\n─┼─\n3│4\n─┼─\n5│6\n─┴─\n```\n\ntext\n",
                c.stringFromRead(b("\n   <p>text</p>\n   <table>\n     <tr><th>1</th><th>2</th></tr>\n     <tr><td>3</td><td>4</td></tr>\n     <tr><td>5</td><td>6</td></tr>\n   </table>\n   <p>text</p>\n"), 12L));
    }

    @Test
    void testFenceTablesNested() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).fenceTables();
        assertEquals("text\n\n```\n─┬─────\n1│2    \n─┼─┬───\n3│4│5  \n │─┼───\n │6│7  \n─┴─┴───\n```\n\ntext\n",
                c.stringFromRead(b("\n   <p>text</p>\n   <table>\n     <tr><th>1</th><th>2</th></tr>\n     <tr>\n       <td>3</td>\n       <td>\n         <table>\n           <tr><td>4</td><td>5</td></tr>\n           <tr><td>6</td><td>7</td></tr>\n         </table>\n       </td>\n     </tr>\n   </table>\n   <p>text</p>\n"), 12L));
    }

    @Test
    void testH1() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("# Hi\n\nfoo\n",
                c.stringFromRead(b("\n   <h1>Hi</h1>\n   <p>foo</p>\n"), 21L));
    }

    @Test
    void testH3() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("### Hi\n\nfoo\n",
                c.stringFromRead(b("\n   <h3>Hi</h3>\n   <p>foo</p>\n"), 21L));
    }

    @Test
    void testHeaderOverflow() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("> ### Foo\n",
                    c.stringFromRead(b("<blockquote><h3>Foo</h3></blockquote>"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertEquals("> ### F\n> ### o\n> ### o\n",
                    c.stringFromRead(b("<blockquote><h3>Foo</h3></blockquote>"), 7L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<blockquote><h3>Foo</h3></blockquote>"), 6L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<blockquote><h3>Foo</h3></blockquote>"), 7L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L).allowWidthOverflow();
            assertEquals("> ### F\n> ### o\n> ### o\n",
                    c.stringFromRead(b("<blockquote><h3>Foo</h3></blockquote>"), 6L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L).allowWidthOverflow();
            assertEquals("> ### Foo\n",
                    c.stringFromRead(b("<blockquote><h3>Foo</h3></blockquote>"), 7L));
        }
    }

    @Test
    void testHeaderWidth() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("\n        <h2>\n            <table>\n                        <h3>Anything</h3>\n            </table>\n        </h2>\n"), 7L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("\n        <h2>\n            <table>\n                <h3>Anything</h3>\n            </table>\n        </h2>\n"), 5L));
        }
    }

    @Test
    void testImgAlt() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello [world]\n",
                c.stringFromRead(b("<p>Hello <img src='foo.jpg' alt='world'></p>"), 80L));
    }

    @Test
    void testImgNoalt() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello xy\n",
                    c.stringFromRead(b("<p>Hello x<img src='foo.jpg'>y</p>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello xy\n",
                    c.stringFromRead(b("<p>Hello x<img src='foo.jpg' alt=''>y</p>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).emptyImgMode(new ImageRenderMode.Filename());
            assertEquals("Hello x[foo.jpg]y\n",
                    c.stringFromRead(b("<p>Hello x<img src='foo.jpg' alt=''>y</p>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).emptyImgMode(new ImageRenderMode.Filename());
            assertEquals("Hello x[foo.jpg]y\n",
                    c.stringFromRead(b("<p>Hello x<img src='http://www.example.com/path/foo.jpg' alt=''>y</p>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).emptyImgMode(new ImageRenderMode.ShowAlways());
            assertEquals("Hello x[]y\n",
                    c.stringFromRead(b("<p>Hello x<img src='foo.jpg'>y</p>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).emptyImgMode(new ImageRenderMode.Replace("XYZ"));
            assertEquals("Hello x[XYZ]y\n",
                    c.stringFromRead(b("<p>Hello x<img src='foo.jpg'>y</p>"), 80L));
        }
    }

    @Test
    void testImgNosrc() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello xy\n",
                c.stringFromRead(b("<p>Hello x<img alt='myalt'>y</p>"), 80L));
    }

    @Test
    void testIssue16() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("",
                c.stringFromRead(b("<ul><li><!----></li></ul>"), 10L));
    }

    @Test
    void testIssue187() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("/\n\n─────────────────\n/                \n                 \n─────────────────\nna na na na na na\n na na na na na n\na na na na       \n─────────────────\n",
                c.stringFromRead(b("<div><table><tbody><tr><td><div><table><tbody><tr><td><div><pre>na na na na na na na na na na na na na na na</p></div></td></tr>/<tbody></table></div></td></tr>/<tbody></table></div>"), 17L));
    }

    @Test
    void testIssue252() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("\n\n\n",
                c.stringFromRead(b("<table><td rowspan=8><tr><tr>"), 10L));
    }

    @Test
    void testIssue2851() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("/p>\n\n─────────────────────────\n             ┬───────────────\n─┬───────────┘               ────\n─┼───────┬───────┘**[Bar][1]**   \n<│       │               \n─┴───────┴───────────────\n\n[1]: /\n",
                c.stringFromRead(b("/p>\n<table><td colspan=\"3\"><td rowspan=\"3\" colspan=\"5\"><tr><td rowspan=\"2\"><tr><td rowspan=\"2\"><tr><td><td rowspan=\"2\"><b>Foo<td rowspan=\"2\" colspan=\"8\"><b><a href=\"/\" title=\"title\">Bar<tr><td><td><"), 10000L));
    }

    @Test
    void testIssue2852() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("───────\nfoo bar\n───────\n\n─┬─\nx│y\n─┴─\n",
                c.stringFromRead(b("\n    <table>\n    <td rowspan=\"2\">foo bar</table>\n    <table>\n    <td>x<td>y"), 10000L));
    }

    @Test
    void testIssue54Oob() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬──────┬─\n\u00a0│Blah  │\u00a0\n │blah  │ \n │blah  │ \n─┴──────┴─\n",
                c.stringFromRead(b("\n<html>\n<body>\n    <table>\n        <tr>\n            <td>\n                <table>\n                    <tr>\n                        <td>&nbsp;</td>\n                        <td>\n                            <table>\n                                <tr>\n                                    <td>Blah blah blah\n                                    </td>\n                                </tr>\n                            </table>\n                        </td>\n                        <td>&nbsp;</td>\n                    </tr>\n                </table>\n            </td>\n        </tr>\n    </table>\n</body>\n"), 10L));
    }

    @Test
    void testIssue93X() throws Exception {
        var c = Config.withDecorator(new TrivialDecorator());
        assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(new byte[] {60, 116, 97, 98, 108, 101, 62, 60, 116, 114, 62, 60, 116, 100, 62, 120, 105, 60, 48, 62, 0, 0, 0, 60, 116, 97, 98, 108, 101, 62, 58, 58, 58, 62, 58, 62, 62, 62, 58, 60, 112, 32, 32, 32, 32, 32, 32, 32, 71, 87, 85, 78, 16, 16, 62, 60, 15, 16, 16, 16, 16, 16, 16, 15, 38, 16, 16, 16, 15, 1, 16, 16, 16, 16, 16, 16, -94, 111, 107, 99, 91, 112, 57, 64, 94, 100, 60, 111, 108, 47, 62, 127, 60, 108, 73, 62, 125, 109, 121, 102, 99, 122, 110, 102, 114, 98, 60, 97, 32, 104, 114, 101, 102, 61, 98, 111, 103, 32, 105, 100, 61, 100, 62, 60, 111, 15, 15, 15, 15, 15, 15, 15, 39, 15, 15, 15, 106, 102, 59, 99, 32, 32, 32, 86, 102, 122, 110, 104, 93, 108, 71, 114, 117, 110, 100, 96, 121, 57, 60, 107, 116, 109, -9, 62, 60, 32, 60, 122, 98, 99, 98, 97, 32, 119, 127, 127, 62, 60, 112, 62, 121, 116, 60, 47, 116, 100, 62, 62, 60, 111, 98, 62, 123, 110, 109, 97, 101, 105, 119, 60, 112, 101, 101, 122, 102, 63, 120, 97, 62, 60, 101, 62, 60, 120, 109, 112, 32, 28, 52, 55, 50, 50, 49, 52, -71, -106, 99, 62, -1, 112, 76, 85, 60, 112, 62, 73, 100, 116, 116, 60, 75, 50, 73, 116, 120, 110, 127, -1, 118, 32, 42, 40, 49, 33, 112, 32, 36, 107, 57, 60, 5, -93, 62, 49, 55, 32, 33, 118, 99, 63, 60, 109, 107, 43, 119, 100, 62, 60, 104, 58, 101, -93, -93, -93, -93, -36, -36, -36, -36, -36, -36, -36, -36, -36, -36, -36, -36, 1, 107, 117, 107, 108, 44, 102, 58, 60, 116, 101, 97, 106, 98, 59, 60, 115, 109, 52, 58, 115, 98, 62, -24, 110, 114, 32, 60, 117, 93, 120, 112, 119, 111, 59, 98, 120, 61, -50, 19, 61, -50, 19, 59, 1, 110, 102, 60, 115, 0, -14, 64, -53, 8, 111, 50, 59, 121, 122, 32, 42, 35, 32, 37, 101, 120, 104, 121, 0, -14, 59, 63, 121, -25, -126, -126, -126, -86, -86, 1, 32, 0, 0, 0, 28, -122, -56, 90, 119, 48, 60, 111, 108, 118, 119, 116, 113, 59, 100, 60, 117, 43, 110, 99, 9, -40, -99, -119, -40, -99, -10, -89, 62, 60, 104, 61, 43, 28, -122, -56, 105, 119, 48, 60, 122, 110, 0, -14, 61, 61, 114, -25, -126, -126, -126, -86, -86, -86, -23, -34, -34, -94, -93, -93, -93, -93, -93, -93, -93, 85, 100, 116, 99, 61, 60, -93, -93, -93, -93, -93, -36, -36, 1, 109, 112, 105, 10, 59, 105, -36, -41, 10, 59, 122, 100, 100, 121, 97, 43, 43, 43, 102, 122, 100, 60, 62, 114, 116, 122, 115, 61, 60, 115, 101, 62, -41, -41, -41, -41, -41, 98, 59, 60, 109, 120, 57, 60, 97, 102, 113, -27, 43, 43, 43, 43, 43, 43, 43, 43, 43, 35, 43, 43, 101, 58, 60, 116, 98, 101, 107, 98, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 98, 99, 62, 60, 112, 102, 59, 124, 107, 111, 97, 98, 108, 118, 60, 116, 102, 101, 104, 97, 62, 60, -1, 127, 46, 60, 116, 101, 62, 60, 105, 102, 63, 116, 116, 60, 47, 116, 101, 62, 62, 60, 115, 98, 62, 123, 109, 108, 97, 100, 119, 118, 60, 111, 99, 97, 103, 99, 62, 60, -1, 127, 46, 60, 103, 99, 62, 60, 116, 98, 63, 60, 101, 62, 60, 109, 109, -25, -126, -126, -126, -43, -43, -43, -23, -34, -34, 59, 101, 103, 58, 60, 100, 111, 61, 65, 114, 104, 60, 47, 101, 109, 62, 60, 99, 99, -84, 97, 97, 58, 60, 119, 99, 64, 126, 118, 104, 100, 100, 107, 105, 60, 120, 98, -1, -1, -1, 0, 60, -1, 127, 46, 60, 113, 127}, 1L));
    }

    @Test
    void testLink() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello, [world][1]\n\n[1]: http://www.example.com/\n",
                c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">world</a></p>"), 80L));
    }

    @Test
    void testLink2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello, [world][1]!\n\n[1]: http://www.example.com/\n",
                c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">world</a>!</p>"), 80L));
    }

    @Test
    void testLink3() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello, [w][1]orld\n\n[1]: http://www.example.com/\n",
                c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">w</a>orld</p>"), 80L));
    }

    @Test
    void testLinkIdLongline() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("[quitelong\nline][1]\n\n[1]: foo\n",
                c.stringFromRead(b("<a href=\"foo\" id=\"i\">quitelongline</a>"), 10L));
    }

    @Test
    void testLinkWrap() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("[Hello][1]\n\n[1]: http:\n//www.exam\nple.com/\n",
                c.stringFromRead(b("\n       <a href=\"http://www.example.com/\">Hello</a>"), 10L));
    }

    @Test
    void testLinksFootnotes() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello, [world][1]\n\n[1]: http://www.example.com/\n",
                    c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">world</a></p>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).linkFootnotes(false);
            assertEquals("Hello, [world]\n",
                    c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">world</a></p>"), 80L));
        }
    }

    @Test
    void testLinksFootnotesRich() throws Exception {
        {
            var c = Config.rich().linkFootnotes(true);
            assertEquals("Hello, world[1]\n\n[1]: http://www.example.com/\n",
                    c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">world</a></p>"), 80L));
        }
        {
            var c = Config.rich();
            assertEquals("Hello, world\n",
                    c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">world</a></p>"), 80L));
        }
    }

    @Test
    void testLinksFootnotesTrivial() throws Exception {
        {
            var c = Config.withDecorator(new TrivialDecorator()).linkFootnotes(true);
            assertEquals("Hello, world[1]\n\n[1]: http://www.example.com/\n",
                    c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">world</a></p>"), 80L));
        }
        {
            var c = Config.withDecorator(new TrivialDecorator());
            assertEquals("Hello, world\n",
                    c.stringFromRead(b("\n       <p>Hello, <a href=\"http://www.example.com/\">world</a></p>"), 80L));
        }
    }

    @Test
    void testLinksOutsideTable() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("──────────────┬───────────────\n[[Twitter]][1]│[[Facebook]][2]\n──────────────┴───────────────\n\n[1]: https://example.com/verylonglinks\n[2]: http://www.facebook.com/pages\n",
                c.stringFromRead(b("\n<table>\n<tbody>\n<tr><td><a href=\"https://example.com/verylonglinks\"><img src=\"http://www.twitter.com/img/icon_twitter.png\" alt=\"Twitter\"></a></td>\n    <td><a href=\"http://www.facebook.com/pages\"><img src=\"http://www.facebook.com/icon_facebook.png\" alt=\"Facebook\"></a></td>\n        </tr>\n        </tbody>\n        </table>\n"), 80L));
    }

    @Test
    void testListInTable() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("──────\n1.  0 \n2.  1 \n3.  2 \n4.  3 \n5.  4 \n6.  5 \n7.  6 \n8.  7 \n9.  8 \n10. 9 \n11. 10\n──────\n",
                c.stringFromRead(b("<table>\n<td><ol>\n<li>0</li>\n<li>1</li>\n<li>2</li>\n<li>3</li>\n<li>4</li>\n<li>5</li>\n<li>6</li>\n<li>7</li>\n<li>8</li>\n<li>9</li>\n<li>10</li>\n</ol></td>\n</table>"), 6L));
    }

    @Test
    void testMaxWidth() throws Exception {
        var c = Config.plainNoDecorate();
        assertEquals("─────\n3,266\n─────\n",
                c.stringFromRead(b("<table><td><p>3,266</p>"), Long.parseUnsignedLong("18446744073709551615")));
    }

    @Test
    void testMultiParse() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("one two three four five six seven eight nine ten eleven twelve thirteen fourteen\nfifteen sixteen seventeen\n",
                    c.renderToString(Html2Text.parse(b("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen")), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("one two three four five six seven eight nine ten eleven twelve\nthirteen fourteen fifteen sixteen seventeen\n",
                    c.renderToString(Html2Text.parse(b("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen")), 70L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("one two three four five six seven eight nine ten\neleven twelve thirteen fourteen fifteen sixteen\nseventeen\n",
                    c.renderToString(Html2Text.parse(b("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen")), 50L));
        }
    }

    @Test
    void testMultiPre() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("\u00a0\n\nSenior Security Engineer\n\n\u00a0\n\nSingapore Washington DC\n\n\u00a0\n\nemail: x.y@z.com\n\n\u00a0\n\nmobile:33 999999\n\nphone: 33 999999\n",
                c.stringFromRead(b("<head><style><!--\npre\n\t{\n\tmargin:0cm;\n\tmargin-bottom:.0001pt;\n\t}\n--></style></head>\n<body>\n    <pre>&nbsp;</pre>\n    <pre>Senior Security Engineer</pre>\n    <pre>&nbsp;</pre>\n    <pre>Singapore Washington DC</pre>\n    <pre>&nbsp;</pre>\n    <pre>email: x.y@z.com</pre>\n    <pre>&nbsp;</pre>\n    <pre>mobile:33 999999</pre>\n    <pre>phone: 33 999999</pre>\n</body>\n"), 80L));
    }

    @Test
    void testNarrowWidthNested() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<h1>Hi</h1>"), 1L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<blockquote>Hi</blockquote>"), 1L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<ul><li>Hi</li></ul>"), 1L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<ol><li>Hi</li></ul>"), 1L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<dl><dt>Foo</dt><dd>Definition of foo</dd></dl>"), 1L));
        }
    }

    @Test
    void testNbspIndent() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Top\n\u00a0Indented\n\u00a0\u00a0Indented again\n",
                c.stringFromRead(b("\n   <div>Top</div>\n   <div>&nbsp;Indented</div>\n   <div>&nbsp;&nbsp;Indented again</div>\n"), 21L));
    }

    @Test
    void testNestedOl() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("1. Item 1\n2. Item 2\n   1. SubItem 2.1\n   2. SubItem 2.2\n      1. Sub Item 2.2.1\n",
                c.stringFromRead(b("\n    <ol>\n      <li>Item 1</li>\n      <li>Item 2\n      <ol>\n        <li>SubItem 2.1</li>\n        <li>SubItem 2.2\n          <ol>\n            <li>Sub Item 2.2.1</li>\n          </ol>\n        </li>\n      </ol>\n    </ol>"), 80L));
    }

    @Test
    void testNestedTable1() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬─┬─┬─┬─┬─┬─┬─\n1│2│3│4│5│6│7│8│9\n─┼─┼─┼─┼─┼─┼─┼─┼─\n1│2│3│4│5│6│7│8│9\n─┼─┼─┼─┼─┼─┼─┼─┼─\n1│2│3│4│5│6│7│8│9\n─┴─┴─┴─┴─┴─┴─┴─┴─\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td>\n          <table><tr><td>1</td><td>2</td><td>3</td></tr></table>\n       </td>\n       <td>\n          <table><tr><td>4</td><td>5</td><td>6</td></tr></table>\n       </td>\n       <td>\n          <table><tr><td>7</td><td>8</td><td>9</td></tr></table>\n       </td>\n     </tr>\n     <tr>\n       <td>\n          <table><tr><td>1</td><td>2</td><td>3</td></tr></table>\n       </td>\n       <td>\n          <table><tr><td>4</td><td>5</td><td>6</td></tr></table>\n       </td>\n       <td>\n          <table><tr><td>7</td><td>8</td><td>9</td></tr></table>\n       </td>\n     </tr>\n     <tr>\n       <td>\n          <table><tr><td>1</td><td>2</td><td>3</td></tr></table>\n       </td>\n       <td>\n          <table><tr><td>4</td><td>5</td><td>6</td></tr></table>\n       </td>\n       <td>\n          <table><tr><td>7</td><td>8</td><td>9</td></tr></table>\n       </td>\n     </tr>\n   </table>\n"), 21L));
    }

    @Test
    void testNestedTable2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬───────\n1│a│one    \n─┼─│two    \n2│b│three  \n │ │four   \n │ │five   \n─┴─┴───────\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td>\n          <table>\n             <tr><td>1</td><td>a</td></tr>\n             <tr><td>2</td><td>b</td></tr>\n          </table>\n       </td>\n       <td><pre>one\ntwo\nthree\nfour\nfive\n</pre>\n       </td>\n     </tr>\n   </table>\n"), 11L));
    }

    @Test
    void testNestedUl() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("* Item 1\n* Item 2\n  * SubItem 2.1\n  * SubItem 2.2\n    * Sub Item 2.2.1\n",
                c.stringFromRead(b("\n    <ul>\n      <li>Item 1</li>\n      <li>Item 2\n      <ul>\n        <li>SubItem 2.1</li>\n        <li>SubItem 2.2\n          <ul>\n            <li>Sub Item 2.2.1</li>\n          </ul>\n        </li>\n      </ul>\n    </ul>"), 80L));
    }

    @Test
    void testNoscript() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello\n\n**There**\n",
                c.stringFromRead(b("<p>Hello</p>\n        <noscript><p><strong>There</strong></p></noscript>"), 80L));
    }

    @Test
    void testOl1() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("1. Item one\n2. Item two\n3. Item\n   three\n",
                c.stringFromRead(b("\n        <ol>\n          <li>Item one</li>\n          <li>Item two</li>\n          <li>Item three</li>\n        </ol>\n     "), 11L));
    }

    @Test
    void testOl2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("1.  Item one\n2.  Item two\n3.  Item three\n4.  Item four\n5.  Item five\n6.  Item six\n7.  Item seven\n8.  Item eight\n9.  Item nine\n10. Item ten\n",
                c.stringFromRead(b("\n        <ol>\n          <li>Item one</li>\n          <li>Item two</li>\n          <li>Item three</li>\n          <li>Item four</li>\n          <li>Item five</li>\n          <li>Item six</li>\n          <li>Item seven</li>\n          <li>Item eight</li>\n          <li>Item nine</li>\n          <li>Item ten</li>\n        </ol>\n     "), 20L));
    }

    @Test
    void testOlOverflow() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("1. 1. Foo\n",
                    c.stringFromRead(b("<ol><li><ol><li>Foo</li></ol></li></ol>"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertEquals("1. 1. F\n      o\n      o\n",
                    c.stringFromRead(b("<ol><li><ol><li>Foo</li></ol></li></ol>"), 7L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<ol><li><ol><li>Foo</li></ol></li></ol>"), 5L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<ol><li><ol><li>Foo</li></ol></li></ol>"), 6L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L).allowWidthOverflow();
            assertEquals("1. 1. F\n      o\n      o\n",
                    c.stringFromRead(b("<ol><li><ol><li>Foo</li></ol></li></ol>"), 5L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L).allowWidthOverflow();
            assertEquals("1. 1. Foo\n",
                    c.stringFromRead(b("<ol><li><ol><li>Foo</li></ol></li></ol>"), 6L));
        }
    }

    @Test
    void testOlStart() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("3. Item three\n4. Item four\n",
                c.stringFromRead(b("\n        <ol start=\"3\">\n          <li>Item three</li>\n          <li>Item four</li>\n        </ol>\n     "), 20L));
    }

    @Test
    void testOlStart9() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("9.  Item nine\n10. Item ten\n",
                c.stringFromRead(b("\n        <ol start=\"9\">\n          <li>Item nine</li>\n          <li>Item ten</li>\n        </ol>\n     "), 20L));
    }

    @Test
    void testOlStartNeg() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("-1. Item minus one\n0.  Item zero\n1.  Item one\n",
                c.stringFromRead(b("\n        <ol start=\"-1\">\n          <li>Item minus one</li>\n          <li>Item zero</li>\n          <li>Item one</li>\n        </ol>\n     "), 20L));
    }

    @Test
    void testOverflowWideChar() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("\ud83d\ude03"), 1L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L).allowWidthOverflow();
            assertEquals("\ud83d\ude03\n",
                    c.stringFromRead(b("\ud83d\ude03"), 1L));
        }
    }

    @Test
    void testPara() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello\n",
                c.stringFromRead(b("<p>Hello</p>"), 10L));
    }

    @Test
    void testPara2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello, world!\n",
                c.stringFromRead(b("<p>Hello, world!</p>"), 20L));
    }

    @Test
    void testPre() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("foo\nbar\nwib   asdf;\n\nHello\n",
                c.stringFromRead(b("\n       <pre>foo\nbar\nwib   asdf;\n</pre>\n<p>Hello</p>\n     "), 20L));
    }

    @Test
    void testPre2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello  sp\nworld\n",
                c.stringFromRead(b("<pre>Hello  sp\nworld</pre>"), 21L));
    }

    @Test
    void testPreBr() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Foo\nBar\n",
                c.stringFromRead(b("<pre>Foo<br>Bar</pre>"), 10L));
    }

    @Test
    void testPreEmptyline() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("X \n",
                c.stringFromRead(b("<pre>X<span id=\"i\"> </span></pre>"), 10L));
    }

    @Test
    void testPreRich() throws Exception {
        {
            var c = Config.rich();
            assertEquals("[Preformat=false]test\n",
                    lines(c.renderToLines(Html2Text.parse(b("<pre>test</pre>")), 100L)));
        }
        {
            var c = Config.rich();
            assertEquals("[Preformat=false]test\n[Preformat=true]long\n",
                    lines(c.renderToLines(Html2Text.parse(b("<pre>testlong</pre>")), 4L)));
        }
        {
            var c = Config.rich();
            assertEquals("[]test\n[]long\n",
                    lines(c.renderToLines(Html2Text.parse(b("<p style=\"white-space: pre\">testlong</p>")), 4L)));
        }
    }

    @Test
    void testPreSpan() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello $sp\nHi $foo\nHi foo, bar\n",
                c.stringFromRead(b("\n<pre>Hello <span>$</span>sp\n<span>Hi</span> <span>$</span><span>foo</span>\n<span>Hi</span> <span>foo</span><span>, </span><span>bar</span>\n</pre>"), 21L));
    }

    @Test
    void testPreTab() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("        world\n",
                    c.stringFromRead(b("<pre>\tworld</pre>"), 40L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("H       world\n",
                    c.stringFromRead(b("<pre>H\tworld</pre>"), 40L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("He      world\n",
                    c.stringFromRead(b("<pre>He\tworld</pre>"), 40L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hel     world\n",
                    c.stringFromRead(b("<pre>Hel\tworld</pre>"), 40L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hell    world\n",
                    c.stringFromRead(b("<pre>Hell\tworld</pre>"), 40L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello   world\n",
                    c.stringFromRead(b("<pre>Hello\tworld</pre>"), 40L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Helloo  world\n",
                    c.stringFromRead(b("<pre>Helloo\tworld</pre>"), 40L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hellooo world\n",
                    c.stringFromRead(b("<pre>Hellooo\tworld</pre>"), 40L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Helloooo        world\n",
                    c.stringFromRead(b("<pre>Helloooo\tworld</pre>"), 40L));
        }
    }

    @Test
    void testPreTab2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("        t0\nx       t1\nxx      t2\nxxx     t3\nxxxx    t4\nxxxxx   t5\nxxxxxx  t6\nxxxxxxx t7\nxxxxxxxx        t8\nxxxxxxxxx       t9\n",
                c.stringFromRead(b("<pre>\tt0\nx\tt1\nxx\tt2\nxxx\tt3\nxxxx\tt4\nxxxxx\tt5\nxxxxxx\tt6\nxxxxxxx\tt7\nxxxxxxxx\tt8\nxxxxxxxxx\tt9</pre>"), 40L));
    }

    @Test
    void testPreTab3() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("        t\nx       t\nxx      t\nxxx     t\nxxxx    t\nxxxxx   t\nxxxxxx  t\nxxxxxxx t\nxxxxxxxx  \nt\nxxxxxxxxx \nt\n",
                    c.stringFromRead(b("<pre>\tt\nx\tt\nxx\tt\nxxx\tt\nxxxx\tt\nxxxxx\tt\nxxxxxx\tt\nxxxxxxx\tt\nxxxxxxxx\tt\nxxxxxxxxx\tt</pre>"), 10L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("        t\nx       t\nxx      t\nxxx     t\nxxxx    t\nxxxxx   t\nxxxxxx  t\nxxxxxxx t\nxxxxxxxx \nt\nxxxxxxxxx\n        t\n",
                    c.stringFromRead(b("<pre>\tt\nx\tt\nxx\tt\nxxx\tt\nxxxx\tt\nxxxxx\tt\nxxxxxx\tt\nxxxxxxx\tt\nxxxxxxxx\tt\nxxxxxxxxx\tt</pre>"), 9L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("        \nt\nx       \nt\nxx      \nt\nxxx     \nt\nxxxx    \nt\nxxxxx   \nt\nxxxxxx  \nt\nxxxxxxx \nt\nxxxxxxxx\n        \nt\nxxxxxxxx\nx       \nt\n",
                    c.stringFromRead(b("<pre>\tt\nx\tt\nxx\tt\nxxx\tt\nxxxx\tt\nxxxxx\tt\nxxxxxx\tt\nxxxxxxx\tt\nxxxxxxxx\tt\nxxxxxxxxx\tt</pre>"), 8L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("       \nt\nx      \nt\nxx     \nt\nxxx    \nt\nxxxx   \nt\nxxxxx  \nt\nxxxxxx \nt\nxxxxxxx\n       \nt\nxxxxxxx\nx      \nt\nxxxxxxx\nxx     \nt\n",
                    c.stringFromRead(b("<pre>\tt\nx\tt\nxx\tt\nxxx\tt\nxxxx\tt\nxxxxx\tt\nxxxxxx\tt\nxxxxxxx\tt\nxxxxxxxx\tt\nxxxxxxxxx\tt</pre>"), 7L));
        }
    }

    @Test
    void testPreservingEmptyNewlinesInPreBlocks() throws Exception {
        var c = Config.withDecorator(new TrivialDecorator());
        assertEquals("Test.\n\n\nEnd.\n",
                c.stringFromRead(b("<pre>\nTest.\n\n\nEnd.\n</pre>"), 20L));
    }

    @Test
    void testReadCustom() throws Exception {
        var c = Config.withDecorator(new TrivialDecorator());
        assertEquals("[_]bold\n",
                lines(c.renderToLines(Html2Text.parse(b("<strong>bold</strong>")), 80L)));
    }

    @Test
    void testReadRich() throws Exception {
        var c = Config.rich();
        assertEquals("[Strong]bold\n",
                lines(c.renderToLines(Html2Text.parse(b("<strong>bold</strong>")), 80L)));
    }

    @Test
    void testReadRichNodecorate() throws Exception {
        var c = Config.rich();
        assertEquals("[Strong]bold\n",
                lines(c.renderToLines(Html2Text.parse(b("<strong>bold</strong>")), 80L)));
    }

    @Test
    void testRendererZeroWidth() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<ul><li><table><tr><td>x</td></tr></table></li></ul>\n"), 2L));
    }

    @Test
    void testRowspan1() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("────┬────┬──┬──┬────\nH0  │H1  │H2│H3│H4  \n────┼────┼──┴──┴────\nX   │Boo │oh no     \n────┤Foo ├─────┬────\nX123│    │O    │one \n213 │    │     │two \n────┼────┴─────┤    \nY   │Hmm oh    │    \n────┴──────────┴────\n",
                c.stringFromRead(b("<table>\n    <tr><th>H0</th><th>H1</th><th>H2</th><th>H3</th><th>H4</th></tr>\n    <tr><td>X</td><td rowspan=2>Boo\n            Foo</td><td colspan=3>oh no</td></tr>\n    <tr><td>X123213</td><td rowspan=1 colspan=2>O</td><td rowspan=2>one two</td></tr>\n    <tr><td>Y</td><td colspan=3>Hmm oh</td></tr>\n</table>"), 20L));
    }

    @Test
    void testRowspan2Emptyrow() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("────┬────┬──┬──┬────\nH0  │H1  │H2│H3│H4  \n────┼────┼──┴──┴────\nX   │Boo │oh no     \n────┤Foo ├─────┬────\nX123│    │O    │one \n213 │    │     │two \n────┤    │     │    \n────┼────┴─────┤    \nY   │Hmm oh    │    \n────┴──────────┴────\n",
                c.stringFromRead(b("<table>\n    <tr><th>H0</th><th>H1</th><th>H2</th><th>H3</th><th>H4</th></tr>\n    <tr><td>X</td><td rowspan=3>Boo\n            Foo</td><td colspan=3>oh no</td></tr>\n    <tr><td>X123213</td><td rowspan=2 colspan=2>O</td><td rowspan=3>one two</td></tr>\n    <tr></tr>\n    <tr><td>Y</td><td colspan=3>Hmm oh</td></tr>\n</table>"), 20L));
    }

    @Test
    void testRowspan3Shortrow() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("───┬────┬──┬──┬─────\nH0 │H1  │H2│H3│H4   \n───┴────┴──┴──┴─────\nfoo\n───┬────┬───────────\nX  │B F │oh no      \n   │b b │           \n───┤o t ├─────┬─────\nY  │the │O    │one  \n   │    │     │two  \n───┼────┴──┬──┤thr  \nW  │Hmm oh │  │fou  \n   │       │  │fiv  \n───┴───────┴──┴─────\n",
                c.stringFromRead(b("<table>\n    <tr><th>H0</th><th>H1</th><th>H2</th><th>H3</th><th>H4</th></tr>\n    <tr><td>foo</td></tr>\n    <tr><td>X</td><td rowspan=2>B\n            F b b o t the</td><td colspan=3>oh no</td></tr>\n    <tr><td>Y</td><td rowspan=1 colspan=2>O</td><td rowspan=2>one two thr fou fiv</td></tr>\n    <tr><td>W</td><td colspan=2>Hmm oh</td></tr>\n</table>"), 20L));
    }

    @Test
    void testRowspanUnderflow() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("\n",
                c.stringFromRead(b("<table>\n  <tr>\n    <td></td>\n    <td rowspan=\"2\"></td>\n  </tr>\n  <tr>\n    <td></td>\n  </tr>\n</table>\n        "), 20L));
    }

    @Test
    void testRowspanZero() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─\nx\n─\n",
                c.stringFromRead(b("<table><tr><td rowspan=\"0\">x</td></tr></table>"), 80L));
    }

    @Test
    void testS() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hi y\u0336o\u0336u\u0336thee!\n",
                c.stringFromRead(b("Hi <s>you</s>thee!"), 40L));
    }

    @Test
    void testSillyColspan() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("───\nfoo\n───\n",
                c.stringFromRead(b("\n  <table>\n    <tr>\n      <td colspan=\"9007199254740991\">foo</td.\n    </tr>\n  </table>\n"), 80L));
    }

    @Test
    void testStripNl() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("One Two Three\n",
                c.stringFromRead(b("\n        <p>\n           One\n           Two\n           Three\n        </p>\n     "), 40L));
    }

    @Test
    void testStripNl2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("One Two Three\n",
                c.stringFromRead(b("\n        <p>\n           One\n           <span>\n               Two\n           </span>\n           Three\n        </p>\n     "), 40L));
    }

    @Test
    void testStripNlTbl() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("──────────────\nOne Two Three \n──────────────\n",
                c.stringFromRead(b("\n       <table>\n         <tr>\n            <td>\n               One\n               <span>\n                   Two\n               </span>\n               Three\n            </td>\n          </tr>\n        </table>\n     "), 20L));
    }

    @Test
    void testStripNlTblP() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("──────────────\nOne Two Three \n──────────────\n",
                c.stringFromRead(b("\n       <table>\n         <tr>\n            <td><p>\n               One\n               <span>\n                   Two\n               </span>\n               Three\n            </p></td>\n          </tr>\n        </table>\n     "), 20L));
    }

    @Test
    void testSubblock() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Here's a [link][1].\n* Bullet\n* Bullet\n* Bullet\n\n[1]: https://example.com/\n",
                c.stringFromRead(b("<div>\n     <div>Here's a <a href=\"https://example.com/\">link</a>.</div>\n     <div><ul>\n     <li>Bullet</li>\n     <li>Bullet</li>\n     <li>Bullet</li>\n     </ul></div>\n     </div>"), 80L));
    }

    @Test
    void testSuperscript() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Exponential x^{y}\n",
                    c.stringFromRead(b("Exponential x<sup>y</sup>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Exponential 2³²\n",
                    c.stringFromRead(b("Exponential 2<sup>32</sup>"), 80L));
        }
    }

    @Test
    void testSvg() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello [world]\n",
                    c.stringFromRead(b("<p>Hello <svg><title>world</title></svg></p>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n",
                    c.stringFromRead(b("<p>Hello<svg><style>blah</style></svg></p>"), 80L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n",
                    c.stringFromRead(b("<p>Hello<svg></svg></p>"), 80L));
        }
    }

    @Test
    void testTable() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬─\n1│2│3\n─┴─┴─\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td>1</td>\n       <td>2</td>\n       <td>3</td>\n     </tr>\n   </table>\n"), 12L));
    }

    @Test
    void testTable2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─┬─┬─\n1│2│3\n─┼─┼─\n4│5│6\n─┴─┴─\n",
                c.stringFromRead(b("\n   <table>\n     <tr>\n       <td>1</td>\n       <td>2</td>\n       <td>3</td>\n     </tr>\n     <tr>\n       <td>4</td>\n       <td>5</td>\n       <td>6</td>\n     </tr>\n   </table>\n"), 12L));
    }

    @Test
    void testTableCellId() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─────────\nhi, world\n─────────\n",
                c.stringFromRead(b("<html><body><table>\n        <tr>\n            <td id=\"bodyCell\">hi, world</td>\n        </tr>\n    </table></body></html>"), 10L));
    }

    @Test
    void testTableEmptySingleRow() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("",
                c.stringFromRead(b("\n   <table><tr></tr></table>\n"), 12L));
    }

    @Test
    void testTableEmptySingleRowEmptyCell() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("",
                c.stringFromRead(b("\n   <table><tr><td></td></tr></table>\n"), 12L));
    }

    @Test
    void testTableEmptySingleRowWsCell() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("",
                    c.stringFromRead(b("\n   <table><tr><td> </td></tr></table>\n"), 12L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("",
                    c.stringFromRead(b("\n   <table><tr><td>\n</td></tr></table>\n"), 12L));
        }
    }

    @Test
    void testTableNoId() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─────────\nhi, world\n─────────\n",
                c.stringFromRead(b("<html><body><table>\n        <tr>\n            <td>hi, world</td>\n        </tr>\n    </table></body></html>"), 10L));
    }

    @Test
    void testTableRawMode() throws Exception {
        var c = Config.withDecorator(new TrivialDecorator()).rawMode(true);
        assertEquals("Aliquam erat volutpat. Nunc eleifend leo vitae magna. In id erat non orci\ncommodo lobortis.\nAliquam erat volutpat.\nLorem ipsum dolor sit amet, consectetuer adipiscing elit. Donec hendrerit tempor\ntellus.\n",
                c.stringFromRead(b("<table><tr>\n    <td><ol><li></li></ol></td>\n    <td><ol><li>\n        Aliquam erat volutpat.  Nunc eleifend leo vitae magna.  In id erat non orci commodo lobortis.\n    </li>\n    <li>\n        Aliquam erat volutpat.\n    </li>\n    <li></li>\n    </ol></td>\n    <td><ol><li>\n        Lorem ipsum dolor sit amet, consectetuer adipiscing elit.  Donec hendrerit tempor tellus.\n    </li></ol></td>\n</tr>\n</table>"), 80L));
    }

    @Test
    void testTableRowId() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─────────\nhi, world\n─────────\n",
                c.stringFromRead(b("<html><body><table>\n        <tr id=\"bodyrow\">\n            <td>hi, world</td>\n        </tr>\n    </table></body></html>"), 10L));
    }

    @Test
    void testTableTableId() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─────────\nhi, world\n─────────\n",
                c.stringFromRead(b("<html><body><table id=\"bodytable\">\n        <tr>\n            <td>hi, world</td>\n        </tr>\n    </table></body></html>"), 10L));
    }

    @Test
    void testTableTbodyId() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─────────\nhi, world\n─────────\n",
                c.stringFromRead(b("<html><body><table>\n      <tbody id=\"tb\">\n        <tr>\n            <td>hi, world</td>\n        </tr>\n      </tbody>\n    </table></body></html>"), 10L));
    }

    @Test
    void testTableTooNarrow() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("───┬───────────────────────────────────────────────────┬────────────────────────\n   │1. Aliquam erat volutpat. Lorem ipsum dolor sit    │1. Lorem ipsum dolor sit\n   │   amet,                                           │                        \n───┴───────────────────────────────────────────────────┴────────────────────────\n",
                c.stringFromRead(b("<table><tr>\n    <td><ol><li></li></ol></td>\n    <td>\n        <ol><li>Aliquam erat volutpat. Lorem ipsum dolor sit amet,</li></ol>\n    </td>\n    <td>\n        <ol><li>Lorem ipsum dolor sit</li></ol>\n    </td>\n</tr></table>"), 80L));
    }

    @Test
    void testTableVerticalRows() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("─────\nwid\n/////\nkin\n/////\nder\n─────\n",
                c.stringFromRead(b("\n<table>\n    <tr>\n        <td>wid</td>\n        <td>kin</td>\n        <td>der</td>\n    </tr>\n</table>\n"), 5L));
    }

    @Test
    void testTableWithoutBorders() throws Exception {
        var c = Config.withDecorator(new TrivialDecorator()).noTableBorders();
        assertEquals("Aliquam erat volutpat. Nunc eleifend leo     Lorem ipsum dolor sit amet,       \nvitae magna. In id erat non orci commodo     consectetuer adipiscing elit.     \nlobortis.                                    Donec hendrerit tempor tellus.    \nAliquam erat volutpat.                                                         \n",
                c.stringFromRead(b("<table><tr>\n    <td><ol><li></li></ol></td>\n    <td><ol><li>\n        Aliquam erat volutpat.  Nunc eleifend leo vitae magna.  In id erat non orci commodo lobortis.\n    </li>\n    <li>\n        Aliquam erat volutpat.\n    </li>\n    <li></li>\n    </ol></td>\n    <td><ol><li>\n        Lorem ipsum dolor sit amet, consectetuer adipiscing elit.  Donec hendrerit tempor tellus.\n    </li></ol></td>\n</tr>\n</table>"), 80L));
    }

    @Test
    void testThead() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("────┬────┬────\nCol1│Col2│Col3\n────┼────┼────\n1   │2   │3   \n────┴────┴────\n",
                c.stringFromRead(b("\n   <table>\n     <thead>\n       <tr>\n         <th>Col1</th>\n         <th>Col2</th>\n         <th>Col3</th>\n       </tr>\n     </thead>\n     <tbody>\n       <tr>\n         <td>1</td>\n         <td>2</td>\n         <td>3</td>\n       </tr>\n     </tbody>\n   </table>\n"), 15L));
    }

    @Test
    void testTrivialDecorator() throws Exception {
        var c = Config.withDecorator(new TrivialDecorator());
        assertEquals("Here's a link.\nBullet\nBullet\nBullet\n",
                c.stringFromRead(b("<div>\n     <div>Here's a <a href=\"https://example.com/\">link</a>.</div>\n     <div><ul>\n     <li>Bullet</li>\n     <li>Bullet</li>\n     <li>Bullet</li>\n     </ul></div>\n     </div>"), 80L));
    }

    @Test
    void testUl() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("* Item one\n* Item two\n* Item\n  three\n",
                c.stringFromRead(b("\n        <ul>\n          <li>Item one</li>\n          <li>Item two</li>\n          <li>Item three</li>\n        </ul>\n     "), 10L));
    }

    @Test
    void testUlOverflow() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("* * Foo\n",
                    c.stringFromRead(b("<ul><li><ul><li>Foo</li></ul></li></ul>"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertEquals("* * F\n    o\n    o\n",
                    c.stringFromRead(b("<ul><li><ul><li>Foo</li></ul></li></ul>"), 5L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<ul><li><ul><li>Foo</li></ul></li></ul>"), 3L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L);
            assertFails(Html2TextException.Kind.TOO_NARROW, () -> c.stringFromRead(b("<ul><li><ul><li>Foo</li></ul></li></ul>"), 4L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(1L).allowWidthOverflow();
            assertEquals("* * F\n    o\n    o\n",
                    c.stringFromRead(b("<ul><li><ul><li>Foo</li></ul></li></ul>"), 3L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).minWrapWidth(3L).allowWidthOverflow();
            assertEquals("* * Foo\n",
                    c.stringFromRead(b("<ul><li><ul><li>Foo</li></ul></li></ul>"), 4L));
        }
    }

    @Test
    void testUlTinyTable() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("* ─\n  x\n  ─\n",
                c.stringFromRead(b("<ul><li><table><tr><td>x</td></tr></table></li></ul>\n"), 12L));
    }

    @Test
    void testUnicode() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("────┬────┬───\nနတမစ│နတမစ│aaa\n────┴────┴───\n",
                c.stringFromRead(b("<table>\n      <td>နတမစ</td>\n      <td>နတမစ</td>\n      <td>aaa</td>\n</table>"), 15L));
    }

    @Test
    void testUnknownElement() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("──────────────\nOne Two Three \n──────────────\n",
                c.stringFromRead(b("\n       <foo>\n       <table>\n         <tr>\n            <td>\n               One\n               <span><yyy>\n                   Two\n               </yyy></span>\n               Three\n            </td>\n          </tr>\n        </table>\n        </foo>\n     "), 20L));
    }

    @Test
    void testWrap() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello,\nworld.\nSuperlon\ngwordrea\nlly\n",
                c.stringFromRead(b("<p>Hello, world.  Superlongwordreally</p>"), 8L));
    }

    @Test
    void testWrap2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Hello, world. This\nis a long sentence\nwith a few words,\nwhich we want to be\nwrapped correctly.\n",
                c.stringFromRead(b("<p>Hello, world.  This is a long sentence with a\nfew words, which we want to be wrapped correctly.</p>"), 20L));
    }

    @Test
    void testWrap3() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("[http://example.org/blah/\n][1] one two three\n\n[1]: dest\n",
                c.stringFromRead(b("<p><a href=\"dest\">http://example.org/blah/</a> one two three"), 25L));
    }

    @Test
    void testWrap4() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("──────────\nHello,    \nthis      \nshould be \nwrapped.  \n──────────\n",
                c.stringFromRead(b("<table><tr><td colspan=\"2\"><p>Hello, this should be wrapped.</p></table>"), 10L));
    }

    @Test
    void testWrapMax() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).maxWrapWidth(10L);
        assertEquals("This is a\nbit of\ntext to\nwrap\n\n* This is a\n  bit of\n  text to\n  wrap too\n",
                c.stringFromRead(b("\n        <p>This is a bit of text to wrap<p>\n        <ul>\n          <li>This is a bit of text to wrap too</li>\n          </li>\n        </ul>"), 20L));
    }

    @Test
    void testWrapMax2() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true).maxWrapWidth(17L);
        assertEquals("plain para at the\nfull screen width\n* bullet point uses\n  same width so its\n  margin is 2 chars\n  further right\n  * nested bullets in\n    turn move 2 chars\n    right each time\n    * result: you never\n      get text squashed\n      too narrow\n",
                c.stringFromRead(b("\n        <p>plain para at the full screen width</p>\n        <ul>\n          <li>bullet point uses same width so its margin is 2 chars further right\n\n          <ul><li>nested bullets in turn move 2 chars right each time\n             <ul><li>result: you never get text squashed too narrow</li></ul>\n          </li></ul>\n        </li></ul>"), 80L));
    }

    @Test
    void testWrapNbspEnt() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Don't break a line between the words\nFoo\u00a0and\u00a0Bar\n",
                c.stringFromRead(b("<p>Don't break a line between the words Foo&nbsp;and&nbsp;Bar</p>"), 40L));
    }

    @Test
    void testWrapNbspUnicode() throws Exception {
        var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
        assertEquals("Don't break a line between the words\nFoo\u00a0and\u00a0Bar\n",
                c.stringFromRead(b("<p>Don't break a line between the words Foo\u00a0and\u00a0Bar</p>"), 40L));
    }

    @Test
    void testWrapWordBoundaries() throws Exception {
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello there boo\n",
                    c.stringFromRead(b("Hello there boo"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello there boo\n",
                    c.stringFromRead(b("Hello there boo"), 15L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello there\nboo\n",
                    c.stringFromRead(b("Hello there boo"), 14L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello there\nboo\n",
                    c.stringFromRead(b("Hello there boo"), 13L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello there\nboo\n",
                    c.stringFromRead(b("Hello there boo"), 12L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello there\nboo\n",
                    c.stringFromRead(b("Hello there boo"), 11L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\nthere boo\n",
                    c.stringFromRead(b("Hello there boo"), 10L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\nthere\nboo\n",
                    c.stringFromRead(b("Hello there boo"), 6L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\nthere\nboo\n",
                    c.stringFromRead(b("Hello there boo"), 5L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hell\no\nther\ne\nboo\n",
                    c.stringFromRead(b("Hello there boo"), 4L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("H\ne\nl\nl\no\nt\nh\ne\nr\ne\nb\no\no\n",
                    c.stringFromRead(b("Hello there boo"), 1L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello *there* boo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 20L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello *there*\nboo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 15L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello *there*\nboo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 14L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello *there*\nboo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 13L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n*there* boo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 12L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n*there* boo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 11L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n*there*\nboo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 10L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n*there\n* boo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 6L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hello\n*ther\ne*\nboo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 5L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("Hell\no\n*the\nre*\nboo\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 4L));
        }
        {
            var c = Config.plainNoDecorate().doDecorate().linkFootnotes(true);
            assertEquals("H\ne\nl\nl\no\n*\nt\nh\ne\nr\ne\n*\nb\no\no\n",
                    c.stringFromRead(b("Hello <em>there</em> boo"), 1L));
        }
    }
}
