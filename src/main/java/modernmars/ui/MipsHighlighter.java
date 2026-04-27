package modernmars.ui;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import modernmars.util.MipsVocabulary;

/**
 * Regex-based syntax highlighter for MIPS assembly source. Produces
 * {@link StyleSpans} that {@link org.fxmisc.richtext.CodeArea} consumes
 * via {@code setStyleSpans}.
 *
 * <p>The recognised token classes are: {@code comment}, {@code string},
 * {@code directive} (anything starting with {@code .}), {@code label}
 * (identifier followed by {@code :}), {@code register} (e.g. {@code $t0},
 * {@code $f12}, {@code $zero}), {@code instruction} (a hard-coded set of
 * common MIPS opcodes), and {@code number} (decimal/hex/binary).</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class MipsHighlighter
{
    /** Compiled master regex with named groups. Opcodes are pulled from
     *  {@link MipsVocabulary} so the highlighter and the autocomplete
     *  popup stay in sync. */
    private static final Pattern PATTERN = Pattern.compile(
        "(?<COMMENT>#[^\n]*)"
            + "|(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\")"
            + "|(?<DIRECTIVE>\\.[A-Za-z_][A-Za-z0-9_]*)"
            + "|(?<LABEL>^[ \\t]*[A-Za-z_][A-Za-z0-9_]*[ \\t]*:)"
            + "|(?<REGISTER>\\$[A-Za-z0-9]+)"
            + "|(?<INSTRUCTION>\\b(?:"
                + String.join("|", MipsVocabulary.opcodeNames())
                + ")\\b)"
            + "|(?<NUMBER>(?:-?0[xX][0-9A-Fa-f]+)|(?:-?0[bB][01]+)|(?:-?[0-9]+))",
        Pattern.MULTILINE);

    /** Utility class - no instances. */
    private MipsHighlighter()
    {
    }

    /**
     * Builds the style spans for the entire document.
     *
     * @param text full editor text.
     * @return spans tagging each recognised region with a CSS class.
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text)
    {
        Matcher matcher = PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        int last = 0;
        while (matcher.find())
        {
            String styleClass = matchedStyle(matcher);
            spans.add(Collections.emptyList(), matcher.start() - last);
            spans.add(Collections.singletonList(styleClass),
                matcher.end() - matcher.start());
            last = matcher.end();
        }
        spans.add(Collections.emptyList(), text.length() - last);
        return spans.create();
    }

    /**
     * Returns the CSS style class for whichever named group matched.
     *
     * @param m matcher positioned at a successful find.
     * @return non-null CSS style class name.
     */
    private static String matchedStyle(Matcher m)
    {
        if (m.group("COMMENT") != null)
        {
            return "mips-comment";
        }
        if (m.group("STRING") != null)
        {
            return "mips-string";
        }
        if (m.group("DIRECTIVE") != null)
        {
            return "mips-directive";
        }
        if (m.group("LABEL") != null)
        {
            return "mips-label";
        }
        if (m.group("REGISTER") != null)
        {
            return "mips-register";
        }
        if (m.group("INSTRUCTION") != null)
        {
            return "mips-instruction";
        }
        if (m.group("NUMBER") != null)
        {
            return "mips-number";
        }
        return "";
    }
}
