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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class BroadcastService extends Service {

    /**
     * 配信モジュール
     */
    private VoiceSender mVoiceSender = new VoiceSender();

    // 着信時に配信を停止するために着信を感知するためのTelephonyManager
    private TelephonyManager mTelephonyManager;

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {

        @Override
        public void onCallStateChanged(int state, String number) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING: // 着信時
                    Log.d(C.TAG, "Stop because the ringing.");
                    mVoiceSender.stop(); // 配信終了
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        // 配信の開始時、停止時にメッセージを表示するためのHandlerを登録する
        mVoiceSender.addBroadcastStateChangedHandle(new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case VoiceSender.MSG_ERROR_NOT_SUPPORTED_RECORDING_PARAMETERS:
                        Toast.makeText(
                                BroadcastService.this,
                                R.string.disable_rec_start_change_channel_or_sample,
                                Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_REC_START:
                        Toast.makeText(BroadcastService.this,
                                R.string.disable_rec_start, Toast.LENGTH_LONG)
                                .show();
                        break;
                    case VoiceSender.MSG_REC_STARTED:
                        break;
                    case VoiceSender.MSG_ERROR_AUDIO_RECORD:
                        Toast.makeText(BroadcastService.this, R.string.disable_rec,
                                Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_PCM_BUFFER_OVERFLOW:
                        Toast.makeText(BroadcastService.this,
                                R.string.encode_too_slow, Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ENCODE_STARTED:
                        break;
                    case VoiceSender.MSG_ERROR_AUDIO_ENCODE:
                        Toast.makeText(BroadcastService.this,
                                R.string.disable_encode, Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_MP3_BUFFER_OVERFLOW:
                        Toast.makeText(BroadcastService.this,
                                R.string.send_data_too_slow, Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST:
                    case VoiceSender.MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER:
                        Toast.makeText(BroadcastService.this,
                                R.string.disable_connect_server, Toast.LENGTH_LONG)
                                .show();
                        break;
                    case VoiceSender.MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER:
                    case VoiceSender.MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_AUTHENTICATION_REQUIRED:
                        Toast.makeText(BroadcastService.this,
                                R.string.disable_connect_server, Toast.LENGTH_LONG)
                                .show();
                        break;
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_IN_USE:
                        Toast.makeText(BroadcastService.this,
                                R.string.disable_connect_server_mountpoint_in_use,
                                Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_TOO_LONG:
                        Toast.makeText(
                                BroadcastService.this,
                                R.string.disable_connect_server_mountpoint_too_long,
                                Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_CONTENT_TYPE_NOT_SUPPORTED:
                        Toast.makeText(
                                BroadcastService.this,
                                R.string.disable_connect_server_content_type_not_supported,
                                Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_TOO_MANY_SOURCES_CONNECTED:
                        Toast.makeText(
                                BroadcastService.this,
                                R.string.disable_connect_server_too_many_sources_connected,
                                Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_UNKNOWN_ERROR:
                        Toast.makeText(BroadcastService.this,
                                R.string.disable_connect_server_unknown_error,
                                Toast.LENGTH_LONG).show();
                        break;
                    case VoiceSender.MSG_ERROR_SEND_HEADER_DATA:
                    case VoiceSender.MSG_ERROR_RECV_HEADER_RESPONSE:
                        Toast.makeText(BroadcastService.this,
                                R.string.disable_connect_server, Toast.LENGTH_LONG)
                                .show();
                        break;
                    case VoiceSender.MSG_SEND_STREAM_STARTED:
                        break;
                    case VoiceSender.MSG_ERROR_SEND_STREAM_DATA:
                        Toast.makeText(BroadcastService.this,
                                R.string.disconnected_server, Toast.LENGTH_LONG)
                                .show();
                        break;
                    case VoiceSender.MSG_SEND_STREAM_ENDED:
                    case VoiceSender.MSG_RECONNECT_STARTED:
                    case VoiceSender.MSG_STOP_WAIT_RECONNECT:
                        break;
                    default:
                        Log.w(C.TAG, "Unknown received message " + msg.what
                                + " when start.");
                        break;
                }
            }
        });

        // コールバックを配信するためのHandlerを登録する
        mVoiceSender.addBroadcastStateChangedHandle(new Handler() {

            @Override
            public void handleMessage(Message msg) {
                // コールバックを実行する
                {
                    final int n = broadcastStateChangedCallbackList
                            .beginBroadcast();

                    for (int i = 0; i < n; ++i) {
                        final BroadcastStateChangedCallbackInterface callback = broadcastStateChangedCallbackList
                                .getBroadcastItem(i);
                        if (callback != null) {
                            try {
                                callback.changed(msg.what);
                            } catch (RemoteException e) {
                                // 例外はどうしようもないので無視しておく
                                Log.w(C.TAG,
                                        "RemoteException("
                                                + e.toString() + ") occurred.");
                            }
                        }
                    }

                    broadcastStateChangedCallbackList.finishBroadcast();
                }

                // Notificationを更新する
                {
                    // 配信情報を取得する
                    final BroadcastInfo broadcastingInfo = mVoiceSender
                            .getBroadcastInfo();

                    NotificationManager nm = (NotificationManager) BroadcastService.this
                            .getSystemService(Context.NOTIFICATION_SERVICE);

                    /*
                     * 配信中でもbroadcastingInfoが取得できないこともある（配信開始直後）ので、
                     * broadcastingInfoがnullであるかもチェックする。
                     */
                    if ((mVoiceSender.getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPING || mVoiceSender
                            .getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED)
                            || broadcastingInfo == null) {
                        // Notificationを消す
                        nm.cancel(C.NOTIFICATION_ID);
                    } else {
                        // Notificationを表示する
                        final String notificationTitle = String.format(
                                BroadcastService.this
                                        .getString(R.string.broadcasting_notification_title_format),
                                broadcastingInfo.getChannelTitle());
                        Notification n = new Notification(R.drawable.icon,
                                notificationTitle, System.currentTimeMillis());
                        Intent intent = new Intent(BroadcastService.this,
                                MainActivity.class);
                        PendingIntent contentIntent = PendingIntent
                                .getActivity(BroadcastService.this, 0, intent,
                                        Intent.FLAG_ACTIVITY_NEW_TASK);
                        n.setLatestEventInfo(
                                BroadcastService.this,
                                notificationTitle,
                                String.valueOf(broadcastingInfo.getAudioBrate())
                                        + "kbps/"
                                        + getChsString(broadcastingInfo
                                                .getAudioChannel()),
                                contentIntent);
                        nm.notify(C.NOTIFICATION_ID, n);
                    }
                }
            }
        });

        // 着信を感知する
        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 着信の感知をしないようにする
        mTelephonyManager.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_NONE);
    }

    /**
     * チャンネル数を文字列で取得する
     * 
     * @param chs チャンネル数
     * @return チャンネル数の文字列
     */
    private String getChsString(int chs) {
        switch (chs) {
            case 1:
                return getString(R.string.mono);
            case 2:
                return getString(R.string.stereo);
            default:
                return String.valueOf(chs);
        }
    }

    /**
     * 配信状態が変化した通知をするコールバックのリスト
     */
    private final RemoteCallbackList<BroadcastStateChangedCallbackInterface> broadcastStateChangedCallbackList = new RemoteCallbackList<BroadcastStateChangedCallbackInterface>();

    @Override
    public IBinder onBind(Intent arg0) {
        return mInterfaceImpl;
    }

    /**
     * サービスのインターフェース実装
     */
    private BroadcastServiceInterface.Stub mInterfaceImpl = new BroadcastServiceInterface.Stub() {

        @Override
        public void start(BroadcastConfig broadcastConfig)
                throws RemoteException {
            mVoiceSender.start(broadcastConfig);

        }

        @Override
        public void stop() throws RemoteException {
            mVoiceSender.stop();
        }

        @Override
        public int getBroadcastState() throws RemoteException {
            return mVoiceSender.getBroadcastState();
        }

        @Override
        public BroadcastInfo getBroadcastInfo() throws RemoteException {
            return mVoiceSender.getBroadcastInfo();
        }

        @Override
        public char getVolumeRate() throws RemoteException {
            return mVoiceSender.getVolumeRate();
        }

        @Override
        public void setVolumeRate(char volumeRate) throws RemoteException {
            mVoiceSender.setVolumeRate(volumeRate);
        }

        @Override
        public void registerBroadcastStateChangedCallback(
                BroadcastStateChangedCallbackInterface callback)
                throws RemoteException {
            broadcastStateChangedCallbackList.register(callback);
        }

        @Override
        public void unregisterBroadcastStateChangedCallback(
                BroadcastStateChangedCallbackInterface callback)
                throws RemoteException {
            broadcastStateChangedCallbackList.unregister(callback);
        }
    };
}
