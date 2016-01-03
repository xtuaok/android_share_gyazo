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


import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ShareGyazo extends Activity {
    private static final String LOG_TAG = "ShareGyazo";
    private static final int BUFSIZ = 4096;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File temp;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_gyazo);

        Uri uri;
        uri = getIntent().getData();
        if (uri == null)
            uri = Uri.parse(getIntent().getExtras().get("android.intent.extra.STREAM").toString());
        if (uri == null) {
            finish();
            return;
        }
        Log.d(LOG_TAG, "Writing temporary file");
        // copy to cache (Security reason)
        try {
            byte[] buff = new byte[BUFSIZ];
            InputStream is = getApplicationContext().getContentResolver().openInputStream(uri);
            temp = File.createTempFile("temp", ".bin", getCacheDir());
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temp));
            while (is.available() > 0) {
                int r = is.read(buff, 0, BUFSIZ);
                bos.write(buff, 0, r);
            }
            bos.flush();
            bos.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOError", e);
            finish();
            return;
        }

        Intent intent = new Intent(ShareGyazo.this, UploadService.class);
        // intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setData(Uri.fromFile(temp));
        startService(intent);
        finish();
    }
}
