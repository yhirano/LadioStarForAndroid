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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.uraroji.garage.android.ladiostar.bugreport.AppUncaughtExceptionHandler;
import com.uraroji.garage.android.ladiostar.util.RandomStringUtils;
import com.uraroji.garage.android.netladiolib.Channel;
import com.uraroji.garage.android.netladiolib.Headline;
import com.uraroji.garage.android.netladiolib.HeadlineManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * メイン画面
 */
public class MainActivity extends Activity {

    /**
     * 設定情報
     */
    private SharedPreferences mPref;

    private final static int MENU_ID_REFERENCE_SITE = Menu.FIRST + 1;

    private final static int MENU_ID_HELP = Menu.FIRST + 2;

    private final static int MENU_ID_SETTING = Menu.FIRST + 3;

    /**
     * ミュート前の音量
     */
    private char mVolumeRateBeforeMute = 100;
    
    private TextView mBroadcastStatusTextView;

    private ProgressBar mLoudnessProgressBar;
    
    private TextView mListenersNumTextView;

    private TextView mBroadcastTimeTextView;

    private SeekBar mVolumeRateSeekbar;
    
    private Button mStartStopButton;

    /**
     * 配信の開始時、停止時にメッセージやダイアログを表示するためのHandler
     */
    private final Handler mBroadcastWatchHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case VoiceSender.MSG_ERROR_NOT_SUPPORTED_RECORDING_PARAMETERS:
                case VoiceSender.MSG_ERROR_REC_START:
                case VoiceSender.MSG_ERROR_AUDIO_RECORD:
                case VoiceSender.MSG_ERROR_PCM_BUFFER_OVERFLOW:
                case VoiceSender.MSG_ERROR_AUDIO_ENCODE:
                case VoiceSender.MSG_ERROR_MP3_BUFFER_OVERFLOW:
                case VoiceSender.MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST:
                case VoiceSender.MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER:
                case VoiceSender.MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER:
                case VoiceSender.MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA:
                case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_AUTHENTICATION_REQUIRED:
                case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_IN_USE:
                case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_TOO_LONG:
                case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_CONTENT_TYPE_NOT_SUPPORTED:
                case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_TOO_MANY_SOURCES_CONNECTED:
                case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_UNKNOWN_ERROR:
                case VoiceSender.MSG_ERROR_SEND_HEADER_DATA:
                case VoiceSender.MSG_ERROR_RECV_HEADER_RESPONSE:
                case VoiceSender.MSG_ERROR_SEND_STREAM_DATA:
                case VoiceSender.MSG_SEND_STREAM_STARTED:
                case VoiceSender.MSG_SEND_STREAM_ENDED:
                case VoiceSender.MSG_RECONNECT_STARTED:
                case VoiceSender.MSG_STOP_WAIT_RECONNECT:
                    switchViewAsBroadcastState();
                    break;
                case VoiceSender.MSG_REC_STARTED:
                case VoiceSender.MSG_ENCODE_STARTED:
                    break;
                default:
                    Log.w(C.TAG, "Unknown received message " + msg.what + " when start.");
                    break;
            }
        }
    };

    /**
     * 配信の開始時、停止時にメッセージやダイアログを表示するためのHandler
     */
    private final Handler mServiceWatchHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // サービスに接続できなかった場合のメッセージを表示する。
            switch (msg.what) {
                case BroadcastServiceConnector.MSG_CONNECTED_SERVICE:
                    break;
                case BroadcastServiceConnector.MSG_ERROR_START_SERVICE_CONNECTION:
                    switchViewAsBroadcastState();
                    (new AlertDialog.Builder(MainActivity.this))
                            .setMessage(R.string.disable_rec_start)
                            .setPositiveButton(R.string.close, null).create()
                            .show();
                    break;
                case BroadcastServiceConnector.MSG_ERROR_STOP_SERVICE_CONNECTION:
                    switchViewAsBroadcastState();
                    (new AlertDialog.Builder(MainActivity.this))
                            .setMessage(R.string.failed_rec_stop)
                            .setPositiveButton(R.string.close, null).create()
                            .show();
                    break;
                default:
                    Log.w(C.TAG, "Unknown received message " + msg.what + " when start.");
                    break;
            }
        }
    };

    /**
     * 音の大きさを表示するためのHandler
     */
    private final Handler mLoudnessHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            mLoudnessProgressBar.setProgress(msg.arg1);
        }
    };

    /**
     * 自動でリスナー数を取得
     */
    private final BroadcastListenerFetcher mBroadcastListenerFetcher = new BroadcastListenerFetcher();

    /**
     * 配信時間をカウント
     */
    private final BroadcastTimeCounter mBroadcastTimeCounter = new BroadcastTimeCounter();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // 補足されない例外をキャッチするハンドラを登録（バグレポート用）
        Thread.setDefaultUncaughtExceptionHandler(new AppUncaughtExceptionHandler(
                this));

        // VoiceSenderManager初期化
        BroadcastManager.getConnector().init(getApplicationContext());

        // アプリ名とバージョン名をVoiceSenderに設定する
        {
            String appName = null;
            String appVersion = null;
            // アプリ名とバージョン名を取得する
            {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = null;
                try {
                    ai = pm.getApplicationInfo(getPackageName(), 0);
                    appName = pm.getApplicationLabel(ai).toString();
                } catch (NameNotFoundException e) {
                    // アプリ名が取得できない場合はアプリ名にnullを設定する
                    appName = null;
                }
                PackageInfo pi = null;
                try {
                    pi = pm.getPackageInfo(getPackageName(), 0);
                    appVersion = pi.versionName;
                } catch (NameNotFoundException e) {
                    // バージョンが取得できない場合はアプリ名にnullを設定する
                    appVersion = null;
                }
            }
            VoiceSender.setUserAgentInfo(appName, appVersion);
        }

        // 設定情報
        {
            // 設定情報を取得
            mPref = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());

            // 設定が変更された場合に場合に、設定情報の表示を更新する
            mPref.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {

                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                        String key) {
                    invalidateBroadcastSetting();
                }
            });

            // 未設定項目をデフォルトの設定で埋める
            setDefaultSetting();
        }

        mBroadcastStatusTextView = (TextView) findViewById(R.id.BroadcastStatusTextView);

        mLoudnessProgressBar = (ProgressBar) findViewById(R.id.LoudnessProgressBar);
        mLoudnessProgressBar.setMax(VoiceSender.MAX_LOUDNESS);
        
        mListenersNumTextView = (TextView) findViewById(R.id.ListenersNumTextView);
        
        mBroadcastTimeTextView = (TextView) findViewById(R.id.BroadcastTimeTextView);
        
        mStartStopButton = (Button) findViewById(R.id.StartStopButton);
        mStartStopButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                switch (BroadcastManager.getConnector().getBroadcastState()) {
                    case VoiceSender.BROADCAST_STATE_STOPPED:
                        start();
                        break;
                    case VoiceSender.BROADCAST_STATE_CONNECTING:
                    case VoiceSender.BROADCAST_STATE_BROADCASTING:
                        stop();
                        break;
                    case VoiceSender.BROADCAST_STATE_STOPPING:
                    default:
                        break;
                }
            }
        });

        final TextView volumeRateTextView = (TextView) findViewById(R.id.VolumeRateTextView);

        mVolumeRateSeekbar = (SeekBar) findViewById(R.id.VolumeRateSeekBar);
        mVolumeRateSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                ;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                ;
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                volumeRateTextView.setText(String.valueOf(progress) + "%");
                BroadcastManager.getConnector().setVolumeRate(
                        (char) progress);
            }
        });

        final Button muteButton = (Button) findViewById(R.id.MuteButton);
        muteButton.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                char volumeRate = BroadcastManager.getConnector()
                        .getVolumeRate();
                // 音量が0以外の場合はミュートにする
                if (volumeRate > 0) {
                    mVolumeRateBeforeMute = volumeRate;
                    mVolumeRateSeekbar.setProgress(0);
                }
                // 音量が0の場合はミュート解除する
                else {
                    mVolumeRateSeekbar.setProgress(mVolumeRateBeforeMute);
                }
            }
        });
        
        /*
         * サービスに接続した直後にボタン類の書き換えを行う。
         * サービスに接続するまでは配信中かどうかを確認できないため、ボタン類の書き換えができない。
         * よって、サービス接続直後にボタン類の書き換えを行う。
         */
        BroadcastManager.getConnector().addServiceConnectChangeHandler(
                new Handler() {

                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case BroadcastServiceConnector.MSG_CONNECTED_SERVICE:
                                switchViewAsBroadcastState();

                                final char volumeRate = BroadcastManager.getConnector()
                                        .getVolumeRate();
                                mVolumeRateSeekbar.setProgress(volumeRate);
                                mVolumeRateBeforeMute = volumeRate;

                                /*
                                 * サービスは1度起動したら、配信されていない状態でアプリを終了しない限り 停止しない。
                                 * よって、1度サービスを起動してしまえば音量を取得できるため、
                                 * サービス接続通知による音量取得は必要なくなるので、
                                 * サービス接続通知の受信をする必要が無い。
                                 */
                                BroadcastManager.getConnector()
                                        .removeServiceConnectChangeHandler(this);
                                break;
                            default:
                                Log.w(C.TAG, "Unknown received message " + msg.what
                                        + " when watch service connection.");
                                break;
                        }
                    }
                });

        // 配信開始後にメッセージやダイアログを表示するために、BoladcastManagerにHandlerを設定する。
        BroadcastManager.getConnector()
                .addBroadcastStateChangedHandler(mBroadcastWatchHandler);
        BroadcastManager.getConnector()
                .addServiceConnectChangeHandler(mServiceWatchHandler);
        // 音の大きさを表示するために、BoladcastManagerにHandlerを設定する。
        BroadcastManager.getConnector().addLoudnessHandler(mLoudnessHandler);

        // リスナー数の取得開始
        mBroadcastListenerFetcher.start();

        // 配信時間更新
        mBroadcastTimeCounter.start();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // 前回バグで強制終了した場合はダイアログ表示
        AppUncaughtExceptionHandler.showBugReportDialogIfExist();

        // 配信情報の表示を表示する
        invalidateBroadcastSetting();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 配信開始後にメッセージ・ダイアログを表示するための設定済みHandlerを削除する
        BroadcastManager.getConnector()
                .removeBroadcastStateChangedHandler(mBroadcastWatchHandler);
        BroadcastManager.getConnector()
                .removeServiceConnectChangeHandler(mServiceWatchHandler);
        // 音の大きさを表示するための設定済みHandlerを削除する
        BroadcastManager.getConnector().removeLoudnessHandler(mLoudnessHandler);

        // リスナー数の取得終了
        mBroadcastListenerFetcher.shutdown();

        // 配信時間更新終了
        mBroadcastTimeCounter.shutdown();

        switchViewAsBroadcastState();
        BroadcastManager.getConnector().release();
    }

    // オプションメニュー作成
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // メニューアイテムを追加
        menu.add(Menu.NONE, MENU_ID_REFERENCE_SITE, Menu.NONE,
                R.string.reference_site).setIcon(R.drawable.ic_menu_globe);
        menu.add(Menu.NONE, MENU_ID_HELP, Menu.NONE, R.string.help).setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(Menu.NONE, MENU_ID_SETTING, Menu.NONE, R.string.setting).setIcon(
                android.R.drawable.ic_menu_preferences);

        return super.onCreateOptionsMenu(menu);
    }

    // オプションメニュー表示
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 関連サイトが存在する場合にのみ有効にする
        menu.findItem(MENU_ID_REFERENCE_SITE).setEnabled(getSettingChannelUrl().length() != 0);
        // 配信中は設定を無効にする
        menu.findItem(MENU_ID_SETTING)
                .setEnabled(
                        BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED);

        return super.onPrepareOptionsMenu(menu);
    }

    // オプションメニューアイテムの選択
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_REFERENCE_SITE:
                String urlString = getSettingChannelUrl();
                if (urlString.length() != 0) {
                    try {
                        Uri uri = Uri.parse(urlString);
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return false;
                    } catch (ActivityNotFoundException e) {
                        return super.onOptionsItemSelected(item);
                    }
                } else {
                    return super.onOptionsItemSelected(item);
                }
            case MENU_ID_HELP:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.help_main_url))));
                    return false;
                } catch (ActivityNotFoundException e) {
                    return super.onOptionsItemSelected(item);
                }
            case MENU_ID_SETTING:
                if (BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED) {
                    try {
                        startActivity(new Intent(this,
                                LadioStarPreferenceActivity.class));
                        return false;
                    } catch (ActivityNotFoundException e) {
                        return super.onOptionsItemSelected(item);
                    }
                } else {
                    return super.onOptionsItemSelected(item);
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int volume;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                volume = mVolumeRateSeekbar.getProgress();
                if (volume > 0) {
                    mVolumeRateSeekbar.setProgress(volume - 1);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                volume = mVolumeRateSeekbar.getProgress();
                if (volume < mVolumeRateSeekbar.getMax()) {
                    mVolumeRateSeekbar.setProgress(volume + 1);
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                // ボリュームダウン・アップのキー押し上げを殺す
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    /**
     * 設定値の入っていない設定項目に対してデフォルトの設定値を入れる
     */
    private void setDefaultSetting() {
        SharedPreferences.Editor prefEditor = mPref.edit();

        String djName = mPref.getString("channel_dj_name", "");
        if (djName.length() == 0) {
            prefEditor.putString("channel_dj_name",
                    getString(R.string.anonymous_dj_name));
        }

        String title = mPref.getString("channel_title", "");
        if (title.length() == 0) {
            prefEditor.putString("channel_title",
                    getString(R.string.anonymous_title));
        }

        String description = mPref.getString("channel_description", "");
        if (description.length() == 0) {
            prefEditor.putString("channel_description",
                    getString(R.string.anonymous_description));
        }

        String genre = mPref.getString("channel_genre", "");
        if (genre.length() == 0) {
            prefEditor.putString("channel_genre",
                    getString(R.string.anonymous_genre));
        }

        String mount = mPref.getString("channel_mount", "");
        if ((mount.length() == 0) || (mount.length() == 1 && mount.charAt(0) == '/')) {
            prefEditor.putString("channel_mount",
                    "/" + RandomStringUtils.randomAlphabetic(C.DEFAULT_MOUNT_STRING_LENGTH));
        } else if (mount.length() != 0 && mount.charAt(0) != '/') {
            prefEditor.putString("channel_mount", "/" + mount);
        }

        String server = mPref.getString("channel_server", "");
        if (server.length() == 0) {
            prefEditor.putString("channel_server", "");
        }

        String bitrate = mPref.getString("audio_bitrate", "");
        if (bitrate.length() == 0) {
            prefEditor.putString("audio_bitrate",
                    String.valueOf(C.DEFAULT_AUDIO_BITRATE));
        }

        String channel = mPref.getString("audio_channel", "");
        if (channel.length() == 0) {
            prefEditor.putString("audio_channel",
                    String.valueOf(C.DEFAULT_AUDIO_CHANNEL));
        }

        String sampleRate = mPref.getString("audio_sample_rate", "");
        if (sampleRate.length() == 0) {
            prefEditor.putString("audio_sample_rate",
                    String.valueOf(C.DEFAULT_AUDIO_SAMPLE_RATE));
        }

        String mp3EncodeQuality = mPref.getString("audio_mp3_encode_quality", "");
        if (mp3EncodeQuality.length() == 0) {
            prefEditor.putString("audio_mp3_encode_quality",
                    String.valueOf(C.DEFAULT_AUDIO_MP3_ENCODE_QUALITY));
        }

        prefEditor.commit();
    }

    /**
     * 設定値「DJ名」を取得する
     * 
     * @return 設定値「DJ名」
     */
    private String getSettingChannelDjName() {
        String result = mPref.getString("channel_dj_name", getString(R.string.anonymous_dj_name));
        if (result.length() == 0) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.putString("channel_dj_name",
                    getString(R.string.anonymous_dj_name));
            prefEditor.commit();
            result = mPref.getString("channel_dj_name", getString(R.string.anonymous_dj_name));
        }
        return result;
    }

    /**
     * 設定値「タイトル」を取得する
     * 
     * @return 設定値「タイトル」
     */
    private String getSettingChannelTitle() {
        String result = mPref.getString("channel_title",
                getString(R.string.anonymous_title));
        if (result.length() == 0) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.putString("channel_title",
                    getString(R.string.anonymous_title));
            prefEditor.commit();
            result = mPref.getString("channel_title",
                    getString(R.string.anonymous_title));
        }
        return result;
    }

    /**
     * 設定値「番組の説明」を取得する
     * 
     * @return 設定値「番組の説明」
     */
    private String getSettingChannelDescription() {
        String result = mPref.getString("channel_description",
                getString(R.string.anonymous_description));
        if (result.length() == 0) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.putString("channel_description",
                    getString(R.string.anonymous_description));
            prefEditor.commit();
            result = mPref.getString("channel_description",
                    getString(R.string.anonymous_description));
        }
        return result;
    }

    /**
     * 設定値「関連URL」を取得する
     * 
     * @return 設定値「関連URL」
     */
    private String getSettingChannelUrl() {
        String result = mPref.getString("channel_url", "");

        // 関連URLが空の場合は、そのまま空の文字列を返す
        if (result.length() == 0) {
            return result;
        }
        // 関連URLが空でない場合
        else {
            // 関連URLがURLとして不正な場合
            if (isValidHttpUrl(result) == false) {
                SharedPreferences.Editor prefEditor = mPref.edit();
                prefEditor.putString("channel_url", "");
                prefEditor.commit();
                result = mPref.getString("channel_url", "");
            }

            return result;
        }
    }

    /**
     * 指定された文字列がHTTP URLとして正しい形式かを調べる
     * 
     * @param url URLとして正しいかを調査したい文字列
     * @return ただしい場合はtrue、それ以外の場合はfalse
     */
    private static boolean isValidHttpUrl(String url) {
        if (url == null || url.length() == 0) {
            return false;
        }

        try {
            URL u = new URL(url);
            // httpプロトコルでない
            if ("http".equals(u.getProtocol()) == false) {
                return false;
            }
        } catch (MalformedURLException e) {
            // URLとして正しくない
            return false;
        }

        return true;
    }

    /**
     * 設定値「ジャンル」を取得する
     * 
     * @return 設定値「ジャンル」
     */
    private String getSettingChannelGenre() {
        String result = mPref.getString("channel_genre",
                getString(R.string.anonymous_genre));
        if (result.length() == 0) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.putString("channel_genre",
                    getString(R.string.anonymous_genre));
            prefEditor.commit();
            result = mPref.getString("channel_genre",
                    getString(R.string.anonymous_genre));
        }
        return result;
    }

    /**
     * 設定値「マウント名」を取得する
     * 
     * @return 設定値「マウント名」
     */
    private String getSettingChannelMount() {
        String result = mPref.getString("channel_mount", "");
        if ((result.length() == 0) || (result.length() == 1 && result.charAt(0) == '/')) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.putString("channel_mount",
                    "/" + RandomStringUtils.randomAlphabetic(C.DEFAULT_MOUNT_STRING_LENGTH));
            prefEditor.commit();
            result = mPref.getString("channel_mount", "");
        } else if (result.length() != 0 && result.charAt(0) != '/') {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.putString("channel_mount", "/" + result);
            prefEditor.commit();
            result = mPref.getString("channel_mount", "");
        }

        return result;
    }

    /**
     * 設定値「配信サーバ」を取得する
     * 
     * @return 設定値「配信サーバ」
     */
    private String getSettingChannelServer() {
        return mPref.getString("channel_server", "");
    }

    /**
     * 設定値「ビットレート」を取得する
     * 
     * @return 設定値「ビットレート」
     */
    private int getSettingAudioBitrate() {
        String result = mPref.getString("audio_bitrate",
                String.valueOf(C.DEFAULT_AUDIO_BITRATE));
        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException e) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.remove("audio_bitrate");
            prefEditor.commit();
            return C.DEFAULT_AUDIO_BITRATE;
        }
    }

    /**
     * 設定値「モノラル・ステレオ」を取得する
     * 
     * @return 設定値「モノラル・ステレオ」
     */
    private int getSettingAudioChannel() {
        String result = mPref.getString("audio_channel",
                String.valueOf(C.DEFAULT_AUDIO_CHANNEL));
        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException e) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.remove("audio_channel");
            prefEditor.commit();
            return C.DEFAULT_AUDIO_CHANNEL;
        }
    }

    /**
     * 設定値「サンプリングレート」を取得する
     * 
     * @return 設定値「サンプリングレート」
     */
    private int getSettingAudioSampleRate() {
        String result = mPref.getString("audio_sample_rate",
                String.valueOf(C.DEFAULT_AUDIO_SAMPLE_RATE));
        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException e) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.remove("audio_sample_rate");
            prefEditor.commit();
            return C.DEFAULT_AUDIO_SAMPLE_RATE;
        }
    }

    /**
     * 設定値「エンコード品質」を取得する
     * 
     * @return 設定値「エンコード品質」
     */
    private int getSettingAudioMp3EncodeQuarity() {
        String result = mPref.getString("audio_mp3_encode_quality",
                String.valueOf(C.DEFAULT_AUDIO_MP3_ENCODE_QUALITY));
        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException e) {
            SharedPreferences.Editor prefEditor = mPref.edit();
            prefEditor.remove("audio_mp3_encode_quality");
            prefEditor.commit();
            return C.DEFAULT_AUDIO_MP3_ENCODE_QUALITY;
        }
    }

    /**
     * 設定情報の表示を更新する
     */
    private void invalidateBroadcastSetting() {
        final TextView djNameTextView = (TextView) findViewById(R.id.ChannelDjTextView);
        djNameTextView.setText(getSettingChannelDjName());

        final TextView titleTextView = (TextView) findViewById(R.id.ChannelTitleTextView);
        titleTextView.setText(getSettingChannelTitle());

        final TextView genreTextView = (TextView) findViewById(R.id.ChannelGenreTextView);
        genreTextView.setText(getSettingChannelGenre());

        final TextView descriptionTextView = (TextView) findViewById(R.id.ChannelDescriptionTextView);
        descriptionTextView.setText(getSettingChannelDescription());

        final TextView mountTextView = (TextView) findViewById(R.id.ChannelMountTextView);
        mountTextView.setText(getSettingChannelMount());
    }

    /**
     * 放送を開始する
     */
    private void start() {
        if (BroadcastManager.getConnector().getBroadcastState() != VoiceSender.BROADCAST_STATE_STOPPED) {
            return;
        }

        final ScheduledExecutorService loadingDialogDismissScheduler = Executors
                .newSingleThreadScheduledExecutor();
        final Object loadingDialogLock = new Object();

        // 接続中のプログレスダイアログを表示し、配信が開始したらダイアログを消す
        final ProgressDialog loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage(getString(R.string.connecting));
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        loadingDialog.setCancelable(false);
        loadingDialog.show();

        // 接続が開始・ないしはエラーの場合はダイアログを閉じる
        final Handler broadcastStateHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case VoiceSender.MSG_ERROR_NOT_SUPPORTED_RECORDING_PARAMETERS:
                    case VoiceSender.MSG_ERROR_REC_START:
                    case VoiceSender.MSG_ERROR_AUDIO_RECORD:
                    case VoiceSender.MSG_ERROR_PCM_BUFFER_OVERFLOW:
                    case VoiceSender.MSG_ERROR_AUDIO_ENCODE:
                    case VoiceSender.MSG_ERROR_MP3_BUFFER_OVERFLOW:
                    case VoiceSender.MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST:
                    case VoiceSender.MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER:
                    case VoiceSender.MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER:
                    case VoiceSender.MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_AUTHENTICATION_REQUIRED:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_IN_USE:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_TOO_LONG:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_CONTENT_TYPE_NOT_SUPPORTED:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_TOO_MANY_SOURCES_CONNECTED:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_UNKNOWN_ERROR:
                    case VoiceSender.MSG_ERROR_SEND_HEADER_DATA:
                    case VoiceSender.MSG_ERROR_RECV_HEADER_RESPONSE:
                    case VoiceSender.MSG_ERROR_SEND_STREAM_DATA:
                    case VoiceSender.MSG_SEND_STREAM_STARTED:
                    case VoiceSender.MSG_SEND_STREAM_ENDED:
                    case VoiceSender.MSG_STOP_WAIT_RECONNECT:
                        synchronized (loadingDialogLock) {
                            loadingDialog.dismiss();
                            /*
                             * このHandlerは接続中のプログレスダイアログを消すだけなので、
                             * 接続中のプログレスダイアログを消したらこのHandlerは必要なくなるので削除
                             */
                            BroadcastManager.getConnector()
                                    .removeBroadcastStateChangedHandler(this);
                            loadingDialogDismissScheduler.shutdown();
                        }
                        break;
                    case VoiceSender.MSG_REC_STARTED:
                    case VoiceSender.MSG_ENCODE_STARTED:
                    case VoiceSender.MSG_RECONNECT_STARTED:
                        break;
                    default:
                        Log.w(C.TAG, "Unknown received message " + msg.what
                                + " when dismiss start dialog.");
                        break;
                }
            }
        };
        BroadcastManager.getConnector().addBroadcastStateChangedHandler(broadcastStateHandler);

        // 接続開始後、一定時間接続が開始しない場合にはダイアログを閉じる
        final Handler switchViewHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                // 一定時間接続が開始しないのでダイアログを閉じる場合
                switchViewAsBroadcastState();
            }
        };
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                synchronized (loadingDialogLock) {
                    loadingDialog.dismiss();
                    BroadcastManager.getConnector()
                            .removeBroadcastStateChangedHandler(broadcastStateHandler);
                    loadingDialogDismissScheduler.shutdown();
                }
                switchViewHandler.sendEmptyMessage(0);
            }
        };
        loadingDialogDismissScheduler.schedule(runnable,
                C.WAIT_SEC_FROM_REC_START_TO_SEND_DATA + 3, TimeUnit.SECONDS);

        BroadcastManager.getConnector().start(new BroadcastConfig(getSettingAudioBitrate(),
                getSettingAudioChannel(), getSettingAudioSampleRate(),
                getSettingAudioMp3EncodeQuarity(), getSettingChannelDjName(),
                getSettingChannelTitle(), getSettingChannelDescription(),
                getSettingChannelUrl(), getSettingChannelGenre(),
                getSettingChannelMount(), getSettingChannelServer()));
    }

    /**
     * 放送を中止する
     */
    private void stop() {
        if (BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPING
                || BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED) {
            return;
        }

        final ProgressDialog loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage(getString(R.string.disconnecting));
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        loadingDialog.setCancelable(false);
        loadingDialog.show();

        /*
         * 配信停止後にプログレスダイアログを消し、ボタンなどの内容を変えるための、BoladcastManagerにHandlerを設定する
         */
        BroadcastManager.getConnector().addBroadcastStateChangedHandler(new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case VoiceSender.MSG_ERROR_NOT_SUPPORTED_RECORDING_PARAMETERS:
                    case VoiceSender.MSG_ERROR_REC_START:
                    case VoiceSender.MSG_ERROR_AUDIO_RECORD:
                    case VoiceSender.MSG_ERROR_PCM_BUFFER_OVERFLOW:
                    case VoiceSender.MSG_ERROR_AUDIO_ENCODE:
                    case VoiceSender.MSG_ERROR_MP3_BUFFER_OVERFLOW:
                    case VoiceSender.MSG_ERROR_FETCH_NET_LADIO_SERVER_LIST:
                    case VoiceSender.MSG_ERROR_NOT_FOUND_NET_LADIO_BROADCAST_SERVER:
                    case VoiceSender.MSG_ERROR_CREATE_SOCKET_TO_NET_LADIO_SERVER:
                    case VoiceSender.MSG_ERROR_INTERRUPTED_WAIT_FROM_REC_START_TO_SEND_DATA:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_AUTHENTICATION_REQUIRED:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_IN_USE:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_MOUNTPOINT_TOO_LONG:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_CONTENT_TYPE_NOT_SUPPORTED:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_TOO_MANY_SOURCES_CONNECTED:
                    case VoiceSender.MSG_ERROR_RECEIVED_RESPONSE_UNKNOWN_ERROR:
                    case VoiceSender.MSG_ERROR_SEND_HEADER_DATA:
                    case VoiceSender.MSG_ERROR_RECV_HEADER_RESPONSE:
                    case VoiceSender.MSG_ERROR_SEND_STREAM_DATA:
                    case VoiceSender.MSG_SEND_STREAM_ENDED:
                    case VoiceSender.MSG_STOP_WAIT_RECONNECT:
                        // プログレスダイアログを消すだけ
                        loadingDialog.dismiss();
                        switchViewAsBroadcastState();
                        BroadcastManager.getConnector().removeBroadcastStateChangedHandler(this);
                        break;
                    case VoiceSender.MSG_REC_STARTED:
                    case VoiceSender.MSG_ENCODE_STARTED:
                    case VoiceSender.MSG_SEND_STREAM_STARTED:
                    case VoiceSender.MSG_RECONNECT_STARTED:
                        break;
                    default:
                        Log.w(C.TAG, "Unknown received message " + msg.what
                                + " when stop.");
                        break;
                }
            }
        });
        BroadcastManager.getConnector().addServiceConnectChangeHandler(new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case BroadcastServiceConnector.MSG_ERROR_STOP_SERVICE_CONNECTION:
                        loadingDialog.dismiss();
                        switchViewAsBroadcastState();
                        (new AlertDialog.Builder(MainActivity.this))
                                .setMessage(R.string.failed_rec_stop)
                                .setPositiveButton(R.string.close, null).create()
                                .show();
                        BroadcastManager.getConnector().removeServiceConnectChangeHandler(this);
                        break;
                    default:
                        Log.w(C.TAG, "Unknown received message " + msg.what
                                + " when stop.");
                        break;
                }
            }
        });
        BroadcastManager.getConnector().stop();
    }

    /**
     * 配信状態に応じて、ボタンのテキストなどを切り替える
     */
    private void switchViewAsBroadcastState() {
        switch (BroadcastManager.getConnector().getBroadcastState()) {
            case VoiceSender.BROADCAST_STATE_CONNECTING:
                mBroadcastStatusTextView.setText(R.string.connecting);
                mStartStopButton.setText(R.string.stop);
                break;
            case VoiceSender.BROADCAST_STATE_BROADCASTING:
                mBroadcastStatusTextView.setText(R.string.broadcasting);
                mStartStopButton.setText(R.string.stop);
                break;
            case VoiceSender.BROADCAST_STATE_STOPPING:
            case VoiceSender.BROADCAST_STATE_STOPPED:
            default:
                mBroadcastStatusTextView.setText(R.string.not_broadcasting);
                mStartStopButton.setText(R.string.start);
                break;
        }
    }

    /**
     * 一定時間ごとに自動でリスナー数を取得するクラス
     */
    private class BroadcastListenerFetcher {

        /**
         * ヘッドライン取得
         */
        private static final int MSG_FETCHED_HEADLINE = 0;

        /**
         * ヘッドライン取得失敗
         */
        private static final int MSG_ERROR_FETCH_HEADLINE = 1;

        private final ScheduledExecutorService mmScheduler = Executors
                .newSingleThreadScheduledExecutor();

        private final Runnable mmFetchListenerTask = new Runnable() {

            @Override
            public void run() {
                final BroadcastInfo broadcastingInfo = BroadcastManager
                        .getConnector().getBroadcastInfo(); // 配信中の情報を取得する

                // 配信中でない場合
                if (broadcastingInfo == null
                        || BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED) {
                    if (C.LOCAL_LOG) {
                        Log.v(C.TAG, "Failed fetch listeners num because not broadcasting.");
                    }

                    mmHandler.sendEmptyMessage(MSG_ERROR_FETCH_HEADLINE);

                    // 指定秒数後に再びリスナー数を取得する
                    mmScheduler.schedule(this, C.LISTENER_FETCH_INTERVAL_SEC, TimeUnit.SECONDS);
                    return;
                }

                // ヘッドライン
                Headline headline = HeadlineManager.getHeadline();

                try {
                    if (C.LOCAL_LOG) {
                        Log.v(C.TAG, "Start fetching listeners num.");
                    }

                    headline.fecthHeadline(); // ヘッドライン取得
                    // ヘッドラインから番組の再生URLと同じURLを持つ番組を探す
                    Channel channel = headline.getChannel(Channel
                            .createPlayUrl(broadcastingInfo.getServerName(),
                                    broadcastingInfo.getServerPort(),
                                    broadcastingInfo.getChannelMount())); // URLと同じ番組を探す

                    // ヘッドライン取得
                    if (channel != null) {
                        if (C.LOCAL_LOG) {
                            Log.v(C.TAG, "Success fetch listeners num.");
                        }

                        Message msg = mmHandler.obtainMessage(
                                MSG_FETCHED_HEADLINE, channel);
                        msg.sendToTarget();
                    }
                    // 該当するURLが存在しない
                    else {
                        if (C.LOCAL_LOG) {
                            Log.v(C.TAG, "Success fetch listeners num, but not found.");
                        }

                        mmHandler.sendEmptyMessage(MSG_ERROR_FETCH_HEADLINE);
                    }
                } catch (IOException e) {
                    if (C.LOCAL_LOG) {
                        Log.v(C.TAG, "Failed fetch listeners num.");
                    }

                    // 取得失敗
                    mmHandler.sendEmptyMessage(MSG_ERROR_FETCH_HEADLINE);
                }

                headline.clearChannels(); // ヘッドライン情報は必要無いのでクリア

                // 指定秒数後に再びリスナー数を取得する
                mmScheduler.schedule(this, C.LISTENER_FETCH_INTERVAL_SEC, TimeUnit.SECONDS);
            }
        };

        /**
         * リスナー数を取得した後の処理Handler
         */
        private final Handler mmHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_FETCHED_HEADLINE:
                        final Channel channel = (Channel) msg.obj;
                        if (channel != null) {
                            mListenersNumTextView.setText(String
                                    .format("%s %d / %s %d / %s %d",
                                            getString(R.string.listeners_num),
                                            channel.getCln(),
                                            getString(R.string.max_listeners_num),
                                            channel.getMax(),
                                            getString(R.string.total_listeners_num),
                                            channel.getClns()));
                        } else {
                            mListenersNumTextView
                                    .setText(R.string.unknown_listeners_num);
                        }
                        break;
                    case MSG_ERROR_FETCH_HEADLINE:
                        mListenersNumTextView
                                .setText(R.string.unknown_listeners_num);
                        break;
                    default:
                        Log.w(C.TAG, "Unknown received message "
                                + msg.what
                                + " when fetch listeners num.");
                        mListenersNumTextView
                                .setText(R.string.unknown_listeners_num);
                        break;
                }
            }
        };

        /**
         * リスナー数の取得を開始する
         */
        public void start() {
            mmScheduler.schedule(mmFetchListenerTask, 0, TimeUnit.SECONDS);
        }

        /**
         * リスナー数の取得を終了する
         */
        public void shutdown() {
            mmScheduler.shutdown();
        }
    }
    
    /**
     * 配信秒数をカウントするクラス
     */
    private class BroadcastTimeCounter {
        
        /**
         * 時間更新
         */
        private static final int MSG_UPDATE = 0;

        /**
         * 配信情報
         */
        private BroadcastInfo mmBroadcastInfo;
        
        /**
         * ロックオブジェクト
         */
        private final Object mmLock = new Object();
        
        private final ScheduledExecutorService mmScheduler = Executors
                .newSingleThreadScheduledExecutor();

        private final Runnable mmFetchListenerTask = new Runnable() {

            @Override
            public void run() {
                synchronized (mmLock) {
                    if (mmBroadcastInfo == null) {
                        mmBroadcastInfo = BroadcastManager
                                .getConnector().getBroadcastInfo(); // 配信中の情報を取得する
                    }

                    // 配信中でない場合
                    if (mmBroadcastInfo == null
                            || BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED) {
                        // 配信中で無い場合は、再び配信中になった場合に配信情報を取得しにいくように配信情報を空にする
                        mmBroadcastInfo = null;

                        // 1秒後に再び配信秒数をカウントする
                        mmScheduler.schedule(this, 1, TimeUnit.SECONDS);
                        return;
                    }

                    // 配信中なので時刻を更新する
                    mmHandler.sendEmptyMessage(MSG_UPDATE);

                    // 1秒後に再び配信秒数をカウントする
                    mmScheduler.schedule(this, 1, TimeUnit.SECONDS);
                }
            }
        };

        /**
         * リスナー数を取得した後の処理Handler
         */
        private final Handler mmHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE:
                        long startTime = 0;
                        synchronized (mmLock) {
                            if (mmBroadcastInfo == null) {
                                break;
                            } else {
                                startTime = mmBroadcastInfo.getStartTime();
                            }
                        }
                        
                        // 経過秒数
                        final long postSec = (System.currentTimeMillis() - startTime) / 1000;
                        // 0以下であることはあり得ないはずだが一応チェック
                        if (postSec > 0) {
                            // 経過時間を表示
                            final long hour = postSec / 3600;
                            final long min = (postSec % 3600) / 60;
                            final long sec = postSec % 60;
                            mBroadcastTimeTextView.setText(String.format("%d:%02d:%02d", hour, min, sec));
                        }
                        break;
                    default:
                        Log.w(C.TAG, "Unknown received message "
                                + msg.what
                                + " when update broadcast time.");
                        break;
                }
            }
        };

        /**
         * リスナー数の取得を開始する
         */
        public void start() {
            mmScheduler.schedule(mmFetchListenerTask, 0, TimeUnit.SECONDS);
        }

        /**
         * リスナー数の取得を終了する
         */
        public void shutdown() {
            mmScheduler.shutdown();
        }
    }
}
