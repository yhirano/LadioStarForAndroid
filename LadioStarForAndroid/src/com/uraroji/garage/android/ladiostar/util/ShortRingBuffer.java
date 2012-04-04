/* 
 * Copyright (c) 2011 Y.Hirano
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.uraroji.garage.android.ladiostar.util;

import java.nio.BufferOverflowException;

/**
 * Ring buffer class.
 */
public final class ShortRingBuffer {
    private short[] buffer = null;

    private int size = 0;

    private int head = 0;

    private int tail = 0;

    /**
     * Constructor.
     * 
     * @param size Size of ring buffer. (NOT bytes.)
     */
    public ShortRingBuffer(int size) {
        this.size = size;
        buffer = new short[size];
    }

    /**
     * Return size of ring buffer.
     * 
     * @return Size of ring buffer.
     */
    public int size() {
        return buffer.length;
    }

    /**
     * Return size of available for writing.
     * 
     * @return Size of available for writing
     */
    public int putAvailable() {
        if (tail == head) {
            return size - 1;
        }
        if (tail < head) {
            return head - tail - 1;
        }
        return size - (tail - head) - 1;
    }

    /**
     * Return size of available for reading.
     * 
     * @return Size of available for reading
     */
    public int getAvailable() {
        if (tail == head) {
            return 0;
        }
        if (tail < head) {
            return size - (head - tail);
        }
        return tail - head;
    }

    /**
     * Write data to ring buffer.
     * 
     * @param data Write data
     * @param offset
     * @param len
     */
    public void put(short[] data, int offset, int len) {
        put(data, offset, len, false);
    }

    /**
     * Write data to ring buffer.
     * 
     * @param data Write data
     * @param offset
     * @param len
     */
    public void put(short[] data, int offset, int len, boolean overwrite) {
        if (len <= 0) {
            return;
        }

        if (overwrite == false) {
            if (putAvailable() < len) {
                throw new BufferOverflowException();
            }
        } else {
            if (size - 1 < len) {
                throw new BufferOverflowException();
            }
        }

        if (overwrite == true)
        {
            int removeLen = len - putAvailable();
            if (removeLen > 0)
            {
                remove(removeLen);
            }
        }

        if (tail >= head) {
            final int l = Math.min(len, size - tail);
            System.arraycopy(data, offset, buffer, tail, l);
            tail += l;
            if (tail >= size) {
                tail = 0;
            }
            if (len > l) {
                put(data, offset + l, len - l);
            }
        } else {
            final int l = Math.min(len, head - tail - 1);
            System.arraycopy(data, offset, buffer, tail, l);
            tail += l;
            if (tail >= size) {
                tail = 0;
            }
        }
    }

    /**
     * Read data from ring buffer.
     * 
     * @param data Put data here
     * @param offset
     * @param len
     * @return Read size. Error -1.
     */
    public int get(short[] data, int offset, int len) {
        if (len <= 0) {
            return 0;
        }

        int getLength = 0;

        if (getAvailable() <= 0) {
            return -1;
        }
        len = Math.min(len, getAvailable());

        if (head < tail) {
            final int l = Math.min(len, tail - head);
            System.arraycopy(buffer, head, data, offset, l);
            head += l;
            if (head >= size) {
                head = 0;
            }
            getLength = l;
        } else {
            final int l = Math.min(len, size - head);
            System.arraycopy(buffer, head, data, offset, l);
            head += l;
            if (head >= size) {
                head = 0;
            }
            getLength = l;
            if (len > l) {
                getLength += get(data, offset + l, len - l);
            }
        }

        return getLength;
    }

    /**
     * Remove data from ring buffer.
     * 
     * @param len
     */
    private void remove(int len) {
        if (len <= 0) {
            return;
        }

        if (getAvailable() <= len) {
            clear();
            return;
        }

        len = Math.min(len, getAvailable());

        if (head < tail) {
            final int l = Math.min(len, tail - head);
            head += l;
            if (head >= size) {
                head = 0;
            }
        } else {
            final int l = Math.min(len, size - head);
            head += l;
            if (head >= size) {
                head = 0;
            }
            if (len > l) {
                remove(len - l);
            }
        }
    }

    /**
     * Clear ring buffer.
     */
    public void clear() {
        head = 0;
        tail = 0;
    }
}
