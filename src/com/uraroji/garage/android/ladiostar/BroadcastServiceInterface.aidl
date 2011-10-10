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

import com.uraroji.garage.android.ladiostar.BroadcastConfig;
import com.uraroji.garage.android.ladiostar.BroadcastInfo;
import com.uraroji.garage.android.ladiostar.BroadcastStateChangedCallbackInterface;

/**
 * 配信サービスのインターフェース
 */
interface BroadcastServiceInterface {

	/**
	 * 配信を開始する
	 * 
	 * @param broadcastConfig
	 *            配信設定
	 */
	void start(in BroadcastConfig broadcastConfig);

	/**
	 * 配信を停止する
	 */
	void stop();

	/**
	 * 配信状態を取得すする
	 * 
	 * @see VoiceSender#BROADCAST_STATE_STOPPED
	 * @see VoiceSender#BROADCAST_STATE_CONNECTING
	 * @see VoiceSender#BROADCAST_STATE_BROADCASTING
	 * @see VoiceSender#BROADCAST_STATE_STOPPING
	 */
	int getBroadcastState();

	/**
	 * 配信情報を取得する
	 * 
	 * @return 配信中の番組の情報。<br />
	 *         配信中でない場合はnull。
	 */
	BroadcastInfo getBroadcastInfo();

	/**
	 * 音量を取得する
	 * 
	 * @return 音量。1倍を100%とする。
	 */
	char getVolumeRate();

	/**
	 * 音量を設定する
	 * 
	 * @param volumeRate
	 *            音量。1倍を100%とする。
	 */
	void setVolumeRate(in char volumeRate);

	/**
	 * 動作状態が変わったことを通知するコールバックを登録
	 *
	 * @param callback 登録するコールバック
	 */
	void registerBroadcastStateChangedCallback(BroadcastStateChangedCallbackInterface callback);

	/**
	 * 登録済みの動作状態が変わったことを通知するコールバックを削除
	 *
	 * @param callback 削除するコールバック
	 */
	void unregisterBroadcastStateChangedCallback(BroadcastStateChangedCallbackInterface callback);
}
