// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)
//
// The html2text command: html2text's operations behind a JSON-lines protocol. It reads JSON requests, one per line,
// on standard input and writes one JSON response line per request (see README.md for the protocol).

package io.github.jugglerchris.html2text.cli;

import io.github.jugglerchris.html2text.Colour;
import io.github.jugglerchris.html2text.Config;
import io.github.jugglerchris.html2text.Html2Text;
import io.github.jugglerchris.html2text.Html2TextException;
import io.github.jugglerchris.html2text.ImageRenderMode;
import io.github.jugglerchris.html2text.RenderTree;
import io.github.jugglerchris.html2text.RustPanic;
import io.github.jugglerchris.html2text.json.Json;
import io.github.jugglerchris.html2text.json.JsonException;
import io.github.jugglerchris.html2text.json.JsonNumber;
import io.github.jugglerchris.html2text.render.PlainDecorator;
import io.github.jugglerchris.html2text.render.RichAnnotation;
import io.github.jugglerchris.html2text.render.RichDecorator;
import io.github.jugglerchris.html2text.render.TaggedLine;
import io.github.jugglerchris.html2text.render.TaggedLineElement;
import io.github.jugglerchris.html2text.render.TextDecorator;
import io.github.jugglerchris.html2text.render.TrivialDecorator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** The command-line entry point. */
public final class Main {
    static final String USAGE = """
            usage: html2text < requests.jsonl

            Reads one JSON request per line on standard input and writes one JSON response per line.
            Requests: {"op":"from_read",...}, {"op":"from_read_with_decorator",...}, {"op":"from_read_rich",...},
                      {"op":"from_read_coloured",...}, {"op":"config",...}, {"op":"parse",...},
                      {"op":"parsed_style",...}, {"op":"cli",...}
            """;

    private static final String CONFIG_NAMES = "config must be \"plain\", \"plain_no_decorate\", \"rich\" or \"trivial\"";
    private static final String STEP_SHAPE = "a step must be an array starting with a method name";

    /** A request that does not follow the protocol. */
    static final class RequestError extends Exception {
        private static final long serialVersionUID = 1L;

        RequestError(String message) {
            super(message);
        }
    }

    private Main() {}

