package modernmars.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Curated vocabulary for the Modern MARS editor: every MIPS keyword the
 * autocomplete popup is willing to suggest, plus the syntax highlighter's
 * opcode set.
 *
 * <p>The lists below were assembled from the ATCS-Compilers MIPS Lab
 * handout ({@code MIPSLab_2023.pdf}) and the lecture handout
 * {@code mipsasmtable.jpg}. The intent is "what students will actually
 * type for the MIPS lab," not exhaustive MIPS-32 coverage.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class MipsVocabulary
{
    /**
     * One completion entry. Carries the trigger token (what the user
     * typed against), the literal replacement, and a short description
     * surfaced in the popup.
     *
     * @param trigger the token the user types to surface this entry.
     * @param replacement what gets inserted when the user accepts.
     * @param description short hint text shown to the right.
     * @param category coarse grouping label, used for the popup decor.
     */
    public record Entry(String trigger, String replacement,
                        String description, String category)
    {
    }

    /**
     * MIPS instructions and pseudo-instructions covered by the lab and
     * the assembly-table handout. Index/order is preserved for the
     * popup; alphabetical for the highlighter regex.
     */
    public static final List<Entry> INSTRUCTIONS = Collections.unmodifiableList(
        Arrays.asList(
            // Arithmetic
            new Entry("add",  "add",  "$d = $s + $t",                       "arith"),
            new Entry("addi", "addi", "$t = $s + imm",                      "arith"),
            new Entry("addiu","addiu","$t = $s + imm (no overflow trap)",    "arith"),
            new Entry("addu", "addu", "$d = $s + $t (unsigned)",            "arith"),
            new Entry("sub",  "sub",  "$d = $s - $t",                       "arith"),
            new Entry("subu", "subu", "$d = $s - $t (unsigned)",            "arith"),
            new Entry("mul",  "mul",  "$d = $s * $t (pseudo)",              "arith"),
            new Entry("mult", "mult", "Hi/Lo = $s * $t (signed)",           "arith"),
            new Entry("multu","multu","Hi/Lo = $s * $t (unsigned)",          "arith"),
            new Entry("div",  "div",  "Lo = $s / $t,  Hi = $s mod $t",      "arith"),
            new Entry("divu", "divu", "unsigned div / mod",                 "arith"),
            new Entry("mfhi", "mfhi", "$d = Hi (high word of mult/div)",    "arith"),
            new Entry("mflo", "mflo", "$d = Lo (low word of mult/div)",     "arith"),

            // Logical
            new Entry("and",  "and",  "$d = $s & $t",                       "logic"),
            new Entry("andi", "andi", "$t = $s & imm",                      "logic"),
            new Entry("or",   "or",   "$d = $s | $t",                       "logic"),
            new Entry("ori",  "ori",  "$t = $s | imm",                      "logic"),
            new Entry("xor",  "xor",  "$d = $s ^ $t",                       "logic"),
            new Entry("xori", "xori", "$t = $s ^ imm",                      "logic"),
            new Entry("nor",  "nor",  "$d = ~($s | $t)",                    "logic"),
            new Entry("sll",  "sll",  "$d = $t << shamt (logical)",         "logic"),
            new Entry("srl",  "srl",  "$d = $t >> shamt (logical)",         "logic"),
            new Entry("sra",  "sra",  "$d = $t >> shamt (arithmetic)",      "logic"),

            // Data transfer
            new Entry("lw",   "lw",   "$t = Mem[$s + offset]",              "mem"),
            new Entry("sw",   "sw",   "Mem[$s + offset] = $t",              "mem"),
            new Entry("lb",   "lb",   "$t = Mem[byte] (sign-extended)",     "mem"),
            new Entry("lbu",  "lbu",  "$t = Mem[byte] (zero-extended)",     "mem"),
            new Entry("sb",   "sb",   "Mem[byte] = low byte of $t",         "mem"),
            new Entry("lh",   "lh",   "$t = Mem[half] (sign-extended)",     "mem"),
            new Entry("sh",   "sh",   "Mem[half] = low half of $t",         "mem"),
            new Entry("lui",  "lui",  "$t = imm << 16",                     "mem"),
            new Entry("la",   "la",   "$t = address of label  (pseudo)",    "mem"),
            new Entry("li",   "li",   "$t = constant  (pseudo)",            "mem"),
            new Entry("move", "move", "$d = $s  (pseudo)",                  "mem"),

            // Branch
            new Entry("beq",  "beq",  "if ($s == $t) goto label",           "branch"),
            new Entry("bne",  "bne",  "if ($s != $t) goto label",           "branch"),
            new Entry("blt",  "blt",  "if ($s <  $t) goto label  (pseudo)", "branch"),
            new Entry("ble",  "ble",  "if ($s <= $t) goto label  (pseudo)", "branch"),
            new Entry("bgt",  "bgt",  "if ($s >  $t) goto label  (pseudo)", "branch"),
            new Entry("bge",  "bge",  "if ($s >= $t) goto label  (pseudo)", "branch"),
            new Entry("slt",  "slt",  "$d = ($s <  $t) ? 1 : 0",            "branch"),
            new Entry("slti", "slti", "$t = ($s <  imm) ? 1 : 0",           "branch"),
            new Entry("sltu", "sltu", "$d = ($s <  $t) ? 1 : 0  (unsigned)","branch"),

            // Jump
            new Entry("j",    "j",    "goto label",                         "jump"),
            new Entry("jal",  "jal",  "$ra = PC+4; goto label",             "jump"),
            new Entry("jr",   "jr",   "goto address in $s",                 "jump"),
            new Entry("jalr", "jalr", "$d = PC+4; goto address in $s",      "jump"),

            // Misc
            new Entry("syscall","syscall","System call - dispatched on $v0","sys"),
            new Entry("nop",  "nop",  "no operation",                       "sys"),
            new Entry("break","break","raise breakpoint exception",         "sys")
        ));

    /** Conventional MIPS register names with hint text. */
    public static final List<Entry> REGISTERS = Collections.unmodifiableList(
        Arrays.asList(
            new Entry("$zero", "$zero", "constant 0",                     "reg"),
            new Entry("$at",   "$at",   "assembler temporary",            "reg"),
            new Entry("$v0",   "$v0",   "syscall code / return value",    "reg"),
            new Entry("$v1",   "$v1",   "secondary return value",         "reg"),
            new Entry("$a0",   "$a0",   "argument 0",                     "reg"),
            new Entry("$a1",   "$a1",   "argument 1",                     "reg"),
            new Entry("$a2",   "$a2",   "argument 2",                     "reg"),
            new Entry("$a3",   "$a3",   "argument 3",                     "reg"),
            new Entry("$t0",   "$t0",   "temporary",                      "reg"),
            new Entry("$t1",   "$t1",   "temporary",                      "reg"),
            new Entry("$t2",   "$t2",   "temporary",                      "reg"),
            new Entry("$t3",   "$t3",   "temporary",                      "reg"),
            new Entry("$t4",   "$t4",   "temporary",                      "reg"),
            new Entry("$t5",   "$t5",   "temporary",                      "reg"),
            new Entry("$t6",   "$t6",   "temporary",                      "reg"),
            new Entry("$t7",   "$t7",   "temporary",                      "reg"),
            new Entry("$t8",   "$t8",   "temporary",                      "reg"),
            new Entry("$t9",   "$t9",   "temporary",                      "reg"),
            new Entry("$s0",   "$s0",   "saved",                          "reg"),
            new Entry("$s1",   "$s1",   "saved",                          "reg"),
            new Entry("$s2",   "$s2",   "saved",                          "reg"),
            new Entry("$s3",   "$s3",   "saved",                          "reg"),
            new Entry("$s4",   "$s4",   "saved",                          "reg"),
            new Entry("$s5",   "$s5",   "saved",                          "reg"),
            new Entry("$s6",   "$s6",   "saved",                          "reg"),
            new Entry("$s7",   "$s7",   "saved",                          "reg"),
            new Entry("$gp",   "$gp",   "global pointer",                 "reg"),
            new Entry("$sp",   "$sp",   "stack pointer",                  "reg"),
            new Entry("$fp",   "$fp",   "frame pointer",                  "reg"),
            new Entry("$ra",   "$ra",   "return address",                 "reg")
        ));

    /** Assembler directives that show up in the lab. */
    public static final List<Entry> DIRECTIVES = Collections.unmodifiableList(
        Arrays.asList(
            new Entry(".text",   ".text",   "code segment (instructions)",     "dir"),
            new Entry(".data",   ".data",   "static data segment",             "dir"),
            new Entry(".globl",  ".globl",  "make label visible globally",     "dir"),
            new Entry(".asciiz", ".asciiz", "null-terminated string literal",  "dir"),
            new Entry(".ascii",  ".ascii",  "string literal (no terminator)",  "dir"),
            new Entry(".word",   ".word",   "32-bit word",                     "dir"),
            new Entry(".half",   ".half",   "16-bit half-word",                "dir"),
            new Entry(".byte",   ".byte",   "8-bit byte",                      "dir"),
            new Entry(".space",  ".space",  "reserve N bytes",                 "dir"),
            new Entry(".align",  ".align",  "align to 2^n boundary",           "dir")
        ));

    /** Useful syscall snippets - direct lifts from the lab handout. */
    public static final List<Entry> SNIPPETS = Collections.unmodifiableList(
        Arrays.asList(
            new Entry("print_int",    "li $v0, 1\nsyscall",
                "print integer in $a0", "snippet"),
            new Entry("print_string", "li $v0, 4\nsyscall",
                "print string at $a0",  "snippet"),
            new Entry("read_int",     "li $v0, 5\nsyscall",
                "read int into $v0",    "snippet"),
            new Entry("read_string",  "li $v0, 8\nsyscall",
                "read string into buffer at $a0 (max length in $a1)", "snippet"),
            new Entry("alloc",        "li $v0, 9\nsyscall",
                "sbrk: allocate $a0 bytes; address returned in $v0", "snippet"),
            new Entry("exit",         "li $v0, 10\nsyscall",
                "terminate the program", "snippet")
        ));

    /**
     * Returns every triggerable entry concatenated in stable order:
     * instructions, registers, directives, then snippets.
     *
     * @return immutable concatenation of all four lists.
     */
    public static List<Entry> all()
    {
        ArrayList<Entry> out = new ArrayList<>(
            INSTRUCTIONS.size() + REGISTERS.size()
                + DIRECTIVES.size() + SNIPPETS.size());
        out.addAll(INSTRUCTIONS);
        out.addAll(REGISTERS);
        out.addAll(DIRECTIVES);
        out.addAll(SNIPPETS);
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns just the opcode names, for the syntax highlighter.
     *
     * @return immutable list of opcode strings.
     */
    public static List<String> opcodeNames()
    {
        ArrayList<String> out = new ArrayList<>(INSTRUCTIONS.size());
        for (Entry e : INSTRUCTIONS)
        {
            out.add(e.replacement());
        }
        return Collections.unmodifiableList(out);
    }

    /** Utility class - no instances. */
    private MipsVocabulary()
    {
    }
}
