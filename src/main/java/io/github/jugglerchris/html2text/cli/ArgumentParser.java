// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)
//
// The command-line parsing of the html2text-cli program, with the usage, help and error texts of the argparse crate
// (version 0.2.2) that the original uses.

package io.github.jugglerchris.html2text.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** An argparse-style parser: options with short and long names, and optional positional arguments. */
final class ArgumentParser {
    private static final int OPTION_WIDTH = 24;
    private static final int TOTAL_WIDTH = 79;

    /** How an option takes its value. */
    enum Action {
        /** A flag. */
        FLAG,
        /** A flag that asks for the help text. */
        HELP,
        /** An option with a value, checked by the option's validator. */
        VALUE
    }

    private record Option(List<String> names, Action action, String help, String metavar, Predicate<String> valid) {}

    private record Positional(String name, String help) {}

    /** The outcome of parsing: the values set, or the exit status with what the parser printed. */
    record Result(Map<String, String> values, List<String> positionals, int status, String stdout, String stderr) {}

    private final List<Option> options = new ArrayList<>();
    private final List<Positional> positionals = new ArrayList<>();

    ArgumentParser() {
        options.add(new Option(List.of("-h", "--help"), Action.HELP, "Show this help message and exit", "", s -> true));
    }

    /** Adds an optional positional argument. */
    void addArgument(String name, String help) {
        positionals.add(new Positional(name, help));
    }

    /** Adds an option; its key in the result is its last name. Values must pass {@code valid}. */
    void addOption(List<String> names, Action action, String help, Predicate<String> valid) {
        String longest = names.get(0);
        for (String name : names) {
            if (name.length() > longest.length()) {
                longest = name;
            }
        }
        String metavar = longest.length() > 2 ? longest.substring(2).toUpperCase().replace("-", "_") : "";
        options.add(new Option(names, action, help, metavar, valid));
    }

    private Option find(String name) {
        for (Option opt : options) {
            if (opt.names().contains(name)) {
                return opt;
            }
        }
        return null;
    }

    private static String key(Option opt) {
        return opt.names().get(opt.names().size() - 1);
    }

    /** Parses {@code args} (the program name first). */
    Result parse(List<String> args) {
        String name = args.isEmpty() ? "unknown" : args.get(0);
        Map<String, String> values = new LinkedHashMap<>();
        List<String> postponed = new ArrayList<>();
        String error = null;
        boolean help = false;
        loop:
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals("--")) {
                postponed.addAll(args.subList(i + 1, args.size()));
                break;
            } else if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                String optname = eq < 0 ? arg : arg.substring(0, eq);
                Option opt = find(optname);
                if (opt == null) {
                    error = "Unknown option " + arg;
                } else if (opt.action() != Action.VALUE) {
                    if (eq >= 0) {
                        error = "Option " + optname + " does not accept an argument";
                    } else if (opt.action() == Action.HELP) {
                        help = true;
                    } else {
                        values.put(key(opt), "true");
                    }
                } else {
                    String value = eq >= 0 ? arg.substring(eq + 1) : i + 1 < args.size() ? args.get(++i) : null;
                    error = setValue(values, opt, value);
                }
            } else if (arg.startsWith("-") && arg.length() > 1) {
                for (int j = 1; j < arg.length(); ) {
                    int ch = arg.codePointAt(j);
                    j += Character.charCount(ch);
                    Option opt = find("-" + new String(Character.toChars(ch)));
                    if (opt == null) {
                        error = "Unknown short option \"" + new String(Character.toChars(ch)) + "\"";
                    } else if (opt.action() == Action.HELP) {
                        help = true;
                    } else if (opt.action() == Action.FLAG) {
                        values.put(key(opt), "true");
                        continue;
                    } else {
                        String value = j < arg.length() ? arg.substring(j) : i + 1 < args.size() ? args.get(++i) : null;
                        error = setValue(values, opt, value);
                    }
                    break;
                }
            } else {
                postponed.add(arg);
                continue;
            }
            if (error != null || help) {
                break loop;
            }
        }
        if (help) {
            return new Result(values, postponed, 0, helpText(name), "");
        }
        if (error == null && postponed.size() > positionals.size()) {
            error = "Unexpected argument " + postponed.get(positionals.size());
        }
        if (error != null) {
            return new Result(values, postponed, 2, "", usage(name) + name + ": " + error + "\n");
        }
        return new Result(values, postponed, -1, "", "");
    }

    private static String setValue(Map<String, String> values, Option opt, String value) {
        if (value == null) {
            List<String> quoted = new ArrayList<>();
            for (String n : opt.names()) {
                quoted.add("\"" + n + "\"");
            }
            return "Option [" + String.join(", ", quoted) + "] requires an argument";
        }
        if (!opt.valid().test(value)) {
            return "Bad value " + value;
        }
        values.put(key(opt), value);
        return null;
    }

    private String usage(String name) {
        StringBuilder out = new StringBuilder("Usage:\n  ").append(name);
        if (!options.isEmpty()) {
            out.append(" [OPTIONS]");
            for (Positional p : positionals) {
                out.append(" [").append(p.name().toUpperCase()).append(']');
            }
        }
        return out.append('\n').toString();
    }

    private String helpText(String name) {
        StringBuilder out = new StringBuilder(usage(name)).append('\n');
        if (!positionals.isEmpty()) {
            out.append("\nPositional arguments:\n");
            for (Positional p : positionals) {
                entry(out, p.name(), p.help());
            }
        }
        out.append("\nOptional arguments:\n");
        for (Option opt : options) {
            String names = String.join(",", opt.names());
            entry(out, opt.action() == Action.VALUE ? names + " " + opt.metavar() : names, opt.help());
        }
        return out.toString();
    }

    private static void entry(StringBuilder out, String label, String help) {
        out.append("  ").append(label);
        int used = 2 + label.length();
        if (used >= OPTION_WIDTH) {
            out.append('\n').append(" ".repeat(OPTION_WIDTH));
        } else {
            out.append(" ".repeat(OPTION_WIDTH - used));
        }
        int off = OPTION_WIDTH;
        boolean first = true;
        for (String word : help.split("[ \t\r\n]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!first) {
                if (off + word.length() + 1 > TOTAL_WIDTH) {
                    out.append('\n').append(" ".repeat(OPTION_WIDTH));
                    off = OPTION_WIDTH;
                } else {
                    out.append(' ');
                    off += 1;
                }
            }
            out.append(word);
            off += word.length();
            first = false;
        }
        out.append('\n');
    }
}
