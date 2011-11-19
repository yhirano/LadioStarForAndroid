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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.RandomStringUtils;

import com.uraroji.garage.android.ladiostar.bugreport.AppUncaughtExceptionHandler;
import com.uraroji.garage.android.netladiolib.Channel;
import com.uraroji.garage.android.netladiolib.Headline;
import com.uraroji.garage.android.netladiolib.HeadlineManager;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.TabHost.TabSpec;

/**
 * メイン画面
 */
public class MainActivity extends TabActivity {

	private final static int MENU_ID_REFERENCE_SITE = Menu.FIRST + 1;

	private final static int MENU_ID_HELP = Menu.FIRST + 2;
	
	private final static int MENU_ID_SETTING = Menu.FIRST + 3;

	private TextView mBroadcastStatusTextView;
	
	private Button mStartStopButton;

	private SettingInfoAdapter mSettingInfoAdapter;
	
	/*
	 * 配信の開始時、停止時にメッセージやダイアログを表示するためのHandler
	 */
	private final Handler mBroadcastWatchHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			/*
			 * サービスで配信する場合は、サービス側がメッセージやダイアログを表示するのでこのアクティビティでは、
			 * サービスに接続できなかった場合のメッセージを表示する。
			 */
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
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// 補足されない例外をキャッチするハンドラを登録（バグレポート用）
		Thread.setDefaultUncaughtExceptionHandler(new AppUncaughtExceptionHandler(
				this));

