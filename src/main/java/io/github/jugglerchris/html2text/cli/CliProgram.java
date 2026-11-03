// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)
//
// Java port of the html2text-cli program (html2text-cli/src/main.rs, default features), run in-process with its
// standard streams and exit status captured.

package io.github.jugglerchris.html2text.cli;

import io.github.jugglerchris.html2text.Config;
import io.github.jugglerchris.html2text.Html2Text;
import io.github.jugglerchris.html2text.Html2TextException;
import io.github.jugglerchris.html2text.RustPanic;
import io.github.jugglerchris.html2text.render.RichAnnotation;
import io.github.jugglerchris.html2text.render.TrivialDecorator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The html2text command-line program. */
final class CliProgram {
    private static final String RESET = "\u001b[m";
    private static final String FG_RESET = "\u001b[39m";
    private static final String BG_RESET = "\u001b[49m";
    private static final String FG_BLUE = "\u001b[38;5;4m";

    /**
     * What a run of the program produced.
     *
     * @param status the exit status
     * @param stdout the standard output
     * @param stderr the standard error
     */
    record Outcome(int status, String stdout, String stderr) {}

    /** The program asked for an input or output file, which this in-process run does not provide. */
    static final class FilesNotAvailable extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private CliProgram() {}

    private static boolean isUsize(String s) {
        String digits = s.startsWith("+") ? s.substring(1) : s;
        if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        try {
            Long.parseUnsignedLong(digits);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static long usize(String s) {
        return Long.parseUnsignedLong(s.startsWith("+") ? s.substring(1) : s);
    }

    private static ArgumentParser parser() {
        ArgumentParser ap = new ArgumentParser();
        ap.addArgument("infile", "Input HTML file (default is standard input)");
        ap.addOption(List.of("-w", "--width"), ArgumentParser.Action.VALUE, "Column width to format to (default is 80)",
                CliProgram::isUsize);
        ap.addOption(List.of("-W", "--wrap-width"), ArgumentParser.Action.VALUE,
                "Maximum text wrap width (default same as width)", CliProgram::isUsize);
        ap.addOption(List.of("-o", "--output"), ArgumentParser.Action.VALUE, "Output file (default is standard output)",
                s -> true);
        ap.addOption(List.of("-L", "--literal"), ArgumentParser.Action.FLAG, "Output only literal text (no decorations)",
                s -> true);
        String[][] flags = {
            {"--pad-width", "Pad blocks to their full width"},
            {"--link-footnotes", "Enable link footnotes"},
            {"--no-link-footnotes", "Enable link footnotes"},
            {"--colour", "Use ANSI terminal colours"},
            {"--css", "Enable CSS"},
            {"--ignore-css-colour",
                "With --css, ignore CSS colour information (still hides elements with e.g. display: none)"},
            {"--only-css", "Don't use default non-CSS colours"},
            {"--show-css", "Show the parsed CSS instead of rendered output"},
        };
        for (String[] flag : flags) {
            ap.addOption(List.of(flag[0]), ArgumentParser.Action.FLAG, flag[1], s -> true);
        }
        ap.addOption(List.of("--agent-css"), ArgumentParser.Action.VALUE, "Add some CSS rules (to the agent spreadsheet)",
                s -> true);
        return ap;
    }

    /** Runs the program with the arguments (program name first) and {@code input} as standard input. */
    static Outcome run(List<String> args, byte[] input) throws FilesNotAvailable {
        ArgumentParser.Result parsed = parser().parse(args);
        if (parsed.status() >= 0) {
            return new Outcome(parsed.status(), parsed.stdout(), parsed.stderr());
        }
        Map<String, String> v = parsed.values();
        if (!parsed.positionals().isEmpty() || v.containsKey("--output")) {
            throw new FilesNotAvailable();
        }
        long width = v.containsKey("--width") ? usize(v.get("--width")) : 80;
        try {
            String data;
            if (v.containsKey("--colour")) {
                Config<RichAnnotation> conf = update(Config.rich(), v);
                boolean useCssColours = !v.containsKey("--ignore-css-colour");
                boolean onlyCss = v.containsKey("--only-css");
                data = conf.coloured(input, width, (anns, s) -> defaultColourMap(anns, s, useCssColours, onlyCss));
            } else if (v.containsKey("--show-css")) {
                data = Html2Text.domToParsedStyle(update(Config.plain(), v).parseHtml(input));
            } else if (v.containsKey("--literal")) {
                data = update(Config.withDecorator(new TrivialDecorator()), v).stringFromRead(input, width);
            } else {
                data = update(Config.plain(), v).stringFromRead(input, width);
            }
            return new Outcome(0, data, "");
        } catch (FootnoteConflict e) {
            return new Outcome(1, "", "Error: can't specify both --link-footnotes and --no-link-footnotes\n");
        } catch (Html2TextException e) {
            throw RustPanic.unwrapErr(e.kind().variant());
        }
    }

    /** Both --link-footnotes and --no-link-footnotes were given: the program exits with status 1. */
    private static final class FootnoteConflict extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static <A> Config<A> update(Config<A> config, Map<String, String> v)
            throws Html2TextException, FootnoteConflict {
        if (v.containsKey("--wrap-width")) {
            config = config.maxWrapWidth(usize(v.get("--wrap-width")));
        }
        if (v.containsKey("--css")) {
            config = config.useDocCss();
        }
        String agentCss = v.getOrDefault("--agent-css", "");
        if (!agentCss.isEmpty()) {
            config = config.addAgentCss(agentCss);
        }
        if (v.containsKey("--link-footnotes") && v.containsKey("--no-link-footnotes")) {
            throw new FootnoteConflict();
        } else if (v.containsKey("--link-footnotes")) {
            config = config.linkFootnotes(true);
        } else if (v.containsKey("--no-link-footnotes")) {
            config = config.linkFootnotes(false);
        }
        if (v.containsKey("--pad-width")) {
            config = config.padBlockWidth();
        }
        return config;
    }

    /** The program's terminal colours for a span: ANSI escapes around the text (termion's sequences). */
    static String defaultColourMap(List<RichAnnotation> annotations, String s, boolean useCssColours,
            boolean noDefaultColours) {
        boolean haveExplicitColour = noDefaultColours;
        List<String> start = new ArrayList<>();
        List<String> finish = new ArrayList<>();
        for (RichAnnotation annotation : annotations) {
            String open = null;
            String close = null;
            switch (annotation) {
                case RichAnnotation.Link l -> {
                    open = "\u001b[4m";
                    close = RESET;
                }
                case RichAnnotation.Emphasis e -> {
                    open = "\u001b[1m";
                    close = RESET;
                }
                case RichAnnotation.Image i -> open = FG_BLUE;
                case RichAnnotation.Code c -> open = FG_BLUE;
                case RichAnnotation.Preformat p -> open = FG_BLUE;
                case RichAnnotation.Strong t -> open = "\u001b[38;5;11m";
                case RichAnnotation.Strikeout t -> open = "\u001b[38;5;8m";
                case RichAnnotation.Fg c -> {
                    if (useCssColours) {
                        start.add("\u001b[38;2;" + c.colour().r() + ";" + c.colour().g() + ";" + c.colour().b() + "m");
                        finish.add(FG_RESET);
                        haveExplicitColour = true;
                    }
                }
                case RichAnnotation.Bg c -> {
                    if (useCssColours) {
                        start.add("\u001b[48;2;" + c.colour().r() + ";" + c.colour().g() + ";" + c.colour().b() + "m");
                        finish.add(BG_RESET);
                    }
                }
                case RichAnnotation.Default d -> {}
            }
            if (close != null) {
                start.add(open);
                finish.add(close);
            } else if (open != null && !haveExplicitColour) {
                start.add(open);
                finish.add(FG_RESET);
            }
        }
        Collections.reverse(finish);
        return String.join("", start) + s + String.join("", finish);
    }
}
