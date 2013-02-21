package xtuaok.sharegyazo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;

import android.preference.PreferenceManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.support.v4.app.NotificationCompat;
import android.content.ClipboardManager;
import android.util.Log;
import android.widget.Toast;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
    private int mNotifyAction;
    private Bitmap mBitmap;
    private NotificationManager mNotificationManager;
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
        Uri uri = intent.getData();
        String result = "";

        mContext = getApplicationContext();
        mGyazoCGI = prefs.getString(GyazoPreference.PREF_GYAZO_CGI, DEFAULT_UPLOADER);
        mGyazoID  = prefs.getString(GyazoPreference.PREF_GYAZO_ID, "");
        mCopyURL  = prefs.getBoolean(GyazoPreference.PREF_COPY_URL, true);
        mOpenURL  = prefs.getBoolean(GyazoPreference.PREF_OPEN_BROWSER, false);
        mShareURL = prefs.getBoolean(GyazoPreference.PREF_SHARE_TEXT, false);
        mNotifyAction = Integer.parseInt(prefs.getString(GyazoPreference.PREF_NOTIFY_ACTION, "0"));

        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        // Notify UPLOADING
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getString(R.string.dialog_message_uploading))
            .setSmallIcon(R.drawable.ic_launcher)
            .setTicker(getString(R.string.dialog_message_uploading))
            .setOngoing(true);
        mNotificationManager.notify(NOTIFY_UPLOADING, builder.build());

        mHandler.post(new Runnable() {            
            public void run() {
                Toast.makeText(mContext, getString(R.string.dialog_message_uploading), Toast.LENGTH_LONG).show();                
            }
        });

        result = doUpload(mGyazoCGI, uri);
        done(result);
    }
    
    private void done(String result) {
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
            mHandler.post(new Runnable() {            
                public void run() {
                    Toast.makeText(mContext, getString(R.string.failed_to_upload), Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        if (mCopyURL) {
            ClipData clip = ClipData.newPlainText("url text", result);
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);
            mHandler.post(new Runnable() {            
                public void run() {
                    Toast.makeText(mContext, getString(R.string.toast_copy_url), Toast.LENGTH_LONG).show();
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
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_launcher)
            .setLargeIcon(mBitmap)
            .addAction(android.R.drawable.ic_menu_view, "Open", intentView)
            .addAction(android.R.drawable.ic_menu_send, "Send", intentSend)
            .addAction(android.R.drawable.ic_menu_set_as, "Copy", intentCopy);

        // Backward Compatibility
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            if (mNotifyAction == 1) {
                builder.setContentIntent(intentView);
            } else if (mNotifyAction == 2) {
                builder.setContentIntent(intentSend);
            }
        } else {
            builder.setContentIntent(intentCopy);
        }

        Notification notification =
                new NotificationCompat.BigTextStyle(builder)
                    .bigText(result)
                    .setSummaryText(getString(R.string.dialog_message_done))
                    .build();
        mNotificationManager.cancel(NOTIFY_UPLOADING);
        mNotificationManager.notify(result, NOTIFY_DONE, notification);
    }

    private String doUpload(String cgi, Uri uri) {
        String result = "";
        InputStream in;
        try {
            in = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            in.close();
            in = getContentResolver().openInputStream(uri);
            Log.d(LOG_TAG, "ImageType: " + options.outMimeType);

            if (options.outMimeType.endsWith("png")) {
                byte[] img_byte = readBytes(in);
                in.close();
                // for notification image
                int scale = 1;
                int h = options.outHeight;
                int w = options.outWidth;
                while (true) {
                    if (h / scale > android.R.dimen.notification_large_icon_height ||
                        w / scale > android.R.dimen.notification_large_icon_width) {
                        scale = scale * 2;
                    } else {
                        break;
                    }
                }
                Log.d(LOG_TAG, "Scale: " + scale);
                options.inSampleSize = scale;
                options.inJustDecodeBounds = false;
                mBitmap = BitmapFactory.decodeByteArray(img_byte, 0, img_byte.length, options);
                result = postGyazo(cgi, img_byte);
            } else {
                // TODO: more better solution for OutOfMemory.
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                int scale = 1;
                int h = options.outHeight;
                int w = options.outWidth;
                while (true) {
                    if (h / scale > MAX_IMAGE_PIXEL || w / scale > MAX_IMAGE_PIXEL) {
                        scale = scale * 2;
                    } else {
                        break;
                    }
                }
                Log.d(LOG_TAG, "Scale: " + scale);
                options.inSampleSize = scale;
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeStream(in, null, options);
                in.close();
                mBitmap = Bitmap.createScaledBitmap(bitmap,
                        android.R.dimen.notification_large_icon_width,
                        android.R.dimen.notification_large_icon_height, false);
                bitmap.compress(CompressFormat.PNG, 100, byteBuffer);
                bitmap.recycle();
                result = postGyazo(cgi, byteBuffer.toByteArray());
            }
        } catch (OutOfMemoryError e0) {
            e0.printStackTrace();
            mErrorMessage = "Out of Memory";
        } catch (java.io.FileNotFoundException e1) {
            e1.printStackTrace();
            mErrorMessage = "File not found or not local";
        } catch (IOException e2) {
            e2.printStackTrace();
            mErrorMessage = "IOError";
        }
        return result;
    }

    private String postGyazo(String cgi, byte[] imgData) throws ClientProtocolException, IOException {
        List<NameValuePair> nValuePairs = new ArrayList<NameValuePair>();
        nValuePairs.add(new BasicNameValuePair("id", mGyazoID));  
        String ret;
        HttpMultipartPostRequest request = new HttpMultipartPostRequest(cgi, nValuePairs, imgData);
        Log.d(LOG_TAG, "send POST request");
        ret = request.send();
        return ret;
    }

    // copy pasted from Stackoverflow.com
    private byte[] readBytes(InputStream inputStream) throws IOException {
        // this dynamically extends to take the bytes you read
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the byteBuffer
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }

        // and then we can return your byte array.
        return byteBuffer.toByteArray();
    }

}
