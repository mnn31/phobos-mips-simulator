package modernmars.util;

/**
 * Canonical names for the 32 MIPS general-purpose registers, mapped to
 * the indices used by {@code mars.mips.hardware.RegisterFile}.
 *
 * <p>The names follow standard MIPS calling conventions ($zero, $at, $v0 ..
 * $v1, $a0 .. $a3, $t0 .. $t9, $s0 .. $s7, $k0, $k1, $gp, $sp, $fp, $ra).
 * Index {@code i} of {@link #NAMES} is the conventional name for register
 * number {@code i}.</p>
 *
 * @author Manan
 * @version 2026-04-26
 */
public final class RegisterNames
{
    /** Conventional names for $0 .. $31, indexed by register number. */
    public static final String[] NAMES = {
        "$zero", "$at",   "$v0", "$v1",
        "$a0",   "$a1",   "$a2", "$a3",
        "$t0",   "$t1",   "$t2", "$t3",
        "$t4",   "$t5",   "$t6", "$t7",
        "$s0",   "$s1",   "$s2", "$s3",
        "$s4",   "$s5",   "$s6", "$s7",
        "$t8",   "$t9",   "$k0", "$k1",
        "$gp",   "$sp",   "$fp", "$ra"
    };

    /** Utility class - no instances. */
    private RegisterNames()
    {
    }
}
