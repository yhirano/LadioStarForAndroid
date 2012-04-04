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

package com.uraroji.garage.android.ladiostar.util.test;

import com.uraroji.garage.android.ladiostar.util.ShortRingBuffer;

import junit.framework.TestCase;

import java.nio.BufferOverflowException;

public class ShortRingBufferTest extends TestCase {
    public void testBasic() {
        ShortRingBuffer buf = new ShortRingBuffer(5);

        assertEquals(buf.size(), 5);
        assertEquals(buf.putAvailable(), 4);
        assertEquals(buf.getAvailable(), 0);

        short[] rbuf = new short[5];

        // 1データ書き込み、読み込みテスト
        buf.put(new short[] { 0 }, 0, 1);
        assertEquals(buf.putAvailable(), 3);
        assertEquals(buf.getAvailable(), 1);
        buf.get(rbuf, 0, 1);
        assertEquals(rbuf[0], 0);
        assertEquals(buf.putAvailable(), 4);
        assertEquals(buf.getAvailable(), 0);

        // 4データ書き込み、読み込みテスト
        buf.put(new short[] { 0, 1, 2, 3 }, 0, 4);
        assertEquals(buf.putAvailable(), 0);
        assertEquals(buf.getAvailable(), 4);
        buf.get(rbuf, 0, 3);
        for (int i = 0; i < 3; ++i) {
            assertEquals(rbuf[i], i);
        }
        assertEquals(buf.putAvailable(), 3);
        assertEquals(buf.getAvailable(), 1);
        buf.get(rbuf, 0, 1);
        assertEquals(rbuf[0], 3);
        assertEquals(buf.putAvailable(), 4);
        assertEquals(buf.getAvailable(), 0);
    }

    public void testBufferOverflowException() {
        ShortRingBuffer buf = new ShortRingBuffer(5);

        buf.put(new short[] { 0 }, 0, 1);
        buf.put(new short[] { 1 }, 0, 1);
        buf.put(new short[] { 2 }, 0, 1);
        buf.put(new short[] { 3 }, 0, 1);
        try {
            buf.put(new short[] { 3 }, 0, 1);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof BufferOverflowException);
        }
    }

    public void testOverwriteBufferOverflowException() {
        ShortRingBuffer buf = new ShortRingBuffer(5);

        buf.put(new short[] { 0 }, 0, 1, true);
        buf.put(new short[] { 0, 1 }, 0, 2, true);
        buf.put(new short[] { 0, 1, 2 }, 0, 3, true);
        buf.put(new short[] { 0, 1, 2, 3 }, 0, 4, true);
        try {
            buf.put(new short[] { 0, 1, 2, 3, 4 }, 0, 5, true);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof BufferOverflowException);
        }
    }

    public void testOverwrite() {
        ShortRingBuffer buf = new ShortRingBuffer(5);
        short[] rbuf = new short[5];

        buf.put(new short[] { 0, 1, 2 }, 0, 3);
        assertEquals(buf.putAvailable(), 1);
        assertEquals(buf.getAvailable(), 3);
        buf.put(new short[] { 3, 4, 5 }, 0, 3, true);
        assertEquals(buf.putAvailable(), 0);
        assertEquals(buf.getAvailable(), 4);
        buf.get(rbuf, 0, 4);
        short[] correct = new short[] { 2, 3, 4, 5 };
        for (int i = 0; i < 4; ++i) {
            assertEquals(rbuf[i], correct[i]);
        }

        buf.put(new short[] { 0, 1, 2 }, 0, 3);
        assertEquals(buf.putAvailable(), 1);
        assertEquals(buf.getAvailable(), 3);
        buf.put(new short[] { 3, 4, 5 }, 0, 3, true);
        assertEquals(buf.putAvailable(), 0);
        assertEquals(buf.getAvailable(), 4);
        buf.put(new short[] { 6, 7 }, 0, 2, true);
        assertEquals(buf.putAvailable(), 0);
        assertEquals(buf.getAvailable(), 4);
        buf.get(rbuf, 0, 4);
        correct = new short[] { 4, 5, 6, 7 };
        for (int i = 0; i < 4; ++i) {
            assertEquals(rbuf[i], correct[i]);
        }
    }

    public void testClear() {
        ShortRingBuffer buf = new ShortRingBuffer(5);

        buf.put(new short[] { 0 }, 0, 1);
        buf.put(new short[] { 1 }, 0, 1);
        buf.put(new short[] { 2 }, 0, 1);
        buf.put(new short[] { 3 }, 0, 1);

        buf.clear();
        assertEquals(buf.size(), 5);
        assertEquals(buf.putAvailable(), 4);
        assertEquals(buf.getAvailable(), 0);
    }
}
