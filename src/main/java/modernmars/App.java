package modernmars;

import javafx.application.Application;
import javafx.stage.Stage;

import modernmars.ui.MainWindow;

/**
 * JavaFX entry point for Modern MARS.
 *
 * <p>Boots the JavaFX runtime, builds the {@link MainWindow}, and shows
 * the primary stage. All real work happens inside {@link MainWindow}.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class App extends Application
{
    /**
     * Standard JavaFX stage callback. Builds and shows the main window.
     *
     * @param primaryStage the primary stage supplied by JavaFX.
     */
    @Override
    public void start(Stage primaryStage)
    {
        new MainWindow().show(primaryStage);
    }

    /**
     * Standard Java {@code main} - delegates to {@link Application#launch}.
     *
     * @param args ignored command-line arguments.
     */
    public static void main(String[] args)
    {
        launch(args);
    }
}
