package modernmars.ui;

import java.util.Collections;
import java.util.List;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

/**
 * Source editor pane: a {@link CodeArea} with line numbers and
 * regex-driven MIPS syntax highlighting that updates on every
 * keystroke.
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class EditorPane extends BorderPane
{
    /** CSS class applied to the paragraph at the current PC. */
    private static final String CURRENT_LINE_STYLE = "current-line";

    /** Underlying RichTextFX code area. */
    private final CodeArea codeArea;

    /** Currently-highlighted paragraph index, or -1 if none. */
    private int highlightedParagraph;

    /**
     * Builds the editor pane with line numbers and live MIPS highlighting.
     */
    public EditorPane()
    {
        getStyleClass().add("editor-pane");
        codeArea = new CodeArea();
        codeArea.getStyleClass().add("mips-editor");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        highlightedParagraph = -1;

        // Re-highlight whenever the text changes. RichTextFX recommends
        // this exact pattern; the cost is negligible for student-sized
        // programs.
        codeArea.textProperty().addListener((obs, oldText, newText) ->
            codeArea.setStyleSpans(0,
                MipsHighlighter.computeHighlighting(newText)));

        Label header = new Label("Editor");
        header.getStyleClass().add("pane-header");
        setTop(header);
        setCenter(new VirtualizedScrollPane<>(codeArea));

        // Curated MIPS keyword autocomplete popup. Activates as the user
        // types; Ctrl/Cmd-Space forces it open even mid-token.
        new AutocompletePopup(codeArea);
    }

    /**
     * Replaces the editor contents with {@code text} and re-highlights it.
     *
     * @param text new editor contents.
     */
    public void setText(String text)
    {
        codeArea.replaceText(text == null ? "" : text);
        codeArea.moveTo(0);
    }

    /**
     * @return current editor text.
     */
    public String getText()
    {
        return codeArea.getText();
    }

    /**
     * Highlights the given source line as "the line about to execute".
     * Pass {@code -1} (or any out-of-range value) to clear the
     * highlight.
     *
     * @param line 1-based source line; -1 to clear.
     */
    public void highlightLine(int line)
    {
        // Clear previous highlight first.
        if (highlightedParagraph >= 0
                && highlightedParagraph < codeArea.getParagraphs().size())
        {
            codeArea.setParagraphStyle(highlightedParagraph,
                Collections.<String>emptyList());
        }
        highlightedParagraph = -1;
        if (line <= 0 || line > codeArea.getParagraphs().size())
        {
            return;
        }
        // RichTextFX paragraphs are 0-indexed.
        int idx = line - 1;
        codeArea.setParagraphStyle(idx, List.of(CURRENT_LINE_STYLE));
        highlightedParagraph = idx;
        // Scroll the line into view if it's off-screen.
        codeArea.showParagraphInViewport(idx);
    }
}
