/*
 * Copyright (C) 2012 xtuaok (http://twitter.com/xtuaok)
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.text.ClipboardManager;
import android.util.Log;
import android.widget.Toast;

public class ShareGyazo extends Activity {
    private static final String DEFAULT_UPLOADER = "http://gyazo.com/upload.cgi";
    private static final String LOG_TAG = "ShareToGyazo";
    private Context mContext;

    private String mGyazoCGI;
    private String mGyazoID;
    private boolean mCopyURL;
    private boolean mOpenURL;
    private boolean mShareURL;

    private static final int MAX_IMAGE_PIXEL = 1920; // HD

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_gyazo);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());;
        mContext = getApplicationContext();
        Uri uri;
        uri = getIntent().getData();
        if (uri == null)
            uri = Uri.parse(getIntent().getExtras().get("android.intent.extra.STREAM").toString());
        if (uri == null) {
            finish();
            return;
        }
        mGyazoCGI = prefs.getString(GyazoPreference.PREF_GYAZO_CGI, DEFAULT_UPLOADER);
        mGyazoID  = prefs.getString(GyazoPreference.PREF_GYAZO_ID, "");
        mCopyURL  = prefs.getBoolean(GyazoPreference.PREF_COPY_URL, true);
        mOpenURL  = prefs.getBoolean(GyazoPreference.PREF_OPEN_BROWSER, false);
        mShareURL = prefs.getBoolean(GyazoPreference.PREF_SHARE_TEXT, false);

        new UploadAsyncTask().execute(mGyazoCGI, uri.toString());
    }

    public class UploadAsyncTask  extends AsyncTask<String, Integer, String> {
        private ProgressDialog mDialog;
        private String mErrorMessage = "";

        private String postGyazo(String cgi, byte[] imgData) throws ClientProtocolException, IOException {
            List<NameValuePair> nValuePairs = new ArrayList<NameValuePair>();
            nValuePairs.add(new BasicNameValuePair("id", mGyazoID));  
            String ret;
            HttpMultipartPostRequest request = new HttpMultipartPostRequest(cgi, nValuePairs, imgData);
            Log.d(LOG_TAG, "send POST request");
            ret = request.send();
            return ret;
        }

        public UploadAsyncTask(){
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
                    bitmap.recycle();
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

        @Override
        protected void onPostExecute(String result) {
            Intent intent = new Intent();
            if(mDialog != null){
                mDialog.dismiss();
            }
            Log.i(LOG_TAG, "Result: " + result);
            if (mErrorMessage != "") {
                Toast.makeText(mContext, mErrorMessage, Toast.LENGTH_LONG).show();
            }
            if (result == null || !result.startsWith("http")) {
                Toast.makeText(mContext, getString(R.string.failed_to_upload), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (mCopyURL) {
                ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cm.setText(result);
                Toast.makeText(mContext, getString(R.string.toast_copy_url), Toast.LENGTH_LONG).show();
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
            finish();
        }

        @Override
        protected void onPreExecute() {
            mDialog = new ProgressDialog(ShareGyazo.this);
            mDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mDialog.setTitle(getString(R.string.dialog_title_wait));
            mDialog.setMessage(getString(R.string.dialog_message_uploading));
            mDialog.show();
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
