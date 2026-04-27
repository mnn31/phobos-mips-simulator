package modernmars.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

/**
 * Bridge between MARS' headless I/O (which uses {@code System.in} and
 * {@code System.out}) and the JavaFX console pane.
 *
 * <p>{@code mars.util.SystemIO} reads syscall input from {@code System.in}
 * and writes syscall output to {@code System.out} when MARS runs without
 * its own GUI. To capture that output and feed user input from the
 * JavaFX console we install a custom {@link PrintStream} on
 * {@code System.out}/{@code System.err} and a piped
 * {@link InputStream} on {@code System.in}.</p>
 *
 * <p>The redirection is installed for the lifetime of a single simulation
 * run and torn down afterwards via {@link #uninstall()} so the IDE-style
 * UI does not permanently swallow stdout.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class ConsoleIO
{
    /** Sink that receives every chunk MARS writes. */
    private final Consumer<String> outputSink;

    /** Lines the user has typed but the program has not yet read. */
    private final LinkedBlockingDeque<String> pendingInput;

    /** Streams written to {@code System.in} so MARS can read user input. */
    private PipedOutputStream stdinPipe;
    private PipedInputStream stdinReader;

    /** Original streams, restored on {@link #uninstall()}. */
    private PrintStream savedOut;
    private PrintStream savedErr;
    private InputStream savedIn;

    /**
     * Builds a console bridge that pushes every captured chunk of program
     * output to {@code outputSink}.
     *
     * @param outputSink consumer invoked on every write to {@code System.out}
     *                   or {@code System.err}; must be thread-safe (the
     *                   simulator runs on a worker thread).
     */
    public ConsoleIO(Consumer<String> outputSink)
    {
        this.outputSink = outputSink;
        this.pendingInput = new LinkedBlockingDeque<>();
    }

    /**
     * Installs the redirection. Subsequent reads from {@code System.in}
     * will block until {@link #supplyLine(String)} is called.
     */
    public void install()
    {
        savedOut = System.out;
        savedErr = System.err;
        savedIn = System.in;
        PrintStream sink = new PrintStream(new SinkStream(outputSink), true);
        System.setOut(sink);
        System.setErr(sink);
        try
        {
            stdinPipe = new PipedOutputStream();
            stdinReader = new PipedInputStream(stdinPipe, 1 << 14);
            System.setIn(stdinReader);
        }
        catch (IOException ioe)
        {
            // Pipe setup failed - fall back to an empty stream so the
            // simulator does not deadlock on read().
            System.setIn(new ByteArrayInputStream(new byte[0]));
        }
    }

    /**
     * Restores the original {@code System.in/out/err} streams.
     */
    public void uninstall()
    {
        if (savedOut != null)
        {
            System.setOut(savedOut);
        }
        if (savedErr != null)
        {
            System.setErr(savedErr);
        }
        if (savedIn != null)
        {
            System.setIn(savedIn);
        }
        try
        {
            if (stdinPipe != null)
            {
                stdinPipe.close();
            }
        }
        catch (IOException ignored)
        {
        }
    }

    /**
     * Hands a line of text to the program reading {@code System.in}.
     *
     * @param line text the user typed in the console; a newline is
     *             appended automatically so {@code BufferedReader.readLine}
     *             unblocks.
     */
    public void supplyLine(String line)
    {
        pendingInput.offer(line);
        try
        {
            if (stdinPipe != null)
            {
                stdinPipe.write((line + "\n").getBytes());
                stdinPipe.flush();
            }
        }
        catch (IOException ignored)
        {
            // Pipe closed - input is silently dropped.
        }
    }

    /**
     * OutputStream that forwards every byte chunk to a string consumer.
     */
    private static final class SinkStream extends OutputStream
    {
        /** Where the bytes go after being decoded as a String. */
        private final Consumer<String> sink;

        /**
         * Builds a sink stream forwarding to {@code sink}.
         *
         * @param sink target consumer; called once per write batch.
         */
        SinkStream(Consumer<String> sink)
        {
            this.sink = sink;
        }

        @Override
        public void write(int b)
        {
            sink.accept(String.valueOf((char) b));
        }

        @Override
        public void write(byte[] buf, int off, int len)
        {
            sink.accept(new String(buf, off, len));
        }
    }
}
