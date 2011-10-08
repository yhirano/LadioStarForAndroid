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

import android.content.Context;
import android.os.Handler;

/**
 * 配信のインターフェース
 */
public interface BroadcastInterface {

	/**
	 * 初期化
	 * 
	 * 一番はじめに配信をする前に初期化すること
	 * 
	 * @param context
	 *            コンテキスト。アプリケーションのコンテキストを渡すこと。
	 */
	public void init(Context context);

	/**
	 * 配信を開始する
	 * 
	 * @param broadcastConfig
	 *            配信設定
	 */
	public void start(BroadcastConfig broadcastConfig);

	/**
	 * 配信を停止する
	 */
	public void stop();

	/**
	 * 配信に使用したリソースを解放する。 アプリケーションの終了時などにリソースを解放すること。
	 */
	public void release();

	/**
	 * 配信中かを取得する
	 * 
	 * @return trueの場合は録音中、それ以外はfalse
	 */
	public boolean isBroadcasting();

	/**
	 * 配信情報を取得する
	 * 
	 * @return 配信中の番組の情報。<br />
	 *         配信中でない場合はnull。
	 */
	public BroadcastInfo getBroadcastInfo();
	
	/**
	 * 音量を取得する
	 * 
	 * @return 音量。1倍を100%とする。
	 */
	public char getVolumeRate();

	/**
	 * 音量を設定する
	 * 
	 * @param volumeRate
	 *            音量。1倍を100%とする。
	 */
	public void setVolumeRate(char volumeRate);

	/**
	 * 動作の状態変化を通知するハンドラを追加する
	 * 
	 * 動作状態が変わった際には、Handlerのwhatに変更後の状態が格納される。
	 * 
	 * @param handler
	 *            動作の状態変化を通知するハンドラ
	 * 
	 * @see BroadcastAtService#MSG_CONNECTED_SERVICE
	 * @see BroadcastAtService#MSG_ERROR_START_SERVICE_CONNECTION
	 * @see BroadcastAtService#MSG_ERROR_STOP_SERVICE_CONNECTION
	 * @see VoiceSender#MSG_ERROR_NOT_SUPPORTED_RECORDING_PARAMETERS
	 * @see VoiceSender#MSG_ERROR_REC_START
	 * @see VoiceSender#MSG_REC_STARTED
	 * @see VoiceSender#MSG_ERROR_AUDIO_RECORD
	 * @see VoiceSender#MSG_ERROR_PCM_BUFFER_OVERFLOW
	 * @see VoiceSender#MSG_ENCODE_STARTED
	 * @see VoiceSender#MSG_ERROR_AUDIO_ENCODE
	 * @see VoiceSender#MSG_ERROR_MP3_BUFFER_OVERFLOW
	 * @see VoiceSender#MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST
	 * @see VoiceSender#MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER
	 * @see VoiceSender#MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER
	 * @see VoiceSender#MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA
	 * @see VoiceSender#MSG_ERROR_RECEIVED_RESPONSE_AUTHENTICATION_REQUIRED
	 * @see VoiceSender#MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_IN_USE
	 * @see VoiceSender#MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_TOO_LONG
	 * @see VoiceSender#MSG_ERROR_RECEIVED_RESPONSE_CONTENT_TYPE_NOT_SUPPORTED
	 * @see VoiceSender#MSG_ERROR_RECEIVED_RESPONSE_TOO_MANY_SOURCES_CONNECTED
	 * @see VoiceSender#MSG_ERROR_RECEIVED_RESPONSE_UNKNOWN_ERROR
	 * @see VoiceSender#MSG_ERROR_SEND_HEADER_DATA
	 * @see VoiceSender#MSG_ERROR_RECV_HEADER_RESPONSE
	 * @see VoiceSender#MSG_SEND_STREAM_STARTED
	 * @see VoiceSender#MSG_ERROR_SEND_STREAM_DATA
	 * @see VoiceSender#MSG_SEND_STREAM_ENDED
	 */
	public void addBroadcastStateChangedHandler(Handler handler);

	/**
	 * 動作の状態変化を通知するハンドラを削除する
	 * 
	 * @param handler
	 *            動作の状態変化を通知するハンドラ
	 */
	public void removeBroadcastStateChangedHandler(Handler handler);

	/**
	 * 動作の状態変化を通知するハンドラをクリアする
	 */
	public void clearBroadcastStateChangedHandler();
}
