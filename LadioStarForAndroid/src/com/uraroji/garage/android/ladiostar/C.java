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
 * アプリケーションの静的な設定情報保持
 */
public class C {

    /**
     * 設定値「マウント名」のデフォルトの文字の長さ。<br />
     * マウント名が未指定の場合に、ランダムな文字列でマウント名を生成するが、
     * その時の文字列の長さ。
     */
    public static final int DEFAULT_MOUNT_STRING_LENGTH = 14;
    
    /**
     * 設定値「ビットレート」のデフォルト値 (kbps)
     */
    public static final int DEFAULT_AUDIO_BITRATE = 32;

    /**
     * 設定値「モノラル・ステレオ」のデフォルト値。<br />
     * 1か2を指定すること
     */
    public static final int DEFAULT_AUDIO_CHANNEL = 1;

    /**
     * 設定値「サンプリングレート」のデフォルト値 (Hz)。<br />
     * 理由がなければ44100を指定のこと。
     */
    public static final int DEFAULT_AUDIO_SAMPLE_RATE = 44100;

    /**
     * 設定値「エンコード品質」のデフォルト値。<br />
     * <br />
     * 0〜9で指定する。0が高品質・低速、9が低品質・高速である。<br />
     * スマートフォンなので、デフォルト品質を標準より低い7とした。<br />
     * <br />
     * LAMEのドキュメントによると<br />
     * recommended:<br />
     * 2 near-best quality, not too slow<br />
     * 5 good quality, fast<br />
     * 7 ok quality, really fast
     */
    public static final int DEFAULT_AUDIO_MP3_ENCODE_QUALITY = 7;

    /**
     * ログのタグ
     */
    public static final String TAG = "LadioStar";

    /**
     * Verboseログを表示するか。<br />
     * リリースの場合にはfalse。
     */
    public static final boolean LOCAL_LOG = false;

    /**
     * PCMバッファに最大何秒分のデータをためこむかを指定する。<br />
     * <br />
     * PCMバッファとは、録音したデータをいったんため込んでおくためのバッファである。<br />
     * 録音しているスレッドと別のスレッドが、このバッファのデータを読み込みMP3エンコードをする。<br />
     * 1以上の整数を指定すること。<br />
     * あまり小さい数にすると、録音が開始できないことがあるので注意。
     */
    public static final int PCM_BUFFER_SEC = 5;

    /**
     * 録音バッファに最大何秒分のデータをためこむかを指定する。<br />
     * <br />
     * 録音バッファとは、システムから録音したデータをため込むバッファである。<br />
     * 録音バッファのデータをPCMバッファにコピーし、録音しているスレッドと別のスレッドがPCMバッファのデータを読み込みMP3エンコードをする。<br />
     * 1以上の整数でかつ、{@link #PCM_BUFFER_SEC} よりも小さい値にすること。<br />
     * （{@link #PCM_BUFFER_SEC} よりも大きい値でも問題ないが意味がないため。）
     */
    public static final int REC_BUFFER_SEC = 2;

    /**
     * 録音データ取得バッファに最大何ミリ秒分のデータをためこむかを指定する。<br />
     * <br />
     * 録音データ取得バッファとは、システムから録音したデータ受け取るためのバッファである。<br />
     * 1以上の整数でかつ、1000 / {@link #LOUDNESS_NOTIFY_TIMES_PER_SEC} よりも小さい値を指定すること。<br />
     * あまり大きい値にすると、マイクの音の大きさメーターが鈍くなるので注意。<br />
     */
    public static final int READ_REC_BUFFER_MSEC = 50;

    /**
     * エンコーダに渡すデータバッファに最大何秒分のデータを格納するかを指定する。<br />
     * <br />
     * PCMバッファからこのバッファにいったんデータをコピーし、エンコーダにこのバッファのデータを渡す。<br />
     * 1以上の整数でかつ、 {@link #PCM_BUFFER_SEC} よりも小さい値にすること。
     */
    public static final int ENCODE_PCM_BUFFER_SEC = 2;

    /**
     * MP3バッファに最大何秒分のデータをためこむかを指定する。<br />
     * <br />
     * MP3バッファとは、エンコードしたデータをいったんため込んでおくためのバッファである。<br />
     * エンコードしているスレッドと別のスレッドが、このバッファのデータを読み込みデータの送信をする。<br />
     * 1以上の整数を指定すること。
     */
    public static final int MP3_BUFFER_SEC = 40;

    /**
     * 録音を開始してから、送信開始までに待つ時間を指定する。<br />
     * <br />
     * 録音開始から送信開始まで少し待たせることで、MP3バッファにデータがため込まれるので、音声の途切れなどに強くなるはず。
     * {@link #MP3_BUFFER_SEC} よりも十分小さい値にすること。 0以下を指定した場合は、録音開始直後に送信開始する。
     */
    public static final int WAIT_SEC_FROM_REC_START_TO_SEND_DATA = 5;

    /**
     * リスナー数取得した後に次回リスナー数の取得を開始するまでの秒数
     */
    public static final int LISTENER_FETCH_INTERVAL_SEC = 30;

    /**
     * 切断した際の再接続までの待ち時間（ミリ秒）
     */
    public static final long WAIT_RECONNECT_MSEC = 4000;

    /**
     * 1秒間に何回音の大きさを通知するか<br />
     * <br />
     * 1以上の整数を指定すること。
     */
    public static final int LOUDNESS_NOTIFY_TIMES_PER_SEC = 5;

    /**
     * NotificationのID。<br />
     * ユニークなIDを取得するために、R.layout.mainのリソースIDを使う。
     */
    public static final int NOTIFICATION_ID = R.layout.main;

    /**
     * コンストラクタ。<br />
     * シングルトンなのでprivateとする。
     */
    private C() {
    }
}
