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

import com.uraroji.garage.android.ladiostar.util.RandomStringUtils;

import junit.framework.TestCase;

public class RandomStringUtilsTest extends TestCase {

    public void testBasic() {
        random(1000, 8);
        random(1000, 9);
        random(1000, 10);
        random(1000, 11);
        random(1000, 12);
        random(1000, 13);
        random(1000, 14);
        random(1000, 15);
    }

    private static void random(int paternNum, int length) {
        String[] strings = new String[paternNum];

        // Create random strings.
        for (int i = 0; i < strings.length; ++i) {
            strings[i] = RandomStringUtils.randomAlphabetic(length);
        }

        // Check string length.
        for (int i = 0; i < strings.length; ++i) {
            assertEquals(strings[i].length(), length);
        }

        // Check only alphabet.
        for (int i = 0; i < strings.length; ++i) {
            char[] str = strings[i].toCharArray();
            for (int j = 0; j < str.length; ++j) {
                assertTrue(('A' <= str[j] && str[j] <= 'Z') || ('a' <= str[j] && str[j] <= 'z'));
            }
        }

        // Check random string.
        for (int i = 0; i < strings.length; ++i) {
            for (int j = i + 1; j < strings.length; ++j) {
                assertFalse(strings[i].equals(strings[j]));
            }
        }
    }

    public void testZeroLength() {
        assertTrue(RandomStringUtils.randomAlphabetic(0).equals(""));
    }

    public void testNegativeLength() {
        try {
            RandomStringUtils.randomAlphabetic(-1);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
