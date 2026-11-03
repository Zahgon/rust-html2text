// Copyright (c) David Tolnay; ported in jugglerchris/rust-html2text. serde_json is licensed under the Apache
// License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <http://opensource.org/licenses/MIT>, at your option.
//
// Java port of serde_json 1.0.154's Number (number.rs, without the arbitrary_precision feature).

package io.github.jugglerchris.html2text.json;

import java.util.OptionalLong;

/**
 * A JSON number as serde_json holds it: a non-negative integer (u64), a negative integer (i64) or a finite float
 * (f64). Equality is serde_json's: the same kind and value ({@code 0.0} equals {@code -0.0}).
 */
public final class JsonNumber {
    /** serde_json's representation of a number. */
    public enum Kind {
        /** An integer from 0 to 2^64-1 ({@code N::PosInt}). */
        POS_INT,
        /** An integer below zero ({@code N::NegInt}). */
        NEG_INT,
        /** A finite float ({@code N::Float}). */
        FLOAT
    }

    private final Kind kind;
    /** The u64 or i64 value, or the bits of the f64. */
    private final long bits;

    private JsonNumber(Kind kind, long bits) {
        this.kind = kind;
        this.bits = bits;
    }

    /** The number for the unsigned 64-bit integer {@code value} ({@code Number::from(u64)}). */
    public static JsonNumber ofU64(long value) {
        return new JsonNumber(Kind.POS_INT, value);
    }

    /** The number for the signed 64-bit integer {@code value} ({@code Number::from(i64)}). */
    public static JsonNumber ofI64(long value) {
        return new JsonNumber(value < 0 ? Kind.NEG_INT : Kind.POS_INT, value);
    }

    /**
     * The number for the float {@code value} ({@code Number::from_f64}).
     *
     * @throws IllegalArgumentException if {@code value} is NaN or infinite
     */
    public static JsonNumber ofF64(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("not a JSON number: " + value);
        }
        return new JsonNumber(Kind.FLOAT, Double.doubleToRawLongBits(value));
    }

    /** How serde_json holds this number. */
    public Kind kind() {
        return kind;
    }

    /** {@code as_u64}: the value of a non-negative integer, as an unsigned long; empty for other numbers. */
    public OptionalLong asU64() {
        return kind == Kind.POS_INT ? OptionalLong.of(bits) : OptionalLong.empty();
    }

    /** {@code as_i64}: the value of an integer from -2^63 to 2^63-1; empty for other numbers. */
    public OptionalLong asI64() {
        return kind == Kind.NEG_INT || kind == Kind.POS_INT && bits >= 0 ? OptionalLong.of(bits) : OptionalLong.empty();
    }

    /** {@code as_f64}: the value as the nearest double (always present in this configuration of serde_json). */
    public double asF64() {
        return switch (kind) {
            case POS_INT -> u64ToF64(bits);
            case NEG_INT -> bits;
            case FLOAT -> Double.longBitsToDouble(bits);
        };
    }

    /** Converts an unsigned 64-bit integer to the nearest double, ties to even (Rust's {@code u64 as f64}). */
    static double u64ToF64(long value) {
        return value >= 0 ? value : (double) (value >>> 1 | value & 1) * 2.0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNumber n && kind == n.kind
                && (kind == Kind.FLOAT ? asF64() == n.asF64() : bits == n.bits);
    }

    @Override
    public int hashCode() {
        return kind == Kind.FLOAT && asF64() == 0 ? 0 : Long.hashCode(bits);
    }

    /** serde_json's Display of the number: {@code 18446744073709551615}, {@code -5}, {@code 1.5}, {@code 1e+20}. */
    @Override
    public String toString() {
        return switch (kind) {
            case POS_INT -> Long.toUnsignedString(bits);
            case NEG_INT -> Long.toString(bits);
            case FLOAT -> Zmij.format(asF64());
        };
    }
}
