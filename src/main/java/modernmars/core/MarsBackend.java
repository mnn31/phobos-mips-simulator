package modernmars.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mars.ProgramStatement;

import mars.ErrorList;
import mars.ErrorMessage;
import mars.Globals;
import mars.MIPSprogram;
import mars.ProcessingException;
import mars.Settings;
import mars.mips.hardware.AddressErrorException;
import mars.mips.hardware.Memory;
import mars.mips.hardware.RegisterFile;
import mars.simulator.BackStepper;
import mars.util.FilenameFinder;

/**
 * Thin wrapper around the MARS assembler/simulator that exposes a tidy,
 * UI-friendly interface for assemble, step, and run.
 *
 * <p>MARS' public API is built around static singletons (notably
 * {@code Globals}, {@code RegisterFile}, and {@code Memory}). This class
 * is therefore a logical-singleton too - construct one instance per
 * application lifetime and reuse it.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class MarsBackend
{
    /** Big number used as the "run forever" cap when the user presses Run. */
    private static final int RUN_STEP_CAP = Integer.MAX_VALUE - 1;

    /** Currently loaded MIPS program (one source file at a time for v1). */
    private MIPSprogram program;

    /** Path of the currently assembled source, or {@code null} if none. */
    private String currentFile;

    /** Whether {@link #assemble(String)} has succeeded since last reset. */
    private boolean assembled;

    /** Map of text-segment address to source line number (1-based). */
    private final Map<Integer, Integer> addressToLine = new HashMap<>();

    /**
     * Initialises the MARS globals. Safe to call multiple times - subsequent
     * calls reset the simulator state but do not re-load configuration.
     */
    public MarsBackend()
    {
        Globals.initialize(false);
        // Sensible defaults for student labs.
        Settings s = Globals.getSettings();
        s.setBooleanSettingNonPersistent(Settings.DELAYED_BRANCHING_ENABLED, false);
        s.setBooleanSettingNonPersistent(Settings.SELF_MODIFYING_CODE_ENABLED, false);
    }

    /**
     * Returns whether the most recent assemble call succeeded.
     *
     * @return {@code true} iff there is a runnable program in memory.
     */
    public boolean isAssembled()
    {
        return assembled;
    }

    /**
     * Assembles a single MIPS source file, leaving its machine code loaded
     * into MARS' static {@link Memory} ready for simulation.
     *
     * @param sourcePath absolute path to a {@code .asm} file on disk.
     * @return the {@link AssembleResult} describing success/failure with
     *         per-line error and warning messages.
     */
    public AssembleResult assemble(String sourcePath)
    {
        assembled = false;
        currentFile = sourcePath;
        program = new MIPSprogram();
        // CRITICAL: MARS' SimThread references Globals.program on every
        // executed instruction (to record back-step entries). The
        // command-line entry-point intentionally leaves it null because
        // it doesn't want backstep logging; we DO want backstep here,
        // so we mirror what the GUI's RunAssembleAction does and point
        // the static field at our newly-constructed program. Without
        // this, every step throws NullPointerException inside MARS.
        Globals.program = program;
        try
        {
            ArrayList<String> files = FilenameFinder.getFilenameList(
                singletonStringList(sourcePath),
                FilenameFinder.MATCH_ALL_EXTENSIONS);
            ArrayList<MIPSprogram> programs =
                program.prepareFilesForAssembly(files, sourcePath, null);
            ErrorList warnings = program.assemble(
                programs,
                /*extendedAssemblerEnabled=*/ true,
                /*warningsAreErrors=*/ false);
            // Reset PC so a subsequent run/step starts at the program entry.
            RegisterFile.initializeProgramCounter(/*startAtMain=*/ false);
            // Build address -> source line map for current-line highlighting.
            buildAddressMap();
            assembled = true;
            return AssembleResult.ok(toMessages(warnings));
        }
        catch (ProcessingException pe)
        {
            return AssembleResult.fail(toMessages(pe.errors()));
        }
    }

    /**
     * Executes a single MIPS instruction at the current PC.
     *
     * @return outcome of the step (terminated, stopped, or still running).
     * @throws IllegalStateException if no program has been assembled yet.
     */
    public RunResult step()
    {
        requireAssembled();
        try
        {
            boolean done = program.simulateStepAtPC(null);
            if (done)
            {
                // Subsequent step/run calls on a finished program will
                // crash MARS - force the user (or auto-flow) through
                // assemble() again before another execution.
                assembled = false;
                return RunResult.terminated();
            }
            return RunResult.paused();
        }
        catch (ProcessingException pe)
        {
            assembled = false;
            return RunResult.error(toMessages(pe.errors()));
        }
    }

    /**
     * Runs the program from the current PC to termination (or until the
     * step cap is hit).
     *
     * @return outcome of the run, including any runtime error messages.
     * @throws IllegalStateException if no program has been assembled yet.
     */
    public RunResult run()
    {
        requireAssembled();
        try
        {
            boolean done = program.simulate(RUN_STEP_CAP);
            if (done)
            {
                assembled = false;
                return RunResult.terminated();
            }
            return RunResult.paused();
        }
        catch (ProcessingException pe)
        {
            assembled = false;
            return RunResult.error(toMessages(pe.errors()));
        }
    }

    /**
     * Steps backwards: undoes the most recent simulator step using
     * MARS' built-in {@link BackStepper}. Safe to call repeatedly until
     * the stepper is empty, in which case the call is a no-op.
     *
     * @return {@code true} if a step was actually undone.
     */
    public boolean backStep()
    {
        if (program == null)
        {
            return false;
        }
        BackStepper bs = program.getBackStepper();
        if (bs == null || bs.empty())
        {
            return false;
        }
        bs.backStep();
        // Re-arm the assembled flag so subsequent step/run calls work.
        assembled = true;
        return true;
    }

    /**
     * @return {@code true} if there is at least one step we could undo.
     */
    public boolean canBackStep()
    {
        if (program == null)
        {
            return false;
        }
        BackStepper bs = program.getBackStepper();
        return bs != null && !bs.empty();
    }

    /**
     * Reads the current value of a general-purpose register by index.
     *
     * @param regNumber register number, 0 .. 31.
     * @return the 32-bit value held in that register.
     */
    public int getRegister(int regNumber)
    {
        return RegisterFile.getValue(regNumber);
    }

    /**
     * Returns the current program counter.
     *
     * @return the current PC value.
     */
    public int getProgramCounter()
    {
        return RegisterFile.getProgramCounter();
    }

    /**
     * Reads a 32-bit word from MARS' simulated memory.
     *
     * @param address byte address - must be 4-byte aligned.
     * @return the word at that address, or zero if the address is invalid.
     */
    public int readWord(int address)
    {
        try
        {
            return Memory.getInstance().getWord(address);
        }
        catch (AddressErrorException aee)
        {
            return 0;
        }
    }

    /**
     * Returns the path of the most recently assembled source file.
     *
     * @return absolute path of the loaded source, or {@code null} if
     *         nothing has been assembled.
     */
    public String currentFile()
    {
        return currentFile;
    }

    /**
     * Returns the 1-based source line that the program counter is
     * currently pointing at, or -1 if the PC does not correspond to a
     * known instruction (e.g. before assemble, or after the program
     * has terminated).
     *
     * @return source line number, or -1 when no mapping is available.
     */
    public int currentSourceLine()
    {
        Integer line = addressToLine.get(getProgramCounter());
        return line == null ? -1 : line;
    }

    /**
     * Walks the assembled program statements and rebuilds the
     * address-to-source-line map used by {@link #currentSourceLine()}.
     */
    private void buildAddressMap()
    {
        addressToLine.clear();
        if (program == null)
        {
            return;
        }
        @SuppressWarnings("unchecked")
        ArrayList<ProgramStatement> stmts = program.getMachineList();
        if (stmts == null)
        {
            return;
        }
        for (ProgramStatement s : stmts)
        {
            if (s == null)
            {
                continue;
            }
            int line = s.getSourceLine();
            if (line > 0)
            {
                addressToLine.put(s.getAddress(), line);
            }
        }
    }

    /**
     * Throws {@link IllegalStateException} if no program is loaded.
     */
    private void requireAssembled()
    {
        if (!assembled)
        {
            throw new IllegalStateException(
                "Program has not been successfully assembled.");
        }
    }

    /**
     * Adapts a single string into a typed {@link ArrayList} so it can
     * be passed to {@code FilenameFinder.getFilenameList}.
     *
     * @param value the single string to wrap.
     * @return a one-element ArrayList holding {@code value}.
     */
    private static ArrayList<String> singletonStringList(String value)
    {
        ArrayList<String> list = new ArrayList<>(1);
        list.add(value);
        return list;
    }

    /**
     * Converts a MARS {@link ErrorList} into a list of UI-facing
     * {@link Diagnostic} records. Tolerates a {@code null} input.
     *
     * @param errors error list returned by MARS, or {@code null}.
     * @return diagnostics in the same order MARS reported them.
     */
    private static List<Diagnostic> toMessages(ErrorList errors)
    {
        if (errors == null)
        {
            return Collections.emptyList();
        }
        ArrayList<Diagnostic> out = new ArrayList<>();
        @SuppressWarnings("unchecked")
        ArrayList<ErrorMessage> raw = errors.getErrorMessages();
        for (ErrorMessage m : raw)
        {
            out.add(new Diagnostic(
                m.getFilename(), m.getLine(), m.getPosition(),
                m.getMessage(), m.isWarning()));
        }
        return out;
    }

    /**
     * Diagnostic record - a single error or warning from the assembler
     * or simulator.
     *
     * @param file filename the diagnostic refers to (may be empty).
     * @param line 1-based line number.
     * @param column 1-based column position.
     * @param message human-readable message text.
     * @param warning {@code true} for warnings, {@code false} for errors.
     */
    public record Diagnostic(String file, int line, int column,
                             String message, boolean warning)
    {
    }

    /**
     * Outcome of an {@link MarsBackend#assemble(String)} call.
     *
     * @param success whether assembly produced a runnable program.
     * @param messages all diagnostics (errors and warnings).
     */
    public record AssembleResult(boolean success, List<Diagnostic> messages)
    {
        /**
         * Successful result wrapping any non-fatal warnings.
         *
         * @param msgs warnings emitted by the assembler.
         * @return success result holding {@code msgs}.
         */
        static AssembleResult ok(List<Diagnostic> msgs)
        {
            return new AssembleResult(true, msgs);
        }

        /**
         * Failed result wrapping the assembler errors.
         *
         * @param msgs error diagnostics.
         * @return failure result holding {@code msgs}.
         */
        static AssembleResult fail(List<Diagnostic> msgs)
        {
            return new AssembleResult(false, msgs);
        }
    }

    /**
     * Outcome of {@link MarsBackend#step()} or {@link MarsBackend#run()}.
     */
    public static final class RunResult
    {
        /** Distinct lifecycle states of a simulation step/run. */
        public enum Kind
        {
            /** Program reached an exit syscall or fell off the end. */
            TERMINATED,
            /** Simulator stopped without exiting (e.g. step boundary). */
            PAUSED,
            /** Runtime exception during execution. */
            ERROR
        }

        /** The kind of outcome - never {@code null}. */
        private final Kind kind;

        /** Diagnostics if {@link #kind} is {@link Kind#ERROR}. */
        private final List<Diagnostic> errors;

        /**
         * Builds a result with the given kind and (possibly empty) messages.
         *
         * @param kind outcome category.
         * @param errors error messages, empty unless {@code kind} is ERROR.
         */
        private RunResult(Kind kind, List<Diagnostic> errors)
        {
            this.kind = kind;
            this.errors = errors;
        }

        /**
         * Builds a result indicating the program ran to completion.
         *
         * @return a new {@code RunResult} of kind TERMINATED.
         */
        static RunResult terminated()
        {
            return new RunResult(Kind.TERMINATED, Collections.emptyList());
        }

        /**
         * Builds a result indicating execution paused but did not exit.
         *
         * @return a new {@code RunResult} of kind PAUSED.
         */
        static RunResult paused()
        {
            return new RunResult(Kind.PAUSED, Collections.emptyList());
        }

        /**
         * Builds a result describing a runtime simulation error.
         *
         * @param msgs runtime error diagnostics.
         * @return a new {@code RunResult} of kind ERROR.
         */
        static RunResult error(List<Diagnostic> msgs)
        {
            return new RunResult(Kind.ERROR, msgs);
        }

        /**
         * Returns the kind tag of this result.
         *
         * @return one of {@link Kind#TERMINATED}, {@link Kind#PAUSED},
         *         {@link Kind#ERROR}.
         */
        public Kind kind()
        {
            return kind;
        }

        /**
         * Returns any runtime error diagnostics. Always empty unless
         * {@link #kind()} is {@link Kind#ERROR}.
         *
         * @return list of diagnostics; never {@code null}.
         */
        public List<Diagnostic> errors()
        {
            return errors;
        }
    }
}
