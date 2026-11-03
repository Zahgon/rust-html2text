// Copyright (c) David Tolnay; ported in jugglerchris/rust-html2text. zmij is a port of Victor Zverovich's zmij
// (https://github.com/vitaut/zmij), and is licensed under the MIT license <http://opensource.org/licenses/MIT>.
//
// Java port of zmij 1.0.23's f64 formatting (Buffer::format_finite), which serde_json 1.0.154 uses to print floats:
// the shortest correctly rounded decimal, in fixed notation for decimal exponents -5..=15 and otherwise in
// exponential notation such as 1e+20 or 1.5e-7.

package io.github.jugglerchris.html2text.json;

import java.math.BigInteger;

final class Zmij {
    private static final long THRESHOLD = 1_000_000_000_000_000L;
    private static final int EXTRA_SHIFT = 6;
    private static final int DEC_EXP_MIN = -293;
    /** The 128-bit significands (hi, lo) of 10^(i + DEC_EXP_MIN), rounded down. */
    private static final long[] POW10 = new long[618 * 2];

    static {
        for (int i = 0; i < 618; i++) {
            int k = i + DEC_EXP_MIN;
            BigInteger p = BigInteger.TEN.pow(Math.abs(k));
            int bits = p.bitLength();
            BigInteger sig = k < 0 ? BigInteger.ONE.shiftLeft(bits + 127).divide(p)
                    : bits <= 128 ? p.shiftLeft(128 - bits) : p.shiftRight(bits - 128);
            POW10[2 * i] = sig.shiftRight(64).longValue();
            POW10[2 * i + 1] = sig.longValue();
        }
    }

    private Zmij() {}

    private record Decimal(long sig, int exp, int lastDigit, boolean hasLastDigit) {}

    /** Returns (x * y + c) >> 64 for unsigned 64-bit x, y and c. */
    private static long mulAddHi(long x, long y, long c) {
        long lo = x * y;
        return Math.unsignedMultiplyHigh(x, y) + (Long.compareUnsigned(lo + c, lo) < 0 ? 1 : 0);
    }

    /** Converts binSig * 2^(rawExp - 1075) to the shortest decimal; regular is false for powers of two. */
    private static Decimal toDecimal(long binSig, int rawExp, boolean regular) {
        int binExp = rawExp - 1075;
        int decExp = (binExp * 315_653 - (regular ? 0 : 131_072)) >> 20;
        int shift = (binExp + ((-(decExp + 1) * 217_707) >> 16) + 1 + EXTRA_SHIFT) & 0xff;
        int index = 2 * (-decExp - 1 - DEC_EXP_MIN);
        long hi = POW10[index];
        long x = binSig << shift;
        long lo = hi * x;
        long pLo = lo + Math.unsignedMultiplyHigh(POW10[index + 1], x);
        long pHi = Math.unsignedMultiplyHigh(hi, x) + (Long.compareUnsigned(pLo, lo) < 0 ? 1 : 0);
        long integral = pHi >>> EXTRA_SHIFT;
        long fractional = pHi << (64 - EXTRA_SHIFT) | pLo >>> EXTRA_SHIFT;
        if (!regular) {
            long halfUlp = hi >>> (EXTRA_SHIFT + 1 - shift);
            boolean roundUp = Long.compareUnsigned(halfUlp, -1L - fractional) > 0;
            boolean roundDown = Long.compareUnsigned(halfUlp >>> 1, fractional) > 0;
            int digit = (int) mulAddHi(fractional, 10, Long.MAX_VALUE);
            int low = (int) mulAddHi(fractional - (halfUlp >>> 1), 10, -1L);
            return new Decimal(integral + (roundUp ? 1 : 0), decExp, Math.max(digit, low), !(roundUp || roundDown));
        }
        long halfUlp = (hi >>> (EXTRA_SHIFT + 1 - shift)) + 1 - (binSig & 1);
        boolean roundUp = Long.compareUnsigned(fractional + halfUlp, fractional) < 0;
        boolean roundDown = Long.compareUnsigned(halfUlp, fractional) > 0;
        int digit = fractional == 1L << 62 ? 2 : (int) mulAddHi(fractional, 10, Long.MIN_VALUE + 6);
        return new Decimal(integral + (roundUp ? 1 : 0), decExp, digit, !(roundUp || roundDown));
    }

    /** serde_json's text for the finite double {@code value}. */
    static String format(double value) {
        long bits = Double.doubleToRawLongBits(value);
        int binExp = (int) (bits >>> 52) & 0x7ff;
        long binSig = bits & (1L << 52) - 1;
        StringBuilder out = new StringBuilder(24).append(bits < 0 ? "-" : "");
        Decimal dec;
        if (binExp == 0) {
            if (binSig == 0) {
                return out.append("0.0").toString();
            }
            dec = toDecimal(binSig, 1, true);
            long decSig = dec.sig * 10 + (dec.hasLastDigit ? dec.lastDigit : 0);
            int decExp = dec.exp;
            for (; decSig < THRESHOLD; decExp--) {
                decSig *= 10;
            }
            int last = (int) (decSig % 10);
            dec = new Decimal(decSig / 10, decExp, last, last != 0);
        } else {
            dec = toDecimal(binSig | 1L << 52, binExp, binSig != 0);
        }
        boolean extra = dec.sig >= THRESHOLD;
        int decExp = dec.exp + 15 + (extra ? 1 : 0);
        String sig = Long.toString(dec.sig);
        String sig16 = "0".repeat(16 - sig.length()) + sig;
        int numDigits = 16;
        while (numDigits > 0 && sig16.charAt(numDigits - 1) == '0') {
            numDigits--;
        }
        String all = sig16 + (char) ('0' + dec.lastDigit);
        int length = (extra ? 1 : 0) + (dec.hasLastDigit ? 17 : numDigits) - 1;
        String digits = extra ? all.substring(0, length) : all.substring(1, 1 + length);
        if (decExp >= -5 && decExp <= 15) {
            if (length - 1 <= decExp) {
                return out.append(digits).repeat('0', decExp + 1 - length).append(".0").toString();
            } else if (decExp >= 0) {
                return out.append(digits, 0, decExp + 1).append('.').append(digits, decExp + 1, length).toString();
            }
            return out.append("0.").repeat('0', -decExp - 1).append(digits).toString();
        }
        out.append(digits.charAt(0));
        if (length > 1) {
            out.append('.').append(digits, 1, length);
        }
        return out.append('e').append(decExp >= 0 ? '+' : '-').append(Math.abs(decExp)).toString();
    }
}
