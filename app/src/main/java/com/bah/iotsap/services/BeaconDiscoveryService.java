package com.bah.iotsap.services;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.IBinder;

import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.bah.iotsap.App;
import com.bah.iotsap.db.SQLDB;
import com.bah.iotsap.util.DBUtil;
import com.bah.iotsap.util.FileRW;
import com.bah.iotsap.util.LocationDiscovery;
import com.estimote.coresdk.observation.region.beacon.BeaconRegion;
import com.estimote.coresdk.recognition.utils.MacAddress;
import com.estimote.coresdk.service.BeaconManager;
import com.estimote.mgmtsdk.connection.api.DeviceConnectionProvider;
import com.estimote.coresdk.recognition.packets.Beacon;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class BeaconDiscoveryService extends Service {

    private static final String TAG = "BeaconDiscoveryService";
    // Intent action strings
    public static final String RECEIVE_JSON = "com.bah.iotsap.services.BeaconDiscoveryService.RECEIVE_JSON";
    public static final String START = "com.bah.iotsap.services.BeaconDiscoveryService.START";
    public static final String STOP  = "com.bah.iotsap.services.BeaconDiscoveryService.STOP";
    // SharedPreferences strings
    public static final String PREF_BEACON_SERVICE = "pref_beacon_service";

    private ArrayList<Beacon> beaconArrayList;
    private BeaconManager beaconManager;
    private DeviceConnectionProvider connectionProvider;
    private BeaconRegion beacons;
    private LocationDiscovery mLocationDiscovery;
    private Context mContext;

    private int id = App.ID;

    private ArrayList rows;

    public BeaconDiscoveryService() {
    }

    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate(): Entered");

        //Initialize Location Discovery Object
        mLocationDiscovery = new LocationDiscovery();
        mLocationDiscovery.configureLocationClass(this);
        mLocationDiscovery.startLocationUpdates();

        mContext = this;

        rows = new ArrayList();

        FileRW.init(mContext,"beacon");

        beacons = new BeaconRegion(
                "monitored region",
                UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D"),
                null , null
        );
        beaconArrayList = new ArrayList<>();
        beaconManager = new BeaconManager(this);
        beaconManager.setRangingListener(new BeaconManager.BeaconRangingListener() {
            @Override
            public void onBeaconsDiscovered(BeaconRegion region, final List<Beacon> rangedBeacons) {
                if(rangedBeacons.size() > 0) {
                    for (Beacon beacon : rangedBeacons) {
                        beaconArrayList.add(beacon);
                        final String deviceName = ("iBeacon: " + beacon.getUniqueKey());
                        final MacAddress deviceMac = beacon.getMacAddress();
                        String date = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                        final Integer rssi = beacon.getRssi();
                        final Location location = mLocationDiscovery.getLocation();
                        String time = date.substring(8);
                        date = date.substring(0,8);

                        try {
                            JSONObject item = new JSONObject();
                            item.put("date", date);
                            item.put("time", time);
                            item.put("mac", deviceMac);
                            item.put("name", deviceName);
                            if(location != null) {
                                item.put("latitude", location.getLatitude());
                                item.put("longitude", location.getLongitude());
                                item.put("altitude", location.getAltitude());
                            }
                            item.put("id",id);
                            item.put("rssi",rssi);
                            item.put("type", "BEACON");
                            Log.d("Service:", item.toString());

                            FileRW.append(mContext, "beacon", item.toString());
                            sendMessageToActivity(item.toString());
                        } catch (JSONException e) {

                        }

                        ContentValues contentValues =
                                DBUtil.insert(date, time, deviceMac.toString(),
                                              deviceName, location, id, rssi, "beacon");
                        long newRowId = App.db.insert(SQLDB.DataTypes.TABLE_NAME, null, contentValues);

                        rows.add(newRowId);
                    }
                }
            }
        });
        connectionProvider = new DeviceConnectionProvider(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");
        if(START.equals(intent.getAction())) {
            Log.i(TAG, "onStartCommand(): Entered START action");

            beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
                @Override
                public void onServiceReady() {
                    beaconManager.setForegroundScanPeriod(5000, 5000);
                    beaconManager.startMonitoring(beacons);
                    beaconManager.startRanging(beacons);
                }
            });
        }
        else if(STOP.equals(intent.getAction())) {
            Log.i(TAG, "onStartCommand(): Entered STOP action");
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        connectionProvider.destroy();
        beaconArrayList.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendMessageToActivity(String device) {
        Intent intent = new Intent(RECEIVE_JSON).putExtra("json", device);
        Log.d("Beacon: ",device);
        LocalBroadcastManager.getInstance(BeaconDiscoveryService.this).sendBroadcast(intent);
    }
}