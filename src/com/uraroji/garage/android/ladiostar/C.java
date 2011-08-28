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

import android.net.Uri;

/**
 * アプリケーションの静的な設定情報保持
 */
public class C {

	/**
	 * 設定値「ビットレート」のデフォルト値 (kbps)
	 */
    public static final int DEFAULT_AUDIO_BITRATE = 32;
    
	/**
	 * 設定値「モノラル・ステレオ」のデフォルト値
	 * 
	 * 1か2を指定すること
	 */
    public static final int DEFAULT_AUDIO_CHANNEL = 1;
    
	/**
	 * 設定値「サンプリングレート」のデフォルト値 (Hz)
	 * 
	 * 理由がなければ44100を指定のこと。
	 */
    public static final int DEFAULT_AUDIO_SAMPLE_RATE = 44100;

	/**
	 * 設定値「エンコード品質」のデフォルト値
	 * 
	 * 0〜9で指定する。0が高品質・低速、9が低品質・高速である。<br />
	 * スマートフォンなので、デフォルト品質を標準より低い7とした。
	 * 
	 * LAMEのドキュメントによると<br />
	 * recommended:<br />
	 * 2 near-best quality, not too slow<br />
	 * 5 good quality, fast<br />
	 * 7 ok quality, really fast
	 */
    public static final int DEFAULT_AUDIO_MP3_ENCODE_QUALITY = 7;
    
    /**
     * メイン画面でヘルプを選択した際に表示するサイトのURL
     */
	public static final Uri HELP_MAIN_URL = Uri
			.parse("http://garage.uraroji.com/software/ladio-star/%E4%BD%BF%E3%81%84%E6%96%B9/");
    
    /**
     * 設定画面でヘルプを選択した際に表示するサイトのURL
     */
	public static final Uri HELP_SETTING_URL = Uri
			.parse("http://garage.uraroji.com/software/ladio-star/%E4%BD%BF%E3%81%84%E6%96%B9/%E8%A8%AD%E5%AE%9A%E3%81%AB%E3%81%A4%E3%81%84%E3%81%A6/");

    /**
	 * ログのタグ
	 */
	public static final String TAG = "LadioStar";

	/**
	 * Verboseログを表示するか
	 * 
	 * リリースの場合にはfalse
	 */
	public static final boolean LOCAL_LOG = false;

	/**
	 * 配信をサービスで行うか
	 * 
	 * サービスで行う場合、LadioStarが終了しても配信を継続する
	 */
	public static final boolean BROADCAST_AT_SERVICE = true;

	/**
	 * PCMバッファに最大何秒分のデータをためこむかを指定する。
	 * 
	 * PCMバッファとは、録音したデータをいったんため込んでおくためのバッファである。<br />
	 * 録音しているスレッドと別のスレッドが、このバッファのデータを読み込みMP3エンコードとデータの送信をする。<br />
	 * 1以上の整数を指定すること。
	 */
	public static final int PCM_BUFFER_SEC = 20;
	
	/**
	 * 録音バッファに最大何秒分のデータをためこむかを指定する。
	 * 
	 * 録音バッファとは、システムから録音したデータ直接受け取るためのバッファである。<br />
	 * 録音バッファのデータをPCMバッファにコピーし、録音しているスレッドと別のスレッドがPCMバッファのデータを読み込みMP3エンコードとデータの送信をする。<br />
	 * 1以上の整数でかつ、{@link C#PCM_BUFFER_SEC} よりも小さい値にすること。
	 */
	public static final int REC_BUFFER_SEC = 2;

	/**
	 * エンコーダに渡すデータバッファに最大何秒分のデータを格納するかを指定する。
	 * 
	 * PCMバッファからこのバッファにいったんデータをコピーし、エンコーダにこのバッファのデータを渡す。<br />
	 * 1以上の整数でかつ、 {@link C#PCM_BUFFER_SEC} よりも小さい値にすること。
	 */
	public static final int ENCODE_PCM_BUFFER_SEC = 3;
	
	/**
	 * PCMバッファにデータが存在しない場合、録音スレッドからPCMバッファに書き込みの通知があるまで、エンコード・送信スレッドを待たせる。
	 * これを有効にすると無駄にエンコード・送信スレッドが回らなくなるので処理が軽くなるはず。
	 */
	public static final boolean WAIT_TO_WROTE_PCM_BUFFER = true;
	
	/**
	 * 録音を開始してから、送信開始までに待つ時間を指定する。
	 * 
	 * 録音開始から送信開始まで少し待たせることで、PCMバッファにデータがため込まれるので、音声の途切れなどに強くなるはず。
	 * {@link C#PCM_BUFFER_SEC} よりも十分小さい値にすること。
	 * 0以下を指定した場合は、録音開始直後に送信開始する。
	 */
	public static final int WAIT_SEC_FROM_REC_START_TO_SEND_DATA = 9;
	
	/**
	 * NotificationのID
	 * 
	 * ユニークなIDを取得するために、R.layout.mainのリソースIDを使う
	 */
	public static final int NOTIFICATION_ID = R.layout.main;

	/**
	 * コンストラクタ
	 * 
	 * シングルトンなのでprivateとする
	 */
	private C() {
	}
}
