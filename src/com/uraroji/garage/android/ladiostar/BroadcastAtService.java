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
public class BroadcastAtService implements BroadcastInterface {

	/**
	 * サービスに接続した
	 */
	public static final int MSG_CONNECTED_SERVICE = 100;

	/**
	 * サービスへの接続に失敗したため配信を開始できない
	 * 
	 * エラーがあった場合、このメッセージは
	 * {@link BroadcastAtService#start(int, int, int, int, String, String, String, String, String, String, String, String)}
	 * で再生が開始できない場合にのみ発行される。
	 */
	public static final int MSG_ERROR_START_SERVICE_CONNECTION = 101;

	/**
	 * サービスへの接続に失敗したため配信を停止できない
	 * 
	 * {@link VoiceSender#stop()}で終了できない場合にのみ発行される。
	 */
	public static final int MSG_ERROR_STOP_SERVICE_CONNECTION = 102;

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
	private ArrayList<Handler> mHandlerList = new ArrayList<Handler>();;

	/**
	 * mHandlerListのロックオブジェクト
	 */
	private final Object mHandlerListLock = new Object();

	@Override
	public void init(Context context) {
		this.mContext = context;

		Intent intent = new Intent(BroadcastServiceInterface.class.getName());
		context.startService(intent); // Broadcastサービス開始
		context.bindService(intent, mBroadcastServiceConn,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void start(BroadcastConfig broadcastConfig) {
		if (C.LOCAL_LOG) {
			Log.v(C.TAG, "Trying to start.");
		}

		try {
			mBroadcastServiceInterface.start(broadcastConfig);
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ") occurred.");
			notifyPlayStateChanged(MSG_ERROR_START_SERVICE_CONNECTION);
		}
	}

	@Override
	public void stop() {
		if (C.LOCAL_LOG) {
			Log.v(C.TAG, "Trying to stop.");
		}

		try {
			mBroadcastServiceInterface.stop();
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ")  occurred.");
			notifyPlayStateChanged(MSG_ERROR_STOP_SERVICE_CONNECTION);
		}
	}

	@Override
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

	@Override
	public int getBroadcastState() {
		try {
			return mBroadcastServiceInterface.getBroadcastState();
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ")  occurred.");
			// どうしようもないのでとりあえずBROADCAST_STATE_STOPPEDを返す
			return VoiceSender.BROADCAST_STATE_STOPPED;
		}
	}


	@Override
	public BroadcastInfo getBroadcastInfo() {
		try {
			return mBroadcastServiceInterface.getBroadcastInfo();
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ")  occurred.");
			// どうしようもないのでとりあえずnullを返す
			return null;
		}
	}

	@Override
	public char getVolumeRate() {
		try {
			return mBroadcastServiceInterface.getVolumeRate();
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ") occuerd.");
			// どうしようもないので100を返す
			return 100;
		}
	}

	@Override
	public void setVolumeRate(char volumeRate) {
		try {
			mBroadcastServiceInterface.setVolumeRate(volumeRate);
		} catch (RemoteException e) {
			Log.w(C.TAG, "RemoteException(" + e.toString() + ") occuerd.");
			// どうしようもないのでなにもしない
		}
	}

	@Override
	public void addBroadcastStateChangedHandler(Handler handler) {
		synchronized (mHandlerListLock) {
			if (handler != null) {
				mHandlerList.add(handler);
			}
		}
	}

	@Override
	public void removeBroadcastStateChangedHandler(Handler handler) {
		synchronized (mHandlerListLock) {
			mHandlerList.remove(handler);
		}
	}

	@Override
	public void clearBroadcastStateChangedHandler() {
		synchronized (mHandlerListLock) {
			mHandlerList.clear();
		}
	}

	/**
	 * 登録されたハンドラにメッセージを送信する
	 * 
	 * @param what
	 */
	private void notifyPlayStateChanged(int what) {
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
	 * 再生サービスからのコールバック
	 */
	BroadcastStateChangedCallbackInterface mRemoteCallback = new BroadcastStateChangedCallbackInterface.Stub() {

		@Override
		public void changed(int changedState) throws RemoteException {
			notifyPlayStateChanged(changedState);
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
			
			notifyPlayStateChanged(MSG_CONNECTED_SERVICE);
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
