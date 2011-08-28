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

import java.security.InvalidParameterException;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 配信設定
 */
public final class BroadcastConfig implements Parcelable {

	/**
	 * ビットレート（kbps）
	 */
	private final int mAudioBrate;

	/**
	 * 録音するチャンネル数
	 */
	private final int mAudioChannel;

	/**
	 * 録音するサンプリングレート（Hz）
	 */
	private final int mAudioSampleRate;

	/**
	 * エンコードの品質。<br />
	 * 0〜9で指定する。0が高品質・低速、9が低品質・高速である。
	 */
	private final int mAudioMp3EncodeQuality;

	/**
	 * DJ名
	 */
	private final String mChannelDjName;

	/**
	 * タイトル
	 */
	private final String mChannelTitle;

	/**
	 * 番組の説明
	 */
	private final String mChannelDescription;

	/**
	 * 関連URL
	 */
	private final String mChannelUrl;

	/**
	 * ジャンル
	 */
	private final String mChannelGenre;

	/**
	 * マウント
	 */
	private final String mChannelMount;

	/**
	 * 配信サーバ
	 * 
	 * 配信サーバを自動で選択する場合はの場合はnullもしくは文字列
	 */
	private final String mChannelServer;
	
	/**
	 * コンストラクタ
	 * 
	 * @param audioBrate
	 *            ビットレート（kbps）
	 * @param audioChannel
	 *            録音するチャンネル数
	 * @param audioSampleRate
	 *            録音するサンプリングレート（Hz）
	 * @param audioMp3EncodeQuality
	 *            エンコードの品質。<br />
	 *            0〜9で指定する。0が高品質・低速、9が低品質・高速である。
	 * @param channelDjName
	 *            DJ名
	 * @param channelTitle
	 *            タイトル
	 * @param channelDescription
	 *            番組の説明
	 * @param channelUrl
	 *            関連URL
	 * @param channelGenre
	 *            ジャンル
	 * @param channelMount
	 *            マウント
	 * @param channelServer
	 *            配信サーバ。<br />
	 *            配信サーバを自動で選択する場合はの場合はnullもしくは空文字列を指定すること。
	 * 
	 * @throws InvalidParameterException
	 *             audioBrateが0以下
	 * @throws InvalidParameterException
	 *             audioChannelが1か2以外
	 * @throws InvalidParameterException
	 *             audioSampleRateが0以下
	 * @throws InvalidParameterException
	 *             audioMp3EncodeQualityが0未満
	 * @throws InvalidParameterException
	 *             audioMp3EncodeQualityが9より大きい
	 * @throws InvalidParameterException
	 *             channelMountにnullを指定した
	 * @throws InvalidParameterException
	 *             channelMountがマウントとして不正
	 */
	public BroadcastConfig(int audioBrate, int audioChannel,
			int audioSampleRate, int audioMp3EncodeQuality,
			String channelDjName, String channelTitle,
			String channelDescription, String channelUrl, String channelGenre,
			String channelMount, String channelServer) {
		if (audioBrate <= 0) {
			throw new InvalidParameterException(
					"audioBrate must be greater than 0.");
		}

		switch (audioChannel) {
		case 1:
		case 2:
			break;
		default:
			throw new InvalidParameterException("audioChannel must be 1 or 2.");
		}

		if (audioSampleRate <= 0) {
			throw new InvalidParameterException(
					"audioSampleRate must be greater than 0.");
		}

		if (audioMp3EncodeQuality < 0 || audioMp3EncodeQuality > 9) {
			throw new InvalidParameterException(
					"audioMp3EncodeQuality must be 0-9.");
		}

		if (channelMount == null) {
			throw new InvalidParameterException(
					"channelMount must be not null.");
		}
		if ((channelMount.length() == 0)
				|| (channelMount.length() == 1 && channelMount.charAt(0) == '/')) {
			throw new InvalidParameterException("channelMount is invalid.");
		}

		this.mAudioBrate = audioBrate;
		this.mAudioChannel = audioChannel;
		this.mAudioSampleRate = audioSampleRate;
		this.mAudioMp3EncodeQuality = audioMp3EncodeQuality;
		if (channelDjName != null) {
			this.mChannelDjName = channelDjName;
		} else {
			this.mChannelDjName = "";
		}
		if (channelTitle != null) {
			this.mChannelTitle = channelTitle;
		} else {
			this.mChannelTitle = "";
		}
		if (channelDescription != null) {
			this.mChannelDescription = channelDescription;
		} else {
			this.mChannelDescription = "";
		}
		if (channelUrl != null) {
			this.mChannelUrl = channelUrl;
		} else {
			this.mChannelUrl = "";
		}
		if (channelGenre != null) {
			this.mChannelGenre = channelGenre;
		} else {
			this.mChannelGenre = "";
		}
		this.mChannelMount = channelMount;
		this.mChannelServer = channelServer;
	}

