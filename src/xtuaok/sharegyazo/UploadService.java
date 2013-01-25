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
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.text.ClipboardManager;
import android.util.Log;
import android.widget.Toast;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;

public class UploadService extends IntentService {
    private static final String LOG_TAG = "ShareToGyazo";
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
    private Notification mNotification;
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
        mNotification = new Notification(
                R.drawable.ic_launcher,
                getString(R.string.dialog_message_uploading),
                System.currentTimeMillis());
        mNotification.flags = Notification.FLAG_ONGOING_EVENT;
        mNotification.setLatestEventInfo(
                mContext,
                getString(R.string.app_name),
                getString(R.string.dialog_message_uploading),
                null);
        mNotificationManager.notify(NOTIFY_UPLOADING, mNotification);

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
        PendingIntent pIntent = null;

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
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setText(result);
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
        if (mNotifyAction > 0) {
            Intent notifyIntent = new Intent();
            switch (mNotifyAction) {
            case 1:
                notifyIntent.setAction(Intent.ACTION_VIEW);
                notifyIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                notifyIntent.setData(Uri.parse(result));
                notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                break;
            case 2:
                notifyIntent.setAction(Intent.ACTION_SEND);
                notifyIntent.setType("text/plain");
                notifyIntent.addCategory(Intent.CATEGORY_DEFAULT);
                notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                notifyIntent.putExtra(Intent.EXTRA_TEXT, result);                
                break;
            }
            pIntent = PendingIntent.getActivity(mContext, 0, notifyIntent, 0);
        }
        Notification notification = new Notification(
                R.drawable.ic_launcher,
                getString(R.string.dialog_message_done),
                System.currentTimeMillis());
        notification.setLatestEventInfo(
                mContext,
                getString(R.string.app_name),
                getString(R.string.dialog_message_done),
                pIntent);
        notification.flags = Notification.FLAG_AUTO_CANCEL;
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
