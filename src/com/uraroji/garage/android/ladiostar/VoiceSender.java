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
import java.security.InvalidParameterException;
import java.util.ArrayList;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.uraroji.garage.android.ladiostar.util.ByteRingBuffer;
import com.uraroji.garage.android.ladiostar.util.ShortRingBuffer;
import com.uraroji.garage.android.lame.SimpleLame;
import com.uraroji.garage.android.netladiolib.Server;
import com.uraroji.garage.android.netladiolib.ServersInfo;

/**
 * マイクから取得した音声をMP3変換し、サーバに送信する
 * 
 * 別スレッドでマイクからの録音、MP3への変換、音声のサーバへの送信を行う
 */
public class VoiceSender {

	static {
		System.loadLibrary("mp3lame");

		setUserAgentInfo(null, null);
	}

	/**
	 * サンプリングレート等の録音設定を端末がサポートしていない可能性がある
	 * 
	 * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_STARTED} よりも前に発行される。<br />
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
	 * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_REC_STARTED} よりも後に発行される。<br />
	 * このメッセージが発行された直後に処理は終了する。
	 */
	public static final int MSG_ERROR_AUDIO_RECORD = 3;

	/**
	 * 録音に対してエンコードが遅いなどの理由でバッファオーバーフローした
	 * 
	 * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_REC_STARTED} よりも後に発行される。<br />
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
	 * 
	 * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_ENCODE_STARTED} よりも後に発行される。<br />
	 * このメッセージが発行された直後に処理は終了する。
	 */
	public static final int MSG_ERROR_AUDIO_ENCODE = 6;

	/**
	 * エンコードに対して送信が遅いなどの理由でバッファオーバーフローした
	 * 
	 * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_ENCODE_STARTED} よりも後に発行される。<br />
	 * このメッセージが発行された直後に処理は終了する。
	 */
	public static final int MSG_ERROR_MP3_BUFFER_OVERFLOW = 7;
	
	/**
	 * ねとらじのサーバに接続が失敗した。 サーバ情報一覧が取得できない。
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
	 * エラーがあった場合、このメッセージは {@link VoiceSender#MSG_SEND_STREAM_STARTED} よりも後に発行される。<br />
	 * このメッセージが発行された直後に処理は終了する。
	 */
	public static final int MSG_ERROR_SEND_STREAM_DATA = 21;

	/**
	 * ストリームの配信を正常に終了した
	 * 
	 * このメッセージは {@link VoiceSender#MSG_ERROR_SEND_STREAM_DATA} が発行された場合には発行されない。<br />
	 * このメッセージが発行された直後に処理は終了する。
	 */
	public static final int MSG_SEND_STREAM_ENDED = 22;

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
	 * 動作中か
	 */
	private volatile boolean mIsWorking = false;

	/**
	 * 音量
	 * 
	 * 1倍を100とする音量のレート
	 * 
	 * @see mVolumeRateFloat
	 * mVolumeRateとmVolumeRateFloatはリンクしている
	 */
	private volatile char mVolumeRate = 100;

	/**
	 * 音量
	 * 
	 * 1倍を1とする音量のレート
	 * 
	 * @see mVolumeRate
	 * mVolumeRateとmVolumeRateFloatはリンクしている
	 */
	private volatile float mVolumeRateFloat = mVolumeRate / 100F;
	
	/**
	 * 配信情報
	 * 
	 * 配信中の番組の情報を格納する
	 */
	private BroadcastInfo mBroadcastingInfo;
	
	/**
	 * mBroadcastingInfoのロックオブジェクト
	 */
	private final Object mBroadcastingInfoLock = new Object();
	
	/**
	 * 録音を開始した時刻
	 */
	private volatile long mRecStartTime = -1;

	/**
	 * 配信の状態変化を通知するハンドラのリスト
	 */
	private ArrayList<Handler> mHandlerList = new ArrayList<Handler>();;

	/**
	 * mHandlerListのロックオブジェクト
	 */
	private final Object mHandlerListLock = new Object();

