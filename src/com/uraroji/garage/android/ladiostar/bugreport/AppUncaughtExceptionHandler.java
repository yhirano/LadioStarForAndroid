/*
 * Referenced by http://www.adamrocker.com/blog/288/bug-report-system-for-android.html
 */

package com.uraroji.garage.android.ladiostar.bugreport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import com.uraroji.garage.android.ladiostar.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;

public class AppUncaughtExceptionHandler implements UncaughtExceptionHandler {
	private static final String FILE_NAME = "bugreport.txt";

	private static Context sContext;
	private static PackageInfo sPackInfo;
	private UncaughtExceptionHandler mDefaultUEH;
	
	public AppUncaughtExceptionHandler(Context context) {
		sContext = context;
		try {
			// パッケージ情報取得
			sPackInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		} catch (NameNotFoundException e) {
			// パッケージ情報が取得できない場合はどうしようもないので何もしない
		}
		mDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
	}
	
	public void uncaughtException(Thread th, Throwable t) {
		try {
			saveState(t);
		} catch (FileNotFoundException e) {
			// 保存に失敗した場合はどうしようもないので何もしない
		}
		mDefaultUEH.uncaughtException(th, t);
	}
	
	private void saveState(Throwable e) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(sContext.openFileOutput(FILE_NAME, Context.MODE_WORLD_READABLE));
        StringBuilder sb = new StringBuilder();
        sb.append("Exception\r\n");
        sb.append(e);
        sb.append("\r\n");
        sb.append("\r\n");
        sb.append("Stack trace\r\n");
        pw.print(sb.toString());
		for (StackTraceElement stack : e.getStackTrace()) {
            sb.setLength(0);
            sb.append(stack.getClassName()).append("#");
            sb.append(stack.getMethodName()).append(":");
            sb.append(stack.getLineNumber());
            pw.println(sb.toString());
        }
        pw.close();
	}
		
	public static final void showBugReportDialogIfExist() {
		File file = sContext.getFileStreamPath(FILE_NAME);
		if (file != null & file.exists()) {
			AlertDialog.Builder builder = new AlertDialog.Builder(sContext);
			builder.setMessage(sContext.getString(R.string.send_bugreport));
			builder.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					deleteBugReport();
				}
			});
			builder.setNegativeButton(R.string.cancel, new OnClickListener(){
				public void onClick(DialogInterface dialog, int which) {
					finish(dialog);
				}});
			builder.setPositiveButton(R.string.post, new OnClickListener(){
				public void onClick(DialogInterface dialog, int which) {
					postBugReportInBackground();// バグ報告
					dialog.dismiss();
				}});
			AlertDialog dialog = builder.create();
			dialog.show();
		}
	}
	
	private static void postBugReportInBackground() {
		new Thread(new Runnable(){
			public void run() {
				postBugReport();
				deleteBugReport();
			}}).start();
	}
	
	private static void postBugReport() {
        List<NameValuePair> nvps = new ArrayList<NameValuePair>(6);
        String bug = getFileBody(sContext.getFileStreamPath(FILE_NAME));
        nvps.add(new BasicNameValuePair("dev", Build.DEVICE));
        nvps.add(new BasicNameValuePair("mod", Build.MODEL));
        nvps.add(new BasicNameValuePair("sdk", Build.VERSION.SDK));
        nvps.add(new BasicNameValuePair("app_name", sContext.getString(R.string.app_name)));
		// 通常はnullになることはないはず
        if (sPackInfo != null) {
			nvps.add(new BasicNameValuePair("ver_name", sPackInfo.versionName));
			nvps.add(new BasicNameValuePair("ver_code", String
					.valueOf(sPackInfo.versionCode)));
		}
        nvps.add(new BasicNameValuePair("bug", bug));
        try {
        	HttpPost httpPost = new HttpPost("http://bugslife.uraroji.com/bug");
            httpPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
            DefaultHttpClient httpClient = new DefaultHttpClient();
            httpClient.execute(httpPost);
        } catch (IOException e) {
            // 送信に失敗した場合は何もしない
        }
	}
	
	private static String getFileBody(File file) {
		StringBuilder sb = new StringBuilder();
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;
			while((line = br.readLine()) != null) {
				sb.append(line).append("\r\n");
			}
		} catch (Exception e) {
			// バグレポートファイルの中身を取得できない場合は、返す文字列にその旨を記述する
			sb.append("Failed to read bug report file.\r\n");
		}
		return sb.toString();
	}
	
	private static void deleteBugReport() {
		File file = sContext.getFileStreamPath(FILE_NAME);
		if (file.exists()) {
			file.delete();
		}
	}
	
	private static void finish(DialogInterface dialog) {
		deleteBugReport();
		dialog.dismiss();
	}
} 