		/*
		 * サービスに接続した直後にボタン類の書き換えを行う。
		 * サービスに接続するまでは配信中かどうかを確認できないため、ボタン類の書き換えができない。
		 * よって、サービス接続直後にボタン類の書き換えを行う。
		 */
		BroadcastManager.getConnector().addBroadcastStateChangedHandler(
				new Handler() {

			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case BroadcastServiceConnector.MSG_CONNECTED_SERVICE:
					switchViewAsBroadcastState();
					/*
					 * サービスは1度起動したら、配信されていない状態でアプリを終了しない限り
					 * 停止しない。
					 * よって、1度サービスを起動してしまえば配信中かどうかを取得できるため、
					 * サービス接続通知によるボタン類書き換えは必要なくなるので、
					 * サービス接続通知の受信をする必要が無い。
					 */
							BroadcastManager.getConnector()
									.removeBroadcastStateChangedHandler(this);
					break;
				default:
					Log.w(C.TAG, "Unknown received message " + msg.what
							+ " when watch service connection.");
					break;
				}
			}
		});
		
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
		
		// 未設定項目をデフォルトの設定で埋める
		setDefaultSetting();

		// TabHostを取得
		final TabHost tabHost = getTabHost();

		// 配信状況タブの作成
		TabSpec bloadcastStatusTab = tabHost.newTabSpec("BroadcastStatusTab");
		bloadcastStatusTab.setIndicator(getString(R.string.broadcast_state));
		bloadcastStatusTab.setContent(R.id.BroadcastStatusLinearLayout);
		tabHost.addTab(bloadcastStatusTab);

		// 配信情報タブの作成
		TabSpec bloadcastInfoTab = tabHost.newTabSpec("BroadcastInfoTab");
		bloadcastInfoTab.setIndicator(getString(R.string.broadcast_info));
		bloadcastInfoTab.setContent(R.id.SettingInfoListView);
		tabHost.addTab(bloadcastInfoTab);

		ListView settingInfoListView = (ListView) findViewById(R.id.SettingInfoListView);
		mSettingInfoAdapter = new SettingInfoAdapter(this);
		settingInfoListView.setAdapter(mSettingInfoAdapter);

		mBroadcastStatusTextView = (TextView) findViewById(R.id.BroadcastStatusTextView);
		
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
		
		final TextView listenersNumTextView = (TextView)findViewById(R.id.ListenersNumTextView);
		
		final Button fetchLitenersNumButton = (Button) findViewById(R.id.FetchLitenersNumButton);
		fetchLitenersNumButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				final BroadcastInfo broadcastingInfo = BroadcastManager
						.getConnector().getBroadcastInfo(); // 配信中の情報を取得する

				// 配信中の場合
				if (broadcastingInfo != null
						&& BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_BROADCASTING) {

					fetchLitenersNumButton.setEnabled(false); // 更新ボタンを押せないようにする
					fetchLitenersNumButton.setText(R.string.updating); // 更新中の表示にする
					new Thread() {

						/**
						 * ヘッドライン取得
						 */
						private static final int MSG_FETCHED_HEADLINE = 0;

						/**
						 * ヘッドライン取得失敗
						 */
						private static final int MSG_ERROR_FETCH_HEADLINE = 1;
						
						@Override
						public void run() {
							Headline headline = HeadlineManager.getHeadline();
							try {
								headline.fecthHeadline(); // ヘッドライン取得
								// ヘッドラインから番組の再生URLと同じURLを持つ番組を探す
								Channel channel = headline.getChannel(Channel.createPlayUrl(
										broadcastingInfo.getServerName(),
										broadcastingInfo.getServerPort(),
										broadcastingInfo.getChannelMount())); // URLと同じ番組を探す

								// ヘッドライン取得
								if (channel != null) {
									Message msg = mmHandler.obtainMessage(MSG_FETCHED_HEADLINE, channel);
									msg.sendToTarget();
								} 
								// 該当するURLが存在しない
								else {
									mmHandler.sendEmptyMessage(MSG_ERROR_FETCH_HEADLINE);
								}
							} catch (IOException e) {
								// 取得失敗
								mmHandler.sendEmptyMessage(MSG_ERROR_FETCH_HEADLINE);
							}
							
							headline.clearChannels(); // ヘッドライン情報は必要無いのでクリア
						}

						private Handler mmHandler = new Handler() {
							
							@Override
							public void handleMessage(Message msg) {
								switch (msg.what) {
								case MSG_FETCHED_HEADLINE:
									Channel channel = (Channel) msg.obj;
									if (channel != null) {
										listenersNumTextView.setText(String
												.format("%s %d / %s %d / %s %d",
														getString(R.string.listeners_num),
														channel.getCln(),
														getString(R.string.max_listeners_num),
														channel.getMax(),
														getString(R.string.total_listeners_num),
														channel.getClns()));
									} else {
										listenersNumTextView
												.setText(R.string.unknown_listeners_num);
									}
									break;
								case MSG_ERROR_FETCH_HEADLINE:
									listenersNumTextView
											.setText(R.string.unknown_listeners_num);
									break;
								default:
									Log.w(C.TAG, "Unknown received message "
											+ msg.what
											+ " when fetch listeners num.");
									listenersNumTextView
											.setText(R.string.unknown_listeners_num);
									break;
								}
								
								fetchLitenersNumButton.setText(R.string.update); // 更新の表示にする
								fetchLitenersNumButton.setEnabled(true); // 更新ボタンを押せるようにする
							}
						};
					}.start();
				}
				// 配信中でない場合
				else {
					listenersNumTextView
							.setText(R.string.unknown_listeners_num);
				}
			}
		});
		
		final TextView volumeRateTextView = (TextView)findViewById(R.id.VolumeRateTextView);
		
		final SeekBar volumeRateSeekbar = (SeekBar)findViewById(R.id.VolumeRateSeekBar);
		volumeRateSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

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
		
		/*
		 * サービスに接続した直後にボタン類の書き換えを行う。
		 * サービスに接続するまでは配信中かどうかを確認できないため、ボタン類の書き換えができない。
		 * よって、サービス接続直後にボタン類の書き換えを行う。
		 */
		BroadcastManager.getConnector().addBroadcastStateChangedHandler(
				new Handler() {

			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case BroadcastServiceConnector.MSG_CONNECTED_SERVICE:
					switchViewAsBroadcastState();
					volumeRateSeekbar.setProgress(BroadcastManager.getConnector().getVolumeRate());
					/*
					 * サービスは1度起動したら、配信されていない状態でアプリを終了しない限り
					 * 停止しない。
					 * よって、1度サービスを起動してしまえば音量を取得できるため、
					 * サービス接続通知による音量取得は必要なくなるので、
					 * サービス接続通知の受信をする必要が無い。
					 */
					BroadcastManager.getConnector()
							.removeBroadcastStateChangedHandler(this);
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
	}

	@Override
	protected void onStart() {
		super.onStart();

		// 前回バグで強制終了した場合はダイアログ表示
		AppUncaughtExceptionHandler.showBugReportDialogIfExist();
		
		mSettingInfoAdapter.update();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// 配信開始後にメッセージ・ダイアログを表示するための設定済みHandlerを削除する
		BroadcastManager.getConnector()
				.removeBroadcastStateChangedHandler(mBroadcastWatchHandler);

		switchViewAsBroadcastState();
		BroadcastManager.getConnector().release();
	}

	// オプションメニュー作成
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// メニューアイテムを追加
		menu.add(Menu.NONE, MENU_ID_REFERENCE_SITE, Menu.NONE,
				R.string.open_channel_url_site);
		menu.add(Menu.NONE, MENU_ID_HELP, Menu.NONE, R.string.help);
		menu.add(Menu.NONE, MENU_ID_SETTING, Menu.NONE, R.string.setting);

		return super.onCreateOptionsMenu(menu);
	}

	// オプションメニュー表示
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// 関連サイトが存在する場合にのみ有効にする
		menu.findItem(MENU_ID_REFERENCE_SITE).setEnabled(getSettingChannelUrl().length() != 0);
		// 配信中は設定を無効にする
		menu.findItem(MENU_ID_SETTING).setEnabled(BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED);
		
		return super.onPrepareOptionsMenu(menu);
	}

	// オプションメニューアイテムの選択
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_ID_REFERENCE_SITE:
			String urlString = getSettingChannelUrl();
			if (urlString.length() != 0) {
				Uri uri = Uri.parse(urlString);
				startActivity(new Intent(Intent.ACTION_VIEW, uri));
				return false;
			} else {
				return super.onOptionsItemSelected(item);
			}
		case MENU_ID_HELP: 
			try {
				startActivity(new Intent(Intent.ACTION_VIEW, C.HELP_MAIN_URL));
				return false;
			} catch (ActivityNotFoundException e) {
				return super.onOptionsItemSelected(item);
			}
		case MENU_ID_SETTING:
			if (BroadcastManager.getConnector().getBroadcastState() == VoiceSender.BROADCAST_STATE_STOPPED) {
				startActivity(new Intent(this,
						LadioStarPreferenceActivity.class));
				return false;
			} else {
				return super.onOptionsItemSelected(item);
			}
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * 設定値の入っていない設定項目に対してデフォルトの設定値を入れる
	 */
	private void setDefaultSetting() {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		SharedPreferences.Editor prefEditor = pref.edit();

		String djName = pref.getString("channel_dj_name", "");
		if (djName.length() == 0) {
			prefEditor.putString("channel_dj_name",
					getString(R.string.anonymous_dj_name));
		}

		String title = pref.getString("channel_title", "");
		if (title.length() == 0) {
			prefEditor.putString("channel_title",
					getString(R.string.anonymous_title));
		}

		String description = pref.getString("channel_description", "");
		if (description.length() == 0) {
			prefEditor.putString("channel_description",
					getString(R.string.anonymous_description));
		}

		String genre = pref.getString("channel_genre", "");
		if (genre.length() == 0) {
			prefEditor.putString("channel_genre",
					getString(R.string.anonymous_genre));
		}

		String mount = pref.getString("channel_mount", "");
		if ((mount.length() == 0) || (mount.length() == 1 && mount.charAt(0) == '/')) {
			prefEditor.putString("channel_mount",
					"/" + RandomStringUtils.randomAlphabetic(14));
		} else if (mount.length() != 0 && mount.charAt(0) != '/') {
			prefEditor.putString("channel_mount", "/" + mount);
		}

		String server = pref.getString("channel_server", "");
		if (server.length() == 0) {
			prefEditor.putString("channel_server", "");
		}

		String bitrate = pref.getString("audio_bitrate", "");
		if (bitrate.length() == 0) {
			prefEditor.putString("audio_bitrate",
					String.valueOf(C.DEFAULT_AUDIO_BITRATE));
		}

		String channel = pref.getString("audio_channel", "");
		if (channel.length() == 0) {
			prefEditor.putString("audio_channel",
					String.valueOf(C.DEFAULT_AUDIO_CHANNEL));
		}

		String sampleRate = pref.getString("audio_sample_rate", "");
		if (sampleRate.length() == 0) {
			prefEditor.putString("audio_sample_rate",
					String.valueOf(C.DEFAULT_AUDIO_SAMPLE_RATE));
		}

		String mp3EncodeQuality = pref.getString("audio_mp3_encode_quality", "");
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
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("channel_dj_name", getString(R.string.anonymous_dj_name));
		if (result.length() == 0) {
			SharedPreferences.Editor prefEditor = pref.edit();
			prefEditor.putString("channel_dj_name",
					getString(R.string.anonymous_dj_name));
			prefEditor.commit();
			result = pref.getString("channel_dj_name", getString(R.string.anonymous_dj_name));
		}
		return result;
	}

	/**
	 * 設定値「タイトル」を取得する
	 * 
	 * @return 設定値「タイトル」
	 */
	private String getSettingChannelTitle() {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("channel_title",
				getString(R.string.anonymous_title));
		if (result.length() == 0) {
			SharedPreferences.Editor prefEditor = pref.edit();
			prefEditor.putString("channel_title",
					getString(R.string.anonymous_title));
			prefEditor.commit();
			result = pref.getString("channel_title",
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
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("channel_description",
				getString(R.string.anonymous_description));
		if (result.length() == 0) {
			SharedPreferences.Editor prefEditor = pref.edit();
			prefEditor.putString("channel_description",
					getString(R.string.anonymous_description));
			prefEditor.commit();
			result = pref.getString("channel_description",
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
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("channel_url", "");
		
		// 関連URLが空の場合は、そのまま空の文字列を返す
		if (result.length() == 0) {
			return result;
		}
		// 関連URLが空でない場合
		else {
			// 関連URLがURLとして不正な場合
			if (isValidHttpUrl(result) == false) {
				SharedPreferences.Editor prefEditor = pref.edit();
				prefEditor.putString("channel_url", "");
				prefEditor.commit();
				result = pref.getString("channel_url", "");
			}
			
			return result;
		}
	}

	/**
	 * 指定された文字列がHTTP URLとして正しい形式かを調べる
	 * 
	 * @param url
	 *            URLとして正しいかを調査したい文字列
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
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("channel_genre",
				getString(R.string.anonymous_genre));
		if (result.length() == 0) {
			SharedPreferences.Editor prefEditor = pref.edit();
			prefEditor.putString("channel_genre",
					getString(R.string.anonymous_genre));
			prefEditor.commit();
			result = pref.getString("channel_genre",
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
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("channel_mount", "");
		if ((result.length() == 0) || (result.length() == 1 && result.charAt(0) == '/')) {
			SharedPreferences.Editor prefEditor = pref.edit();
			prefEditor.putString("channel_mount",
					"/" + RandomStringUtils.randomAlphabetic(14));
			prefEditor.commit();
			result = pref.getString("channel_mount", "");
		} else if (result.length() != 0 && result.charAt(0) != '/') {
			SharedPreferences.Editor prefEditor = pref.edit();
			prefEditor.putString("channel_mount", "/" + result);
			prefEditor.commit();
			result = pref.getString("channel_mount", "");
		}

		return result;
	}

	/**
	 * 設定値「配信サーバ」を取得する
	 * 
	 * @return 設定値「配信サーバ」
	 */
	private String getSettingChannelServer() {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		return pref.getString("channel_server", "");
	}

	/**
	 * 設定値「ビットレート」を取得する
	 * 
	 * @return 設定値「ビットレート」
	 */
	private int getSettingAudioBitrate() {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("audio_bitrate",
				String.valueOf(C.DEFAULT_AUDIO_BITRATE));
		try {
			return Integer.parseInt(result);
		} catch (NumberFormatException e) {
			SharedPreferences.Editor prefEditor = pref.edit();
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
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("audio_channel",
				String.valueOf(C.DEFAULT_AUDIO_CHANNEL));
		try {
			return Integer.parseInt(result);
		} catch (NumberFormatException e) {
			SharedPreferences.Editor prefEditor = pref.edit();
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
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("audio_sample_rate",
				String.valueOf(C.DEFAULT_AUDIO_SAMPLE_RATE));
		try {
			return Integer.parseInt(result);
		} catch (NumberFormatException e) {
			SharedPreferences.Editor prefEditor = pref.edit();
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
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		String result = pref.getString("audio_mp3_encode_quality",
				String.valueOf(C.DEFAULT_AUDIO_MP3_ENCODE_QUALITY));
		try {
			return Integer.parseInt(result);
		} catch (NumberFormatException e) {
			SharedPreferences.Editor prefEditor = pref.edit();
			prefEditor.remove("audio_mp3_encode_quality");
			prefEditor.commit();
			return C.DEFAULT_AUDIO_MP3_ENCODE_QUALITY;
		}
	}
	
	/**
	 * 放送を開始する
	 */
	private void start() {
		if (BroadcastManager.getConnector().getBroadcastState() != VoiceSender.BROADCAST_STATE_STOPPED) {
			return;
		}

		final ScheduledExecutorService loadingDialogDismissScheduler = Executors.newSingleThreadScheduledExecutor();
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
					Log.w(C.TAG, "Unknown received message " + msg.what + " when dismiss start dialog.");
					break;
				}
			}
		};
		BroadcastManager.getConnector().addBroadcastStateChangedHandler(broadcastStateHandler);
		
		// 接続開始後、一定時間接続が開始しない場合にはダイアログを閉じる
		final Handler switchViewHandler = new Handler(){
			
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
					// プログレスダイアログを消すだけ。メッセージの表示は別のHandlerで表示している。
					loadingDialog.dismiss();
					switchViewAsBroadcastState();
					BroadcastManager.getConnector().removeBroadcastStateChangedHandler(this);
					break;
				case BroadcastServiceConnector.MSG_ERROR_STOP_SERVICE_CONNECTION:
					loadingDialog.dismiss();
					switchViewAsBroadcastState();
					(new AlertDialog.Builder(MainActivity.this))
							.setMessage(R.string.failed_rec_stop)
							.setPositiveButton(R.string.close, null).create()
							.show();
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
	 * チャンネル数を文字列で取得する
	 * 
	 * @param chs
	 *            チャンネル数
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
	 * エンコード品質の説明を取得する
	 * 
	 * @param mp3EncQuality
	 *            エンコード品質。0〜9で指定する。
	 * @return エンコード品質の説明
	 */
	private String getMp3EncQualityString(int mp3EncQuality) {
		switch (mp3EncQuality) {
		case 0:
			return getString(R.string.audio_mp3_encode_quality_q0);
		case 1:
			return getString(R.string.audio_mp3_encode_quality_q1);
		case 2:
			return getString(R.string.audio_mp3_encode_quality_q2);
		case 3:
			return getString(R.string.audio_mp3_encode_quality_q3);
		case 4:
			return getString(R.string.audio_mp3_encode_quality_q4);
		case 5:
			return getString(R.string.audio_mp3_encode_quality_q5);
		case 6:
			return getString(R.string.audio_mp3_encode_quality_q6);
		case 7:
			return getString(R.string.audio_mp3_encode_quality_q7);
		case 8:
			return getString(R.string.audio_mp3_encode_quality_q8);
		case 9:
			return getString(R.string.audio_mp3_encode_quality_q9);
		default:
			return getString(R.string.unknown) + "(" + String.valueOf(mp3EncQuality) + ")";
		}
	}
	
	private class SettingInfoAdapter extends BaseAdapter {

		private LayoutInflater mInflater;

		/**
		 * 設定名と設定内容の組み合わせ
		 * 
		 * ["Title", "今日からラジオ"]といった組み合わせを格納する。
		 */
		private class SettingInfo {

			/**
			 * キー
			 */
			/*package*/ String title;

			/**
			 * 内容
			 */
			/*package*/ String value;

			/**
			 * コンストラクタ
			 * 
			 * @param title
			 *            キー
			 * @param value
			 *            内容
			 */
			public SettingInfo(String title, String value) {
				this.title = title;
				this.value = value;
			}
		}

		/**
		 * 表示内容のリスト
		 */
		private ArrayList<SettingInfo> mInfoList = new ArrayList<SettingInfo>();

		/**
		 * コンストラクタ
		 * 
		 * @param context
		 *            コンテキスト
		 */
		public SettingInfoAdapter(Context context) {
			super();

			this.mInflater = LayoutInflater.from(context);

			generateSettingInfoList();
		}

		private void generateSettingInfoList() {
			mInfoList.clear();
			addInfoList(R.string.channel_dj_name, getSettingChannelDjName());
			addInfoList(R.string.channel_title, getSettingChannelTitle());
			addInfoList(R.string.channel_description, getSettingChannelDescription());
			addInfoList(R.string.channel_url, getSettingChannelUrl());
			addInfoList(R.string.channel_genre, getSettingChannelGenre());
			addInfoList(R.string.channel_mount, getSettingChannelMount());
			{
				String server = getSettingChannelServer();
				if (server.length() == 0) {
					server = getString(R.string.auto);
				}
				addInfoList(R.string.channel_server, server);
			}
			addInfoList(R.string.audio_bitrate, getSettingAudioBitrate() + "kbps");
			addInfoList(R.string.audio_channel, getChsString(getSettingAudioChannel()));
			addInfoList(R.string.audio_sample_rate, String.valueOf(getSettingAudioSampleRate()) + "Hz");
			addInfoList(R.string.audio_mp3_encode_quality, getMp3EncQualityString(getSettingAudioMp3EncodeQuarity()));
		}

		/**
		 * 指定したキーと内容を表示内容に追加する
		 * 
		 * @param titleId
		 *            キーの文字列ID
		 * @param value
		 *            内容
		 */
		private void addInfoList(int titleId, String value) {
			if (value != null && value.length() != 0) {
				mInfoList.add(new SettingInfo(getString(titleId), value));
			}
		}

		@Override
		public int getCount() {
			// 広告 + 番組
			return mInfoList.size() + 1;
		}

		@Override
		public Object getItem(int position) {
			// 使用しないのでnullを返す
			return null;
		}

		@Override
		public long getItemId(int position) {
			// 使用しないのでpositionを返す
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			
			if (position == 0) {
				/*
				 * convertView.getTagで何かが入っている場合、番組の表示で使用されたconvertViewだと思われるので、
				 * AdMob表示用に改めてconvertViewを作成する。
				 */
				if (view == null || view.getTag() != null) {
					view = mInflater.inflate(R.layout.ad_item_row, null);
					view.setTag(null);
				}
			} else {
				ViewHolder holder = null;

				/*
				 * convertView.getTagで何も入っていない場合、AdMobの表示で使用されたconvertViewだと思われるので、
				 * 番組表示用に改めてconvertViewを作成する。
				 */
				if (view == null || view.getTag() == null) {
					view = mInflater.inflate(R.layout.setting_info_item_row,
							null);
				}
				
				holder = (ViewHolder) view.getTag();

				if (holder == null) {
					holder = new ViewHolder();
					holder.infoTitleTextView = (TextView) view
							.findViewById(R.id.InfoTitleTextView);
					holder.infoValueTextView = (TextView) view
							.findViewById(R.id.InfoValueTextView);

					view.setTag(holder);
				}

				holder.infoTitleTextView
						.setText(mInfoList.get(position - 1).title);
				holder.infoValueTextView
						.setText(mInfoList.get(position - 1).value);
			}

			return view;
		}

		/**
		 * 設定内容を更新する
		 */
		public void update() {
			generateSettingInfoList();
			notifyDataSetChanged();
		}
		
		/**
		 * ChannelInfoAdapter#getViewにおけるViewの保持クラス
		 */
		private class ViewHolder {
			/*package*/ TextView infoTitleTextView;
			/*package*/ TextView infoValueTextView;
		}
	}
}