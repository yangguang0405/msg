package com.example.moonshotsignal;



import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import androidx.core.util.Pair;



public class ScreenCaptureService extends Service {

    private static final String PACKAGE_NAME = "io.dextools.app";
    //private static final String PACKAGE_NAME = "money.moonshot.app";
    private static final String TAG = "ScreenCaptureService";
    private static final String RESULT_CODE = "RESULT_CODE";
    private static final String DATA = "DATA";
    private static final String ACTION = "ACTION";
    private static final String START = "START";
    private static final String STOP = "STOP";
    private static final String SCREENCAP_NAME = "screencap";

    private static int IMAGES_PRODUCED;
    private Timer mTimer;

    private MediaProjection mMediaProjection;
    private String mStoreDir;
    private ImageReader mImageReader;
    private Handler mHandler;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private OrientationChangeCallback mOrientationChangeCallback;


    ExecutorService mExecutorService = Executors.newFixedThreadPool(4);

    private boolean mCapture = false;
    private long mNextAlert = 0;

    public static Intent getStartIntent(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.putExtra(ACTION, START);
        intent.putExtra(RESULT_CODE, resultCode);
        intent.putExtra(DATA, data);
        return intent;
    }

    public static Intent getStopIntent(Context context) {
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.putExtra(ACTION, STOP);
        return intent;
    }

