package io.github.jugglerchris.html2text.html5;

import io.github.jugglerchris.html2text.dom.Attribute;
import io.github.jugglerchris.html2text.dom.Node;
import io.github.jugglerchris.html2text.dom.QualName;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;

/**
 * The canonical DOM dump of the differential tests, identical to the Rust oracle's (tools/domdump/src/main.rs).
 * {@link #main} reads documents from standard input, one per line ("x" and the hex of the bytes), and writes the
 * dump of each followed by "#end".
 */
public final class DomDump {
    private record Item(Node node, int depth, boolean contents) {}

    private DomDump() {}

    static String esc(String s) {
        StringBuilder b = new StringBuilder("\"");
        s.codePoints().forEach(c -> {
            if (c == '\\' || c == '"') {
                b.append('\\').append((char) c);
            } else if (c >= ' ' && c <= '~') {
                b.append((char) c);
            } else {
                b.append("\\u{").append(Integer.toHexString(c)).append('}');
            }
        });
        return b.append('"').toString();
    }

    private static String qual(QualName q) {
        return (q.prefix() == null ? "-" : esc(q.prefix())) + " " + esc(q.ns()) + " " + esc(q.local());
    }

    /** The dump of a document (without the "#end" line). */
    public static String dump(Node doc) {
        StringBuilder out = new StringBuilder();
        ArrayDeque<Item> stack = new ArrayDeque<>();
        stack.push(new Item(doc, 0, false));
        while (!stack.isEmpty()) {
            Item item = stack.pop();
            Node n = item.node();
            int d = item.depth();
            out.append(d).append(' ');
            if (item.contents()) {
                out.append("#contents\n");
            } else {
                switch (n.kind()) {
                    case DOCUMENT -> out.append("#document\n");
                    case DOCTYPE -> out.append("<!DOCTYPE ").append(esc(n.doctypeName())).append(' ')
                        .append(esc(n.publicId())).append(' ').append(esc(n.systemId())).append(">\n");
                    case TEXT -> out.append("T ").append(esc(n.text())).append('\n');
                    case COMMENT -> out.append("C ").append(esc(n.text())).append('\n');
                    case PROCESSING_INSTRUCTION -> out.append("P ").append(esc(n.doctypeName())).append(' ')
                        .append(esc(n.text())).append('\n');
                    case ELEMENT -> {
                        out.append("E ").append(qual(n.name()));
                        out.append(n.isMathmlAnnotationXmlIntegrationPoint() ? " aip\n" : "\n");
                        for (Attribute a : n.attrs()) {
                            out.append(d + 1).append(" @ ").append(qual(a.name())).append('=')
                                .append(esc(a.value())).append('\n');
                        }
                    }
                }
            }
            List<Node> children = n.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(new Item(children.get(i), d + 1, false));
            }
            if (!item.contents() && n.templateContents() != null) {
                stack.push(new Item(n.templateContents(), d + 1, true));
            }
        }
        return out.toString();
    }

    /** Dumps every document read from standard input. */
    public static void main(String[] args) throws IOException {
        String in = new String(System.in.readAllBytes(), StandardCharsets.ISO_8859_1);
        try (Writer out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            for (String line : in.split("\n")) {
                line = line.strip();
                if (line.startsWith("x")) {
                    out.write(dump(HtmlParser.parseDocument(HexFormat.of().parseHex(line.substring(1)))));
                    out.write("#end\n");
                }
            }
        }
    }
}
