package modernmars.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;

/**
 * Top toolbar with the canonical action buttons: Open, Save, Assemble,
 * Run, Step, Reset. Buttons are exposed as fields so {@link MainWindow}
 * can wire handlers.
 *
 * <p>Icons are Unicode glyphs rather than PNG resources - they render
 * crisply at any DPI, theme to whatever the current foreground colour
 * is, and do not require a separate asset pipeline.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class Toolbar extends ToolBar
{
    /** Open a {@code .asm} file from disk. */
    private final Button openButton;

    /** Save the current editor contents back to disk. */
    private final Button saveButton;

    /** Assemble the current editor contents. */
    private final Button assembleButton;

    /** Run from the current PC to termination. */
    private final Button runButton;

    /** Execute one instruction. */
    private final Button stepButton;

    /** Undo the last executed instruction. */
    private final Button backStepButton;

    /** Status label updated by the main window after each action. */
    private final Label statusLabel;

    /**
     * Builds the toolbar; handlers are attached from {@link MainWindow}.
     */
    public Toolbar()
    {
        getStyleClass().add("modern-toolbar");
        openButton = makeButton("\uD83D\uDCC2  Open", "Open .asm file (Ctrl+O)");
        saveButton = makeButton("\uD83D\uDCBE  Save", "Save current file (Ctrl+S)");
        assembleButton = makeButton("\u2699  Assemble", "Assemble source (F3)");
        runButton = makeButton("\u25B6  Run", "Run to termination (F5)");
        stepButton = makeButton("\u21E2  Step", "Execute one instruction (F7)");
        backStepButton = makeButton("\u21E0  Back", "Undo last instruction (F8)");

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("toolbar-status");

        // Reset is intentionally NOT on this toolbar - it sits next to
        // Clear in the console header for proximity to where the user
        // is reading program output.
        getItems().addAll(
            openButton, saveButton,
            new Separator(),
            assembleButton,
            new Separator(),
            runButton, stepButton, backStepButton,
            new Separator(),
            statusLabel);
    }

    /** @return the Backstep button. */
    public Button backStepButton()
    {
        return backStepButton;
    }

    /**
     * Builds a tooltipped toolbar button with the {@code toolbar-button}
     * style class.
     *
     * @param text label text including any leading glyph.
     * @param tooltip hover-tooltip text.
     * @return the constructed Button.
     */
    private static Button makeButton(String text, String tooltip)
    {
        Button b = new Button(text);
        b.getStyleClass().add("toolbar-button");
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    /** @return the Open button. */
    public Button openButton()
    {
        return openButton;
    }

    /** @return the Save button. */
    public Button saveButton()
    {
        return saveButton;
    }

    /** @return the Assemble button. */
    public Button assembleButton()
    {
        return assembleButton;
    }

    /** @return the Run button. */
    public Button runButton()
    {
        return runButton;
    }

    /** @return the Step button. */
    public Button stepButton()
    {
        return stepButton;
    }

    /**
     * Updates the right-hand status label.
     *
     * @param text new status string.
     */
    public void setStatus(String text)
    {
        statusLabel.setText(text);
    }
}
