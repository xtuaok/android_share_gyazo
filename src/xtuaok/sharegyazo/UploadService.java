package xtuaok.sharegyazo;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class UploadService extends Service {
    private static final String LOG_TAG = "ShareToGyazo";
    private static final String DEFAULT_UPLOADER = "http://gyazo.com/upload.cgi";

    private static final int MAX_IMAGE_PIXEL = 1920; // HD

    private final IBinder mBinder = new LocalBinder();

    private NotificationManager mNotifyManager;
    private Notification mNotification;

    private String mGyazoCGI;
    private String mGyazoID;
    private boolean mCopyURL;
    private boolean mOpenURL;
    private boolean mShareURL;

    public class LocalBinder extends Binder {
        public UploadService getService() {
            return UploadService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startService(new Intent(getApplicationContext(), getClass()));
    }

    public void upload(Uri uri) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        mGyazoCGI = prefs.getString(GyazoPreference.PREF_GYAZO_CGI, DEFAULT_UPLOADER);
        mGyazoID  = prefs.getString(GyazoPreference.PREF_GYAZO_ID, "");
        mCopyURL  = prefs.getBoolean(GyazoPreference.PREF_COPY_URL, true);
        mOpenURL  = prefs.getBoolean(GyazoPreference.PREF_OPEN_BROWSER, false);
        mShareURL = prefs.getBoolean(GyazoPreference.PREF_SHARE_TEXT, false);

        new UploadAsyncTask().execute(mGyazoCGI, uri.toString());
    }

    private void createNotification() {
        mNotifyManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE
        );

        mNotification = new Notification(
                R.drawable.ic_launcher,
                "Upload photos",
                System.currentTimeMillis()
        );
        RemoteViews contentView = new RemoteViews(
                getApplicationContext().getPackageName(),
                R.layout.upload_progress_notification
        );
        contentView.setProgressBar(
                R.id.upload_progress_notification_progress_bar,
                10, 0, false
        );
        contentView.setTextViewText(R.id.upload_progress_notification_text_view,
                                    "Upload in progress");

        mNotification.contentView = contentView;
        mNotification.flags = Notification.FLAG_ONGOING_EVENT;

        mNotifyManager.notify(R.string.app_name, mNotification);
    }

    private void removeNotification() {
        if (mNotifyManager == null) {
            mNotifyManager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE
            );
        }
        mNotifyManager.cancelAll();
    }

    public class UploadAsyncTask  extends AsyncTask<String, Integer, String> {
        private String mErrorMessage = "";

        public UploadAsyncTask() {
        }

        private String postGyazo(String cgi, byte[] imgData) throws IOException {
            List<NameValuePair> nValuePairs = new ArrayList<NameValuePair>();
            nValuePairs.add(new BasicNameValuePair("id", mGyazoID));
            String ret;
            HttpMultipartPostRequest request = new HttpMultipartPostRequest(cgi, nValuePairs, imgData);
            Log.d(LOG_TAG, "send POST request");
            ret = request.send();
            return ret;
        }

        @Override
        protected void onPreExecute() {
            createNotification();
        }

        @Override
        protected String doInBackground(String... params) {
            String result = "";
            String cgi = params[0];
            Uri uri = Uri.parse(params[1]);
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
                    // FIXME: more better solution for OutOfMemory.
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
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteBuffer);
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

        @Override
        protected void onPostExecute(String result) {
            removeNotification();

            Intent intent = new Intent();
            Log.i(LOG_TAG, "Result: " + result);
            if (mErrorMessage != "") {
                Toast.makeText(getApplicationContext(), mErrorMessage, Toast.LENGTH_LONG).show();
            }
            if (result == null || !result.startsWith("http")) {
                Toast.makeText(getApplicationContext(), getString(R.string.failed_to_upload), Toast.LENGTH_LONG).show();
                return;
            }
            if (mCopyURL) {
                ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cm.setText(result);
                Toast.makeText(getApplicationContext(), getString(R.string.toast_copy_url), Toast.LENGTH_LONG).show();
            }
            if (mOpenURL) {
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse(result));
                startActivity(intent);
            } else if (mShareURL) {
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.putExtra(Intent.EXTRA_TEXT, result);
                startActivity(intent);
            }

            UploadService.this.stopSelf();
        }
    } // class

    // copy pasted from Stackoverflow.com
    public byte[] readBytes(InputStream inputStream) throws IOException {
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