    /** Runs the command; any argument is a usage error. */
    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0) {
            System.err.print(USAGE);
            System.err.flush();
            System.exit(2);
        }
        int[] status = {1};
        Thread worker = new Thread(null, () -> status[0] = run(), "html2text", 1L << 30);
        worker.start();
        worker.join();
        System.exit(status[0]);
    }

    private static int run() {
        byte[] raw;
        try {
            raw = System.in.readAllBytes();
        } catch (IOException e) {
            return 1;
        }
        Output output = process(raw);
        try {
            System.out.write(output.text().getBytes(StandardCharsets.UTF_8));
            System.out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return output.ok() && !System.out.checkError() ? 0 : 1;
    }

    /**
     * What the command writes for some input.
     *
     * @param text the response lines
     * @param ok whether every request succeeded
     */
    record Output(String text, boolean ok) {}

    /** Answers every request of the input (one per non-empty line). */
    static Output process(byte[] raw) {
        boolean ok = true;
        StringBuilder out = new StringBuilder();
        for (String line : Utf8.decodeLossy(raw).split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            Object response;
            try {
                response = handle(line);
            } catch (JsonException e) {
                response = error("JsonError", e.getMessage());
            } catch (RequestError e) {
                response = error("RequestError", e.getMessage());
            } catch (Html2TextException e) {
                response = error(e.kind().variant(), e.getMessage());
            } catch (RustPanic e) {
                response = error("Panic", e.getMessage());
            }
            ok &= !(response instanceof Map<?, ?> m && m.containsKey("error"));
            out.append(Json.write(response)).append('\n');
        }
        return new Output(out.toString(), ok);
    }

    private static Map<String, Object> error(String name, String message) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("name", name);
        inner.put("message", message);
        return Map.of("error", inner);
    }

    private static Map<String, Object> object(String key, Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(key, value);
        return out;
    }

    private static Object handle(String line) throws RequestError, Html2TextException {
        if (!(Json.parse(line) instanceof Map<?, ?> parsed)) {
            throw new RequestError("a request must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> req = (Map<String, Object>) parsed;
        Object op = req.getOrDefault("op", Json.NULL);
        switch (op instanceof String s ? s : "") {
            case "from_read" -> {
                byte[] h = html(req);
                return object("text", Html2Text.fromRead(h, width(req)));
            }
            case "from_read_with_decorator" -> {
                TextDecorator<?> decorator = switch (req.get("decorator") instanceof String d ? d : "") {
                    case "plain" -> new PlainDecorator();
                    case "trivial" -> new TrivialDecorator();
                    case "rich" -> new RichDecorator();
                    default -> throw new RequestError("decorator must be \"plain\", \"trivial\" or \"rich\"");
                };
                byte[] h = html(req);
                return object("text", Html2Text.fromReadWithDecorator(h, width(req), decorator));
            }
            case "from_read_rich" -> {
                byte[] h = html(req);
                return object("lines", linesJson(Html2Text.fromReadRich(h, width(req))));
            }
            case "from_read_coloured" -> {
                String map = colourMap(req);
                byte[] h = html(req);
                long w = width(req);
                return object("text", Html2Text.fromReadColoured(h, w, (anns, s) -> applyColourMap(map, anns, s)));
            }
            case "config", "parse" -> {
                return configOp(req, op.equals("parse"));
            }
            case "parsed_style" -> {
                byte[] h = html(req);
                return object("text", Html2Text.domToParsedStyle(Config.plain().parseHtml(h)));
            }
            case "cli" -> {
                return cliOp(req);
            }
            default -> throw new RequestError("unknown op: " + Json.write(op));
        }
    }

    /** The bytes of a document member: NAME_bytes when present, else the UTF-8 of the string NAME. */
    private static byte[] inputBytes(Map<String, Object> req, String name, boolean optional) throws RequestError {
        String bytesName = name + "_bytes";
        if (req.containsKey(bytesName)) {
            String message = bytesName + " must be an array of integers from 0 to 255";
            if (!(req.get(bytesName) instanceof List<?> items)) {
                throw new RequestError(message);
            }
            byte[] out = new byte[items.size()];
            for (int i = 0; i < out.length; i++) {
                OptionalLong b = items.get(i) instanceof JsonNumber n ? n.asU64() : OptionalLong.empty();
                if (b.isEmpty() || Long.compareUnsigned(b.getAsLong(), 255) > 0) {
                    throw new RequestError(message);
                }
                out[i] = (byte) b.getAsLong();
            }
            return out;
        }
        Object value = req.get(name);
        if (value instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
        if (value == null && optional) {
            return new byte[0];
        }
        throw new RequestError(name + " must be a string");
    }

    private static byte[] html(Map<String, Object> req) throws RequestError {
        return inputBytes(req, "html", false);
    }

    private static long uint(Object value, String what) throws RequestError {
        OptionalLong n = value instanceof JsonNumber num ? num.asU64() : OptionalLong.empty();
        if (n.isEmpty()) {
            throw new RequestError(what + " must be a non-negative integer");
        }
        return n.getAsLong();
    }

    private static long width(Map<String, Object> req) throws RequestError {
        return uint(req.get("width"), "width");
    }

    private static String colourMap(Map<String, Object> req) throws RequestError {
        Object map = req.get("colour_map");
        if (!"test".equals(map) && !"terminal".equals(map)) {
            throw new RequestError("colour_map must be \"test\" or \"terminal\"");
        }
        return (String) map;
    }

    private static String applyColourMap(String map, List<RichAnnotation> annotations, String s) {
        return map.equals("test") ? testColourMap(annotations, s)
                : CliProgram.defaultColourMap(annotations, s, true, false);
    }

    /** The colour map of the library's own tests. */
    private static String testColourMap(List<RichAnnotation> annotations, String s) {
        String[] tags = {"", ""};
        String[] bgtags = {"", ""};
        for (RichAnnotation ann : annotations) {
            if (ann instanceof RichAnnotation.Fg fg) {
                Colour c = fg.colour();
                String t = c.equals(new Colour(0xff, 0, 0)) ? "R" : c.equals(new Colour(0xff, 0xff, 0xff)) ? "W"
                        : c.equals(new Colour(0, 0xff, 0)) ? "G" : c.equals(new Colour(0, 0, 0xff)) ? "B" : "?";
                tags = new String[] {"<" + t + ">", "</" + t + ">"};
            } else if (ann instanceof RichAnnotation.Bg bg) {
                Colour c = bg.colour();
                String t = c.equals(new Colour(0xff, 0, 0)) ? "r" : c.equals(new Colour(0, 0xff, 0)) ? "g" : ".";
                bgtags = new String[] {"<" + t + ">", "</" + t + ">"};
            }
        }
        return bgtags[0] + tags[0] + s + tags[1] + bgtags[1];
    }

    private static Object annotationJson(Object ann) {
        return switch (ann) {
            case RichAnnotation.Default d -> "Default";
            case RichAnnotation.Link l -> object("Link", l.url());
            case RichAnnotation.Image i -> object("Image", i.src());
            case RichAnnotation.Emphasis e -> "Emphasis";
            case RichAnnotation.Strong s -> "Strong";
            case RichAnnotation.Strikeout s -> "Strikeout";
            case RichAnnotation.Code c -> "Code";
            case RichAnnotation.Preformat p -> object("Preformat", p.continuation());
            case RichAnnotation.Fg c -> object("Colour", c.colour().toString());
            case RichAnnotation.Bg c -> object("BgColour", c.colour().toString());
            default -> Json.NULL;
        };
    }

    private static <A> List<Object> linesJson(List<TaggedLine<List<A>>> lines) {
        List<Object> out = new ArrayList<>();
        for (TaggedLine<List<A>> line : lines) {
            List<Object> elements = new ArrayList<>();
            for (TaggedLineElement<List<A>> element : line) {
                if (element instanceof TaggedLineElement.Str<List<A>> str) {
                    Map<String, Object> e = object("text", str.ts().s());
                    List<Object> tag = new ArrayList<>();
                    for (A ann : str.ts().tag()) {
                        tag.add(annotationJson(ann));
                    }
                    e.put("tag", tag);
                    elements.add(e);
                } else {
                    elements.add(object("fragment", ((TaggedLineElement.FragmentStart<List<A>>) element).name()));
                }
            }
            out.add(elements);
        }
        return out;
    }

    /** Applies one builder step (["method", argument]) to a configuration. */
    private static <A> Config<A> applyStep(Config<A> conf, Object step) throws RequestError, Html2TextException {
        if (!(step instanceof List<?> items) || items.isEmpty() || !(items.get(0) instanceof String name)) {
            throw new RequestError(STEP_SHAPE);
        }
        Object arg = items.size() > 1 ? items.get(1) : null;
        return switch (name) {
            case "add_css" -> conf.addCss(stringArg(arg, name));
            case "add_agent_css" -> conf.addAgentCss(stringArg(arg, name));
            case "use_doc_css" -> conf.useDocCss();
            case "pad_block_width" -> conf.padBlockWidth();
            case "allow_width_overflow" -> conf.allowWidthOverflow();
            case "no_table_borders" -> conf.noTableBorders();
            case "fence_tables" -> conf.fenceTables();
            case "no_link_wrapping" -> conf.noLinkWrapping();
            case "do_decorate" -> conf.doDecorate();
            case "max_wrap_width" -> conf.maxWrapWidth(uint(arg, name));
            case "min_wrap_width" -> conf.minWrapWidth(uint(arg, name));
            case "raw_mode" -> conf.rawMode(boolArg(arg, name));
            case "unicode_strikeout" -> conf.unicodeStrikeout(boolArg(arg, name));
            case "link_footnotes" -> conf.linkFootnotes(boolArg(arg, name));
            case "empty_img_mode" -> conf.emptyImgMode(imageMode(arg));
            default -> throw new RequestError("unknown step: " + Json.write(name));
        };
    }

    private static String stringArg(Object arg, String what) throws RequestError {
        if (arg instanceof String s) {
            return s;
        }
        throw new RequestError(what + " takes a string");
    }

    private static boolean boolArg(Object arg, String what) throws RequestError {
        if (arg instanceof Boolean b) {
            return b;
        }
        throw new RequestError(what + " takes a boolean");
    }

    private static ImageRenderMode imageMode(Object arg) throws RequestError {
        if ("IgnoreEmpty".equals(arg)) {
            return new ImageRenderMode.IgnoreEmpty();
        } else if ("ShowAlways".equals(arg)) {
            return new ImageRenderMode.ShowAlways();
        } else if ("Filename".equals(arg)) {
            return new ImageRenderMode.Filename();
        } else if (arg instanceof Map<?, ?> m && m.size() == 1 && m.get("Replace") instanceof String text) {
            return new ImageRenderMode.Replace(text);
        }
        throw new RequestError(
                "empty_img_mode takes \"IgnoreEmpty\", \"ShowAlways\", \"Filename\" or {\"Replace\":S}");
    }

    private static <A> Config<A> buildConfig(Config<A> conf, Map<String, Object> req)
            throws RequestError, Html2TextException {
        if (req.containsKey("steps")) {
            if (!(req.get("steps") instanceof List<?> steps)) {
                throw new RequestError("steps must be an array");
            }
            for (Object step : steps) {
                conf = applyStep(conf, step);
            }
        }
        return conf;
    }

    private static Object configOp(Map<String, Object> req, boolean parseOp) throws RequestError,
            Html2TextException {
        return switch (req.get("config") instanceof String c ? c : "") {
            case "plain" -> finish(buildConfig(Config.plain(), req), req, parseOp, false);
            case "plain_no_decorate" -> finish(buildConfig(Config.plainNoDecorate(), req), req, parseOp, false);
            case "rich" -> finish(buildConfig(Config.rich(), req), req, parseOp, true);
            case "trivial" -> finish(buildConfig(Config.withDecorator(new TrivialDecorator()), req), req, parseOp,
                    false);
            default -> throw new RequestError(CONFIG_NAMES);
        };
    }

    /** Runs the call (config op) or the rendering (parse op) of a built configuration. */
    private static <A> Object finish(Config<A> conf, Map<String, Object> req, boolean parseOp, boolean rich)
            throws RequestError, Html2TextException {
        Object selector = req.get(parseOp ? "output" : "call");
        String call = selector instanceof String s ? s : "";
        if (parseOp) {
            call = switch (call) {
                case "string" -> "render_to_string";
                case "lines" -> "render_to_lines";
                case "coloured" -> "render_coloured";
                default -> throw new RequestError("output must be \"string\", \"lines\" or \"coloured\"");
            };
        } else if (!List.of("string_from_read", "lines_from_read", "coloured", "render_to_string", "render_to_lines",
                "render_coloured").contains(call)) {
            throw new RequestError("call must be \"string_from_read\", \"lines_from_read\", \"coloured\", "
                    + "\"render_to_string\", \"render_to_lines\" or \"render_coloured\"");
        }
        boolean coloured = call.equals("coloured") || call.equals("render_coloured");
        if (coloured && !rich) {
            throw new RequestError("coloured output needs the \"rich\" config");
        }
        String map = coloured ? colourMap(req) : null;
        byte[] h = html(req);
        long w = width(req);
        RenderTree tree = parseOp ? Html2Text.parse(h) : null;
        if (call.startsWith("render_") && tree == null) {
            tree = conf.domToRenderTree(conf.parseHtml(h));
        }
        return switch (call) {
            case "string_from_read" -> object("text", conf.stringFromRead(h, w));
            case "lines_from_read" -> object("lines", linesJson(conf.linesFromRead(h, w)));
            case "render_to_string" -> object("text", conf.renderToString(tree, w));
            case "render_to_lines" -> object("lines", linesJson(conf.renderToLines(tree, w)));
            default -> {
                @SuppressWarnings("unchecked")
                Config<RichAnnotation> richConf = (Config<RichAnnotation>) conf;
                Config.ColourMap<RichAnnotation> f = (anns, s) -> applyColourMap(map, anns, s);
                yield object("text", call.equals("coloured") ? richConf.coloured(h, w, f)
                        : richConf.renderColoured(tree, w, f));
            }
        };
    }

    private static Object cliOp(Map<String, Object> req) throws RequestError {
        String message = "args must be an array of strings";
        if (!(req.get("args") instanceof List<?> items)) {
            throw new RequestError(message);
        }
        List<String> args = new ArrayList<>();
        args.add("html2text");
        for (Object item : items) {
            if (!(item instanceof String s)) {
                throw new RequestError(message);
            }
            args.add(s);
        }
        byte[] input = inputBytes(req, "stdin", true);
        CliProgram.Outcome outcome;
        try {
            outcome = CliProgram.run(args, input);
        } catch (CliProgram.FilesNotAvailable e) {
            throw new RequestError("the cli op reads and writes no files");
        }
        Map<String, Object> out = object("status", outcome.status());
        out.put("stdout", outcome.stdout());
        out.put("stderr", outcome.stderr());
        return out;
    }
}
