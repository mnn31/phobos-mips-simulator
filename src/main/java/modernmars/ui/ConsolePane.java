package modernmars.ui;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Bottom console: a read-only output area that shows simulator stdout
 * plus a single-line input field that feeds {@code System.in}.
 *
 * <p>Output is appended on the JavaFX application thread regardless of
 * which thread produced it, so the simulator (which runs on a worker
 * thread) can call {@link #appendOutput(String)} freely.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class ConsolePane extends BorderPane
{
    /** Read-only text area that displays everything the program prints. */
    private final TextArea output;

    /** Single-line input field; pressing Enter sends a line to the program. */
    private final TextField input;

    /**
     * Builds the pane and wires the input field. The supplied consumer is
     * invoked on the JavaFX thread whenever the user submits a line.
     *
     * @param onLineSubmitted callback receiving each user-typed line.
     * @param onReset callback fired when the user clicks the Reset
     *                button in the console header; same effect as the
     *                toolbar Reset / F6.
     */
    public ConsolePane(Consumer<String> onLineSubmitted, Runnable onReset)
    {
        getStyleClass().add("console-pane");
        output = new TextArea();
        output.setEditable(false);
        output.setWrapText(false);
        output.getStyleClass().add("console-output");

        input = new TextField();
        input.setPromptText("Program input \u2014 press Enter to send");
        input.getStyleClass().add("console-input");
        input.setOnAction(e ->
        {
            String line = input.getText();
            input.clear();
            onLineSubmitted.accept(line);
            // Echo the line so the user can see what they typed.
            appendOutput(line + "\n");
        });

        Label header = new Label("Console");
        header.getStyleClass().add("pane-header");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button resetButton = new Button("\u21BA  Reset");
        resetButton.getStyleClass().addAll("toolbar-button", "console-reset");
        resetButton.setTooltip(new Tooltip("Re-assemble & reset state (F6)"));
        resetButton.setOnAction(e -> onReset.run());

        Button clearButton = new Button("\u2715  Clear");
        clearButton.getStyleClass().addAll("toolbar-button", "console-clear");
        clearButton.setTooltip(new Tooltip("Clear console output"));
        clearButton.setOnAction(e -> clear());

        HBox headerRow = new HBox(6, header, spacer, resetButton, clearButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(2, 8, 2, 4));

        HBox inputRow = new HBox(input);
        inputRow.getStyleClass().add("console-input-row");
        HBox.setHgrow(input, Priority.ALWAYS);

        setTop(headerRow);
        setCenter(output);
        setBottom(inputRow);
    }

    /**
     * Appends a chunk of program output to the console. Safe to call
     * from any thread.
     *
     * @param text text to append; may contain newlines.
     */
    public void appendOutput(String text)
    {
        if (Platform.isFxApplicationThread())
        {
            output.appendText(text);
        }
        else
        {
            Platform.runLater(() -> output.appendText(text));
        }
    }

    /**
     * Clears the output area on the JavaFX thread.
     */
    public void clear()
    {
        if (Platform.isFxApplicationThread())
        {
            output.clear();
        }
        else
        {
            Platform.runLater(output::clear);
        }
    }
}
