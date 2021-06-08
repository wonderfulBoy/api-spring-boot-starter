package com.sun.tools.javac.util;

import java.util.Arrays;

public class Bits {


    private final static int wordlen = 32;
    private final static int wordshift = 5;
    private final static int wordmask = wordlen - 1;
    private static final int[] unassignedBits = new int[0];
    public int[] bits = null;
    protected BitsState currentState;
    public Bits() {
        this(false);
    }

    public Bits(Bits someBits) {
        this(someBits.dup().bits, BitsState.getState(someBits.bits, false));
    }

    public Bits(boolean reset) {
        this(unassignedBits, BitsState.getState(unassignedBits, reset));
    }

    protected Bits(int[] bits, BitsState initState) {
        this.bits = bits;
        this.currentState = initState;
        switch (initState) {
            case UNKNOWN:
                this.bits = null;
                break;
            case NORMAL:
                Assert.check(bits != unassignedBits);
                break;
        }
    }

    private static int trailingZeroBits(int x) {
        Assert.check(wordlen == 32);
        if (x == 0) {
            return 32;
        }
        int n = 1;
        if ((x & 0xffff) == 0) {
            n += 16;
            x >>>= 16;
        }
        if ((x & 0x00ff) == 0) {
            n += 8;
            x >>>= 8;
        }
        if ((x & 0x000f) == 0) {
            n += 4;
            x >>>= 4;
        }
        if ((x & 0x0003) == 0) {
            n += 2;
            x >>>= 2;
        }
        return n - (x & 1);
    }

    public static void main(String[] args) {
        java.util.Random r = new java.util.Random();
        Bits bits = new Bits();
        for (int i = 0; i < 125; i++) {
            int k;
            do {
                k = r.nextInt(250);
            } while (bits.isMember(k));
            System.out.println("adding " + k);
            bits.incl(k);
        }
        int count = 0;
        for (int i = bits.nextBit(0); i >= 0; i = bits.nextBit(i + 1)) {
            System.out.println("found " + i);
            count++;
        }
        if (count != 125) {
            throw new Error();
        }
    }

    protected void sizeTo(int len) {
        if (bits.length < len) {
            bits = Arrays.copyOf(bits, len);
        }
    }

    public void clear() {
        Assert.check(currentState != BitsState.UNKNOWN);
        for (int i = 0; i < bits.length; i++) {
            bits[i] = 0;
        }
        currentState = BitsState.NORMAL;
    }

    public void reset() {
        internalReset();
    }

    protected void internalReset() {
        bits = null;
        currentState = BitsState.UNKNOWN;
    }

    public boolean isReset() {
        return currentState == BitsState.UNKNOWN;
    }

    public Bits assign(Bits someBits) {
        bits = someBits.dup().bits;
        currentState = BitsState.NORMAL;
        return this;
    }

    public Bits dup() {
        Assert.check(currentState != BitsState.UNKNOWN);
        Bits tmp = new Bits();
        tmp.bits = dupBits();
        currentState = BitsState.NORMAL;
        return tmp;
    }

    protected int[] dupBits() {
        int[] result;
        if (currentState != BitsState.NORMAL) {
            result = bits;
        } else {
            result = new int[bits.length];
            System.arraycopy(bits, 0, result, 0, bits.length);
        }
        return result;
    }

    public void incl(int x) {
        Assert.check(currentState != BitsState.UNKNOWN);
        Assert.check(x >= 0, "Value of x " + x);
        sizeTo((x >>> wordshift) + 1);
        bits[x >>> wordshift] = bits[x >>> wordshift] |
                (1 << (x & wordmask));
        currentState = BitsState.NORMAL;
    }

    public void inclRange(int start, int limit) {
        Assert.check(currentState != BitsState.UNKNOWN);
        sizeTo((limit >>> wordshift) + 1);
        for (int x = start; x < limit; x++) {
            bits[x >>> wordshift] = bits[x >>> wordshift] |
                    (1 << (x & wordmask));
        }
        currentState = BitsState.NORMAL;
    }

    public void excludeFrom(int start) {
        Assert.check(currentState != BitsState.UNKNOWN);
        Bits temp = new Bits();
        temp.sizeTo(bits.length);
        temp.inclRange(0, start);
        internalAndSet(temp);
        currentState = BitsState.NORMAL;
    }

    public void excl(int x) {
        Assert.check(currentState != BitsState.UNKNOWN);
        Assert.check(x >= 0);
        sizeTo((x >>> wordshift) + 1);
        bits[x >>> wordshift] = bits[x >>> wordshift] &
                ~(1 << (x & wordmask));
        currentState = BitsState.NORMAL;
    }

    public boolean isMember(int x) {
        Assert.check(currentState != BitsState.UNKNOWN);
        return
                0 <= x && x < (bits.length << wordshift) &&
                        (bits[x >>> wordshift] & (1 << (x & wordmask))) != 0;
    }

    public Bits andSet(Bits xs) {
        Assert.check(currentState != BitsState.UNKNOWN);
        internalAndSet(xs);
        currentState = BitsState.NORMAL;
        return this;
    }

    protected void internalAndSet(Bits xs) {
        Assert.check(currentState != BitsState.UNKNOWN);
        sizeTo(xs.bits.length);
        for (int i = 0; i < xs.bits.length; i++) {
            bits[i] = bits[i] & xs.bits[i];
        }
    }

    public Bits orSet(Bits xs) {
        Assert.check(currentState != BitsState.UNKNOWN);
        sizeTo(xs.bits.length);
        for (int i = 0; i < xs.bits.length; i++) {
            bits[i] = bits[i] | xs.bits[i];
        }
        currentState = BitsState.NORMAL;
        return this;
    }

    public Bits diffSet(Bits xs) {
        Assert.check(currentState != BitsState.UNKNOWN);
        for (int i = 0; i < bits.length; i++) {
            if (i < xs.bits.length) {
                bits[i] = bits[i] & ~xs.bits[i];
            }
        }
        currentState = BitsState.NORMAL;
        return this;
    }

    public Bits xorSet(Bits xs) {
        Assert.check(currentState != BitsState.UNKNOWN);
        sizeTo(xs.bits.length);
        for (int i = 0; i < xs.bits.length; i++) {
            bits[i] = bits[i] ^ xs.bits[i];
        }
        currentState = BitsState.NORMAL;
        return this;
    }

    public int nextBit(int x) {
        Assert.check(currentState != BitsState.UNKNOWN);
        int windex = x >>> wordshift;
        if (windex >= bits.length) {
            return -1;
        }
        int word = bits[windex] & ~((1 << (x & wordmask)) - 1);
        while (true) {
            if (word != 0) {
                return (windex << wordshift) + trailingZeroBits(word);
            }
            windex++;
            if (windex >= bits.length) {
                return -1;
            }
            word = bits[windex];
        }
    }

    @Override
    public String toString() {
        if (bits != null && bits.length > 0) {
            char[] digits = new char[bits.length * wordlen];
            for (int i = 0; i < bits.length * wordlen; i++) {
                digits[i] = isMember(i) ? '1' : '0';
            }
            return new String(digits);
        } else {
            return "[]";
        }
    }

    protected enum BitsState {

        UNKNOWN,

        UNINIT,

        NORMAL;

        static BitsState getState(int[] someBits, boolean reset) {
            if (reset) {
                return UNKNOWN;
            } else {
                if (someBits != unassignedBits) {
                    return NORMAL;
                } else {
                    return UNINIT;
                }
            }
        }
    }
}
