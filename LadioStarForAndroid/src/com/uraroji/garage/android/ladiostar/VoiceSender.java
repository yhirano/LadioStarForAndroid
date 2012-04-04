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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.uraroji.garage.android.ladiostar.util.ByteRingBuffer;
import com.uraroji.garage.android.ladiostar.util.ShortRingBuffer;
import com.uraroji.garage.android.lame.Lame;
import com.uraroji.garage.android.netladiolib.Server;
import com.uraroji.garage.android.netladiolib.ServersInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * マイクから取得した音声をMP3変換し、サーバに送信する 別スレッドでマイクからの録音、MP3への変換、音声のサーバへの送信を行う
 */
public class VoiceSender {

    static {
        System.loadLibrary("mp3lame");

        setUserAgentInfo(null, null); 
    }

    /**
     * サンプリングレート等の録音設定を端末がサポートしていない可能性がある
     * 
     * エラーがあった場合、このメッセージは
     * {@link VoiceSender#MSG_STARTED} よりも前に発行される。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_NOT_SUPPORTED_RECORDING_PARAMETERS = 0;

    /**
     * 録音の開始に失敗した
     * 
     * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_STARTED} よりも前に発行される。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_REC_START = 1;

    /**
     * 録音が開始した
     */
    public static final int MSG_REC_STARTED = 2;

    /**
     * 録音ができない
     * 
     * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_REC_STARTED}
     * よりも後に発行される。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_AUDIO_RECORD = 3;

    /**
     * 録音に対してエンコードが遅いなどの理由でバッファオーバーフローした
     * 
     * エラーがあった場合、このメッセージは
     * {@link VoiceSender#MSG_REC_STARTED} よりも後に発行される。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_PCM_BUFFER_OVERFLOW = 4;

    /**
     * エンコードが開始した
     */
    public static final int MSG_ENCODE_STARTED = 5;

    /**
     * エンコードに失敗した
     * 
     * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_ENCODE_STARTED}
     * よりも後に発行される。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_AUDIO_ENCODE = 6;

    /**
     * エンコードに対して送信が遅いなどの理由でバッファオーバーフローした
     * 
     * エラーがあった場合、このメッセージは
     * {@link VoiceSender#MSG_ENCODE_STARTED} よりも後に発行される。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_MP3_BUFFER_OVERFLOW = 7;

    /**
     * ねとらじのサーバに接続が失敗した。サーバ情報一覧が取得できない。
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST = 8;

    /**
     * ねとらじの配信サーバが見つからない。何からの理由で配信サーバを自動で選定することができなかった。
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER = 9;

    /**
     * ねとらじサーバへ接続するソケットが生成できなかった
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER = 10;

    /**
     * 録音開始から送信開始までの待ち時間に割り込みがかかった
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA = 11;

    /**
     * 認証失敗
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_RECEIVED_RESPONSE_AUTHENTICATION_REQUIRED = 12;

    /**
     * 同名のマウントが使用中
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_IN_USE = 13;

    /**
     * マウント名前が長すぎるか短すぎる
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_TOO_LONG = 14;

    /**
     * サポート外のストリーム
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_RECEIVED_RESPONSE_CONTENT_TYPE_NOT_SUPPORTED = 15;

    /**
     * 混んでいて接続できない
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_RECEIVED_RESPONSE_TOO_MANY_SOURCES_CONNECTED = 16;

    /**
     * 未知のレスポンスを受信した
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_RECEIVED_RESPONSE_UNKNOWN_ERROR = 17;

    /**
     * ヘッダの送信に失敗した
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_SEND_HEADER_DATA = 18;

    /**
     * ヘッダのレスポンス受信に失敗した
     * 
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_RECV_HEADER_RESPONSE = 19;

    /**
     * ストリームの配信を開始した
     */
    public static final int MSG_SEND_STREAM_STARTED = 20;

    /**
     * ストリームの送信に失敗した
     * 
     * エラーがあった場合、このメッセージは
     * {@link VoiceSender#MSG_SEND_STREAM_STARTED} よりも後に発行される。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_ERROR_SEND_STREAM_DATA = 21;

    /**
     * ストリームの配信を正常に終了した このメッセージは {@link VoiceSender#MSG_ERROR_SEND_STREAM_DATA}
     * が発行された場合には発行されない。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_SEND_STREAM_ENDED = 22;

    /**
     * 再接続を開始した
     * 
     * 再接続設定が有効な場合に、接続が切れた後に再接続処理を開始したら発行する。
     */
    public static final int MSG_RECONNECT_STARTED = 23;

    /**
     * 再接続待ち中に再接続を停止した
     * 
     * 再接続待ち中にユーザーによる配信停止の場合に発行される。<br />
     * このメッセージが発行された直後に処理は終了する。
     */
    public static final int MSG_STOP_WAIT_RECONNECT = 24;

    /**
     * 音の大きさの通知
     */
    public static final int MSG_LOUDNESS = 0;
    
    /**
     * 動作していない
     */
    public static final int BROADCAST_STATE_STOPPED = 0;

    /**
     * 接続中
     */
    public static final int BROADCAST_STATE_CONNECTING = 1;

    /**
     * 配信中
     */
    public static final int BROADCAST_STATE_BROADCASTING = 2;

    /**
     * 停止中
     */
    public static final int BROADCAST_STATE_STOPPING = 4;

    /**
     * ねとらじサーバに通知するUserAgent
     */
    private static String sUserAgent;

    /**
     * PCMバッファ（エンコード待ちデータ）
     */
    private ShortRingBuffer mPcmBuffer;

    /**
     * PCMバッファのロックオブジェクト
     */
    private final Object mPcmBufferLock = new Object();

    /**
     * MP3バッファ（送信待ちデータ）
     */
    private ByteRingBuffer mMp3Buffer;

    /**
     * MP3バッファのロックオブジェクト
     */
    private final Object mMp3BufferLock = new Object();
    
    /**
     * 配信状態
     */
    public BroadcastState mBroadcastState = new BroadcastState();

    /**
     * 再接続するか
     */
    private volatile boolean mIsRecoonect = true;

    /**
     * 音量 1倍を100とする音量のレート
     * 
     * @see mVolumeRateFloat mVolumeRateとmVolumeRateFloatはリンクしている
     */
    private volatile char mVolumeRate = 100;

    /**
     * 音量 1倍を1とする音量のレート
     * 
     * @see mVolumeRate mVolumeRateとmVolumeRateFloatはリンクしている
     */
    private volatile float mVolumeRateFloat = mVolumeRate / 100F;

