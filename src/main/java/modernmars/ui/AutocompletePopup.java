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
 * <p>The popup follows VS Code's behaviour: it appears below the caret,
 * filters as the user types, and is dismissed by Escape or losing
 * focus. Tab and Enter accept the highlighted suggestion. Arrow keys
 * navigate without moving the caret.</p>
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

        popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.getContent().add(list);

        editor.textProperty().addListener((obs, oldText, newText) -> refresh());
        editor.caretPositionProperty().addListener((obs, oldPos, newPos) -> refresh());
        editor.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        editor.focusedProperty().addListener((obs, was, is) ->
        {
            if (Boolean.FALSE.equals(is))
            {
                popup.hide();
            }
        });
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
        showAtCaret();
    }

    /**
     * Positions the popup just below the caret and shows it.
     */
    private void showAtCaret()
    {
        Optional<Bounds> caretBounds = editor.getCaretBounds();
        if (caretBounds.isEmpty() || editor.getScene() == null)
        {
            popup.hide();
            return;
        }
        Bounds b = caretBounds.get();
        if (popup.isShowing())
        {
            popup.setX(b.getMinX());
            popup.setY(b.getMaxY() + 2);
        }
        else
        {
            popup.show(editor, b.getMinX(), b.getMaxY() + 2);
        }
    }

    /**
     * Locates the word currently being typed at the caret. A "word" here
     * is a maximal run of identifier/dollar/dot characters - the same
     * shapes the highlighter recognises - ending at the caret.
     *
     * @return start (inclusive) and end (exclusive) offsets in the
     *         editor text, or empty if no word is active.
     */
    private Optional<int[]> currentWordBounds()
    {
        int caret = editor.getCaretPosition();
        String text = editor.getText();
        if (caret == 0 || caret > text.length())
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
            // Ctrl/Cmd-Space forces the popup open.
            if (event.getCode() == KeyCode.SPACE && event.isShortcutDown())
            {
                refresh();
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

            Label hint = new Label(entry.description());
            hint.getStyleClass().add("autocomplete-hint");

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
