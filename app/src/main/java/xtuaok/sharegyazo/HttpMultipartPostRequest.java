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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import android.util.Pair;
import android.util.Log;

public class HttpMultipartPostRequest {
    private static final String LOG_TAG = HttpMultipartPostRequest.class.toString();
    private static final String BOUNDARY = "----BOUNDARYBOUNDARY----";

    private String mCgi;
    private List<Pair<String, String>> mPostData;
    private File mFile;
    private UploadService mService;

    public HttpMultipartPostRequest(String cgi, List<Pair<String, String>> postData, File file, UploadService service) {
        mCgi = cgi;
        mPostData = postData;
        mFile = file;
        mService = service;
    }

    public String send() {
        final int MAX_BUFSIZ = 1024*64; // 64k bytes

        URLConnection conn = null;
        BufferedReader br = null;
        String res = null;
        DataOutputStream os = null;

        try {
            FileInputStream fis = new FileInputStream(mFile);
            int fileSize = fis.available();
            int bufferSize = Math.min(fileSize, MAX_BUFSIZ);
            byte[] buffer = new byte[bufferSize];

            conn = new URL(mCgi).openConnection();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            ((HttpURLConnection)conn).setRequestMethod("POST");
            ((HttpURLConnection)conn).setRequestProperty("Connection", "Keep-Alive");
            ((HttpURLConnection)conn).setChunkedStreamingMode(0);
            conn.setConnectTimeout(10 * 1000);
            conn.setReadTimeout(60 * 1000);
            conn.setDoOutput(true);
            conn.connect();

            os = new DataOutputStream(conn.getOutputStream());
            os.writeBytes(createBoundaryMessage("imagedata"));

           do {
               int bytes_read = fis.read(buffer, 0, bufferSize);
               os.write(buffer, 0, bytes_read);
               int percent = (int)(((float)(fileSize - fis.available()) / ((float)fileSize)) * 100);
               if (percent == 100) { percent = 99; }
               mService.seProgress(percent);
            } while (fis.available() > 0);

            String endBoundary = "\r\n--" + BOUNDARY + "--\r\n";
            os.writeBytes(endBoundary);

            int iResponseCode = ((HttpURLConnection)conn).getResponseCode();
            if (iResponseCode == HttpURLConnection.HTTP_OK) {
                Log.d(LOG_TAG,  String.format("HTTP: %d", iResponseCode));
                StringBuilder sb = new StringBuilder();
                String line = "";
                br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while ((line = br.readLine()) != null) {
                    Log.d(LOG_TAG,  String.format("HTTPBodyLine: %s", line));
                    sb.append(String.format("%s\r\n", line));
                }
                res = sb.toString();
            } else  {
                Log.e(LOG_TAG,  String.format("HTTPError: %d", iResponseCode));
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, e.getMessage() + "");
        } finally {
            try {
                if (br != null) br.close();
                if (os != null) {
                    os.flush();
                    os.close();
                }
                if (conn != null) ((HttpURLConnection) conn).disconnect();
            }catch (IOException e) {
                Log.e(LOG_TAG, "IOError", e);
            }
        }
        return res;
    }

    private String createBoundaryMessage(String fileName) {
        StringBuffer res = new StringBuffer("--").append(BOUNDARY).append("\r\n");
        for (Pair pair : mPostData) {
            res.append("Content-Disposition: form-data; name=\"").append(pair.first).append("\"\r\n")
            .append("\r\n").append(pair.second).append("\r\n")
            .append("--").append(BOUNDARY).append("\r\n");
        }
        res.append("Content-Disposition: form-data; name=\"")
        .append(fileName).append("\"; filename=\"").append(fileName).append("\"\r\n\r\n");
        return res.toString();
    }
}
