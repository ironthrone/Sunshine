/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.watch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final String TAG = SunshineWatchFace.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,DataApi.DataListener {
        private static final float TIME_TEXT_SIZE_IN_SP = 21;
        private static final float DATE_TEXT_SIZE_IN_SP = 18;
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
//        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mDatePaint;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                invalidate();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        private Bitmap mBackBitmap;
        private Calendar mCalendar;
        private SimpleDateFormat mDateFormat;
        private GoogleApiClient mGoogleApiClient;
        private int maxTemp;
        private int minTemp;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

//            mBackgroundPaint = new Paint();
//            mBackgroundPaint.setColor(resources.getColor(R.color.white));


            mBackBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.watch_bg);
            mTimePaint = createTimePaint(Color.WHITE);

            mDatePaint = createDatePaint(resources.getColor(R.color.gray_white));

            mCalendar = new GregorianCalendar();
            mDateFormat = new SimpleDateFormat("EEE,MMM dd,yyyy");

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mGoogleApiClient.disconnect();
            super.onDestroy();
        }

        private Paint createTimePaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTextSize(DpUtil.sp2px(SunshineWatchFace.this,TIME_TEXT_SIZE_IN_SP));
            paint.setAntiAlias(true);
            return paint;
        }
        private Paint createDatePaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTextSize(DpUtil.sp2px(SunshineWatchFace.this,DATE_TEXT_SIZE_IN_SP));
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (inAmbientMode) {
                if (mLowBitAmbient) {
                    mDatePaint.setAntiAlias(!inAmbientMode);
                }
            }
                invalidate();

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

//        /**
//         * Captures tap event (and tap type) and toggles the background color if the user finishes
//         * a tap.
//         */
//        @Override
//        public void onTapCommand(int tapType, int x, int y, long eventTime) {
//            Resources resources = SunshineWatchFace.this.getResources();
//            switch (tapType) {
//                case TAP_TYPE_TOUCH:
//                    // The user has started touching the screen.
//                    break;
//                case TAP_TYPE_TOUCH_CANCEL:
//                    // The user has started a different gesture or otherwise cancelled the tap.
//                    break;
//                case TAP_TYPE_TAP:
//                    // The user has completed the tap gesture.
//                    mTapCount++;
//                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
//                            R.color.white : R.color.purple));
//                    break;
//            }
//            invalidate();
//        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            if (mBackBitmap == null
                    || mBackBitmap.getWidth() != width
                    || mBackBitmap.getHeight() != height) {
                mBackBitmap = Bitmap.createScaledBitmap(mBackBitmap, width, height, true);
            }

            super.onSurfaceChanged(holder, format, width, height);

        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            mCalendar.setTime(new Date());
            int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
            int minute = mCalendar.get(Calendar.MINUTE);
            int second = mCalendar.get(Calendar.SECOND);
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                canvas.drawText(String.format(Locale.getDefault(),"%d:%02d",hour,minute),
                        mXOffset,mYOffset, mTimePaint);
            } else {
//                canvas.drawBitmap(mBackBitmap,0,0,null );
                canvas.drawColor(ContextCompat.getColor(SunshineWatchFace.this,R.color.primary));


//                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
//                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

                float x = mXOffset;
                float y = mYOffset;
                canvas.drawText(String.format(Locale.getDefault(), "%d:%02d:%02d",hour ,minute
                        , second
                        ),x,y,mTimePaint);
                y += mTimePaint.descent() - mDatePaint.ascent();
                canvas.drawText(mDateFormat.format(mCalendar.getTime()),x,y,mDatePaint);

                if (maxTemp != 0 && minTemp != 0) {
                    y += mDatePaint.descent() - mDatePaint.ascent();
                    canvas.drawText(String.format(Locale.getDefault(),"%d %d",maxTemp,minute),
                            x,y,mDatePaint);

                }
            }
            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "googleapiclient connect");
            requestWeatherData();
        }

        private void requestWeatherData(){
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/update");
            putDataMapRequest.getDataMap().putBoolean("update",true);
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            putDataRequest.setUrgent();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);

        }
        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "googleapiclient connect suspended");

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "googleapiclient failed");

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED ) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().compareTo("/send") == 0) {

                    DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        maxTemp = dataMap.getInt("max");
                        minTemp = dataMap.getInt("min");
                        invalidate();
                    }
                }
            }
        }
    }
}
