package com.github.api.sun.tools.javac.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ByteBuffer {

    public byte[] elems;

    public int length;

    public ByteBuffer() {
        this(64);
    }

    public ByteBuffer(int initialSize) {
        elems = new byte[initialSize];
        length = 0;
    }

    public void appendByte(int b) {
        elems = ArrayUtils.ensureCapacity(elems, length);
        elems[length++] = (byte) b;
    }

    public void appendBytes(byte[] bs, int start, int len) {
        elems = ArrayUtils.ensureCapacity(elems, length + len);
        System.arraycopy(bs, start, elems, length, len);
        length += len;
    }

    public void appendBytes(byte[] bs) {
        appendBytes(bs, 0, bs.length);
    }

    public void appendChar(int x) {
        elems = ArrayUtils.ensureCapacity(elems, length + 1);
        elems[length] = (byte) ((x >> 8) & 0xFF);
        elems[length + 1] = (byte) ((x) & 0xFF);
        length = length + 2;
    }

    public void appendInt(int x) {
        elems = ArrayUtils.ensureCapacity(elems, length + 3);
        elems[length] = (byte) ((x >> 24) & 0xFF);
        elems[length + 1] = (byte) ((x >> 16) & 0xFF);
        elems[length + 2] = (byte) ((x >> 8) & 0xFF);
        elems[length + 3] = (byte) ((x) & 0xFF);
        length = length + 4;
    }

    public void appendLong(long x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeLong(x);
            appendBytes(buffer.toByteArray(), 0, 8);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    public void appendFloat(float x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(4);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeFloat(x);
            appendBytes(buffer.toByteArray(), 0, 4);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    public void appendDouble(double x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeDouble(x);
            appendBytes(buffer.toByteArray(), 0, 8);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    public void appendName(Name name) {
        appendBytes(name.getByteArray(), name.getByteOffset(), name.getByteLength());
    }

    public void reset() {
        length = 0;
    }

    public Name toName(Names names) {
        return names.fromUtf(elems, 0, length);
    }
}