    /**
     * 配信情報<br />
     * 配信中の番組の情報を格納する
     */
    private BroadcastInfo mBroadcastingInfo;

    /**
     * mBroadcastingInfoのロックオブジェクト
     */
    private final Object mBroadcastingInfoLock = new Object();

    /**
     * 配信を開始した時刻。（正確には{@link #start(BroadcastConfig)}を実行した時刻。）<br />
     * {@link System#currentTimeMillis()}で取得した値を格納する。<br />
     * 配信情報に使用。
     */
    private volatile long mStartTime = -1;
    
    /**
     * 録音を開始した時刻。<br />
     * {@link System#currentTimeMillis()}で取得した値を格納する。<br />
     * 録音開始から配信開始までの待ち時間計測に使用。
     */
    private volatile long mRecStartTime = -1;

    /**
     * 配信の状態変化を通知するハンドラのリスト
     */
    private ArrayList<Handler> mBroadcastStateChangedHandlerList = new ArrayList<Handler>();;

    /**
     * {@link VoiceSender#mBroadcastStateChangedHandlerList}のロックオブジェクト
     */
    private final Object mBroadcastStateChangedHandlerListLock = new Object();

    /**
     * 音の大きさを通知するハンドラのリスト
     */
    private ArrayList<Handler> mLoudnessHandlerList = new ArrayList<Handler>();;

    /**
     * {@link VoiceSender#mLoudnessHandlerList}のロックオブジェクト
     */
    private final Object mLoudnessHandlerListLock = new Object();

    /**
     * コンストラクタ
     */
    public VoiceSender() {
    }

    /**
     * 開始する
     * 
     * @param broadcastConfig 配信設定
     * @throws IllegalArgumentException broadcastConfigにnullを指定した
     */
    public final void start(final BroadcastConfig broadcastConfig) {
        if (broadcastConfig == null) {
            throw new IllegalArgumentException("broadcastConfig must be not null.");
        }

        // 既に動作中の場合は何もしない
        if (mBroadcastState.get() != BROADCAST_STATE_STOPPED) {
            return;
        }

        /*
         * PCMバッファ（エンコード待ちバッファ）を生成する PCMバッファサイズは指定の秒数分だけ確保する。
         * サンプリングレート * チャンネル数 * 秒数でバッファサイズを計算する。
         */
        synchronized (mPcmBufferLock) {
            mPcmBuffer = new ShortRingBuffer(broadcastConfig.getAudioSampleRate()
                    * broadcastConfig.getAudioChannel() * C.PCM_BUFFER_SEC);
        }
        Log.d(C.TAG, "PCM buffersize is " + String.valueOf(mPcmBuffer.capacity() * 2)
                + " bytes.");

        /*
         * MP3バッファ（送信待ちバッファ）を生成する MP3バッファサイズは指定の秒数分だけ確保する。
         */
        synchronized (mMp3BufferLock) {
            mMp3Buffer = new ByteRingBuffer(
                    ((broadcastConfig.getAudioBrate() / 8) * 1024) * C.MP3_BUFFER_SEC);
        }
        Log.d(C.TAG, "MP3 buffersize is " + String.valueOf(mMp3Buffer.capacity())
                + " bytes.");

        mBroadcastState.set(BROADCAST_STATE_CONNECTING); // 動作の開始フラグを立てる

        mStartTime = System.currentTimeMillis(); // 開始時刻を設定
        
        (new RecThread(broadcastConfig)).start();
        (new EncodeThread(broadcastConfig)).start();
        (new SendDataThread(broadcastConfig)).start();
    }

    /**
     * 録音スレッド
     */
    private class RecThread extends Thread {

        /**
         * 配信設定
         */
        private BroadcastConfig mBroadcastConfig;

        /**
         * コンストラクタ
         * 
         * @param broadcastConfig 配信設定
         */
        public RecThread(BroadcastConfig broadcastConfig) {
            mBroadcastConfig = broadcastConfig;
        }

        @Override
        public void run() {
            Log.d(C.TAG, "Start Record thread.");

            try {
                android.os.Process
                        .setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                final int channelConfig = getAudioChannelConfig(mBroadcastConfig
                        .getAudioChannel());

                // 録音に最低限必要なバッファサイズ
                final int recBufferSize = AudioRecord.getMinBufferSize(
                        mBroadcastConfig.getAudioSampleRate(), channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT);
                // バッファサイズが取得できない。サンプリングレート等の設定を端末がサポートしていない可能性がある。
                if (recBufferSize < 0) {
                    mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                    notifyRecStateChangedHandle(MSG_ERROR_NOT_SUPPORTED_RECORDING_PARAMETERS); // エラー名を変える
                    return;
                }
                /*
                 * 録音に最低限必要なバッファサイズよりも、エンコード待ちバッファサイズの方が小さい場合は否応なしに中止にする。
                 * PCMバッファサイズを大きくすること。
                 */
                assert (recBufferSize > mPcmBuffer.capacity());
                Log.d(C.TAG,
                        "Recording buffersize is "
                                + String.valueOf(recBufferSize) + " bytes.");
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        mBroadcastConfig.getAudioSampleRate(), channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT, recBufferSize);
                short[] recBuffer = new short[recBufferSize];

                try {
                    try {
                        audioRecord.startRecording(); // 録音を開始する
                    } catch (IllegalStateException e) {
                        Log.w(C.TAG,
                                "IllegalStateException occurred when audio record start.",
                                e);
                        mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                        // 録音の開始に失敗した
                        notifyRecStateChangedHandle(MSG_ERROR_REC_START);
                        return;
                    }

                    mRecStartTime = System.currentTimeMillis(); // 録音を開始した時刻を記憶する

                    // 録音が開始した
                    notifyRecStateChangedHandle(MSG_REC_STARTED);

                    int readSize = 0;
                    while (mBroadcastState.isConnectingOrBroadcasting()) {
                        readSize = audioRecord
                                .read(recBuffer, 0, recBufferSize);
                        if (readSize < 0) {
                            mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                            // 録音ができない
                            notifyRecStateChangedHandle(MSG_ERROR_AUDIO_RECORD);
                            return;
                        }
                        // データが読み込めなかった場合は何もしない
                        else if (readSize == 0) {
                            ;
                        }
                        // データが入っている場合
                        else {
                            try {
                                int availableDataSize = 0;
                                synchronized (mPcmBufferLock) {
                                    // 音声のボリュームを調整する
                                    changeVolume(recBuffer, readSize);

                                    notifyLundness(recBuffer, readSize);
                                    
                                    try {
                                        // バッファに書き込む
                                        mPcmBuffer.put(recBuffer, 0, readSize);
                                        availableDataSize = mPcmBuffer
                                                .getAvailable();
                                    } finally {
                                        mPcmBufferLock.notifyAll();
                                        if (C.LOCAL_LOG) {
                                            Log.v(C.TAG,
                                                    "Notify to write PCM buffer.");
                                        }
                                    }
                                }
                                if (C.LOCAL_LOG) {
                                    Log.v(C.TAG,
                                            "Wrote PCM buffer("
                                                    + String.valueOf(readSize)
                                                    + " bytes). Available buffersize is "
                                                    + String.valueOf(availableDataSize)
                                                    + " bytes.");
                                }
                            } catch (BufferOverflowException e) {
                                Log.w(C.TAG,
                                        "MP3 encoding is slow, it seems to have PCM buffer overflowed.");
                                mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                                // 録音に対してエンコードが遅いなどの理由でバッファオーバーフローした
                                notifyRecStateChangedHandle(MSG_ERROR_PCM_BUFFER_OVERFLOW);
                                return;
                            }
                        }
                    }
                } finally {
                    audioRecord.stop(); // 録音を停止する
                    audioRecord.release();
                    mRecStartTime = -1;
                }
            } finally {
                Log.d(C.TAG, "Finish Record thread.");
            }
        }

