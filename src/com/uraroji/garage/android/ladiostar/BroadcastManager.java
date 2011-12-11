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

package com.uraroji.garage.android.ladiostar;

/**
 * マイクから取得した音声をMP3変換し、サーバに送信する処理（録音と配信）を管理する。<br />
 * 複数の画面から共通して録音・配信処理の管理ができる。
 */
public class BroadcastManager {

    /**
     * 配信サービスとのコネクタ
     */
    private static BroadcastServiceConnector mBroadcastServiceConnector = new BroadcastServiceConnector();

    /**
     * コンストラクタ シングルトンなのでprivateとする
     */
    private BroadcastManager() {
    }

    /**
     * 配信サービスとのコネクタを取得する
     * 
     * @return 配信サービスとのコネクタ
     */
    public static BroadcastServiceConnector getConnector() {
        return mBroadcastServiceConnector;
    }
}
