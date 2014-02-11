/* 
 * Copyright (c) 2011-2014 Yuichi Hirano
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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 配信中の番組の情報
 * 
 * 配信中の番組の情報をプロセス間でやりとりするために作成したクラス
 */
public final class BroadcastInfo implements Parcelable {

    /**
     * 配信設定
     */
    private final BroadcastConfig mBroadcastConfig;

    /**
     * 配信サーバ
     */
    private final String mServerName;

    /**
     * ポート番号
     */
    private final int mServerPort;

    /**
     * 配信を開始した時刻。<br />
     * {@link System#currentTimeMillis()}}で取得した配信開始時刻を格納する。
     */
    private final long mStartTime;
    
    /**
     * コンストラクタ
     * 
     * @param broadcastConfig 配信設定
     * @param serverName 配信サーバ
     * @param serverPort ポート番号
     * @param startTime 配信を開始した時刻。<br />
     *            {@link System#currentTimeMillis()} で取得した配信開始時刻を指定すること。
     */
    public BroadcastInfo(BroadcastConfig broadcastConfig,
            String serverName, int serverPort, long startTime) {
        this.mBroadcastConfig = broadcastConfig;
        this.mServerName = serverName;
        this.mServerPort = serverPort;
        this.mStartTime = startTime;
    }

    public static final Parcelable.Creator<BroadcastInfo> CREATOR = new Parcelable.Creator<BroadcastInfo>() {
        public BroadcastInfo createFromParcel(Parcel in) {
            return new BroadcastInfo(in);
        }

        public BroadcastInfo[] newArray(int size) {
            return new BroadcastInfo[size];
        }
    };

    private BroadcastInfo(Parcel in) {
        this.mBroadcastConfig = in.readParcelable(BroadcastConfig.class.getClassLoader());
        this.mServerName = in.readString();
        this.mServerPort = in.readInt();
        this.mStartTime = in.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mBroadcastConfig, 0);
        dest.writeString(mServerName);
        dest.writeInt(mServerPort);
        dest.writeLong(mStartTime);
    }

    @Override
    public String toString() {
        return "BroadcastInfo [mBroadcastConfig=" + mBroadcastConfig.toString()
                + ", mServerName=" + mServerName + ", mServerPort="
                + Integer.toString(mServerPort) + " mStartTime=" + Long.toString(mStartTime)
                + "]";
    }

    /**
     * ビットレート（kbps）を取得する
     * 
     * @return ビットレート（kbps）
     */
    public final int getAudioBrate() {
        return mBroadcastConfig.getAudioBrate();
    }

    /**
     * 録音するチャンネル数を取得する
     * 
     * @return 録音するチャンネル数
     */
    public final int getAudioChannel() {
        return mBroadcastConfig.getAudioChannel();
    }

    /**
     * 録音するサンプリングレート（Hz）を取得する
     * 
     * @return 録音するサンプリングレート（Hz）
     */
    public final int getAudioSampleRate() {
        return mBroadcastConfig.getAudioSampleRate();
    }

    /**
     * エンコードの品質を取得する
     * 
     * @return エンコードの品質
     */
    public final int getAudioMp3EncodeQuality() {
        return mBroadcastConfig.getAudioMp3EncodeQuality();
    }

    /**
     * DJ名を取得する
     * 
     * @return DJ名
     */
    public final String getChannelDjName() {
        return mBroadcastConfig.getChannelDjName();
    }

    /**
     * タイトルを取得する
     * 
     * @return タイトル
     */
    public final String getChannelTitle() {
        return mBroadcastConfig.getChannelTitle();
    }

    /**
     * 番組の説明を取得する
     * 
     * @return 番組の説明
     */
    public final String getChannelDescription() {
        return mBroadcastConfig.getChannelDescription();
    }

    /**
     * 関連URLを取得する
     * 
     * @return 関連URL
     */
    public final String getChannelUrl() {
        return mBroadcastConfig.getChannelUrl();
    }

    /**
     * ジャンルを取得する
     * 
     * @return ジャンル
     */
    public final String getChannelGenre() {
        return mBroadcastConfig.getChannelGenre();
    }

    /**
     * マウントを取得する
     * 
     * @return マウント
     */
    public final String getChannelMount() {
        return mBroadcastConfig.getChannelMount();
    }

    /**
     * 配信サーバを取得する
     * 
     * @return 配信サーバ
     */
    public final String getServerName() {
        return mServerName;
    }

    /**
     * ポート番号を取得する
     * 
     * @return ポート番号
     */
    public final int getServerPort() {
        return mServerPort;
    }
    
    /**
     * 配信を開始した時刻を取得する
     * 
     * @return 配信を開始した時刻。<br />
     * {@link System#currentTimeMillis()}}で取得した配信開始時刻を返す。
     */
    public final long getStartTime() {
        return mStartTime;
    }
}
