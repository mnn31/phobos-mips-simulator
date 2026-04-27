package modernmars.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import modernmars.core.ConsoleIO;
import modernmars.core.MarsBackend;
import modernmars.core.MarsBackend.AssembleResult;
import modernmars.core.MarsBackend.Diagnostic;
import modernmars.core.MarsBackend.RunResult;

/**
 * Top-level shell. Owns the {@link MarsBackend}, lays out the four
 * panes ({@link EditorPane}, {@link RegistersPane}, {@link MemoryPane},
 * {@link ConsolePane}), wires {@link Toolbar} actions, and runs the
 * simulator on a worker thread so the UI stays responsive.
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class MainWindow
{
    /** Title shown in the OS title bar. */
    private static final String APP_TITLE = "Modern MARS";

    /** Adapter to MARS' assembler/simulator. */
    private final MarsBackend backend;

    /** Captures program stdout/stdin so it can be shown in the console. */
    private final ConsoleIO consoleIO;

    /** Pane components. */
    private final EditorPane editorPane;
    private final RegistersPane registersPane;
    private final MemoryPane memoryPane;
    private final ConsolePane consolePane;
    private final Toolbar toolbar;

    /** Currently loaded source file, or {@code null} for an unsaved buffer. */
    private File currentFile;

    /** Top-level stage; tracked so dialogs can be parented. */
    private Stage stage;

    /**
     * Builds the window contents but does not show them.
     */
    public MainWindow()
    {
        backend = new MarsBackend();
        editorPane = new EditorPane();
        registersPane = new RegistersPane();
        memoryPane = new MemoryPane();
        consolePane = new ConsolePane(this::onConsoleInput);
        toolbar = new Toolbar();
        consoleIO = new ConsoleIO(consolePane::appendOutput);
        consoleIO.install();
    }

    /**
     * Builds the scene, attaches it to {@code stage}, and wires keyboard
     * accelerators.
     *
     * @param primaryStage primary {@link Stage} from {@code App.start}.
     */
    public void show(Stage primaryStage)
    {
        this.stage = primaryStage;

        SplitPane sideStack = new SplitPane(registersPane, memoryPane);
        sideStack.setOrientation(Orientation.VERTICAL);
        sideStack.setDividerPositions(0.55);

        SplitPane top = new SplitPane(editorPane, sideStack);
        top.setOrientation(Orientation.HORIZONTAL);
        top.setDividerPositions(0.62);

        SplitPane root = new SplitPane(top, consolePane);
        root.setOrientation(Orientation.VERTICAL);
        root.setDividerPositions(0.72);

        BorderPane shell = new BorderPane();
        shell.setTop(toolbar);
        shell.setCenter(root);

        wireToolbar();

        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().add(getClass()
            .getResource("/modernmars/theme-dark.css").toExternalForm());

        registerAccelerators(scene);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> consoleIO.uninstall());
        primaryStage.show();

        // Initial register snapshot so the panel isn't empty.
        registersPane.refresh(backend);
        memoryPane.refresh(backend);
    }

    /**
     * Wires every toolbar button to its handler.
     */
    private void wireToolbar()
    {
        toolbar.openButton().setOnAction(e -> openFile());
        toolbar.saveButton().setOnAction(e -> saveFile());
        toolbar.assembleButton().setOnAction(e -> assemble());
        toolbar.runButton().setOnAction(e -> runProgram());
        toolbar.stepButton().setOnAction(e -> stepProgram());
        toolbar.backStepButton().setOnAction(e -> backStepProgram());
        toolbar.resetButton().setOnAction(e -> assemble());
    }

    /**
     * Installs Ctrl/Cmd shortcuts plus function-key accelerators.
     *
     * @param scene scene to attach the accelerators to.
     */
    private void registerAccelerators(Scene scene)
    {
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
            this::openFile);
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
            this::saveFile);
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F3), this::assemble);
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F5), this::runProgram);
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F6), this::assemble);
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F7), this::stepProgram);
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F8), this::backStepProgram);
    }

    /**
     * Undoes the most recent simulator step using MARS' BackStepper.
     */
    private void backStepProgram()
    {
        try
        {
            if (!backend.canBackStep())
            {
                toolbar.setStatus("Nothing to undo");
                return;
            }
            backend.backStep();
            registersPane.refresh(backend);
            memoryPane.refresh(backend);
            toolbar.setStatus("Backstep \u2014 undid one instruction");
        }
        catch (Throwable th)
        {
            reportUnexpectedFailure(th);
        }
    }

    /**
     * Opens a file picker and loads the chosen {@code .asm} file into
     * the editor.
     */
    private void openFile()
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open MIPS source");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "MIPS Assembly", "*.asm", "*.s", "*.S"));
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null)
        {
            return;
        }
        try
        {
            String text = Files.readString(chosen.toPath());
            editorPane.setText(text);
            currentFile = chosen;
            stage.setTitle(APP_TITLE + " \u2014 " + chosen.getName());
            toolbar.setStatus("Loaded " + chosen.getName());
        }
        catch (IOException ioe)
        {
            showError("Could not read file: " + ioe.getMessage());
        }
    }

    /**
     * Saves the editor text back to {@link #currentFile}, prompting
     * for a path if the buffer has never been saved.
     */
    private void saveFile()
    {
        if (currentFile == null)
        {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save MIPS source");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "MIPS Assembly", "*.asm"));
            File chosen = chooser.showSaveDialog(stage);
            if (chosen == null)
            {
                return;
            }
            currentFile = chosen;
        }
        try
        {
            Files.writeString(currentFile.toPath(), editorPane.getText());
            toolbar.setStatus("Saved " + currentFile.getName());
            stage.setTitle(APP_TITLE + " \u2014 " + currentFile.getName());
        }
        catch (IOException ioe)
        {
            showError("Could not save file: " + ioe.getMessage());
        }
    }

    /**
     * Assembles the current editor contents.
     */
    private void assemble()
    {
        File source = ensureSourceOnDisk();
        if (source == null)
        {
            return;
        }
        consolePane.clear();
        registersPane.clearChangeFlags();
        AssembleResult result = backend.assemble(source.getAbsolutePath());
        for (Diagnostic d : result.messages())
        {
            consolePane.appendOutput(formatDiagnostic(d));
        }
        if (result.success())
        {
            toolbar.setStatus("Assembled \u2014 ready to run");
            registersPane.refresh(backend);
            memoryPane.refresh(backend);
        }
        else
        {
            toolbar.setStatus("Assembly failed");
        }
    }

    /**
     * Runs the program to termination on a worker thread. Any exception
     * - including unchecked runtime errors thrown by MARS - is captured
     * and reported through the console so the UI never silently freezes.
     */
    private void runProgram()
    {
        if (!backend.isAssembled())
        {
            assemble();
            if (!backend.isAssembled())
            {
                return;
            }
        }
        toolbar.setStatus("Running\u2026");
        Thread t = new Thread(() ->
        {
            RunResult result;
            try
            {
                result = backend.run();
            }
            catch (Throwable th)
            {
                result = null;
                final Throwable failure = th;
                Platform.runLater(() -> reportUnexpectedFailure(failure));
            }
            if (result != null)
            {
                final RunResult finished = result;
                Platform.runLater(() ->
                {
                    handleRunResult(finished);
                    toolbar.setStatus(statusFor(finished));
                });
            }
        }, "mars-runner");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Executes a single instruction. Any exception is caught and printed
     * to the console rather than allowed to bubble to the FX exception
     * handler.
     */
    private void stepProgram()
    {
        if (!backend.isAssembled())
        {
            assemble();
            if (!backend.isAssembled())
            {
                return;
            }
        }
        try
        {
            RunResult result = backend.step();
            handleRunResult(result);
            toolbar.setStatus(statusFor(result));
        }
        catch (Throwable th)
        {
            reportUnexpectedFailure(th);
        }
    }

    /**
     * Prints an unexpected-failure stack trace summary to the console
     * and updates the toolbar status. Used as the final safety net for
     * any non-{@link mars.ProcessingException} thrown by the simulator.
     *
     * @param failure throwable raised by MARS or the wrapper code.
     */
    private void reportUnexpectedFailure(Throwable failure)
    {
        registersPane.refresh(backend);
        memoryPane.refresh(backend);
        String type = failure.getClass().getSimpleName();
        String msg = failure.getMessage();
        consolePane.appendOutput("[error] simulator " + type
            + (msg == null ? "" : ": " + msg) + "\n");
        consolePane.appendOutput(
            "Tip: click Reset (or F6) to re-assemble before running again.\n");
        toolbar.setStatus("Runtime error \u2014 see console");
    }

    /**
     * Refreshes register/memory panels and prints any error diagnostics.
     *
     * @param result the just-returned run/step outcome.
     */
    private void handleRunResult(RunResult result)
    {
        registersPane.refresh(backend);
        memoryPane.refresh(backend);
        if (result.kind() == RunResult.Kind.ERROR)
        {
            for (Diagnostic d : result.errors())
            {
                consolePane.appendOutput(formatDiagnostic(d));
            }
        }
    }

    /**
     * Builds the right-hand status text for a run/step result.
     *
     * @param result result of the just-finished simulator action.
     * @return short human-readable status string.
     */
    private static String statusFor(RunResult result)
    {
        return switch (result.kind())
        {
            case TERMINATED -> "Program terminated";
            case PAUSED     -> "Paused";
            case ERROR      -> "Runtime error";
        };
    }

    /**
     * If the editor has unsaved edits, write them to disk so MARS can
     * read them. If the buffer has never been saved, prompts the user
     * for a path.
     *
     * @return the on-disk file or {@code null} if the user cancelled.
     */
    private File ensureSourceOnDisk()
    {
        if (currentFile == null)
        {
            saveFile();
            if (currentFile == null)
            {
                return null;
            }
        }
        try
        {
            // Always re-write so MARS sees the latest text.
            Files.writeString(Path.of(currentFile.getAbsolutePath()),
                editorPane.getText());
            return currentFile;
        }
        catch (IOException ioe)
        {
            showError("Could not stage source: " + ioe.getMessage());
            return null;
        }
    }

    /**
     * Forwards a console-input line to the running program.
     *
     * @param line text the user typed (newline appended automatically).
     */
    private void onConsoleInput(String line)
    {
        consoleIO.supplyLine(line);
    }

    /**
     * Formats a single diagnostic as a one-line console message.
     *
     * @param d diagnostic from the assembler or simulator.
     * @return text ending in a newline, suitable for the console.
     */
    private static String formatDiagnostic(Diagnostic d)
    {
        String tag = d.warning() ? "warning" : "error";
        String loc = d.line() > 0
            ? " line " + d.line() + ":" + d.column()
            : "";
        return "[" + tag + "]" + loc + " " + d.message() + "\n";
    }

    /**
     * Shows a modal error dialog parented to {@link #stage}.
     *
     * @param message message to display.
     */
    private void showError(String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Returns the loaded backend, exposed for tests.
     *
     * @return the {@link MarsBackend} the window owns.
     */
    public MarsBackend backend()
    {
        return backend;
    }

    /**
     * Gives the console pane all warnings/errors. Exposed for tests.
     *
     * @param diagnostics list to dump.
     */
    public void dumpDiagnostics(List<Diagnostic> diagnostics)
    {
        for (Diagnostic d : diagnostics)
        {
            consolePane.appendOutput(formatDiagnostic(d));
        }
    }
}
