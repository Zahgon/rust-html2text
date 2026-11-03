// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The JSON-lines protocol of the html2text command, with answers taken from the reference command. */
class MainTest {
    private static void check(String request, String expected) {
        Main.Output out = Main.process((request + "\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(expected + "\n", out.text(), "response to " + request);
        assertEquals(!expected.startsWith("{\"error\""), out.ok(), "success of " + request);
    }

    @Test
    void conversions() {
        check("{\"op\":\"from_read\",\"html\":\"<ul><li>Item one</li><li>Item two</li></ul>\",\"width\":20}",
                "{\"text\":\"* Item one\\n* Item two\\n\"}");
        check("{\"op\":\"from_read_rich\",\"html\":\"<p>a <b>bold</b> <a href=\\\"u\\\">link</a></p>\",\"width\":20}",
                "{\"lines\":[[{\"text\":\"a \",\"tag\":[]},{\"text\":\"bold\",\"tag\":[\"Strong\"]},"
                        + "{\"text\":\" \",\"tag\":[]},{\"text\":\"link\",\"tag\":[{\"Link\":\"u\"}]}]]}");
        check("{\"op\":\"config\",\"config\":\"plain\",\"steps\":[[\"max_wrap_width\",5],[\"no_link_wrapping\"]],"
                + "\"call\":\"string_from_read\",\"html\":\"<p>abc def ghi <a href=\\\"http://example.com/long\\\">x</a>"
                + "</p>\",\"width\":10}",
                "{\"text\":\"abc\\ndef\\nghi\\n[x][1\\n]\\n\\n[1]: http://example.com/long\\n\"}");
        check("{\"op\":\"parse\",\"config\":\"rich\",\"output\":\"coloured\",\"colour_map\":\"terminal\","
                + "\"html\":\"<em>e</em><code>c</code>\",\"width\":10}",
                "{\"text\":\"\\u001b[1me\\u001b[m\\u001b[38;5;4mc\\u001b[39m\\n\"}");
        check("{\"op\":\"parsed_style\",\"html\":\"<style>p > em::after { content: \\\"!\\\" } "
                + ".x { display: none !important }</style>\"}",
                "{\"text\":\"Author rules:\\n  p > em::after {\\n    content: \\\"!\\\"\\n  }\\n  .x {\\n"
                        + "    display: none !important\\n  }\\n\"}");
    }

    @Test
    void errors() {
        check("{\"op\":\"from_read\",\"html\":\"x\",\"width\":0}",
                "{\"error\":{\"name\":\"TooNarrow\",\"message\":\"Output width not wide enough.\"}}");
        check("{\"op\":\"config\",\"config\":\"plain\",\"steps\":[[\"add_css\",\"a[x=\"]],"
                + "\"call\":\"string_from_read\",\"html\":\"x\",\"width\":10}",
                "{\"error\":{\"name\":\"Panic\",\"message\":\"called `Option::unwrap()` on a `None` value\"}}");
        check("{\"op\":\"nope\"}", "{\"error\":{\"name\":\"RequestError\",\"message\":\"unknown op: \\\"nope\\\"\"}}");
        check("{\"op\":\"from_read\",\"html\":\"x\",\"width\":-0}",
                "{\"error\":{\"name\":\"RequestError\",\"message\":\"width must be a non-negative integer\"}}");
        check("{\"op\":\"from_read\",\"html\":\"é\" x}",
                "{\"error\":{\"name\":\"JsonError\",\"message\":\"expected `,` or `}` at line 1 column 31\"}}");
    }

    @Test
    void cliProgram() {
        check("{\"op\":\"cli\",\"args\":[\"-w\",\"x\"],\"stdin\":\"\"}",
                "{\"status\":2,\"stdout\":\"\",\"stderr\":\"Usage:\\n  html2text [OPTIONS] [INFILE]\\n"
                        + "html2text: Bad value x\\n\"}");
        check("{\"op\":\"cli\",\"args\":[\"--link-footnotes\",\"--no-link-footnotes\"],\"stdin\":\"x\"}",
                "{\"status\":1,\"stdout\":\"\",\"stderr\":\"Error: can't specify both --link-footnotes and "
                        + "--no-link-footnotes\\n\"}");
    }

    @Test
    void emptyLinesAndStatus() {
        Main.Output out = Main.process("\n\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("", out.text());
        assertTrue(out.ok());
        assertFalse(Main.process("x\n".getBytes(StandardCharsets.UTF_8)).ok());
    }
}