        /**
         * オーディオのチャンネル数からふさわしいChannelConfigを返す
         * 
         * @param channel オーディオのチャンネル数
         * @return ChannelConfig
         */
        private int getAudioChannelConfig(int channel) {
            switch (channel) {
                case 1:
                    return AudioFormat.CHANNEL_IN_MONO;
                case 2:
                    return AudioFormat.CHANNEL_IN_STEREO;
                default:
                    throw new IllegalArgumentException("Unknown channel num.");
            }
        }

        /**
         * ボリュームを調整する
         * 
         * @param buf ボリュームを調整するPCMバッファ。ここで指定したPCMバッファを直接書き換える。
         * @param size バッファの長さ
         */
        private void changeVolume(short[] buf, int size) {
            if (isVolumeRateChanged() == true) {
                int vi;
                short vs;

                for (int i = 0; i < size; ++i) {
                    vi = (int) (buf[i] * mVolumeRateFloat);
                    if (vi > Short.MAX_VALUE) {
                        vs = Short.MAX_VALUE;
                    } else if (vi < Short.MIN_VALUE) {
                        vs = Short.MIN_VALUE;
                    } else {
                        vs = (short) vi;
                    }
                    buf[i] = vs;
                }
            }
        }

        /**
         * 録音した音の大きさの総和の2乗を格納しておくための領域
         */
        private double mLoudnessSquareTotal = 0;

        /**
         * {@link VoiceSender#mLoudnessSquareTotal}にため込んだ量
         */
        private int mLoudnessTotalLength = 0;

        /**
         * 音の大きさを通知する
         * 
         * @param buf PCMバッファ。
         * @param size バッファの長さ
         */
        private void notifyLundness(short[] buf, int size) {
            final ArrayList<Handler> handerList = getLoudnessHandlerListClone();
            if (handerList.isEmpty()) {
                return;
            }

            // 音の大きさを通知通知するまでに計測するバッファの長さ
            final int limit = (mBroadcastConfig.getAudioSampleRate()
                    * mBroadcastConfig.getAudioChannel()) / C.LOUDNESS_NOTIFY_TIMES_PER_SEC;

            for (int i = 0; i < size; ++i) {
                mLoudnessSquareTotal += buf[i] * buf[i];
                ++mLoudnessTotalLength;

                // 一定量の録音バッファのRMSが計算し終わったら、音の大きさを送信する
                if (mLoudnessTotalLength >= limit) {
                    final double rmsdB = 20.0 * Math.log10(Math.sqrt(mLoudnessSquareTotal / mLoudnessTotalLength));

                    if (C.LOCAL_LOG) {
                        Log.v(C.TAG, "Loudness " + rmsdB);
                    }

                    for (Handler h : handerList) {
                        h.sendMessage(h.obtainMessage(MSG_LOUDNESS, (int)rmsdB, (int)rmsdB));
                    }
                    
                    mLoudnessSquareTotal = 0;
                    mLoudnessTotalLength = 0;
                }
            }
        }