	public static final Parcelable.Creator<BroadcastConfig> CREATOR = new Parcelable.Creator<BroadcastConfig>() {
		public BroadcastConfig createFromParcel(Parcel in) {
			return new BroadcastConfig(in);
		}

		public BroadcastConfig[] newArray(int size) {
			return new BroadcastConfig[size];
		}
	};

	private BroadcastConfig(Parcel in) {
		this.mAudioBrate = in.readInt();
		this.mAudioChannel = in.readInt();
		this.mAudioSampleRate = in.readInt();
		this.mAudioMp3EncodeQuality = in.readInt();
		this.mChannelDjName = in.readString();
		this.mChannelTitle = in.readString();
		this.mChannelDescription = in.readString();
		this.mChannelUrl = in.readString();
		this.mChannelGenre = in.readString();
		this.mChannelMount = in.readString();
		this.mChannelServer = in.readString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(mAudioBrate);
		dest.writeInt(mAudioChannel);
		dest.writeInt(mAudioSampleRate);
		dest.writeInt(mAudioMp3EncodeQuality);
		dest.writeString(mChannelDjName);
		dest.writeString(mChannelTitle);
		dest.writeString(mChannelDescription);
		dest.writeString(mChannelUrl);
		dest.writeString(mChannelGenre);
		dest.writeString(mChannelMount);
		dest.writeString(mChannelServer);
	}

	@Override
	public String toString() {
		return "BroadcastConfig [mAudioBrate=" + mAudioBrate
				+ ", mAudioChannel=" + mAudioChannel + ", mAudioSampleRate="
				+ mAudioSampleRate + ", mAudioMp3EncodeQuality="
				+ mAudioMp3EncodeQuality + ", mChannelDjName=" + mChannelDjName
				+ ", mChannelTitle=" + mChannelTitle + ", mChannelDescription="
				+ mChannelDescription + ", mChannelUrl=" + mChannelUrl
				+ ", mChannelGenre=" + mChannelGenre + ", mChannelMount="
				+ mChannelMount + ", mChannelServer=" + mChannelServer + "]";
	}

	/**
	 * ビットレート（kbps）を取得する
	 * 
	 * @return ビットレート（kbps）
	 */
	public final int getAudioBrate() {
		return mAudioBrate;
	}

	/**
	 * 録音するチャンネル数を取得する
	 * 
	 * @return 録音するチャンネル数
	 */
	public final int getAudioChannel() {
		return mAudioChannel;
	}

	/**
	 * 録音するサンプリングレート（Hz）を取得する
	 * 
	 * @return 録音するサンプリングレート（Hz）
	 */
	public final int getAudioSampleRate() {
		return mAudioSampleRate;
	}

	/**
	 * エンコードの品質を取得する
	 * 
	 * @return エンコードの品質
	 */
	public final int getAudioMp3EncodeQuality() {
		return mAudioMp3EncodeQuality;
	}

	/**
	 * DJ名を取得する
	 * 
	 * @return DJ名
	 */
	public final String getChannelDjName() {
		return mChannelDjName;
	}

	/**
	 * タイトルを取得する
	 * 
	 * @return タイトル
	 */
	public final String getChannelTitle() {
		return mChannelTitle;
	}

	/**
	 * 番組の説明を取得する
	 * 
	 * @return 番組の説明
	 */
	public final String getChannelDescription() {
		return mChannelDescription;
	}

	/**
	 * 関連URLを取得する
	 * 
	 * @return 関連URL
	 */
	public final String getChannelUrl() {
		return mChannelUrl;
	}

	/**
	 * ジャンルを取得する
	 * 
	 * @return ジャンル
	 */
	public final String getChannelGenre() {
		return mChannelGenre;
	}

	/**
	 * マウントを取得する
	 * 
	 * @return マウント
	 */
	public final String getChannelMount() {
		return mChannelMount;
	}
	
	/**
	 * 配信サーバを取得する
	 * 
	 * @return 配信サーバを取得する。<br />
	 *         nullもしくは空文字列が帰る場合は配信サーバを自動で選択すること。
	 */
	public final String getChannelServer() {
		return mChannelServer;
	}
}
