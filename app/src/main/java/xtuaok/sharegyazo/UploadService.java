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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android.graphics.Color;
import android.os.Build;
import android.os.Debug;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.preference.PreferenceManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import androidx.core.app.NotificationCompat;


import android.util.Log;
import android.widget.Toast;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;

import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

public class UploadService extends IntentService {
    private static final String LOG_TAG = "GyazoUploadService";

    private static final String IMGUR_CLIENT_ID = "de242da82e84888";
    // private static final String IMGUR_CLIENT_SECRET = "8b4817cbf69490a7e0aad35a693bcdb0324a1e1f";

    private static final String IMGUR_API = "https://api.imgur.com/";
    private static final String IMGUR_API_ENDPOINT = "https://api.imgur.com/3/upload.json";

    private static final int NOTIFY_ONGOING   = 0x0;
    private static final int NOTIFY_UPLOADING = 0x1;
    private static final int NOTIFY_DONE      = 0x2;
    private static final String NOTIFICATION_CHANNEL_ALL = "N_CH_ALL";


    private Handler mHandler;
    private Context mContext;
    private File mCacheFile;
    private Profile mProfile;
    private String mGyazoCGI;
    private String mGyazoID;
    private boolean mCopyURL;
    private boolean mOpenURL;
    private boolean mShareURL;
    private int mQuality;
    private int mNotifyAction;
    private boolean mNotifyClose;
    private boolean mNotification;
    private NotificationCompat.Builder mUploadNotify;
    private NotificationManager mNotificationManager;
    private String mErrorMessage = "";
    private static final int MAX_IMAGE_PIXEL = 1920; // HD
    private Bitmap.CompressFormat mFormat;
    private Bitmap mNotifyImage;
    

    private boolean mIsRetry = false;
    private boolean mIsImgUr = false;

    public UploadService(String name) {
        super(name);
        setIntentRedelivery(true);
        mHandler = new Handler();
    }

    public UploadService() {
        super("UploadService");
        setIntentRedelivery(true);
        mHandler = new Handler();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        startForeground(NOTIFY_ONGOING, new Notification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int start_id)
    {
        if (flags == START_FLAG_REDELIVERY) {
            mIsRetry = true;
        }
        Log.d(LOG_TAG, "onStartCommand: " + flags);
        return super.onStartCommand(intent, flags, start_id);
    }

    /**
     *  TODO: should choose preference by ID
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String result = "";
        Uri uri = intent.getData();
        File file = new File(uri.getPath());
        mProfile = intent.getParcelableExtra("profile");

        mContext = getApplicationContext();
        mGyazoCGI = mProfile.getURL();
        mGyazoID  = mProfile.getGyazoID();
        mCopyURL  = prefs.getBoolean(GyazoPreference.PREF_COPY_URL, true);
        mOpenURL  = prefs.getBoolean(GyazoPreference.PREF_OPEN_BROWSER, false);
        mQuality = mProfile.getImageQuality();
        mShareURL = prefs.getBoolean(GyazoPreference.PREF_SHARE_TEXT, true);
        mNotifyAction = Integer.parseInt(prefs.getString(GyazoPreference.PREF_NOTIFY_ACTION, "0"));
        mNotifyClose = prefs.getBoolean(GyazoPreference.PREF_AUTO_CLOSE_NOTIFICATION, false);
        mNotification = prefs.getBoolean(GyazoPreference.PREF_SHOW_NOTIFICATION, true);

        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (mGyazoCGI.startsWith(IMGUR_API)) {
            mIsImgUr = true;
            mGyazoCGI = IMGUR_API_ENDPOINT;
        }
        if (mProfile.getFormat().equals("png")) {
            mFormat = CompressFormat.PNG;
        } else {
            mFormat = CompressFormat.JPEG;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mNotificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ALL) == null) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ALL, getString(R.string.notification_name_all), NotificationManager.IMPORTANCE_DEFAULT);

            // Configure the notification channel.
            // notificationChannel.setDescription(getString(R.string.notification_desc_all));
            //notificationChannel.enableLights(true);
            //notificationChannel.setLightColor(Color.RED);
            //notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            //notificationChannel.enableVibration(true);
            mNotificationManager.createNotificationChannel(notificationChannel);

        }

        // Notify UPLOADING
        Intent notifIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        mUploadNotify = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ALL);
        mUploadNotify.setContentTitle(getString(R.string.app_name))
                .setProgress(0, 0, true)
                .setContentText(getString(R.string.convert_image))
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setTicker(getString(R.string.dialog_message_uploading))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(mNotifyClose);

        if (mNotification) {
            mNotificationManager.notify(NOTIFY_UPLOADING, mUploadNotify.build());
        }
        if (!mIsRetry) {
            mHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(mContext, getString(R.string.dialog_message_uploading), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            if (! file.exists()) return;
        }

        result = doUpload(mGyazoCGI, file);
        notify(result);
        file.delete();
        stopForeground(true);
    }

    private Bitmap createNotifyImage(File file) {
        Bitmap bitmap;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), opts);
        int size = Math.max(opts.outHeight, opts.outHeight);
        opts.inSampleSize = 1;
        while (size / opts.inSampleSize > MAX_IMAGE_PIXEL) {
            opts.inSampleSize = opts.inSampleSize * 2;
        }
        opts.inJustDecodeBounds = false;
        bitmap = BitmapFactory.decodeFile(file.getPath(), opts);
        return bitmap;
    }

    /**
     *  Notify result to user
     *
     * @param result  HTTP result string.
     */
    private void notify(String result) {
        Intent intent = new Intent();
        PendingIntent intentView = null;
        PendingIntent intentSend = null;
        PendingIntent intentCopy = null;

        Log.i(LOG_TAG, "DONE: result: " + result);
        if (! mErrorMessage.isEmpty()) {
            mHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(mContext, mErrorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }

        /*
         * Get the bitmap for notification image.
         */
        if (mCacheFile != null && mCacheFile.exists()) {
            mCacheFile.delete();
        }

        if (mIsImgUr) {
            // imgur
            Log.d(LOG_TAG, "response: " + result);
            try {
                JSONObject json = new JSONObject(result);
                result = json.getJSONObject("data").getString("link");
            } catch (JSONException ex) {
                Log.d(LOG_TAG, "JSONException: " + ex.getMessage());
                mErrorMessage = "Error: " + ex.getMessage();
                result = ex.getMessage();
            }
        }

        if (result == null || result.isEmpty() || !result.startsWith("http")) {
            Intent emptyIntent = new Intent();
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            mNotificationManager.cancel(NOTIFY_UPLOADING);

            Notification.Builder builder = new Notification.Builder(this);
            builder.setStyle(new Notification.BigTextStyle()
                    .setSummaryText(getString(R.string.failed_to_upload))
                    .bigText(getString(R.string.failed_to_upload_detail)))
                    .setContentTitle(getString(R.string.app_name))
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setSmallIcon(R.drawable.ic_warning_white_24dp)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setAutoCancel(mNotifyClose);

            if (mNotifyImage != null) {
                builder.setLargeIcon(mNotifyImage);
            }

            if (mNotification) {
                mNotificationManager.notify(NOTIFY_UPLOADING, builder.build());
            }

            mHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(mContext, getString(R.string.failed_to_upload), Toast.LENGTH_LONG).show();
                }
            });
            if (mNotifyImage != null && !mNotifyImage.isRecycled())
                mNotifyImage.recycle();
            return;
        } /* failed */