        /**
         * 音の大きさを通知するハンドラーリストのクローンしたリストを取得する。 浅いクローンなので注意。
         * 
         * @return 音の大きさを通知するハンドラーリストのクローンしたリスト
         */
        @SuppressWarnings("unchecked")
        private ArrayList<Handler> getLoudnessHandlerListClone() {
            synchronized (mLoudnessHandlerListLock) {
                return (ArrayList<Handler>) mLoudnessHandlerList.clone();
            }
        }
    }

    /**
     * エンコードスレッド
     */
    private class EncodeThread extends Thread {

        /**
         * 配信設定
         */
        private BroadcastConfig mBroadcastConfig;

        /**
         * コンストラクタ
         * 
         * @param broadcastConfig 配信設定
         */
        public EncodeThread(BroadcastConfig broadcastConfig) {
            mBroadcastConfig = broadcastConfig;
        }

        @Override
        public void run() {
            Log.d(C.TAG, "Start Encode thread.");

            Lame encoder = null;
            try {
                Lame.log(C.LOCAL_LOG);
                // Lame init
                encoder = new Lame.Builder(mBroadcastConfig.getAudioSampleRate(),
                        mBroadcastConfig.getAudioChannel(), mBroadcastConfig.getAudioSampleRate(),
                        mBroadcastConfig.getAudioBrate())
                        .quality(mBroadcastConfig.getAudioMp3EncodeQuality())
                        .id3tagTitle(mBroadcastConfig.getChannelTitle())
                        .id3tagArtist(mBroadcastConfig.getChannelDjName())
                        .id3tagYear(String.valueOf(Calendar.getInstance().get(Calendar.YEAR)))
                        .id3tagComment(mBroadcastConfig.getChannelDescription()).create();
                Log.d(C.TAG,
                        "SimpleLame is initialized. (SampleRate="
                                + String.valueOf(mBroadcastConfig
                                        .getAudioSampleRate())
                                + ", Channel="
                                + String.valueOf(mBroadcastConfig
                                        .getAudioChannel())
                                + ", BitRate="
                                + String.valueOf(mBroadcastConfig
                                        .getAudioBrate())
                                + ", Quality="
                                + String.valueOf(mBroadcastConfig
                                        .getAudioMp3EncodeQuality()) + ")");

                // 読み込みバッファサイズ。指定の秒数分だけ確保する。
                final int readBufferSize = (mBroadcastConfig
                        .getAudioSampleRate()
                        * mBroadcastConfig.getAudioChannel() * 2 * C.ENCODE_PCM_BUFFER_SEC) + 2;
                // 読み込みバッファ
                short[] readBuffer = new short[readBufferSize];
                Log.d(C.TAG,
                        "Read buffersize is " + String.valueOf(readBufferSize)
                                + " bytes.");

                // MP3バッファサイズ
                final int mp3BufferSize = (int) (7200 + (readBufferSize * 1.25));
                // MP3バッファ
                byte[] mp3buffer = new byte[mp3BufferSize];
                Log.d(C.TAG,
                        "Temporary MP3 encode buffersize is "
                                + String.valueOf(mp3BufferSize) + " bytes.");

                // 読み込みサイズ
                int readSize = 0;
                // エンコード後のバイトサイズ
                int encResult = 0;

                // エンコードが開始した
                notifyRecStateChangedHandle(MSG_ENCODE_STARTED);

                while (mBroadcastState.isConnectingOrBroadcasting()) {
                    readSize = 0;
                    while (mBroadcastState.isConnectingOrBroadcasting()) {
                        synchronized (mPcmBufferLock) {
                            final int availableSize = mPcmBuffer.getAvailable();
                            if (availableSize != 0) {
                                readSize = Math.min(availableSize,
                                        readBufferSize);
                                readSize = mPcmBuffer.get(readBuffer, 0,
                                        readSize);
                            } else {
                                try {
                                    if (C.LOCAL_LOG) {
                                        Log.v(C.TAG,
                                                "Wait to read PCM buffer.");
                                    }

                                    mPcmBufferLock.wait();
                                } catch (InterruptedException e) {
                                    Log.w(C.TAG,
                                            "Interrupted wait to writing PCM bufffer.",
                                            e);
                                    mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                                    // エンコードに失敗した
                                    notifyRecStateChangedHandle(MSG_ERROR_AUDIO_ENCODE);
                                    return;
                                }
                            }
                        }
                        if (readSize != 0) {
                            if (C.LOCAL_LOG) {
                                Log.v(C.TAG,
                                        "Read PCM buffer("
                                                + String.valueOf(readSize)
                                                + " bytes).");
                            }
                            break;
                        }
                    }

                    encResult = 0;
                    if (readSize != 0) {
                        switch (mBroadcastConfig.getAudioChannel()) {
                            case 1: // モノラルの場合
                                encResult = encoder.encode(readBuffer,
                                        readBuffer, readSize, mp3buffer);
                                break;
                            case 2: // ステレオの場合
                                encResult = encoder.encodeBufferInterleaved(
                                        readBuffer, readSize / 2, mp3buffer);
                                break;
                            default: // ここに到達することはあり得ないはずだが一応エラーとする。
                                Log.w(C.TAG,
                                        "Failed LAME encode. PCM channels unknown.");
                                mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                                // エンコードに失敗した
                                notifyRecStateChangedHandle(MSG_ERROR_AUDIO_ENCODE);
                                return;
                        }
                        if (encResult < 0) {
                            Log.w(C.TAG, "Failed LAME encode(error="
                                    + encResult + ").");
                            mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                            // エンコードに失敗した
                            notifyRecStateChangedHandle(MSG_ERROR_AUDIO_ENCODE);
                            return;
                        }
                    }
                    if (encResult != 0) {
                        if (C.LOCAL_LOG) {
                            Log.v(C.TAG,
                                    "Encoded " + String.valueOf(readSize)
                                            + " bytes PCM to "
                                            + String.valueOf(encResult)
                                            + " bytes MP3.");
                        }
                        try {
                            int availableDataSize = 0;
                            synchronized (mMp3BufferLock) {
                                try {
                                    // バッファに書き込む
                                    mMp3Buffer.put(mp3buffer, 0, encResult,
                                            true);
                                    availableDataSize = mMp3Buffer
                                            .getAvailable();
                                } finally {
                                    mMp3BufferLock.notifyAll();
                                    if (C.LOCAL_LOG) {
                                        Log.v(C.TAG,
                                                "Notify to write MP3 buffer.");
                                    }
                                }
                            }
                            if (C.LOCAL_LOG) {
                                Log.v(C.TAG,
                                        "Wrote MP3 buffer("
                                                + String.valueOf(encResult)
                                                + " bytes). Available buffersize is "
                                                + String.valueOf(availableDataSize)
                                                + " bytes.");
                            }
                        } catch (BufferOverflowException e) {
                            Log.w(C.TAG, "MP3 buffer overflowed.");
                            mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                            // エンコードに対して送信が遅いなどの理由でバッファオーバーフローした
                            notifyRecStateChangedHandle(MSG_ERROR_MP3_BUFFER_OVERFLOW);
                            return;
                        }
                    }
                }

                int flushResult = encoder.flush(mp3buffer);
                if (flushResult < 0) {
                    Log.w(C.TAG, "Failed LAME flush(error=" + flushResult
                            + ").");
                    mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                    // エンコードに失敗した
                    notifyRecStateChangedHandle(MSG_ERROR_AUDIO_ENCODE);
                    return;
                }
                if (flushResult != 0) {
                    if (C.LOCAL_LOG) {
                        Log.v(C.TAG,
                                "Encoded remain PCM to "
                                        + String.valueOf(flushResult)
                                        + " bytes MP3.");
                    }
                    try {
                        int availableDataSize = 0;
                        synchronized (mMp3BufferLock) {
                            try {
                                // バッファに書き込む
                                mMp3Buffer.put(mp3buffer, 0, flushResult, true);
                                availableDataSize = mMp3Buffer.getAvailable();
                            } finally {
                                mMp3BufferLock.notifyAll();
                                if (C.LOCAL_LOG) {
                                    Log.v(C.TAG, "Notify to write MP3 buffer.");
                                }
                            }
                        }
                        if (C.LOCAL_LOG) {
                            Log.v(C.TAG,
                                    "Wrote MP3 buffer("
                                            + String.valueOf(flushResult)
                                            + " bytes). Available buffersize is "
                                            + String.valueOf(availableDataSize)
                                            + " bytes.");
                        }
                    } catch (BufferOverflowException e) {
                        Log.w(C.TAG, "MP3 buffer overflowed.");
                        mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                        // エンコードに対して送信が遅いなどの理由でバッファオーバーフローした
                        notifyRecStateChangedHandle(MSG_ERROR_MP3_BUFFER_OVERFLOW);
                        return;
                    }
                }
            } finally {
                encoder.close();
                Log.d(C.TAG, "SimpleLame is closed.");

                Log.d(C.TAG, "Finish Encode thread.");
            }
        }
    }

    /**
     * 送信スレッド
     */
    private class SendDataThread extends Thread {

        /**
         * 配信設定
         */
        private BroadcastConfig mBroadcastConfig;

        /**
         * コンストラクタ
         * 
         * @param broadcastConfig 配信設定
         */
        public SendDataThread(BroadcastConfig broadcastConfig) {
            mBroadcastConfig = broadcastConfig;
        }

        @Override
        public void run() {
            Log.d(C.TAG, "Start Send data thread.");

            try {
                // ねとらじの接続先サーバ
                Server broadcastServer = null;

                // 接続先のサーバを決める
                {
                    // 配信サーバ情報リストを取得する
                    ServersInfo serversInfo = new ServersInfo();
                    try {
                        serversInfo.fetchServerInfo();
                    } catch (IOException e) {
                        Log.w(C.TAG,
                                "IOException occurred when fetch netladio server information.",
                                e);
                        if (mIsRecoonect) {
                            try {
                                reconnect(mBroadcastConfig);
                            } catch (InterruptedException e2) {
                                mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                                notifyRecStateChangedHandle(MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST);
                            }
                        } else {
                            mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                            notifyRecStateChangedHandle(MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST);
                        }
                        return;
                    }

                    // 配信サーバを設定していない場合はもっとも空いているサーバを取得する
                    if (mBroadcastConfig.getChannelServer() == null
                            || mBroadcastConfig.getChannelServer().length() == 0) {
                        broadcastServer = serversInfo.getVacantServer();
                    }
                    // 配信サーバを設定している場合
                    else {
                        broadcastServer = serversInfo
                                .getServer(mBroadcastConfig.getChannelServer());
                    }

                    // 何らかの理由で配信サーバが決まらなかった場合はエラーで終了
                    if (broadcastServer == null) {
                        Log.w(C.TAG, "Not found the broadcast server.");
                        if (mIsRecoonect) {
                            try {
                                reconnect(mBroadcastConfig);
                            } catch (InterruptedException e2) {
                                mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                                notifyRecStateChangedHandle(MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER);
                            }
                        } else {
                            mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                            notifyRecStateChangedHandle(MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER);
                        }
                        return;
                    }
                }

                Socket socket = null;
                InputStream sockIn = null;
                OutputStream sockOut = null;
                try {
                    socket = new Socket(broadcastServer.getServerName()
                            .getName(), broadcastServer.getServerName()
                            .getPort());
                    sockIn = socket.getInputStream();
                    sockOut = socket.getOutputStream();
                } catch (UnknownHostException e) {
                    Log.w(C.TAG,
                            "UnknownHostException occurred when create socket.",
                            e);
                    if (mIsRecoonect) {
                        try {
                            reconnect(mBroadcastConfig);
                        } catch (InterruptedException e2) {
                            mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                            notifyRecStateChangedHandle(MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER);
                        }
                    } else {
                        mBroadcastState.set(BROADCAST_STATE_STOPPING); // 動作中フラグを下げる
                        notifyRecStateChangedHandle(MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER);
                    }
                    return;
                } catch (IOException e) {
                    Log.w(C.TAG,
                            "IOException occurred when create socket.", e);
                    if (mIsRecoonect) {
                        try {
                            reconnect(mBroadcastConfig);
                        } catch (InterruptedException e2) {
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            notifyRecStateChangedHandle(MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER);
                        }
                    } else {
                        mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                        notifyRecStateChangedHandle(MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER);
                    }
                    return;
                }

                // 録音を開始してから送信まで指定された秒数を待つ
                try {
                    waitFromRecStartToSendData();
                } catch (InterruptedException e) {
                    if (mIsRecoonect) {
                        try {
                            reconnect(mBroadcastConfig);
                        } catch (InterruptedException e2) {
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            notifyRecStateChangedHandle(MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA);
                        }
                    } else {
                        mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                        notifyRecStateChangedHandle(MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA);
                    }
                    return;
                }

                // ここに到達するまでにユーザーにより停止が指示されている場合は終了
                if (mBroadcastState.get() == BROADCAST_STATE_STOPPING) {
                    // ストリーム配信正常終了
                    notifyRecStateChangedHandle(MSG_SEND_STREAM_ENDED);
                    return;
                }

                PrintWriter pr = null;
                InputStreamReader in = null;
                BufferedReader br = null;

                try {
                    // ヘッダ送信
                    try {
                        pr = new PrintWriter(new OutputStreamWriter(
                                sockOut, "Shift_JIS"), true);
                        String headerStr = createHeader();
                        pr.println(headerStr);
                        pr.flush();
                    } catch (UnsupportedEncodingException e) {
                        Log.w(C.TAG,
                                "UnsupportedEncodingException occurred when send header.",
                                e);
                        if (mIsRecoonect) {
                            try {
                                reconnect(mBroadcastConfig);
                            } catch (InterruptedException e2) {
                                mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                                // ヘッダの送信に失敗した
                                notifyRecStateChangedHandle(MSG_ERROR_SEND_HEADER_DATA);
                            }
                        } else {
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            // ヘッダの送信に失敗した
                            notifyRecStateChangedHandle(MSG_ERROR_SEND_HEADER_DATA);
                        }
                        return;
                    }

                    try {
                        in = new InputStreamReader(sockIn);
                        br = new BufferedReader(in);
                        String responseStr = br.readLine();
                        // 接続に成功
                        if (responseStr.equals("HTTP/1.0 200 OK")) {
                            Log.i(C.TAG, "Connected to "
                                    + socket.getInetAddress()
                                            .getHostAddress() + ":"
                                    + socket.getPort() + ".");
                        }
                        // 認証失敗
                        else if (responseStr
                                .equals("HTTP/1.0 401 Authentication Required")) {
                            Log.w(C.TAG, "Received error.(" + responseStr
                                    + ")");
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            // 認証失敗
                            notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_AUTHENTICATION_REQUIRED);
                            return;
                        }
                        // 同名のマウントが使用中
                        else if (responseStr
                                .equals("HTTP/1.0 403 Mountpoint in use")) {
                            Log.w(C.TAG, "Received error.(" + responseStr
                                    + ")");
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            // 同名のマウントが使用中
                            notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_IN_USE);
                            return;
                        }
                        // マウント名前が長すぎるか短すぎる
                        else if (responseStr
                                .equals("HTTP/1.0 403 Mountpoint too long")) {
                            Log.w(C.TAG, "Received error.(" + responseStr
                                    + ")");
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            // ヘッダのレスポンス受信に失敗した
                            notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_TOO_LONG);
                            return;
                        }
                        // サポート外のストリーム
                        else if (responseStr
                                .equals("HTTP/1.0 403 Content-type not supported")) {
                            Log.w(C.TAG, "Received error.(" + responseStr
                                    + ")");
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            // ヘッダのレスポンス受信に失敗した
                            notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_CONTENT_TYPE_NOT_SUPPORTED);
                            return;
                        }
                        // 混んでいて接続できない
                        else if (responseStr
                                .equals("HTTP/1.0 403 too many sources connected")) {
                            Log.w(C.TAG, "Received error.(" + responseStr
                                    + ")");
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            // ヘッダのレスポンス受信に失敗した
                            notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_TOO_MANY_SOURCES_CONNECTED);
                            return;
                        }
                        // 未知のレスポンスを受信した
                        else {
                            Log.w(C.TAG, "Received unknown error.("
                                    + responseStr + ")");
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            // 未知のレスポンスを受信した
                            notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_UNKNOWN_ERROR);
                            return;
                        }
                    } catch (IOException e) {
                        Log.w(C.TAG,
                                "IOException occurred when header response receved.",
                                e);
                        if (mIsRecoonect) {
                            try {
                                reconnect(mBroadcastConfig);
                            } catch (InterruptedException e2) {
                                mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                                // ヘッダのレスポンス受信に失敗した
                                notifyRecStateChangedHandle(MSG_ERROR_RECV_HEADER_RESPONSE);
                            }
                        } else {
                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                            // ヘッダのレスポンス受信に失敗した
                            notifyRecStateChangedHandle(MSG_ERROR_RECV_HEADER_RESPONSE);
                        }
                        return;
                    }

                    synchronized (mBroadcastingInfoLock) {
                        mBroadcastingInfo = new BroadcastInfo(
                                mBroadcastConfig, broadcastServer
                                        .getServerName().getName(),
                                broadcastServer.getServerName().getPort(), mStartTime);
                    }

                    // ここに到達するまでにユーザーにより停止が指示されている場合は終了
                    if (mBroadcastState.get() == BROADCAST_STATE_STOPPING) {
                        // ストリーム配信正常終了
                        notifyRecStateChangedHandle(MSG_SEND_STREAM_ENDED);
                        return;
                    }

                    mBroadcastState.set(BROADCAST_STATE_BROADCASTING);

                    // ストリーム配信開始
                    notifyRecStateChangedHandle(MSG_SEND_STREAM_STARTED);

                    // 読み込みサイズ
                    int readSize = 0;
                    // 読み込みバッファ
                    byte[] readBuffer = new byte[16 * 1024];

                    while (mBroadcastState.get() == BROADCAST_STATE_BROADCASTING) {
                        readSize = 0;
                        synchronized (mMp3BufferLock) {
                            final int availableSize = mMp3Buffer.getAvailable();
                            if (availableSize != 0) {
                                readSize = Math.min(availableSize,
                                        readBuffer.length);
                                readSize = mMp3Buffer.get(readBuffer, 0,
                                        readSize);
                            } else {
                                try {
                                    if (C.LOCAL_LOG) {
                                        Log.v(C.TAG,
                                                "Wait to read MP3 buffer.");
                                    }

                                    mMp3BufferLock.wait();
                                } catch (InterruptedException e) {
                                    Log.w(C.TAG,
                                            "Interrupted wait to writing MP3 bufffer.",
                                            e);
                                    if (mIsRecoonect) {
                                        try {
                                            reconnect(mBroadcastConfig);
                                        } catch (InterruptedException e2) {
                                            mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                                            // データの送信に失敗した
                                            notifyRecStateChangedHandle(MSG_ERROR_SEND_STREAM_DATA);
                                        }
                                    } else {
                                        mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                                        // データの送信に失敗した
                                        notifyRecStateChangedHandle(MSG_ERROR_SEND_STREAM_DATA);
                                    }
                                    return;
                                }
                            }
                        }
                        try {
                            if (readSize != 0) {
                                sockOut.write(readBuffer, 0, readSize);
                                if (C.LOCAL_LOG) {
                                    Log.v(C.TAG,
                                            "Sent " + String.valueOf(readSize)
                                                    + " bytes data.");
                                }
                            }
                        } catch (IOException e) {
                            Log.w(C.TAG,
                                    "IOException occurred when send stream.",
                                    e);
                            if (mIsRecoonect) {
                                try {
                                    reconnect(mBroadcastConfig);
                                } catch (InterruptedException e2) {
                                    mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                                    // データの送信に失敗した
                                    notifyRecStateChangedHandle(MSG_ERROR_SEND_STREAM_DATA);
                                }
                            } else {
                                mBroadcastState.set(BROADCAST_STATE_STOPPING);; // 動作中フラグを下げる
                                // データの送信に失敗した
                                notifyRecStateChangedHandle(MSG_ERROR_SEND_STREAM_DATA);
                            }
                            return;
                        }
                    }

                    // ストリーム配信正常終了
                    notifyRecStateChangedHandle(MSG_SEND_STREAM_ENDED);
                } finally {
                    synchronized (mBroadcastingInfoLock) {
                        mBroadcastingInfo = null; // 配信情報を空にする
                    }

                    if (br != null) {
                        try {
                            br.close();
                        } catch (IOException e) {
                            Log.w(C.TAG,
                                    "IOException occurred when BufferedReader close.",
                                    e);
                        }
                        br = null;
                    }
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            Log.w(C.TAG,
                                    "IOException occurred when InputStreamReader close.",
                                    e);
                        }
                        in = null;
                    }
                    if (pr != null) {
                        pr.close();
                        pr = null;
                    }
                    if (sockOut != null) {
                        try {
                            sockOut.close();
                        } catch (IOException e) {
                            Log.w(C.TAG,
                                    "IOException occurred when close socket output stream.",
                                    e);
                        }
                        sockOut = null;
                    }
                    if (sockIn != null) {
                        try {
                            sockIn.close();
                        } catch (IOException e) {
                            Log.w(C.TAG,
                                    "IOException occurred when close socket input stream.",
                                    e);
                        }
                        sockIn = null;
                    }
                    if (socket != null) {
                        try {
                            socket.close();
                            Log.i(C.TAG, "Disconnected to "
                                    + socket.getInetAddress()
                                            .getHostAddress() + ":"
                                    + socket.getPort() + ".");
                        } catch (IOException e) {
                            Log.w(C.TAG,
                                    "IOException occurred when close socket.",
                                    e);
                        }
                        socket = null;
                    }
                }
            } finally {
                if (mBroadcastState.get() == BROADCAST_STATE_STOPPING) {
                    mBroadcastState.set(BROADCAST_STATE_STOPPED);
                }

                Log.d(C.TAG, "Finish Send data thread.");
            }
        }

        /**
         * 録音を開始してから送信まで{@link C#WAIT_SEC_FROM_REC_START_TO_SEND_DATA}で指定された秒数を待つ
         * 
         * @throws InterruptedException 待っている間に割り込みが入った
         */
        private void waitFromRecStartToSendData() throws InterruptedException {
            if (C.WAIT_SEC_FROM_REC_START_TO_SEND_DATA > 0) {
                // 録音開始するまでポーリングで待つ
                while ((mBroadcastState.isConnectingOrBroadcasting())
                        && mRecStartTime < 0) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Log.w(C.TAG, "Interrupted wait to rec margin.", e);
                        throw e;
                    }
                }
                // 録音開始してから指定の秒数だけ待つ
                if ((mBroadcastState.isConnectingOrBroadcasting())
                        && mRecStartTime >= 0) {
                    // 待つ時間のうちすでに録音開始してからここに到達するまでの時間は、待ち時間から差し引く
                    final long waitTime = (C.WAIT_SEC_FROM_REC_START_TO_SEND_DATA * 1000)
                            - (System.currentTimeMillis() - mRecStartTime);
                    if (waitTime > 10) {
                        if (C.LOCAL_LOG) {
                            Log.v(C.TAG,
                                    "Sleep "
                                            + String.valueOf(((float) waitTime) / 1000)
                                            + " sec.");
                        }
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException e) {
                            Log.w(C.TAG, "Interrupted wait to rec margin.", e);
                            throw e;
                        }
                    }
                }
            }
        }

        /**
         * ヘッダを生成する
         * 
         * @return 生成したヘッダ
         */
        private String createHeader() {
            String result = "SOURCE "
                    + mBroadcastConfig.getChannelMount()
                    + " ICE/1.0\r\n";
            result += "Content-Type: audio/mpeg\r\n";
            result += sUserAgent + "\r\n";
            result += "Authorization: Basic c291cmNlOmxhZGlv\r\n";
            result += "ice-name: "
                    + mBroadcastConfig.getChannelTitle()
                    + "\r\n";
            result += "ice-genre: "
                    + mBroadcastConfig.getChannelGenre()
                    + "\r\n";
            result += "ice-description: "
                    + mBroadcastConfig.getChannelDescription()
                    + "\r\n";
            result += "ice-url: "
                    + mBroadcastConfig.getChannelUrl() + "\r\n";
            result += "ice-bitrate: "
                    + String.valueOf(mBroadcastConfig
                            .getAudioBrate()) + "\r\n";
            result += "ice-public: 0\r\n";
            result += "ice-audio-info:ice-samplerate="
                    + String.valueOf(mBroadcastConfig
                            .getAudioSampleRate())
                    + ";ice-bitrate="
                    + String.valueOf(mBroadcastConfig
                            .getAudioBrate())
                    + ";ice-channels="
                    + String.valueOf(mBroadcastConfig
                            .getAudioChannel()) + "\r\n";
            result += "x-ladio-info:charset=sjis;dj="
                    + mBroadcastConfig.getChannelDjName()
                    + "\r\n";
            result += "\r\n"; // ヘッダの終了は空行

            return result;
        }

        /**
         * 再接続する
         * 
         * @param broadcastConfig 接続設定
         */
        private void reconnect(final BroadcastConfig broadcastConfig) throws InterruptedException {
            final long waitTime = System.currentTimeMillis() + C.WAIT_RECONNECT_MSEC;

            if (mBroadcastState.isStoppedOrStopping()) {
                mBroadcastState.set(BROADCAST_STATE_STOPPED);
                notifyRecStateChangedHandle(MSG_STOP_WAIT_RECONNECT);
                return;
            }

            mBroadcastState.set(BROADCAST_STATE_CONNECTING);

            Log.i(C.TAG, "Wait " + String.valueOf(((float) waitTime) / 1000)
                    + "sec before reconnect.");
            notifyRecStateChangedHandle(MSG_RECONNECT_STARTED);
            while (waitTime > System.currentTimeMillis()) {
                if (mBroadcastState.isStoppedOrStopping()) {
                    mBroadcastState.set(BROADCAST_STATE_STOPPED);
                    notifyRecStateChangedHandle(MSG_STOP_WAIT_RECONNECT);
                    return;
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.w(C.TAG, "Interrupted wait to recoonect.", e);
                    throw e;
                }
            }

            Log.i(C.TAG, "Reconnect.");
            (new SendDataThread(broadcastConfig)).start();
        }
    }

    /**
     * 停止する
     */
    public final void stop() {
        mBroadcastState.set(BROADCAST_STATE_STOPPING);;
    }

    /**
     * 配信状態を取得すする
     * 
     * @see VoiceSender#BROADCAST_STATE_STOPPED
     * @see VoiceSender#BROADCAST_STATE_CONNECTING
     * @see VoiceSender#BROADCAST_STATE_BROADCASTING
     * @see VoiceSender#BROADCAST_STATE_STOPPING
     */
    public final int getBroadcastState() {
        return mBroadcastState.get();
    }

    /**
     * 配信情報を取得する {@link #getBroadcastState()}と{@link #getBroadcastInfo()}
     * の間で厳密に同期をとっていないので注意。
     * 
     * @return 配信中の番組の情報。<br />
     *         配信中でない場合はnull。
     */
    public final BroadcastInfo getBroadcastInfo() {
        synchronized (mBroadcastingInfoLock) {
            return mBroadcastingInfo;
        }
    }

    /**
     * 音量を設定する
     * 
     * @return 音量。1倍を100%とする。
     */
    public final char getVolumeRate() {
        return mVolumeRate;
    }

    /**
     * 音量を設定する
     * 
     * @param volumeRate 音量。1倍を100%とする。
     */
    public final void setVolumeRate(char volumeRate) {
        mVolumeRate = volumeRate;
        mVolumeRateFloat = (float) volumeRate / 100F;
        if (C.LOCAL_LOG) {
            Log.v(C.TAG, "Set volume rate " + String.valueOf(mVolumeRateFloat) + ".");
        }
    }

    /**
     * 音量を変化させるか
     * 
     * @return 音量を変化させる場合はtrue、それ以外はfalse
     */
    private final boolean isVolumeRateChanged() {
        return mVolumeRate != 100;
    }

    /**
     * 動作の状態変化を通知するハンドラを追加する 動作状態が変わった際には、Handlerのwhatに変更後の状態が格納される。
     * 
     * @param handler 動作の状態変化を通知するハンドラ
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
     * @see VoiceSender#MSG_RECONNECT_STARTED
     * @see VoiceSender#MSG_STOP_WAIT_RECONNECT
     */
    public final void addBroadcastStateChangedHandler(Handler handler) {
        if (handler != null) {
            synchronized (mBroadcastStateChangedHandlerListLock) {
                mBroadcastStateChangedHandlerList.add(handler);
            }
        }
    }

    /**
     * 動作の状態変化を通知するハンドラを削除する
     * 
     * @param handler 動作の状態変化を通知するハンドラ
     */
    public final void removeBroadcastStateChangedHandler(Handler handler) {
        synchronized (mBroadcastStateChangedHandlerListLock) {
            mBroadcastStateChangedHandlerList.remove(handler);
        }
    }

    /**
     * 動作の状態変化を通知するハンドラをクリアする
     */
    public final void clearBroadcastStateChangedHandler() {
        synchronized (mBroadcastStateChangedHandlerListLock) {
            mBroadcastStateChangedHandlerList.clear();
        }
    }

    /**
     * 音の大きさを通知するハンドラを追加する
     * 
     * @param handler 音の大きさを通知するハンドラ
     */
    public final void addLoudnessdHandler(Handler handler) {
        if (handler != null) {
            synchronized (mLoudnessHandlerListLock) {
                mLoudnessHandlerList.add(handler);
            }
        }
    }

    /**
     * 音の大きさを通知するハンドラを削除する
     * 
     * @param handler 音の大きさを通知するハンドラ
     */
    public final void removeLoudnessHandler(Handler handler) {
        synchronized (mLoudnessHandlerListLock) {
            mLoudnessHandlerList.remove(handler);
        }
    }

    /**
     * 音の大きさを通知するハンドラをクリアする
     */
    public final void clearLoudnessHandler() {
        synchronized (mLoudnessHandlerListLock) {
            mLoudnessHandlerList.clear();
        }
    }

    /**
     * 動作のの状態変化を通知する
     * 
     * @param what 通知する状態
     */
    private void notifyRecStateChangedHandle(int what) {
        for (Handler h : getBroadcastStateChangedHandlerListClone()) {
            if (h != null) {
                h.sendEmptyMessage(what);
            }
        }
    }

    /**
     * 配信状態が変わった際のハンドラーリストのクローンしたリストを取得する。 浅いクローンなので注意。
     * 
     * @return 再生状態が変わった際のハンドラーリストのクローンしたリスト
     */
    @SuppressWarnings("unchecked")
    private ArrayList<Handler> getBroadcastStateChangedHandlerListClone() {
        synchronized (mBroadcastStateChangedHandlerListLock) {
            return (ArrayList<Handler>) mBroadcastStateChangedHandlerList.clone();
        }
    }

    /**
     * ねとらじサーバに通知するUserAgentを設定する
     * 
     * @param appName アプリ名。nullもしくは空文字の場合はアプリ名は"VoiseSender"となる。
     * @param appVersion アプリバージョン
     */
    public static void setUserAgentInfo(String appName, String appVersion) {
        sUserAgent = "User-Agent: ";
        if (appName != null && appName.length() != 0) {
            sUserAgent += appName;
        } else {
            sUserAgent += "VoiseSender";
        }
        if (appVersion != null && appVersion.length() != 0) {
            sUserAgent += ("/" + appVersion);
        }
        sUserAgent += " (Linux; U; Android " + Build.VERSION.RELEASE + "; "
                + Build.MANUFACTURER + " " + Build.MODEL + ")";
    }


    /**
     * 配信状態
     */
    private class BroadcastState {
        /**
         * 配信状態
         */
        private int mBroadcastState = BROADCAST_STATE_STOPPED;
        
        /**
         * ロックオブジェクト
         */
        private final Object mLock = new Object();
        
        /**
         * 配信状態を取得する
         * 
         * @see VoiceSender#BROADCAST_STATE_STOPPED
         * @see VoiceSender#BROADCAST_STATE_CONNECTING
         * @see VoiceSender#BROADCAST_STATE_BROADCASTING
         * @see VoiceSender#BROADCAST_STATE_STOPPING
         */
        public int get() {
            synchronized (mLock) {
                return mBroadcastState;
            }
        }
        
        /**
         * 接続中ないしは配信中であるかを取得する
         * 
         * @return 接続中ないしは配信中である場合はtrue、それ以外はfalse
         */
        public boolean isConnectingOrBroadcasting(){
            synchronized (mLock) {
                return mBroadcastState == BROADCAST_STATE_CONNECTING || mBroadcastState == BROADCAST_STATE_BROADCASTING;
            }
        }
        
        /**
         * 動作していないないしは停止中であるかを取得する
         * 
         * @return 動作していないないしは停止中である場合はtrue、それ以外はfalse
         */
        public boolean isStoppedOrStopping(){
            synchronized (mLock) {
                return mBroadcastState == BROADCAST_STATE_STOPPED || mBroadcastState == BROADCAST_STATE_STOPPING;
            }
        }

        /**
         * 配信状態を設定する
         * 
         * @param broadcastState 配信状態
         * 
         * @see VoiceSender#BROADCAST_STATE_STOPPED
         * @see VoiceSender#BROADCAST_STATE_CONNECTING
         * @see VoiceSender#BROADCAST_STATE_BROADCASTING
         * @see VoiceSender#BROADCAST_STATE_STOPPING
         */
        public void set(int broadcastState) {
            synchronized (mLock) {
                switch (broadcastState) {
                    case BROADCAST_STATE_STOPPED:
                        Log.d(C.TAG, "Broadcast state change to BROADCAST_STATE_STOPPED.");
                        mBroadcastState = broadcastState;
                        break;
                    case BROADCAST_STATE_CONNECTING:
                        Log.d(C.TAG, "Broadcast state change to BROADCAST_STATE_CONNECTING.");
                        mBroadcastState = broadcastState;
                        break;
                    case BROADCAST_STATE_BROADCASTING:
                        Log.d(C.TAG, "Broadcast state change to BROADCAST_STATE_BROADCASTING.");
                        mBroadcastState = broadcastState;
                        break;
                    case BROADCAST_STATE_STOPPING:
                        Log.d(C.TAG, "Broadcast state change to BROADCAST_STATE_STOPPING.");
                        mBroadcastState = broadcastState;
                        break;
                    default:
                        Log.w(C.TAG, "Specified unknown broadcast state.");
                        break;
                }
            }
        }
    }
}
