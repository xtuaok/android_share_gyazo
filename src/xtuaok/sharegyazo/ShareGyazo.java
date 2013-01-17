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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

public class ShareGyazo extends Activity {
    private Uri mUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_gyazo);
        mUri = getIntent().getData();
        if (mUri == null) {
            mUri = Uri.parse(getIntent().getExtras().get("android.intent.extra.STREAM").toString());
        }
        if (mUri == null) {
            finish();
            return;
        }

        Intent intent = new Intent(this, UploadService.class);
        bindService(intent, mUploadServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        unbindService(mUploadServiceConnection);
        super.onDestroy();
    }

    private ServiceConnection mUploadServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            UploadService.LocalBinder binder = (UploadService.LocalBinder) service;
            UploadService uploadService = binder.getService();

            if (mUri != null) {
                uploadService.upload(mUri);
            }

            ShareGyazo.this.finish();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };
}