    private static boolean isStartCommand(Intent intent) {
        return intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA)
                && intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), START);
    }

    private static boolean isStopCommand(Intent intent) {
        return intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), STOP);
    }

    private static int getVirtualDisplayFlags() {
        return DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    }

    private void restartApp() {
        // delete the signal file
        try {
            File file = new File(mStoreDir, "signal.txt");
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete signal file");
        }

        Intent pkg = getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME);
        if (pkg == null) {
            Log.e(TAG, "Can't get launch intent for page " + PACKAGE_NAME);
            return;
        }
        ComponentName componentName = pkg.getComponent();
        Intent intent = Intent.makeRestartActivityTask(componentName);
        startActivity(intent);

        // update flag after 10 seconds
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                mExecutorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        mCapture = true;
                        //TelegramHelper.sendMessage("Android监控App测试消息");
                    }
                });
            }
        });
    }

    private int mSkipLogCount = 0;
    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // capture only 1 image a time
            long now = new Date().getTime();
            if (!mCapture || mNextAlert > now) {
                try (Image image = mImageReader.acquireLatestImage()) {
                    //discard image
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                if (mSkipLogCount > 500) {
                    mSkipLogCount = 0;
                    if (mNextAlert > now) {
                        Log.e(TAG, "Skipped image due to alert interval. " + (mNextAlert - now));
                    } else {
                        Log.e(TAG, "Skipped image");
                    }
                } else {
                    mSkipLogCount++;
                }
                return;
            }
            mCapture = false;
            Log.e(TAG, "Capturing image...");

            mExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    FileOutputStream fos = null;
                    Bitmap bitmap = null;
                    SimpleDateFormat simpleDateFormat =
                            new SimpleDateFormat("yyyyMMdd'T'HHmmss");
                    File imageFile = new File(mStoreDir + "/" + simpleDateFormat.format(new Date()) + ".png");
                    try (Image image = mImageReader.acquireLatestImage()) {
                        if (image != null) {
                            Image.Plane[] planes = image.getPlanes();
                            ByteBuffer buffer = planes[0].getBuffer();
                            int pixelStride = planes[0].getPixelStride();
                            int rowStride = planes[0].getRowStride();
                            int rowPadding = rowStride - pixelStride * mWidth;

                            // create bitmap
                            bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                            bitmap.copyPixelsFromBuffer(buffer);

                            // write bitmap to a file
                            fos = new FileOutputStream(imageFile.getAbsolutePath());
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

                            IMAGES_PRODUCED++;
                            Log.e(TAG, "Captured image: " + IMAGES_PRODUCED);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (fos != null) {
                            try {
                                fos.close();
                            } catch (IOException ioe) {
                                ioe.printStackTrace();
                            }
                        }

                        if (bitmap != null) {
                            bitmap.recycle();
                        }
                    }

                    //do ocr
                    boolean match = false;
                    String matchingText = "";
                    List<String> texts = HttpHelper.ocr(imageFile.getAbsolutePath());
                    if (texts != null && !texts.isEmpty()) {
                        for (String text : texts) {
                            if (text != null && !text.isEmpty()) {
                                if (text.toLowerCase().contains("keywords:".trim().toLowerCase())
                                        || text.toLowerCase().contains("start monitor".trim().toLowerCase())
                                        || text.toLowerCase().contains("stop monitor".trim().toLowerCase()) )
                                {
                                    break;
                                }
                                for (String keyword : Constants.KEY_WORDS) {
                                    if (text.toLowerCase().contains(keyword.trim().toLowerCase())) {
                                        match = true;
                                        matchingText = keyword;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (match) {
                        Log.e(TAG, "Found keyword '" + matchingText + "' on screen of Moonshot app. Ocr results:");
                        for (String text : texts) {
                            Log.e(TAG, text);
                        }
                        Log.e(TAG, "Sending Telegram notification");
                        TelegramHelper.sendMessage("Moonshot界面出现【" + matchingText + "】信息，请关注！");
                        Log.e(TAG, "Sending Telephone notification");
                        TelHelper.notify("Moonshot");
                        //Send alert a minute later if any
                        mNextAlert = new Date().getTime() + 10 * 60 * 1000;
                        mCapture = false;
                    } else {
                        Log.e(TAG, "No matching keyword detected.");

                        // write signal file for kill target app
                        try {
                            imageFile.delete();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to delete screenshot image file");
                        }
                    }

                    // write signal file for kill target app
                    try {
                        File file = new File(mStoreDir, "signal.txt");
                        file.createNewFile();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to create signal file");
                    }
                }
            });
        }
    }

    private class OrientationChangeCallback extends OrientationEventListener {

        OrientationChangeCallback(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            final int rotation = mDisplay.getRotation();
            if (rotation != mRotation) {
                mRotation = rotation;
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.e(TAG, "stopping projection.");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);
                    if (mOrientationChangeCallback != null) mOrientationChangeCallback.disable();
                    mMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                }
            });
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // create store dir
        File externalFilesDir = getExternalFilesDir(null);
        if (externalFilesDir != null) {
            mStoreDir = externalFilesDir.getAbsolutePath() + "/screenshots/";
            File storeDirectory = new File(mStoreDir);
            if (!storeDirectory.exists()) {
                boolean success = storeDirectory.mkdirs();
                if (!success) {
                    Log.e(TAG, "failed to create file storage directory.");
                    stopSelf();
                }
            }
        } else {
            Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.");
            stopSelf();
        }

        // start capture handling thread
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isStartCommand(intent)) {
            // create notification
            Pair<Integer, Notification> notification = NotificationUtils.getNotification(this);
            startForeground(notification.first, notification.second);
            // start projection
            int resultCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(DATA);
            startProjection(resultCode, data);
        } else if (isStopCommand(intent)) {
            stopProjection();
            stopSelf();
        } else {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        MediaProjectionManager mpManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data);
            if (mMediaProjection != null) {
                // display metrics
                mDensity = Resources.getSystem().getDisplayMetrics().densityDpi;
                WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                mDisplay = windowManager.getDefaultDisplay();

                // create virtual display depending on device width / height
                createVirtualDisplay();

                // register orientation change callback
                mOrientationChangeCallback = new OrientationChangeCallback(this);
                if (mOrientationChangeCallback.canDetectOrientation()) {
                    mOrientationChangeCallback.enable();
                }

                // register media projection stop callback
                mMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);

                mTimer = new Timer();
                mTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        restartApp();
                    }

                }, 5000, 20000);
            }
        }
    }

    private void stopProjection() {
        if (mHandler != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mMediaProjection != null) {
                        mMediaProjection.stop();
                    }
                }
            });
        }
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    @SuppressLint("WrongConstant")
    private void createVirtualDisplay() {
        // get width and height
        mWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        mHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        mMediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onCapturedContentResize(int width, int height) {
                super.onCapturedContentResize(width, height);
            }

            @Override
            public void onCapturedContentVisibilityChanged(boolean isVisible) {
                super.onCapturedContentVisibilityChanged(isVisible);
            }

            @Override
            public void onStop() {
                super.onStop();
            }
        }, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight,
                mDensity, getVirtualDisplayFlags(), mImageReader.getSurface(), null, mHandler);
    }
}