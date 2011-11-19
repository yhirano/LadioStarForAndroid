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

import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * サービスで配信する際の配信サービスとの通信クラス
 */
public class BroadcastServiceConnector {

	/**
	 * サービスに接続した
	 */
	public static final int MSG_CONNECTED_SERVICE = 0;

	/**
	 * サービスへの接続に失敗したため配信を開始できない
	 * 
	 * エラーがあった場合、このメッセージは
	 * {@link BroadcastServiceConnector#start(int, int, int, int, String, String, String, String, String, String, String, String)}
	 * で再生が開始できない場合にのみ発行される。
	 */
	public static final int MSG_ERROR_START_SERVICE_CONNECTION = 1;

	/**
	 * サービスへの接続に失敗したため配信を停止できない
	 * 
	 * {@link VoiceSender#stop()}で終了できない場合にのみ発行される。
	 */
	public static final int MSG_ERROR_STOP_SERVICE_CONNECTION = 2;

	/**
	 * BroadcastServiceへのインターフェース
	 */
	private BroadcastServiceInterface mBroadcastServiceInterface;

	/**
	 * コンテキスト
	 */
	private Context mContext;

	/**
	 * 配信の状態変化を通知するハンドラのリスト
	 */
	private ArrayList<Handler> mBroadcastStateChangeHandlerList = new ArrayList<Handler>();;

	/**
	 * mBroadcastStateChangeHandlerListのロックオブジェクト
	 */
	private final Object mBroadcastStateChangeHandlerListLock = new Object();

	/**
	 * サービス接続の状態変化を通知するハンドラのリスト
	 */
	private ArrayList<Handler> mServiceConnectChangeHandlerList = new ArrayList<Handler>();;

	/**
	 * mServiceConnectChangeHandlerListのロックオブジェクト
	 */
	private final Object mServiceConnectChangeHandlerListLock = new Object();
	
	/**
	 * 初期化
	 * 
	 * 一番はじめに配信をする前に初期化すること
	 * 
	 * @param context
	 *            コンテキスト。アプリケーションのコンテキストを渡すこと。
	 */
	public void init(Context context) {
		this.mContext = context;

		Intent intent = new Intent(BroadcastServiceInterface.class.getName());
		context.startService(intent); // Broadcastサービス開始
		context.bindService(intent, mBroadcastServiceConn,
				Context.BIND_AUTO_CREATE);
	}

