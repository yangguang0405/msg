package com.example.moonshotsignal;

import android.util.Log;
import okhttp3.*;

import java.io.*;
public class TelHelper {
    private static final String[] CALL_NUMBERS = new String[] {"17764002048"};
    private static final String TAG = "TelHelper";
    private TelHelper() {

    }

    public static void notify(String text) {
        for (String number : CALL_NUMBERS) {
            OkHttpClient client = new OkHttpClient();
            FormBody.Builder requestBodyBuilder = new FormBody.Builder();
            requestBodyBuilder.addEncoded("phoneNumber", number);
            requestBodyBuilder.addEncoded("text", text);
            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url("https://sipzmgt.socialswap.com/dexdata/notify")
                    .post(requestBodyBuilder.build())
                    .addHeader("Content-Type", "x-www-form-urlencoded");

            okhttp3.Request request = requestBuilder.build();

            try {
                Response response = client.newCall(request).execute();
                if (response.code() == 200) {
                    Log.i(TAG, "Call " + number + " succeeded. Response: " + response.body().string());
                } else {
                    Log.e(TAG, "Failed to call " + number + ". Response: " + response.body().string());
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to call " + number, e);
            }
        }
    }
}
