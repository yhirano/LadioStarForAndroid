/* 
 * Copyright (c) 2011-2014 Yuichi Hirano
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

import java.util.Random;

/**
 * Random String create utility.
 */
public class RandomStringUtils {

    /**
     * Random.
     */
    private static Random random = new Random();

    /**
     * Creates a random alphabet string.
     * 
     * @param The length of random string.
     * @return Random string.
     */
    public static String randomAlphabetic(int length) {
        if (length == 0) {
            return "";
        } else if (length < 0) {
            throw new IllegalArgumentException("Specified random string length " + length
                    + " is negative.");
        }

        char[] buffer = new char[length];

        final char START = 'A';
        final char END = 'z';
        final int GAP = END - START + 1;
        char c = 0;

        for (int i = 0; i < length;) {
            c = (char) (random.nextInt(GAP) + START);
            if (('A' <= c) && (c <= 'Z') || ('a' <= c) && (c <= 'z')) {
                buffer[i] = c;
                ++i;
            } else {
                ;
            }
        }

        return new String(buffer);
    }
}
