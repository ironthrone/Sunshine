package com.example.android.sunshine.app.data;

import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2016/8/16.
 */
public class WeatherDataService extends WearableListenerService {
    private static final String UPDATE_COMMAND_PATH = "/update";
    public static final String SEND_WEATHER_PATH = "/send";
    private static final String UPDATE_KEY = "update";
    private static final String TAG = WeatherDataService.class.getSimpleName();
    public static final String MAX_TEMP_KEY = "max";
    public static final String MIN_TEMP_KEY = "min";
    public static final String ICON_KEY = "icon";

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
            if(dataItem.getUri().getPath().compareTo(UPDATE_COMMAND_PATH) == 0){
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dataMap = dataMapItem.getDataMap();
                boolean update = dataMap.getBoolean(UPDATE_KEY);
                if(update){
                    Utility.sendWeatehrData(this,googleApiClient);
                }
            }
            }
        }
    }



}