	/**
	 * 配信を開始する
	 * 
	 * @param broadcastConfig
	 *            配信設定
	 */
	public void start(BroadcastConfig broadcastConfig) {
		if (C.LOCAL_LOG) {
			Log.v(C.TAG, "Trying to start.");
		}

		try {
			if (mBroadcastServiceInterface != null) {
				mBroadcastServiceInterface.start(broadcastConfig);
			} else {
				Log.w(C.TAG, "Service interface is NULL in start.");
				notifyServiceConnectChanged(MSG_ERROR_START_SERVICE_CONNECTION);
			}
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ") occurred in start.");
			notifyServiceConnectChanged(MSG_ERROR_START_SERVICE_CONNECTION);
		}
	}

	/**
	 * 配信を停止する
	 */
	public void stop() {
		if (C.LOCAL_LOG) {
			Log.v(C.TAG, "Trying to stop.");
		}

		try {
			if (mBroadcastServiceInterface != null) {
				mBroadcastServiceInterface.stop();
			} else {
				Log.w(C.TAG, "Service interface is NULL in stop.");
				notifyServiceConnectChanged(MSG_ERROR_STOP_SERVICE_CONNECTION);
			}
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ")  occurred in stop.");
			notifyServiceConnectChanged(MSG_ERROR_STOP_SERVICE_CONNECTION);
		}
	}

	/**
	 * 配信に使用したリソースを解放する。 アプリケーションの終了時などにリソースを解放すること。
	 */
	public void release() {
		if (C.LOCAL_LOG) {
			Log.v(C.TAG, "Release VoiceSender resouce.");
		}

		mContext.unbindService(mBroadcastServiceConn);
		// 配信中で無い場合はサービスを止める
		if (getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED) {
			mContext.stopService(new Intent(BroadcastServiceInterface.class
					.getName()));
		}
	}

	/**
	 * 配信状態取得すする
	 * 
	 * @see VoiceSender#BROADCAST_STATE_STOPPED
	 * @see VoiceSender#BROADCAST_STATE_CONNECTING
	 * @see VoiceSender#BROADCAST_STATE_BROADCASTING
	 * @see VoiceSender#BROADCAST_STATE_STOPPING
	 */
	public int getBroadcastState() {
		try {
			if (mBroadcastServiceInterface != null) {
				return mBroadcastServiceInterface.getBroadcastState();
			} else {
				Log.w(C.TAG, "Service interface is NULL in isBroadcasting.");
				// どうしようもないのでとりあえずVoiceSender.BROADCAST_STATE_STOPPEDを返す
				return VoiceSender.BROADCAST_STATE_STOPPED;
			}
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString()
					+ ")  occurred in getBroadcastState.");
			// どうしようもないのでとりあえずVoiceSender.BROADCAST_STATE_STOPPEDを返す
			return VoiceSender.BROADCAST_STATE_STOPPED;
		}
	}

	/**
	 * 配信情報を取得する
	 * 
	 * @return 配信中の番組の情報。<br />
	 *         配信中でない場合はnull。
	 */
	public BroadcastInfo getBroadcastInfo() {
		try {
			if (mBroadcastServiceInterface != null) {
				return mBroadcastServiceInterface.getBroadcastInfo();
			} else {
				Log.w(C.TAG, "Service interface is NULL in getBroadcastInfo.");
				// どうしようもないのでとりあえずnullを返す
				return null;
			}
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString()
					+ ")  occurred in getBroadcastInfo.");
			// どうしようもないのでとりあえずnullを返す
			return null;
		}
	}

	/**
	 * 音量を取得する
	 * 
	 * @return 音量。1倍を100%とする。
	 */
	public char getVolumeRate() {
		try {
			if (mBroadcastServiceInterface != null) {
				return mBroadcastServiceInterface.getVolumeRate();
			} else {
				Log.w(C.TAG, "Service interface is NULL in getVolumeRate.");
				// どうしようもないので100を返す
				return 100;
			}
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ") occuerd in getVolumeRate.");
			// どうしようもないので100を返す
			return 100;
		}
	}

	/**
	 * 音量を設定する
	 * 
	 * @param volumeRate
	 *            音量。1倍を100%とする。
	 */
	public void setVolumeRate(char volumeRate) {
		try {
			if (mBroadcastServiceInterface != null) {
				mBroadcastServiceInterface.setVolumeRate(volumeRate);
			} else {
				Log.w(C.TAG, "Service interface is NULL in setVolumeRate.");
				// どうしようもないのでなにもしない
			}
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ") occuerd in setVolumeRate.");
			// どうしようもないのでなにもしない
		}
	}

	/**
	 * 配信の状態変化を通知するハンドラを追加する
	 * 
	 * 配信状態が変わった際には、Handlerのwhatに変更後の状態が格納される。
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
	 * @see VoiceSender#MSG_RECONNECT_STARTED
	 * @see VoiceSender#MSG_STOP_WAIT_RECONNECT
	 */
	public void addBroadcastStateChangedHandler(Handler handler) {
		synchronized (mBroadcastStateChangeHandlerListLock) {
			if (handler != null) {
				mBroadcastStateChangeHandlerList.add(handler);
			}
		}
	}

	/**
	 * 配信の状態変化を通知するハンドラを削除する
	 * 
	 * @param handler
	 *            動作の状態変化を通知するハンドラ
	 */
	public void removeBroadcastStateChangedHandler(Handler handler) {
		synchronized (mBroadcastStateChangeHandlerListLock) {
			mBroadcastStateChangeHandlerList.remove(handler);
		}
	}

	/**
	 * 配信の状態変化を通知するハンドラをクリアする
	 */
	public void clearBroadcastStateChangeHandler() {
		synchronized (mBroadcastStateChangeHandlerListLock) {
			mBroadcastStateChangeHandlerList.clear();
		}
	}

	/**
	 * 登録された配信の状態変化を通知するハンドラにメッセージを送信する
	 * 
	 * @param what
	 */
	private void notifyBroadcastStateChanged(int what) {
		for (Handler h : getBroadcastStateChangeHandlerListClone()) {
			if (h != null) {
				h.sendEmptyMessage(what);
			}
		}
	}

	/**
	 * 配信の状態変化を通知するハンドラリストのクローンしたリストを取得する。
	 * 
	 * 浅いクローンなので注意。
	 * 
	 * @return 配信の状態変化を通知するハンドラリストのクローンしたリスト
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<Handler> getBroadcastStateChangeHandlerListClone() {
		synchronized (mBroadcastStateChangeHandlerListLock) {
			return (ArrayList<Handler>) mBroadcastStateChangeHandlerList.clone();
		}
	}

	/**
	 * 配信の状態変化を通知するハンドラを追加する
	 * 
	 * 配信状態が変わった際には、Handlerのwhatに変更後の状態が格納される。
	 * 
	 * @param handler
	 *            動作の状態変化を通知するハンドラ
	 * 
	 * @see BroadcastServiceConnector#MSG_CONNECTED_SERVICE
	 * @see BroadcastServiceConnector#MSG_ERROR_START_SERVICE_CONNECTION
	 * @see BroadcastServiceConnector#MSG_ERROR_STOP_SERVICE_CONNECTION
	 */
	public void addServiceConnectChangeHandler(Handler handler) {
		synchronized (mServiceConnectChangeHandlerListLock) {
			if (handler != null) {
				mServiceConnectChangeHandlerList.add(handler);
			}
		}
	}

	/**
	 * 配信の状態変化を通知するハンドラを削除する
	 * 
	 * @param handler
	 *            動作の状態変化を通知するハンドラ
	 */
	public void removeServiceConnectChangeHandler(Handler handler) {
		synchronized (mServiceConnectChangeHandlerListLock) {
			mServiceConnectChangeHandlerList.remove(handler);
		}
	}

	/**
	 * 配信の状態変化を通知するハンドラをクリアする
	 */
	public void clearServiceConnectChangeHandler() {
		synchronized (mServiceConnectChangeHandlerListLock) {
			mServiceConnectChangeHandlerList.clear();
		}
	}

	/**
	 * 登録された配信の状態変化を通知するハンドラにメッセージを送信する
	 * 
	 * @param what
	 */
	private void notifyServiceConnectChanged(int what) {
		for (Handler h : getServiceConnectChangeHandlerListClone()) {
			if (h != null) {
				h.sendEmptyMessage(what);
			}
		}
	}

	/**
	 * 配信の状態変化を通知するハンドラリストのクローンしたリストを取得する。
	 * 
	 * 浅いクローンなので注意。
	 * 
	 * @return 配信の状態変化を通知するハンドラリストのクローンしたリスト
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<Handler> getServiceConnectChangeHandlerListClone() {
		synchronized (mServiceConnectChangeHandlerListLock) {
			return (ArrayList<Handler>) mServiceConnectChangeHandlerList.clone();
		}
	}
	
	/**
	 * 再生サービスからのコールバック
	 */
	BroadcastStateChangedCallbackInterface mRemoteCallback = new BroadcastStateChangedCallbackInterface.Stub() {

		@Override
		public void changed(int changedState) throws RemoteException {
			notifyBroadcastStateChanged(changedState);
		}
	};

	/**
	 * サービスへのコネクション
	 */
	private ServiceConnection mBroadcastServiceConn = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// サービスIFを取得する
			mBroadcastServiceInterface = BroadcastServiceInterface.Stub
					.asInterface(service);
			try {
				mBroadcastServiceInterface
						.registerBroadcastStateChangedCallback(mRemoteCallback);
			} catch (RemoteException e) {
				// 例外はどうしようもないので無視しておく
				Log.w(C.TAG, "RemoteException(" + e.toString() + ") occuerd.");
			}
			
			notifyServiceConnectChanged(MSG_CONNECTED_SERVICE);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			try {
				mBroadcastServiceInterface
						.unregisterBroadcastStateChangedCallback(mRemoteCallback);
			} catch (RemoteException e) {
				// 例外はどうしようもないので無視しておく
				Log.w(C.TAG, "RemoteException(" + e.toString() + ") occuerd.");
			}
			mBroadcastServiceInterface = null;
		}
	};
}
