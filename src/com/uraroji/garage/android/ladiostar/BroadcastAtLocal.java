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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

/**
 * アプリケーション内で配信する際の処理クラス
 */
public class BroadcastAtLocal implements BroadcastInterface {

	/**
	 * コンテキスト
	 */
	private Context mContext;

	/**
	 * 録音・配信モジュール
	 */
	private VoiceSender mVoiceSender = new VoiceSender();
	
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
		
		mVoiceSender.addBroadcastStateChangedHandle(new Handler() {

			@Override
			public void handleMessage(Message msg) {
				for (Handler h : getHandlerListClone()) {
					if (h != null) {
						h.sendEmptyMessage(msg.what);
					}
				}
				
				// Notificationを更新する
				{
					NotificationManager nm = (NotificationManager) mContext
							.getSystemService(Context.NOTIFICATION_SERVICE);

					// 配信情報を取得する
					final BroadcastInfo broadcastingInfo = mVoiceSender.getBroadcastInfo();
					
					/*
					 * 配信中でもbroadcastingInfoが取得できないこともある（配信開始直後）ので、
					 * broadcastingInfoがnullであるかもチェックする。
					 */
					if (mVoiceSender.isBroadcasting() == false || broadcastingInfo == null) {
						// Notificationを消す
						nm.cancel(C.NOTIFICATION_ID);
					} else {
						// Notificationを表示する
						final String notificationTitle = String
								.format(mContext
										.getString(R.string.broadcasting_notification_title_format),
										broadcastingInfo.getChannelTitle());
						Notification n = new Notification(R.drawable.icon,
								notificationTitle, System.currentTimeMillis());
						Intent intent = new Intent(mContext, MainActivity.class);
						PendingIntent contentIntent = PendingIntent.getActivity(
								mContext, 0, intent, Intent.FLAG_ACTIVITY_NEW_TASK);
						n.setLatestEventInfo(mContext, notificationTitle,
								String.valueOf(broadcastingInfo.getAudioBrate()) + "kbps/"
										+ getChsString(broadcastingInfo.getAudioChannel()),
								contentIntent);
						nm.notify(C.NOTIFICATION_ID, n);
					}
				}
			}
		});
	}

	@Override
	public void start(BroadcastConfig broadcastConfig) {
		mVoiceSender.start(broadcastConfig);
	}

	@Override
	public void stop() {
		mVoiceSender.stop();
	}

	@Override
	public void release() {
		mVoiceSender.stop();
	}

	@Override
	public boolean isBroadcasting() {
		return mVoiceSender.isBroadcasting();
	}

	@Override
	public BroadcastInfo getBroadcastInfo() {
		return mVoiceSender.getBroadcastInfo();
	}

	@Override
	public char getVolumeRate() {
		return mVoiceSender.getVolumeRate();
	}

	@Override
	public void setVolumeRate(char volumeRate) {
		mVoiceSender.setVolumeRate(volumeRate);
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
	 * チャンネル数を文字列で取得する
	 * 
	 * @param chs
	 *            チャンネル数
	 * @return チャンネル数の文字列
	 */
	private String getChsString(int chs) {
		switch (chs) {
		case 1:
			return mContext.getString(R.string.mono);
		case 2:
			return mContext.getString(R.string.stereo);
		default:
			return String.valueOf(chs);
		}
	}
}