        /*
         * convert Image URL for gyazo.com
         */
        if (mGyazoCGI.contains("://upload.gyazo.com/") &&
                result.startsWith("https://gyazo.com/")) {
            String ext = "." + mProfile.getFormat();
            String hash = result.substring(18, result.length()).replaceAll("[\n\r]", "");
            result = "https://i.gyazo.com/" + hash + ext;
        }

        /* auto copy */
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

        /* auto open URL */
        if (mOpenURL) {
            intent.setAction(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setData(Uri.parse(result));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } else if (mShareURL) { /* auto share URL */
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, result);
            mContext.startActivity(intent);
        }

        if (!mNotification) return;

        // Notifications
        Intent actionIntent;

        // VIEW
        actionIntent = new Intent();
        actionIntent.setAction(Intent.ACTION_VIEW);
        actionIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        actionIntent.setData(Uri.parse(result));
        actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentView = PendingIntent.getActivity(mContext, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // SEND
        actionIntent = new Intent();
        actionIntent.setAction(Intent.ACTION_SEND);
        actionIntent.setType("text/plain");
        actionIntent.addCategory(Intent.CATEGORY_DEFAULT);
        actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        actionIntent.putExtra(Intent.EXTRA_TEXT, result);
        intentSend = PendingIntent.getActivity(mContext, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // COPY
        actionIntent = new Intent(mContext, ClipText.class);
        actionIntent.setAction(Intent.ACTION_MAIN);
        actionIntent.putExtra(Intent.EXTRA_TEXT, result);
        actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentCopy = PendingIntent.getActivity(mContext, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        // build
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ALL);
        builder.setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.message_uploaded))
                .setTicker(result)
                .setSmallIcon(R.drawable.ic_cloud_done_24dp)
                .setLargeIcon(mNotifyImage)
                .setAutoCancel(mNotifyClose);

        NotificationCompat.Action action =
                new NotificationCompat.Action.Builder(R.drawable.ic_open_in_browser_24dp,
                        getString(R.string.open), intentView).build();
        builder.addAction(action);
        action = new NotificationCompat.Action.Builder(R.drawable.ic_share_24dp,
                getString(R.string.share), intentSend).build();
        builder.addAction(action);
        action =  new NotificationCompat.Action.Builder(R.drawable.ic_content_copy_24dp,
                getString(R.string.copy), intentCopy).build();
        builder.addAction(action);

