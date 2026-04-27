package modernmars.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.fxmisc.richtext.CodeArea;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Popup;

import modernmars.util.MipsVocabulary;
import modernmars.util.MipsVocabulary.Entry;

/**
 * Lightweight autocomplete popup that surfaces curated MIPS keywords
 * (instructions, registers, directives, syscall snippets) as the user
 * types in the {@link CodeArea}.
 *
 * <p>The popup follows the VS Code playbook: it appears below the caret,
 * filters as the user types, and is dismissed by Escape. Tab and Enter
 * accept the highlighted suggestion. Arrow keys navigate without
 * moving the caret.</p>
 *
 * <p>Positioning uses {@code getCharacterBoundsOnScreen}, which is
 * unambiguously in screen coordinates - earlier versions of this class
 * used {@code getCaretBounds()} which has historically been ambiguous
 * about coordinate space across RichTextFX releases and could leave
 * the popup positioned off-screen.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class AutocompletePopup
{
    /** Maximum entries shown at once - bigger lists are scrollable. */
    private static final int MAX_VISIBLE = 8;

    /** Underlying JavaFX popup window. */
    private final Popup popup;

    /** List that displays filtered suggestions. */
    private final ListView<Entry> list;

    /** Backing collection driving {@link #list}. */
    private final ObservableList<Entry> items;

    /** Editor we attach to. */
    private final CodeArea editor;

    /** All entries we ever consider, taken from {@link MipsVocabulary}. */
    private final List<Entry> allEntries;

    /** Caret position at which the current word starts. */
    private int wordStart;

    /**
     * Builds the popup and wires it to {@code editor}. The popup is not
     * shown until the user starts typing a word.
     *
     * @param editor RichTextFX code area to attach to.
     */
    public AutocompletePopup(CodeArea editor)
    {
        this.editor = editor;
        this.allEntries = MipsVocabulary.all();
        this.items = FXCollections.observableArrayList();
        this.list = new ListView<>(items);
        list.getStyleClass().add("autocomplete-list");
        list.setCellFactory(lv -> new EntryCell());
        list.setFocusTraversable(false);
        list.setPrefWidth(360);
        // Inline style so the popup is legible even if the parent
        // scene's stylesheet does not propagate to the popup window
        // (which has its own scene). The CSS rules in theme-dark.css
        // still apply when stylesheets ARE inherited.
        list.setStyle(
            "-fx-background-color: #313244;"
            + " -fx-control-inner-background: #313244;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-background-radius: 8;"
            + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 12, 0.2, 0, 4);");

        popup = new Popup();
        popup.setAutoFix(true);
        // Auto-hide: hide if the user clicks outside the popup. We do
        // NOT install a focus-loss listener on the editor, since
        // showing the popup may briefly shuffle focus on some
        // platforms - the popup would then immediately re-hide before
        // the user could interact with it.
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.getContent().add(list);

        editor.textProperty().addListener((obs, oldText, newText) ->
            tryRefresh());
        editor.caretPositionProperty().addListener((obs, oldPos, newPos) ->
            tryRefresh());
        editor.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    /**
     * Wraps {@link #refresh()} in a try/catch so a popup-positioning
     * exception cannot bubble up and break the editor.
     */
    private void tryRefresh()
    {
        try
        {
            refresh();
        }
        catch (RuntimeException ex)
        {
            popup.hide();
        }
    }

    /**
     * Recomputes the suggestion list from the current word under the
     * caret. Hides the popup when there is no word or no match.
     */
    private void refresh()
    {
        Optional<int[]> bounds = currentWordBounds();
        if (bounds.isEmpty())
        {
            popup.hide();
            return;
        }
        wordStart = bounds.get()[0];
        int end = bounds.get()[1];
        String prefix = editor.getText(wordStart, end);
        if (prefix.isEmpty())
        {
            popup.hide();
            return;
        }
        ArrayList<Entry> filtered = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (Entry e : allEntries)
        {
            if (e.trigger().toLowerCase().startsWith(lower))
            {
                filtered.add(e);
                if (filtered.size() >= 30)
                {
                    break;
                }
            }
        }
        if (filtered.isEmpty())
        {
            popup.hide();
            return;
        }
        items.setAll(filtered);
        list.getSelectionModel().select(0);
        list.setPrefHeight(Math.min(MAX_VISIBLE, filtered.size()) * 26 + 4);
        showNearWord();
    }

    /**
     * Positions the popup just below the start of the current word
     * (anchored to the screen, not the editor's local space) and
     * shows it.
     */
    private void showNearWord()
    {
        if (editor.getScene() == null)
        {
            popup.hide();
            return;
        }
        Optional<Bounds> bounds =
            editor.getCharacterBoundsOnScreen(wordStart, wordStart + 1);
        double anchorX;
        double anchorY;
        if (bounds.isPresent())
        {
            // Place the popup right under the first character of the
            // word being completed.
            anchorX = bounds.get().getMinX();
            anchorY = bounds.get().getMaxY() + 2;
        }
        else
        {
            // Fallback: anchor to the bottom-left of the editor itself.
            // Better than not showing at all.
            Bounds editorOnScreen = editor.localToScreen(
                editor.getBoundsInLocal());
            if (editorOnScreen == null)
            {
                popup.hide();
                return;
            }
            anchorX = editorOnScreen.getMinX() + 8;
            anchorY = editorOnScreen.getMinY() + 24;
        }
        if (popup.isShowing())
        {
            popup.setX(anchorX);
            popup.setY(anchorY);
        }
        else
        {
            popup.show(editor, anchorX, anchorY);
        }
    }

    /**
     * Locates the word currently being typed at the caret. A "word"
     * here is a maximal run of identifier/dollar/dot characters - the
     * same shapes the highlighter recognises - ending at the caret.
     *
     * @return start (inclusive) and end (exclusive) offsets in the
     *         editor text, or empty if no word is active.
     */
    private Optional<int[]> currentWordBounds()
    {
        int caret = editor.getCaretPosition();
        String text = editor.getText();
        if (text == null || caret <= 0 || caret > text.length())
        {
            return Optional.empty();
        }
        int start = caret;
        while (start > 0 && isWordChar(text.charAt(start - 1)))
        {
            start--;
        }
        if (start == caret)
        {
            return Optional.empty();
        }
        return Optional.of(new int[]{start, caret});
    }

    /**
     * Returns whether {@code c} can appear inside a MIPS keyword token.
     *
     * @param c character to test.
     * @return {@code true} for letters, digits, {@code _}, {@code $},
     *         and a leading {@code .}.
     */
    private static boolean isWordChar(char c)
    {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    /**
     * Intercepts key events that should drive the popup rather than be
     * passed through to the editor.
     *
     * @param event the key press event from the editor.
     */
    private void onKeyPressed(KeyEvent event)
    {
        if (!popup.isShowing())
        {
            // Cmd/Ctrl-Space forces the popup open even when no word
            // boundary triggered it.
            if (event.getCode() == KeyCode.SPACE && event.isShortcutDown())
            {
                tryRefresh();
                event.consume();
            }
            return;
        }
        switch (event.getCode())
        {
            case DOWN:
                list.getSelectionModel().selectNext();
                event.consume();
                break;
            case UP:
                list.getSelectionModel().selectPrevious();
                event.consume();
                break;
            case ENTER:
            case TAB:
                acceptSelected();
                event.consume();
                break;
            case ESCAPE:
                popup.hide();
                event.consume();
                break;
            default:
                // Other keys edit the buffer; refresh() reacts on text change.
                break;
        }
    }

    /**
     * Replaces the current word with the highlighted suggestion's
     * replacement and hides the popup.
     */
    private void acceptSelected()
    {
        Entry chosen = list.getSelectionModel().getSelectedItem();
        if (chosen == null)
        {
            popup.hide();
            return;
        }
        int caret = editor.getCaretPosition();
        // Defensive bounds: if the user has somehow moved the caret
        // before the word start, fall back to a safe no-op.
        if (caret < wordStart || caret > editor.getLength())
        {
            popup.hide();
            return;
        }
        editor.replaceText(wordStart, caret, chosen.replacement());
        popup.hide();
    }

    /**
     * Cell renderer: shows the trigger, a category chip on the right,
     * and the description in muted text.
     */
    private static final class EntryCell extends ListCell<Entry>
    {
        @Override
        protected void updateItem(Entry entry, boolean empty)
        {
            super.updateItem(entry, empty);
            if (empty || entry == null)
            {
                setText(null);
                setGraphic(null);
                return;
            }
            Label name = new Label(entry.trigger());
            name.getStyleClass().add("autocomplete-name");
            name.setStyle(
                "-fx-text-fill: #cdd6f4;"
                + " -fx-font-family: 'JetBrains Mono','Menlo',monospace;"
                + " -fx-font-weight: bold;");

            Label hint = new Label(entry.description());
            hint.getStyleClass().add("autocomplete-hint");
            hint.setStyle(
                "-fx-text-fill: #a6adc8; -fx-font-size: 11px;");

            Label tag = new Label(entry.category());
            tag.getStyleClass().addAll("autocomplete-tag",
                "autocomplete-tag-" + entry.category());

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(8, name, hint, spacer, tag);
            row.getStyleClass().add("autocomplete-row");
            setText(null);
            setGraphic(row);
            setTooltip(new Tooltip(entry.replacement()));
        }
    }
}
