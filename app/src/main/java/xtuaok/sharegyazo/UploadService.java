/*
 * Copyright (C) 2015 xtuaok (http://twitter.com/xtuaok)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xtuaok.sharegyazo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Guard;
import java.util.ArrayList;
import java.util.List;

import android.os.Debug;
import android.provider.MediaStore;
import android.util.Pair;

import android.database.Cursor;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.preference.PreferenceManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;

public class UploadService extends IntentService {
    private static final String LOG_TAG = "GyazoUploadService";
    private static final String DEFAULT_UPLOADER = "http://gyazo.com/upload.cgi";

    private static final int NOTIFY_UPLOADING = 0x0;
    private static final int NOTIFY_DONE      = 0x1;
    
    private Handler mHandler;
    private Context mContext;
    private String mGyazoCGI;
    private String mGyazoID;
    private boolean mCopyURL;
    private boolean mOpenURL;
    private boolean mShareURL;
    private int mFormat;
    private int mQuality;
    private int mNotifyAction;
    private boolean mNotifyClose;
    private boolean mNotification;
    private Bitmap mBitmap = null;
    private NotificationManager mNotificationManager;
    private Uri mUri;
    private String mErrorMessage = "";

    private static final int MAX_IMAGE_PIXEL = 1920; // HD

    public UploadService(String name) {
        super(name);
    }

    public UploadService() {
        super("UploadService");
        mHandler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String result = "";

        mUri = intent.getData();
        mContext = getApplicationContext();
        mGyazoCGI = prefs.getString(GyazoPreference.PREF_GYAZO_CGI, DEFAULT_UPLOADER);
        mGyazoID  = prefs.getString(GyazoPreference.PREF_GYAZO_ID, "");
        mCopyURL  = prefs.getBoolean(GyazoPreference.PREF_COPY_URL, true);
        mOpenURL  = prefs.getBoolean(GyazoPreference.PREF_OPEN_BROWSER, false);
        mFormat = Integer.parseInt(prefs.getString(GyazoPreference.PREF_IMAGE_FILE_FORMAT, "0"));
        mQuality = Integer.parseInt(prefs.getString(GyazoPreference.PREF_IMAGE_QUALITY, "100"));
        mShareURL = prefs.getBoolean(GyazoPreference.PREF_SHARE_TEXT, true);
        mNotifyAction = Integer.parseInt(prefs.getString(GyazoPreference.PREF_NOTIFY_ACTION, "0"));
        mNotifyClose = prefs.getBoolean(GyazoPreference.PREF_AUTO_CLOSE_NOTIFICATION, false);
        mNotification = prefs.getBoolean(GyazoPreference.PREF_SHOW_NOTIFICATION, true);

        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        // Notify UPLOADING
        Intent notifIntent = new Intent();
        notifIntent.addCategory(Intent.CATEGORY_DEFAULT);
        notifIntent.setAction(Intent.ACTION_VIEW);
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notifIntent.setData(mUri);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getString(R.string.dialog_message_uploading))
            .setProgress(100, 0, false)
            .setContentText(getString(R.string.convert_image))
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setTicker(getString(R.string.dialog_message_uploading))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(mNotifyClose);

        if (mNotification) {
            mNotificationManager.notify(NOTIFY_UPLOADING,  builder.build());
        }
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(mContext, getString(R.string.dialog_message_uploading), Toast.LENGTH_LONG).show();                
            }
        });

        result = doUpload(mGyazoCGI, mUri);
        done(result, mUri);
    }
    
    @SuppressWarnings("deprecation")
    private void done(String result, Uri uri) {
        Intent intent = new Intent();
        PendingIntent intentView = null;
        PendingIntent intentSend = null;
        PendingIntent intentCopy = null;

        Log.i(LOG_TAG, "Result: " + result);
        if (mErrorMessage != "") {
            mHandler.post(new Runnable() {            
                public void run() {
                    Toast.makeText(mContext, mErrorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }

        if (result == null || !result.startsWith("http")) {
            mNotificationManager.cancel(NOTIFY_UPLOADING);

            Intent notifIntent = new Intent();
            notifIntent.addCategory(Intent.CATEGORY_DEFAULT);
            notifIntent.setAction(Intent.ACTION_VIEW);
            notifIntent.setData(uri);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder.setContentTitle(getString(R.string.failed_to_upload))
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setTicker(getString(R.string.failed_to_upload))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setAutoCancel(mNotifyClose);

            builder.setLargeIcon(mBitmap);

            if (mNotification) {
                mNotificationManager.notify(NOTIFY_UPLOADING, builder.build());
            }
            if (mBitmap != null && !mBitmap.isRecycled()) {
                mBitmap.recycle();
            }
            mHandler.post(new Runnable() {            
                public void run() {
                    Toast.makeText(mContext, getString(R.string.failed_to_upload), Toast.LENGTH_LONG).show();
                }
            });
            return;
        }

        if (mGyazoCGI.contains("://gyazo.com/") &&
                result.startsWith("https://gyazo.com/")) {
            String ext = ".png";
            String hash = result.substring(17, result.length());
            if (mFormat == 1) ext = ".png";
            result = "https://i.gyazo.com/" + hash + ext;
        }

        if (mCopyURL) {
            ClipData clip = ClipData.newPlainText("url text", result);
            ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(clip);
            mHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(getApplicationContext(),
                            getString(R.string.toast_copy_url), Toast.LENGTH_LONG).show();
                }
            });
        }

        if (mOpenURL) {
            intent.setAction(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setData(Uri.parse(result));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } else if (mShareURL) {
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, result);
            mContext.startActivity(intent);
        }

        if (!mNotification) {
            return;
        }
        // Notifications
        Intent notifyIntent;

        // VIEW
        notifyIntent = new Intent();
        notifyIntent.setAction(Intent.ACTION_VIEW);
        notifyIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        notifyIntent.setData(Uri.parse(result));
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentView = PendingIntent.getActivity(mContext, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // SEND
        notifyIntent = new Intent();
        notifyIntent.setAction(Intent.ACTION_SEND);
        notifyIntent.setType("text/plain");
        notifyIntent.addCategory(Intent.CATEGORY_DEFAULT);
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notifyIntent.putExtra(Intent.EXTRA_TEXT, result);
        intentSend = PendingIntent.getActivity(mContext, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // COPY
        notifyIntent = new Intent(mContext, ClipText.class);
        notifyIntent.setAction(Intent.ACTION_MAIN);
        notifyIntent.putExtra(Intent.EXTRA_TEXT, result);
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentCopy = PendingIntent.getActivity(mContext, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // build
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(getString(R.string.message_uploaded))
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .addAction(android.R.drawable.ic_menu_view, getString(R.string.open), intentView)
            .addAction(android.R.drawable.ic_menu_share, getString(R.string.share), intentSend)
            .addAction(android.R.drawable.ic_menu_set_as, getString(R.string.copy), intentCopy)
            .setTicker(result)
            .setContentText(result)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setAutoCancel(mNotifyClose);
            // Tap actions
        if (mNotifyAction == 1) {
            builder.setContentIntent(intentView);
        } else if (mNotifyAction == 2) {
            builder.setContentIntent(intentSend);
        } else if (mNotifyAction == 3) {
            builder.setContentIntent(intentCopy);
        }
        builder.setStyle(new Notification.BigPictureStyle()
        .bigPicture(mBitmap)
        .bigLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
        .setBigContentTitle(getString(R.string.message_uploaded))
        .setSummaryText(result));
        mNotificationManager.cancel(NOTIFY_UPLOADING);
        mNotificationManager.notify(result, NOTIFY_DONE, builder.build());
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
    }

    private Matrix getRotatedMatrix(Uri uri, Matrix matrix){
        ExifInterface exif = null;
        Log.d(LOG_TAG, "Check EXIF Orientation");
        if (uri.getScheme().equals("content")) {
            String[] projection = { MediaStore.Images.ImageColumns.ORIENTATION };
            Cursor c = getContentResolver().query(uri, projection, null, null, null);
            if (c.moveToFirst()) {
                int or = c.getInt(0);
                matrix.postRotate(or);
                Log.d(LOG_TAG, "Orientation: " + or);
            }
            return matrix;
        } else if (uri.getScheme().equals("file")) {
            try {
                exif = new ExifInterface(uri.getPath());
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error checking exif", e);
                return matrix;
            }
        } else {
            return matrix;
        }

        int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);

        switch (orientation) {
            case ExifInterface.ORIENTATION_UNDEFINED:
                Log.d(LOG_TAG, "Orientation: UNDEFINED");
                break;
            case ExifInterface.ORIENTATION_NORMAL:
                Log.d(LOG_TAG, "Orientation: NORMAL");
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                Log.d(LOG_TAG, "Orientation: FLIP_HORIZONTAL");
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                Log.d(LOG_TAG, "Orientation: ROTATE_180");
                matrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                Log.d(LOG_TAG, "Orientation: FLIP_VERTICAL");
                matrix.postScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                Log.d(LOG_TAG, "Orientation: ROTATE_90");
                matrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                Log.d(LOG_TAG, "Orientation: TRANSVERSE");
                matrix.postRotate(-90f);
                matrix.postScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                Log.d(LOG_TAG, "Orientation: TRANSPOSE");
                matrix.postRotate(90f);
                matrix.postScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                Log.d(LOG_TAG, "Orientation: ROTATE_270");
                matrix.postRotate(-90f);
                break;
        }
        return matrix;
    }

    private String doUpload(String cgi, Uri uri) {
        String result = "";
        InputStream is;

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            Matrix matrix = new Matrix();

            // get file information
            is = getContentResolver().openInputStream(uri);
            options.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            Log.d(LOG_TAG, "ImageType: " + options.outMimeType);

            // rotate by exif
            if ("image/jpeg".equalsIgnoreCase(options.outMimeType)) {
                matrix = getRotatedMatrix(uri, matrix);
            }

            // sampling size
            options.inSampleSize = 1;
            long memRequire = ((options.outWidth / options.inSampleSize) * (options.outHeight / options.inSampleSize)) * 4;
            while (memRequire > getFreeMemory()) {
                options.inSampleSize = options.inSampleSize * 2;
                memRequire = ((options.outWidth / options.inSampleSize) * (options.outHeight / options.inSampleSize)) * 4;
            }

            // resize if required
            int w = options.outWidth;
            int h = options.outHeight;
            float scale = Math.min((float) MAX_IMAGE_PIXEL / w, (float) MAX_IMAGE_PIXEL / h);
            if (scale < 1.0) {
                matrix.postScale(scale, scale);
            }
            Log.d(LOG_TAG, "SampleSize: " + options.inSampleSize);
            Log.d(LOG_TAG, "Scale: " + scale);

            // create resized bitmap
            options.inJustDecodeBounds = false;
            is = getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(is, null, options);
            mBitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);

            // temporary file
            File tempFile = null;
            FileOutputStream os = null;
            if (mFormat == 0) { // PNG
                tempFile = File.createTempFile("temp", "png", getCacheDir());
                os = new FileOutputStream(tempFile);
                mBitmap.compress(CompressFormat.PNG, mQuality, os);
            } else { // JPEG
                tempFile = File.createTempFile("temp", "jpg", getCacheDir());
                os = new FileOutputStream(tempFile);
                mBitmap.compress(CompressFormat.JPEG, mQuality, os);
            }
            os.flush();
            os.close();
            result = postGyazo(cgi, tempFile);
        } catch (OutOfMemoryError e0) {
            Log.e(LOG_TAG, "OutOfMemory", e0);
            mErrorMessage = "Out of Memory";
        } catch (java.io.FileNotFoundException e1) {
            Log.e(LOG_TAG, "FileNotFound", e1);
            mErrorMessage = "File not found or not local";
        } catch (IOException e2) {
            Log.e(LOG_TAG, "IOError", e2);
            mErrorMessage = "IOError";
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Unhandled Exception", ex);
            mErrorMessage = "Error: "+ex.getMessage();
        }
        return result;
    }

    private String postGyazo(String cgi, File file) throws IOException {
        List<Pair<String,String >> nValuePairs = new ArrayList<Pair<String,String>>();
        nValuePairs.add(new Pair<>("id", mGyazoID));
        String ret;
        HttpMultipartPostRequest request = new HttpMultipartPostRequest(cgi, nValuePairs, file, this);
        Log.d(LOG_TAG, "send POST request");
        ret = request.send();
        return ret;
    }

    private static long getFreeMemory() {
        Runtime r = Runtime.getRuntime();
        return r.maxMemory() - (r.totalMemory() - r.freeMemory())
                - Debug.getNativeHeapAllocatedSize();
    }

    public void seProgress(int perc) {
        if (!mNotification) return;
        Intent notifIntent = new Intent();
        notifIntent.addCategory(Intent.CATEGORY_DEFAULT);
        notifIntent.setAction(Intent.ACTION_VIEW);
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notifIntent.setData(mUri);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getString(R.string.dialog_message_uploading))
                .setProgress(100, perc, false)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setTicker(getString(R.string.dialog_message_uploading))
                .setContentText(perc + "%")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(mNotifyClose);
        mNotificationManager.notify(NOTIFY_UPLOADING, builder.build());
    }
}