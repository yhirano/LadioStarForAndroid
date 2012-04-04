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

import com.uraroji.garage.android.ladiostar.util.ByteRingBuffer;

import junit.framework.TestCase;

import java.nio.BufferOverflowException;
import java.util.Random;

public class ByteRingBufferTest extends TestCase {
    private static final Random rand = new Random();

    public void testBasic() {
        ByteRingBuffer buf = new ByteRingBuffer(5);

        assertEquals(buf.capacity(), 5);
        assertEquals(buf.size(), 6);
        assertEquals(buf.putAvailable(), 5);
        assertEquals(buf.getAvailable(), 0);

        byte[] wbuf = new byte[5];
        byte[] rbuf = new byte[5];

        // 1データ書き込み、読み込みテスト
        rand.nextBytes(wbuf);
        buf.put(wbuf, 0, 1);
        assertEquals(buf.putAvailable(), 4);
        assertEquals(buf.getAvailable(), 1);
        buf.get(rbuf, 0, 1);
        assertEquals(rbuf[0], wbuf[0]);
        assertEquals(buf.putAvailable(), 5);
        assertEquals(buf.getAvailable(), 0);

        // 4データ書き込み、読み込みテスト
        rand.nextBytes(wbuf);
        buf.put(wbuf, 0, 5);
        assertEquals(buf.putAvailable(), 0);
        assertEquals(buf.getAvailable(), 5);
        buf.get(rbuf, 0, 3);
        for (int i = 0; i < 3; ++i) {
            assertEquals(rbuf[i], wbuf[i]);
        }
        assertEquals(buf.putAvailable(), 3);
        assertEquals(buf.getAvailable(), 2);
        buf.get(rbuf, 0, 2);
        for (int i = 0; i < 2; ++i) {
            assertEquals(rbuf[i], wbuf[i + 3]);
        }
        assertEquals(buf.putAvailable(), 5);
        assertEquals(buf.getAvailable(), 0);
    }

    public void testBufferOverflowException() {
        ByteRingBuffer buf = new ByteRingBuffer(5);

        buf.put(new byte[] { 0 }, 0, 1);
        buf.put(new byte[] { 1 }, 0, 1);
        buf.put(new byte[] { 2 }, 0, 1);
        buf.put(new byte[] { 3 }, 0, 1);
        buf.put(new byte[] { 4 }, 0, 1);
        try {
            buf.put(new byte[] { 5 }, 0, 1);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof BufferOverflowException);
        }
    }

    public void testOverwriteBufferOverflowException() {
        ByteRingBuffer buf = new ByteRingBuffer(5);

        buf.put(new byte[] { 0 }, 0, 1, true);
        buf.put(new byte[] { 0, 1 }, 0, 2, true);
        buf.put(new byte[] { 0, 1, 2 }, 0, 3, true);
        buf.put(new byte[] { 0, 1, 2, 3 }, 0, 4, true);
        buf.put(new byte[] { 0, 1, 2, 3, 4 }, 0, 5, true);
        try {
            buf.put(new byte[] { 0, 1, 2, 3, 4, 5 }, 0, 6, true);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof BufferOverflowException);
        }
    }

    public void testOverwrite() {
        ByteRingBuffer buf = new ByteRingBuffer(5);
        byte[] rbuf = new byte[5];

        buf.put(new byte[] { 0, 1, 2, 3 }, 0, 4);
        assertEquals(buf.putAvailable(), 1);
        assertEquals(buf.getAvailable(), 4);
        buf.put(new byte[] { 4, 5, 6 }, 0, 3, true);
        assertEquals(buf.putAvailable(), 0);
        assertEquals(buf.getAvailable(), 5);
        buf.get(rbuf, 0, 5);
        byte[] correct = new byte[] { 2, 3, 4, 5, 6 };
        for (int i = 0; i < 5; ++i) {
            assertEquals(rbuf[i], correct[i]);
        }

        buf.put(new byte[] { 0, 1, 2, 3 }, 0, 4);
        assertEquals(buf.putAvailable(), 1);
        assertEquals(buf.getAvailable(), 4);
        buf.put(new byte[] { 4, 5, 6 }, 0, 3, true);
        assertEquals(buf.putAvailable(), 0);
        assertEquals(buf.getAvailable(), 5);
        buf.put(new byte[] { 7, 8 }, 0, 2, true);
        assertEquals(buf.putAvailable(), 0);
        assertEquals(buf.getAvailable(), 5);
        buf.get(rbuf, 0, 5);
        correct = new byte[] { 4, 5, 6, 7, 8 };
        for (int i = 0; i < 5; ++i) {
            assertEquals(rbuf[i], correct[i]);
        }
    }

    public void testClear() {
        ByteRingBuffer buf = new ByteRingBuffer(5);

        buf.put(new byte[] { 0 }, 0, 1);
        buf.put(new byte[] { 1 }, 0, 1);
        buf.put(new byte[] { 2 }, 0, 1);
        buf.put(new byte[] { 3 }, 0, 1);
        buf.put(new byte[] { 4 }, 0, 1);

        buf.clear();
        assertEquals(buf.capacity(), 5);
        assertEquals(buf.size(), 6);
        assertEquals(buf.putAvailable(), 5);
        assertEquals(buf.getAvailable(), 0);
    }
}
