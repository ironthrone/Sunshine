package com.example.android.sunshine.app.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract.WeatherEntry;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2016/8/16.
 */
public class DataService extends WearableListenerService {
    private static final String UPDATE_COMMAND_PATH = "/update";
    private static final String SEND_WEATHER_PATH = "/send";
    private static final String UPDATE_KEY = "update";
    private static final String TAG = DataService.class.getSimpleName();
    private static final String MAX_TEMP_KEY = "max";
    private static final String MIN_TEMP_KEY = "min";

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);
        Log.d(TAG, "onDataChanged");
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "google api client connect fail");
            return;
        }


//        dataEventBuffer.
        for (DataEvent dataEvent : dataEventBuffer) {
            if(dataEvent.getType() == DataEvent.TYPE_CHANGED){

            DataItem dataItem = dataEvent.getDataItem();
            if(dataItem.getUri().getPath().compareTo("/update") == 0){
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dataMap = dataMapItem.getDataMap();
                boolean update = dataMap.getBoolean(UPDATE_KEY);
                if(update){
                    ContentResolver resolver = getContentResolver();
                    String[] projection = {
                              WeatherEntry.COLUMN_MAX_TEMP,
                            WeatherEntry.COLUMN_MIN_TEMP

                    };
                    String locationSetting = Utility.getPreferredLocation(this);
                    Cursor cursor = resolver.query(WeatherEntry.buildWeatherLocationWithDate(locationSetting, System.currentTimeMillis()),
                            projection,null,null,null);
                    if(cursor != null && cursor.moveToFirst()){
                    int max = cursor.getInt(cursor.getColumnIndex(WeatherEntry.COLUMN_MAX_TEMP));
                    int min = cursor.getInt(cursor.getColumnIndex(WeatherEntry.COLUMN_MIN_TEMP));

                        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(SEND_WEATHER_PATH);
                        putDataMapRequest.getDataMap().putInt(MAX_TEMP_KEY,max);
                        putDataMapRequest.getDataMap().putInt(MIN_TEMP_KEY,min);
                        PutDataRequest putDataReq = putDataMapRequest.asPutDataRequest();
                        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
                    }else {
                        Log.d(TAG, "cursor is null or empty");
                    }
                }
            }
            }
        }
    }



}