        // Tap actions
        if (mNotifyAction == 1) {
            builder.setContentIntent(intentView);
        } else if (mNotifyAction == 2) {
            builder.setContentIntent(intentSend);
        } else if (mNotifyAction == 3) {
            builder.setContentIntent(intentCopy);
        }

        builder.setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(mNotifyImage)
                .bigLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setBigContentTitle(getString(R.string.message_uploaded))
                .setSummaryText(result));
        mNotificationManager.cancel(NOTIFY_UPLOADING);
        mNotificationManager.notify(result, NOTIFY_DONE, builder.build());

        if (mNotifyImage != null && ! mNotifyImage.isRecycled())
            mNotifyImage.recycle();
    }

    /**
     * get image orientation and set into matrix
     *
     * @param path      path to image file (JPEG)
     * @param matrix    matrix
     * @return          rotated or not
     */
    private boolean rotatedMatrix(String path, Matrix matrix) {
        boolean rotate = false;
        Log.d(LOG_TAG, "Check EXIF Orientation");
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
            switch (orientation) {
                case 0: // ExifInterface.ORIENTATION_UNDEFINED:
                    Log.d(LOG_TAG, "Orientation: UNDEFINED");
                    break;
                case 1: // ExifInterface.ORIENTATION_NORMAL:
                    Log.d(LOG_TAG, "Orientation: NORMAL");
                    break;
                case 2: // ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    Log.d(LOG_TAG, "Orientation: FLIP_HORIZONTAL");
                    matrix.postScale(-1f, 1f);
                    break;
                case 3: // ExifInterface.ORIENTATION_ROTATE_180:
                    Log.d(LOG_TAG, "Orientation: ROTATE_180");
                    matrix.postRotate(180f);
                    break;
                case 4: // ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    Log.d(LOG_TAG, "Orientation: FLIP_VERTICAL");
                    matrix.postScale(1f, -1f);
                    break;
                case 5: // ExifInterface.ORIENTATION_TRANSVERSE:
                    Log.d(LOG_TAG, "Orientation: TRANSVERSE");
                    matrix.postRotate(-90f);
                    matrix.postScale(1f, -1f);
                    break;
                case 6: // ExifInterface.ORIENTATION_ROTATE_90:
                    Log.d(LOG_TAG, "Orientation: ROTATE_90");
                    matrix.postRotate(90f);
                    break;
                case 7: // ExifInterface.ORIENTATION_TRANSPOSE:
                    Log.d(LOG_TAG, "Orientation: TRANSPOSE");
                    matrix.postRotate(90f);
                    matrix.postScale(1f, -1f);
                    break;
                case 8: // ExifInterface.ORIENTATION_ROTATE_270:
                    Log.d(LOG_TAG, "Orientation: ROTATE_270");
                    matrix.postRotate(-90f);
                    break;
            }
            if (orientation >= 2) rotate = true;
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception: ", ex);
        }
        return rotate;
    }

    /**
     * convert image file for uploading
     *
     * @param file          The file object to convert
     * @return              new File object in cacheDir, should be deleted when exit.
     * @throws IOException
     */
    private File convertImageFile(File file) throws IOException
    {
        ByteArrayOutputStream baos;
        BufferedOutputStream bos;
        File temp = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        Matrix matrix = new Matrix();

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        Log.d(LOG_TAG, "ImageType: " + options.outMimeType);

        // rotate by exif
        boolean rotate = false;
        if ("image/jpeg".equalsIgnoreCase(options.outMimeType)) {
            rotate = rotatedMatrix(file.getPath(), matrix);
        }

        // sampling size
        options.inSampleSize = 1;
        long memRequire = ((options.outWidth / options.inSampleSize) * (options.outHeight / options.inSampleSize)) * 4;
        Log.d(LOG_TAG, "Require mem: " + memRequire);
        Log.d(LOG_TAG, "Free memory: " + getFreeMemory());
        while (memRequire > getFreeMemory()) {
            options.inSampleSize = options.inSampleSize * 2;
            memRequire = ((options.outWidth / options.inSampleSize) * (options.outHeight / options.inSampleSize)) * 4;
        }

        // resize if required
        int w = options.outWidth;
        int h = options.outHeight;
        float scale = Math.min((float)1.0, Math.min((float) MAX_IMAGE_PIXEL / w, (float) MAX_IMAGE_PIXEL / h));
        if (scale < 1.0) {
            matrix.postScale(scale, scale);
        }
        Log.d(LOG_TAG, "Size: w:" + w + ", h:" + h);
        Log.d(LOG_TAG, "On memory sample size: " + options.inSampleSize);
        Log.d(LOG_TAG, "Upload scale: " + scale);
        // create resized bitmap
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
        if (rotate || scale < 1.0) {
            Bitmap r_bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
            bitmap.recycle();
            bitmap = r_bitmap;
        }

        Log.d(LOG_TAG, "Write a cache file");
        temp = File.createTempFile("temp", "tmp", getCacheDir());
        bos = new BufferedOutputStream(new FileOutputStream(temp));
        bitmap.compress(mFormat, mQuality, bos);
        bos.flush();
        bos.close();

        mNotifyImage = bitmap;

        return temp;
    }

    /**
     * do upload
     *
     * @param cgi       absolute URL String
     * @param source    upload source file
     * @return          result string (HTTP Response Body), {@code null} if failed to upload.
     */
    private String doUpload(String cgi, File source) {
        String result = null;

        try {
            mCacheFile = convertImageFile(source);
            result = postGyazo(cgi, mCacheFile);
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
            mErrorMessage = "Error: " + ex.getMessage();
        }
        return result;
    }

    /**
     * post to gyazo cgi server
     *
     * @param cgi . absolute URL of CGI as String
     * @param file . upload file object
     * @return result string
     * @throws IOException
     */
    private String postGyazo(String cgi, File file) throws IOException {
        String result = "";
        Request request;
        OkHttpClient client = new OkHttpClient();
        final long totalSize = file.length();

        client.setConnectTimeout(15, TimeUnit.SECONDS);
        client.setReadTimeout(15, TimeUnit.SECONDS);
        client.setWriteTimeout(15, TimeUnit.SECONDS);

        if (mIsImgUr) {
            Log.d(LOG_TAG, "Create request body: IMGUR ClientID: " + IMGUR_CLIENT_ID);
            setProgress(0);
            RequestBody requestBody = new MultipartBuilder()
                    .type(MultipartBuilder.FORM)
                    .addFormDataPart("image", file.getName(),
                            new CountingFileRequestBody(file, "image/*", new CountingFileRequestBody.ProgressListener() {
                                private int previous = 0;

                                @Override
                                public void transferred(long num) {
                                    float progress = (num / (float) totalSize) * 100;
                                    if ((int) progress <= previous) return;
                                    previous = (int)progress;
                                    setProgress(previous);
                                }
                            }))
                    .build();
            request = new Request.Builder()
                    .addHeader("Authorization", "Client-ID " + IMGUR_CLIENT_ID)
                    .url(cgi)
                    .post(requestBody)
                    .build();
        } else {
            Log.d(LOG_TAG, "Create request body: GyazoID: " + mGyazoID);
            setProgress(0);
            RequestBody requestBody = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addFormDataPart("id", mGyazoID)
                .addFormDataPart("imagedata", file.getName(),
                        new CountingFileRequestBody(file, "image/*", new CountingFileRequestBody.ProgressListener() {
                            private int previous = 0;

                            @Override
                            public void transferred(long num) {
                                float progress = (num / (float) totalSize) * 100;
                                if ((int) progress <= previous) return;
                                previous = (int)progress;
                                setProgress(previous);
                            }
                        }))
                .build();
            request = new Request.Builder()
                .url(cgi)
                .post(requestBody)
                .build();
        }
        try {
            Log.d(LOG_TAG, "Post execute");
            Response response = client.newCall(request).execute();
            String gyazo_id = response.headers().get("X-Gyazo-Id");
            Log.d(LOG_TAG, "GyazoID: " + gyazo_id);
            if (gyazo_id != null && mGyazoID.isEmpty()) {
                ProfileManager pm = ProfileManager.getInstance(getBaseContext());
                Profile p = pm.getProfileByUUID(mProfile.getUUID());
                p.setGyazoID(gyazo_id);
                pm.storeProfiles();
            }
            result = response.body().string();
            Log.d(LOG_TAG, "HTTP Response: " + response.code());
            Log.d(LOG_TAG, "Response Body: " + result);
        } catch (IOException e) {
            Log.e(LOG_TAG, "HTTP Error", e);
        }
        return result;
    }

    /**
     *
     * get free memory byte count
     * @return byte count
     */
    private static long getFreeMemory() {
        Runtime r = Runtime.getRuntime();
        return r.maxMemory() - (r.totalMemory() - r.freeMemory())
                - Debug.getNativeHeapAllocatedSize();
    }

    /**
     * set upload progress as percent
     *
     * @param percent upload progress percentage.
     */
    public void setProgress(int percent) {
        if (!mNotification) return;
        mUploadNotify.setProgress(100, percent, false)
                .setContentText(getString(R.string.dialog_message_uploading) + " " + percent + "%");
        mNotificationManager.notify(NOTIFY_UPLOADING, mUploadNotify.build());
    }
}