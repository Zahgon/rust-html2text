# html2text

html2text is a Java library which converts HTML to plain text, wrapped to a specified width, or to lines of text
with annotations (links, emphasis, colours...) for showing in a terminal. It is a port of the Rust crate
[html2text](https://github.com/jugglerchris/rust-html2text/) 0.17.4 (with its `css` feature), and produces the same
output byte for byte. It includes its own HTML5 parser (a port of html5ever), a small CSS engine and the Unicode
display widths of the unicode-width crate. It needs only the JDK (Java 21).

The project aims to do a reasonable job of rendering reasonable HTML in a terminal or other places where HTML needs to
be converted to text (for example the text/plain fallback in HTML e-mails).

## Examples

The simple functions like `Html2Text.fromRead()` return formatted text:

```java
import io.github.jugglerchris.html2text.Html2Text;
import java.nio.charset.StandardCharsets;

byte[] html = """
       <ul>
         <li>Item one</li>
         <li>Item two</li>
         <li>Item three</li>
       </ul>""".getBytes(StandardCharsets.UTF_8);
String text = Html2Text.fromRead(html, 20);
// "* Item one\n* Item two\n* Item three\n"
```

`Config` gives more control:

```java
String text = Config.plain()
        .maxWrapWidth(40)
        .useDocCss()
        .stringFromRead(html, 80);
```

`Config.rich().linesFromRead(html, width)` (or `Html2Text.fromReadRich`) returns lines of `TaggedString`s annotated
with `RichAnnotation`s, and `Config.rich().coloured(html, width, colourMap)` applies a function to every annotated
span (for example to add terminal colours). Errors are reported with `Html2TextException` (for example `TOO_NARROW`
when the width cannot fit the content).

## CSS support

Style rules are taken from:
* if `Config.useDocCss()` is called, the document: `<style>` elements, inline `style` attributes and
  `color`/`bgcolor` attributes;
* independently of `useDocCss`, rules added with `Config.addCss(...)` (user rules) and `Config.addAgentCss(...)`.

The following CSS features are implemented:
* basic selector matching (child and descendant combinators, classes, ids and element types), attribute selectors
  `[foo]` and `[foo="bar"]`, and the `nth-child` pseudo-class;
* `color` and `background-color` (annotations `Fg`/`Bg` in rich output);
* `display: none` removes elements, as do `overflow: hidden` (or `overflow-y`) with a zero `height`/`max-height`;
* `content: "text"` inside `::before`/`::after`;
* `white-space` values `normal`, `pre` and `pre-wrap`.

## The html2text command

`mvn package` builds `target/html2text.jar`; `bin/html2text` runs it. The command exposes the library through a
JSON-lines protocol: it takes no arguments, reads standard input as UTF-8 (ill-formed bytes become U+FFFD), treats
every non-empty line as one JSON request and writes one JSON response line per request, in order. The exit status is
0 when every request succeeded, 1 when at least one failed, and 2 (with a usage text on standard error) for any
command-line argument.

Requests (`html` may be replaced by `html_bytes`, an array of byte values; `width` is a non-negative integer):

| Request | Response |
|---|---|
| `{"op":"from_read","html":H,"width":W}` | `{"text":S}` |
| `{"op":"from_read_with_decorator","decorator":"plain"\|"trivial"\|"rich","html":H,"width":W}` | `{"text":S}` |
| `{"op":"from_read_rich","html":H,"width":W}` | `{"lines":LINES}` |
| `{"op":"from_read_coloured","colour_map":"test"\|"terminal","html":H,"width":W}` | `{"text":S}` |
| `{"op":"config","config":C,"steps":[...],"call":CALL,"colour_map":M,"html":H,"width":W}` | `{"text":S}` or `{"lines":LINES}` |
| `{"op":"parse","config":C,"steps":[...],"output":"string"\|"lines"\|"coloured","html":H,"width":W}` | `{"text":S}` or `{"lines":LINES}` |
| `{"op":"parsed_style","html":H}` | `{"text":S}` |
| `{"op":"cli","args":[...],"stdin":S}` | `{"status":N,"stdout":S,"stderr":S}` |

* `C` is `plain`, `plain_no_decorate`, `rich` or `trivial`; `steps` are the builder methods of `Config` in snake
  case, e.g. `["max_wrap_width",10]`, `["use_doc_css"]`, `["empty_img_mode",{"Replace":"[img]"}]`.
* `CALL` is `string_from_read`, `lines_from_read`, `coloured`, `render_to_string`, `render_to_lines` or
  `render_coloured` (the coloured calls need the `rich` configuration and a `colour_map`).
* `LINES` is an array of lines, each an array of `{"text":S,"tag":[ANN...]}` and `{"fragment":NAME}` elements.
* The `cli` op runs the html2text command-line program (options `-w`, `-W`, `-L`, `--pad-width`, `--colour`, `--css`,
  `--show-css`, `--agent-css` and others; see `--help`).
* A failed request is answered with `{"error":{"name":NAME,"message":MESSAGE}}`, NAME being `JsonError`,
  `RequestError`, `TooNarrow`, `CssParseError`, `Fail` or `Panic`.

## Building and testing

```sh
mvn -q -DskipTests package   # build target/html2text.jar
mvn test                     # run the tests
echo '{"op":"from_read","html":"<p>Hello</p>","width":20}' | bin/html2text
```

## Licence

MIT, as the original (see LICENSE). The HTML parser is ported from html5ever and the width tables from
unicode-width (both MIT or Apache-2.0); the JSON module follows serde_json (MIT or Apache-2.0).
