// SPDX-License-Identifier: MIT
// Copyright (c) 2016 Chris Emerson (github.com/jugglerchris/rust-html2text)

package io.github.jugglerchris.html2text;

/**
 * Unsigned 64-bit arithmetic on {@code long}, the semantics of the original's {@code usize} sizes and widths (which
 * wrap on overflow, as a release build of the original does). Addition, subtraction and multiplication are plain
 * {@code long} operations; comparisons, minimum, maximum and division go through these helpers.
 */
public final class Usize {
    /** {@code usize::MAX}. */
    public static final long MAX = -1L;

    private Usize() {}

    /** {@code a < b}. */
    public static boolean lt(long a, long b) {
        return Long.compareUnsigned(a, b) < 0;
    }

    /** {@code a <= b}. */
    public static boolean le(long a, long b) {
        return Long.compareUnsigned(a, b) <= 0;
    }

    /** {@code a > b}. */
    public static boolean gt(long a, long b) {
        return Long.compareUnsigned(a, b) > 0;
    }

    /** {@code a >= b}. */
    public static boolean ge(long a, long b) {
        return Long.compareUnsigned(a, b) >= 0;
    }

    /** {@code a.min(b)}. */
    public static long min(long a, long b) {
        return le(a, b) ? a : b;
    }

    /** {@code a.max(b)}. */
    public static long max(long a, long b) {
        return ge(a, b) ? a : b;
    }

    /** {@code a / b}. */
    public static long div(long a, long b) {
        return Long.divideUnsigned(a, b);
    }

    /** {@code a.saturating_sub(b)}. */
    public static long satSub(long a, long b) {
        return lt(a, b) ? 0 : a - b;
    }

    /** The value as a Java size, for lengths of lists and strings (which never reach 2^31 here). */
    public static int toInt(long a) {
        if (a < 0 || a > Integer.MAX_VALUE) {
            throw new RustPanic("capacity overflow");
        }
        return (int) a;
    }
}