	/**
	 * コンストラクタ
	 */
	public VoiceSender() {
	}

	/**
	 * 開始する
	 * 
	 * @param broadcastConfig
	 *            配信設定
	 * 
	 * @throws InvalidParameterException
	 *             broadcastConfigにnullを指定した
	 */
	public final void start(final BroadcastConfig broadcastConfig) {
		if (broadcastConfig == null) {
			throw new InvalidParameterException("broadcastConfig must be not null.");
		}
		
		// 既に動作中の場合は何もしない
		if (mIsWorking) {
			return;
		}

		/*
		 * PCMバッファ（エンコード待ちバッファ）を生成する
		 * 
		 * PCMバッファサイズは指定の秒数分だけ確保する。
		 * 16bit PCMなので、サンプリングレート * チャンネル数 * 2byte(16bit) * 秒数でバッファサイズを計算
		 */
		synchronized (mPcmBufferLock) {
			mPcmBuffer = new ShortRingBuffer((broadcastConfig.getAudioSampleRate()
					* broadcastConfig.getAudioChannel() * 2 * C.PCM_BUFFER_SEC) + 2);
		}
		Log.d(C.TAG, "PCM buffersize is " + String.valueOf(mPcmBuffer.size() * 2)
				+ " bytes.");

		/*
		 * MP3バッファ（送信待ちバッファ）を生成する
		 * 
		 * MP3バッファサイズは指定の秒数分だけ確保する。
		 */
		synchronized (mMp3BufferLock) {
			mMp3Buffer = new ByteRingBuffer(
					(((broadcastConfig.getAudioBrate() / 8) * 1024) * C.MP3_BUFFER_SEC) + 2);
		}
		Log.d(C.TAG, "MP3 buffersize is " + String.valueOf(mMp3Buffer.size())
				+ " bytes.");

		mIsWorking = true; // 動作の開始フラグを立てる

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
		 * @param broadcastConfig
		 *            配信設定
		 */
		public RecThread(final BroadcastConfig broadcastConfig) {
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
				final int recBufferSizeMin = AudioRecord.getMinBufferSize(
						mBroadcastConfig.getAudioSampleRate(), channelConfig,
						AudioFormat.ENCODING_PCM_16BIT);
				// バッファサイズが取得できない。サンプリングレート等の設定を端末がサポートしていない可能性がある。
				if (recBufferSizeMin < 0) {
					mIsWorking = false; // 動作中フラグを下げる
					notifyRecStateChangedHandle(MSG_ERROR_NOT_SUPPORTED_RECORDING_PARAMETERS);
					// エラー名を変える
					return;
				}
				/*
				 * 録音に最低限必要なバッファサイズよりも、エンコード待ちバッファサイズの方が小さい場合は否応なしに中止にする。
				 * PCMバッファサイズを大きくすること。
				 */
				assert (recBufferSizeMin > mPcmBuffer.size());
				// 録音バッファサイズ。指定の秒数分だけ確保する。
				final int recBufferSize = mBroadcastConfig.getAudioSampleRate()
						* mBroadcastConfig.getAudioChannel() * 2
						* C.REC_BUFFER_SEC;
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
						mIsWorking = false; // 動作中フラグを下げる
						// 録音の開始に失敗した
						notifyRecStateChangedHandle(MSG_ERROR_REC_START);
						return;
					}

					mRecStartTime = System.currentTimeMillis(); // 録音を開始した時刻を記憶する

					// 録音が開始した
					notifyRecStateChangedHandle(MSG_REC_STARTED);

					int readSize = 0;
					while (mIsWorking) {
						readSize = audioRecord
								.read(recBuffer, 0, recBufferSize);
						if (readSize < 0) {
							mIsWorking = false; // 動作中フラグを下げる
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
									if (isVolumeRateEnable() == true) {
										int vi;
										short vs;
										for (int i = 0; i < readSize; ++i) {
											vi = (int) (recBuffer[i] * mVolumeRateFloat);
											if (vi > Short.MAX_VALUE) {
												vs = Short.MAX_VALUE;
											} else if (vi < Short.MIN_VALUE) {
												vs = Short.MIN_VALUE;
											} else {
												vs = (short) vi;
											}
											recBuffer[i] = vs;
										}
									}

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
								mIsWorking = false; // 動作中フラグを下げる
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
	}

	/**
	 * エンコードスレッド
	 */
	private  class EncodeThread extends Thread {

		/**
		 * 配信設定
		 */
		private BroadcastConfig mBroadcastConfig;
		
		/**
		 * コンストラクタ
		 * 
		 * @param broadcastConfig
		 *            配信設定
		 */
		public EncodeThread(final BroadcastConfig broadcastConfig)
		{
			mBroadcastConfig = broadcastConfig;
		}

		@Override
		public void run() {
			Log.d(C.TAG, "Start Encode thread.");

			try {
				// Lame init
				SimpleLame.init(mBroadcastConfig.getAudioSampleRate(),
						mBroadcastConfig.getAudioChannel(),
						mBroadcastConfig.getAudioSampleRate(),
						mBroadcastConfig.getAudioBrate(),
						mBroadcastConfig.getAudioMp3EncodeQuality());
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

				while (mIsWorking) {
					readSize = 0;
					while (mIsWorking) {
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
									mIsWorking = false; // 動作中フラグを下げる
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
							encResult = SimpleLame.encode(readBuffer,
									readBuffer, readSize, mp3buffer);
							break;
						case 2: // ステレオの場合
							encResult = SimpleLame.encodeBufferInterleaved(
									readBuffer, readSize / 2, mp3buffer);
							break;
						default: // ここに到達することはあり得ないはずだが一応エラーとする。
							Log.w(C.TAG,
									"Failed LAME encode. PCM channels unknown.");
							mIsWorking = false; // 動作中フラグを下げる
							// エンコードに失敗した
							notifyRecStateChangedHandle(MSG_ERROR_AUDIO_ENCODE);
							return;
						}
						if (encResult < 0) {
							Log.w(C.TAG, "Failed LAME encode(error="
									+ encResult + ").");
							mIsWorking = false; // 動作中フラグを下げる
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
									mMp3Buffer.put(mp3buffer, 0, encResult);
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
							mIsWorking = false; // 動作中フラグを下げる
							// エンコードに対して送信が遅いなどの理由でバッファオーバーフローした
							notifyRecStateChangedHandle(MSG_ERROR_MP3_BUFFER_OVERFLOW);
							return;
						}
					}
				}

				int flushResult = SimpleLame.flush(mp3buffer);
				if (flushResult < 0) {
					Log.w(C.TAG, "Failed LAME flush(error=" + flushResult
							+ ").");
					mIsWorking = false; // 動作中フラグを下げる
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
								mMp3Buffer.put(mp3buffer, 0, flushResult);
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
						mIsWorking = false; // 動作中フラグを下げる
						// エンコードに対して送信が遅いなどの理由でバッファオーバーフローした
						notifyRecStateChangedHandle(MSG_ERROR_MP3_BUFFER_OVERFLOW);
						return;
					}
				}
			} finally {
				SimpleLame.close();
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
		 * @param broadcastConfig
		 *            配信設定
		 */
		public SendDataThread(final BroadcastConfig broadcastConfig)
		{
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
						mIsWorking = false; // 動作中フラグを下げる
						notifyRecStateChangedHandle(MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST);
						return;
					}
					
					// 配信サーバを設定していない場合はもっとも空いているサーバを取得する
					if (mBroadcastConfig.getChannelServer() == null
							|| mBroadcastConfig.getChannelServer().length() == 0) {
						broadcastServer = serversInfo.getVacantServer();
					}
					// 配信サーバを設定している場合
					else {
						broadcastServer = serversInfo.getServer(mBroadcastConfig.getChannelServer());
					}

					// 何らかの理由で配信サーバが決まらなかった場合はエラーで終了
					if (broadcastServer == null) {
						Log.w(C.TAG, "Not found the broadcast server.");
						mIsWorking = false; // 動作中フラグを下げる
						notifyRecStateChangedHandle(MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER);
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
					mIsWorking = false; // 動作中フラグを下げる
					notifyRecStateChangedHandle(MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER);
					return;
				} catch (IOException e) {
					Log.w(C.TAG,
							"IOException occurred when create socket.", e);
					mIsWorking = false; // 動作中フラグを下げる
					notifyRecStateChangedHandle(MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER);
					return;
				}

				// 録音を開始してから送信まで指定された秒数を待つ
				try {
					waitFromRecStartToSendData();
				} catch (InterruptedException e) {
					mIsWorking = false; // 動作中フラグを下げる
					notifyRecStateChangedHandle(MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA);
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
						String headerStr = "SOURCE "
								+ mBroadcastConfig.getChannelMount()
								+ " ICE/1.0\r\n";
						headerStr += "Content-Type: audio/mpeg\r\n";
						headerStr += sUserAgent + "\r\n";
						headerStr += "Authorization: Basic c291cmNlOmxhZGlv\r\n";
						headerStr += "ice-name: "
								+ mBroadcastConfig.getChannelTitle()
								+ "\r\n";
						headerStr += "ice-genre: "
								+ mBroadcastConfig.getChannelGenre()
								+ "\r\n";
						headerStr += "ice-description: "
								+ mBroadcastConfig.getChannelDescription()
								+ "\r\n";
						headerStr += "ice-url: "
								+ mBroadcastConfig.getChannelUrl() + "\r\n";
						headerStr += "ice-bitrate: "
								+ String.valueOf(mBroadcastConfig
										.getAudioBrate()) + "\r\n";
						headerStr += "ice-public: 0\r\n";
						headerStr += "ice-audio-info:ice-samplerate="
								+ String.valueOf(mBroadcastConfig
										.getAudioSampleRate())
								+ ";ice-bitrate="
								+ String.valueOf(mBroadcastConfig
										.getAudioBrate())
								+ ";ice-channels="
								+ String.valueOf(mBroadcastConfig
										.getAudioChannel()) + "\r\n";
						headerStr += "x-ladio-info:charset=sjis;dj="
								+ mBroadcastConfig.getChannelDjName()
								+ "\r\n";
						headerStr += "\r\n"; // ヘッダの終了は空行
						pr.println(headerStr);
						pr.flush();
					} catch (UnsupportedEncodingException e) {
						Log.w(C.TAG,
								"UnsupportedEncodingException occurred when send header.",
								e);
						mIsWorking = false; // 動作中フラグを下げる
						// ヘッダの送信に失敗した
						notifyRecStateChangedHandle(MSG_ERROR_SEND_HEADER_DATA);
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
							mIsWorking = false; // 動作中フラグを下げる
							// 認証失敗
							notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_AUTHENTICATION_REQUIRED);
							return;
						}
						// 同名のマウントが使用中
						else if (responseStr
								.equals("HTTP/1.0 403 Mountpoint in use")) {
							Log.w(C.TAG, "Received error.(" + responseStr
									+ ")");
							mIsWorking = false; // 動作中フラグを下げる
							// 同名のマウントが使用中
							notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_IN_USE);
							return;
						}
						// マウント名前が長すぎるか短すぎる
						else if (responseStr
								.equals("HTTP/1.0 403 Mountpoint too long")) {
							Log.w(C.TAG, "Received error.(" + responseStr
									+ ")");
							mIsWorking = false; // 動作中フラグを下げる
							// ヘッダのレスポンス受信に失敗した
							notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_TOO_LONG);
							return;
						}
						// サポート外のストリーム
						else if (responseStr
								.equals("HTTP/1.0 403 Content-type not supported")) {
							Log.w(C.TAG, "Received error.(" + responseStr
									+ ")");
							mIsWorking = false; // 動作中フラグを下げる
							// ヘッダのレスポンス受信に失敗した
							notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_CONTENT_TYPE_NOT_SUPPORTED);
							return;
						}
						// 混んでいて接続できない
						else if (responseStr
								.equals("HTTP/1.0 403 too many sources connected")) {
							Log.w(C.TAG, "Received error.(" + responseStr
									+ ")");
							mIsWorking = false; // 動作中フラグを下げる
							// ヘッダのレスポンス受信に失敗した
							notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_TOO_MANY_SOURCES_CONNECTED);
							return;
						}
						// 未知のレスポンスを受信した
						else {
							Log.w(C.TAG, "Received unknown error.("
									+ responseStr + ")");
							mIsWorking = false; // 動作中フラグを下げる
							// 未知のレスポンスを受信した
							notifyRecStateChangedHandle(MSG_ERROR_RECEIVED_RESPONSE_UNKNOWN_ERROR);
							return;
						}
					} catch (IOException e) {
						Log.w(C.TAG,
								"IOException occurred when header response receved.",
								e);
						mIsWorking = false; // 動作中フラグを下げる
						// ヘッダのレスポンス受信に失敗した
						notifyRecStateChangedHandle(MSG_ERROR_RECV_HEADER_RESPONSE);
						return;
					}

					synchronized (mBroadcastingInfoLock) {
						mBroadcastingInfo = new BroadcastInfo(
								mBroadcastConfig, broadcastServer
										.getServerName().getName(),
								broadcastServer.getServerName().getPort());
					}
					
					// ストリーム配信開始
					notifyRecStateChangedHandle(MSG_SEND_STREAM_STARTED);
					
					// 読み込みサイズ
					int readSize = 0;
					// 読み込みバッファ
					byte[] readBuffer = new byte[16 * 1024];

					while (mIsWorking) {
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
									mIsWorking = false; // 動作中フラグを下げる
									// データの送信に失敗した
									notifyRecStateChangedHandle(MSG_ERROR_SEND_STREAM_DATA);
									return;
								}
							}
						}
						try {
							sockOut.write(readBuffer, 0, readSize);
							if (C.LOCAL_LOG) {
								Log.v(C.TAG,
										"Sent "
												+ String.valueOf(readSize)
												+ " bytes data.");
							}
						} catch (IOException e) {
							Log.w(C.TAG,
									"IOException occurred when send stream.",
									e);
							mIsWorking = false; // 動作中フラグを下げる
							// データの送信に失敗した
							notifyRecStateChangedHandle(MSG_ERROR_SEND_STREAM_DATA);
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
				Log.d(C.TAG, "Finish Send data thread.");
			}
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
			throw new InvalidParameterException("Unknown channel num.");
		}
	}

	/**
	 * 録音を開始してから送信まで{@link C#WAIT_SEC_FROM_REC_START_TO_SEND_DATA}で指定された秒数を待つ
	 * 
	 * @throws InterruptedException
	 *             待っている間に割り込みが入った
	 */
	private void waitFromRecStartToSendData() throws InterruptedException {
		if (C.WAIT_SEC_FROM_REC_START_TO_SEND_DATA > 0) {
			// 録音開始するまでポーリングで待つ
			while (mIsWorking == true && mRecStartTime < 0) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Log.w(C.TAG, "Interrupted wait to rec margin.", e);
					throw e;
				}
			}
			// 録音開始してから指定の秒数だけ待つ
			if (mIsWorking == true && mRecStartTime >= 0) {
				// 待つ時間のうちすでに録音開始してからここに到達するまでの時間は、待ち時間から差し引く
				final long waitTime = (C.WAIT_SEC_FROM_REC_START_TO_SEND_DATA * 1000)
						- (System.currentTimeMillis() - mRecStartTime);
				if(waitTime > 10)
				{
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
	 * 停止する
	 */
	public final void stop() {
		mIsWorking = false;
	}

	/**
	 * 動作中かを取得する。
	 * 
	 * 動作中であるとは
	 * {@link #start(int, int, int, int, String, String, String, String, String, String, String, String)}
	 * を実行し、かつ何らかの理由で配信が停止していない状態である。 よって、
	 * {@link #start(int, int, int, int, String, String, String, String, String, String, String, String)}
	 * 開始後に、実際に配信が開始されるまでの間も{@link #isBroadcasting()}はtrueを返すので注意。
	 * 
	 * @return 動作中の場合はtrue、それ以外の場合はfalse
	 */
	public final boolean isBroadcasting() {
		return mIsWorking;
	}

	/**
	 * 配信情報を取得する
	 * 
	 * 放送開始直後には、{@link #isBroadcasting()}でtrueが返ってきても
	 * {@link #getBroadcastInfo()}で nullが返ることがある。 これは
	 * {@link #isBroadcasting()}と{@link #getBroadcastInfo()}
	 * の間で同期をとっていないためである。 しかし、{@link #MSG_STARTED}が通知された以降ならば
	 * {@link #getBroadcastInfo()}はnullでない値が返る。
	 * 
	 * 放送終了間際には、{@link #isBroadcasting()}でfalseが返ってきても
	 * {@link #getBroadcastInfo()}で nullではない値が返ることがある。 これは
	 * {@link #isBroadcasting()}と{@link #getBroadcastInfo()}
	 * の間で厳密に同期をとっていないためである。 しかし、{@link #isBroadcasting()}が返る状況ならまもなく
	 * {@link #getBroadcastInfo()}でもnullが返るようになるはずである。
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
	 * @param volumeRate
	 *            音量。1倍を100%とする。
	 */
	public final void setVolumeRate(char volumeRate) {
		mVolumeRate = volumeRate;
		mVolumeRateFloat = (float)volumeRate / 100F;
		if (C.LOCAL_LOG) {
			Log.v(C.TAG, "Set volume rate " + String.valueOf(mVolumeRateFloat) + ".");
		}
	}

	/**
	 * 音量を変化させるか
	 * 
	 * @return 音量を変化させる場合はtrue、それ以外はfalse
	 */
	private final boolean isVolumeRateEnable() {
		return mVolumeRate != 100;
	}
	
	/**
	 * 動作の状態変化を通知するハンドラを追加する
	 * 
	 * 動作状態が変わった際には、Handlerのwhatに変更後の状態が格納される。
	 * 
	 * @param handler
	 *            動作の状態変化を通知するハンドラ
	 * 
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
	public final void addBroadcastStateChangedHandle(Handler handler) {
		if (handler != null) {
			synchronized (mHandlerListLock) {
				mHandlerList.add(handler);
			}
		}
	}

	/**
	 * 動作の状態変化を通知するハンドラを削除する
	 * 
	 * @param handler
	 *            動作の状態変化を通知するハンドラ
	 */
	public final void removeBroadcastStateChangedHandle(Handler handler) {
		synchronized (mHandlerListLock) {
			mHandlerList.remove(handler);
		}
	}

	/**
	 * 動作の状態変化を通知するハンドラをクリアする
	 */
	public final void clearBroadcastStateChangedHandle() {
		synchronized (mHandlerListLock) {
			mHandlerList.clear();
		}
	}

	/**
	 * 動作のの状態変化を通知する
	 * 
	 * @param what
	 *            通知する状態
	 */
	private void notifyRecStateChangedHandle(int what) {
		for (Handler h : getHandlerListClone()) {
			if (h != null) {
				h.sendEmptyMessage(what);
			}
		}
	}

	/**
	 * 配信状態が変わった際のハンドラーリストのクローンしたリストを取得する。
	 * 
	 * 浅いクローンなので注意。
	 * 
	 * @return 再生状態が変わった際のハンドラーリストのクローンしたリスト
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<Handler> getHandlerListClone() {
		synchronized (mHandlerListLock) {
			return (ArrayList<Handler>) mHandlerList.clone();
		}
	}

	/**
	 * ねとらじサーバに通知するUserAgentを設定する
	 * 
	 * @param appName
	 *            アプリ名。nullもしくは空文字の場合はアプリ名は"VoiseSender"となる。
	 * @param appVersion
	 *            アプリバージョン
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
}
