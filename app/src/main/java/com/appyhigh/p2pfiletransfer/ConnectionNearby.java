package com.appyhigh.p2pfiletransfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import me.rajatdhamija.rsod.RedScreenOfDeath;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.Strategy;

import java.util.HashMap;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class ConnectionNearby extends AppCompatActivity {

    String TAG = "ConnectionNearby";
    RecyclerView mRecyclerView;
    ConnectionDiscoverAdapter mAdapter;
    ConnectionLifecycleCallback connectionLifecycleCallback;
    EndpointDiscoveryCallback endpointDiscoveryCallback;
    HashMap<String, String> deviceList = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_nearby);
        mRecyclerView = findViewById(R.id.recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        RedScreenOfDeath.initRSOD(getApplication(), BuildConfig.BUILD_TYPE);
        View receive = findViewById(R.id.receive);
        View discover = findViewById(R.id.discover);
        receive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startAdvertising();
            }
        });
        discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDiscovery();
            }
        });
        ConnectionFileTransferActivity.ReceiveFilePayloadCallback payloadCallback = new ConnectionFileTransferActivity.ReceiveFilePayloadCallback(this);
//        ConnectionFileTransferActivity.StreamFilePayloadCallback payloadCallback = new ConnectionFileTransferActivity.StreamFilePayloadCallback(this);
        connectionLifecycleCallback = new ConnectionLifecycleCallback() {
            @Override
            public void onConnectionInitiated(@NonNull String endpointId,@NonNull ConnectionInfo info) {
                deviceList.put(endpointId, info.getEndpointName());
//                new AlertDialog.Builder(ConnectionNearby.this)
//                        .setTitle("Accept connection to " + info.getEndpointName())
//                        .setMessage("Confirm the code matches on both devices: " + info.getAuthenticationToken())
//                        .setPositiveButton(
//                                "Accept",
//                                (DialogInterface dialog, int which) ->
//                                        // The user confirmed, so we can accept the connection.
//                                        Nearby.getConnectionsClient(ConnectionNearby.this)
//                                                .acceptConnection(endpointId, payloadCallback))
//                        .setNegativeButton(
//                                android.R.string.cancel,
//                                (DialogInterface dialog, int which) ->
//                                        // The user canceled, so we should reject the connection.
//                                        Nearby.getConnectionsClient(ConnectionNearby.this).rejectConnection(endpointId))
//                        .setIcon(android.R.drawable.ic_dialog_alert)
//                        .show();
                Nearby.getConnectionsClient(ConnectionNearby.this)
                        .acceptConnection(endpointId, payloadCallback);
            }

            @Override
            public void onConnectionResult(@NonNull String endpointId, ConnectionResolution result) {
                switch (result.getStatus().getStatusCode()) {
                    case ConnectionsStatusCodes.STATUS_OK:
                        // We're connected! Can now start sending and receiving data.
                        Intent intent = new Intent(ConnectionNearby.this, ConnectionFileTransferActivity.class);
                        intent.putExtra("deviceName",deviceList.get(endpointId));
                        intent.putExtra("endpointId", endpointId);
                        startActivity(intent);
                        break;
                    case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                        // The connection was rejected by one or both sides.
                        break;
                    case ConnectionsStatusCodes.STATUS_ERROR:
                        // The connection broke before it was able to be accepted.
                        break;
                    default:
                        // Unknown status code
                }
            }

            @Override
            public void onDisconnected(@NonNull String s) {

            }
        };
        mAdapter = new ConnectionDiscoverAdapter(connectionLifecycleCallback);
        mRecyclerView.setAdapter(mAdapter);

        endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
            @Override
            public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                // An endpoint was found. We request a connection to it.
                Log.d(TAG, "onEndpointFound: "+endpointId);
                Log.d(TAG, "onEndpointFound: "+info.getEndpointName()+"  "+info.getServiceId());
                mAdapter.addItem(endpointId, info.getEndpointName());
            }

            @Override
            public void onEndpointLost(@NonNull String endpointId) {
                mAdapter.removeItem(endpointId);
            }
        };
        Log.d(TAG, "onCreate: "+checkPermission());

        if(!isLocationEnabled()){
            startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 103);
        } else if (!checkPermission()) {
            requestPermission();
        }

    }



    private void startAdvertising() {
        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        Nearby.getConnectionsClient(this)
                .startAdvertising(
                        Build.MODEL, getPackageName(), connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're advertising!
                            Log.d(TAG, "startReceiving: ");
                            Toast.makeText(this,"Started Receiving", Toast.LENGTH_SHORT).show();
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We were unable to start advertising.
                            Log.d(TAG, "Unable to StartReceiving: ");
                            Toast.makeText(this,"Unable to start Receiving", Toast.LENGTH_SHORT).show();
                        });
    }


    private void startDiscovery() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        Nearby.getConnectionsClient(this)
                .startDiscovery(getPackageName(), endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're discovering!
                            Log.d(TAG, "startDiscovery: ");
                            Toast.makeText(this,"Discovering....", Toast.LENGTH_SHORT).show();

                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We're unable to start discovering.
                            Log.d(TAG, "Unable to start discovering.");
                            Toast.makeText(this,"Unable to start discovering", Toast.LENGTH_SHORT).show();
                        });
    }

    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        int result3 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        int result4 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION);
        return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED && result3 == PackageManager.PERMISSION_GRANTED && result4 == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 200);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: " + requestCode+ "  "+checkPermission()+ "  "+grantResults.length);
        if (requestCode == 200) {
            if (grantResults.length > 0) {

                if (checkPermission()) {
                    Toast.makeText(this, "Permission Granted, Now you can access this app.", Toast.LENGTH_LONG).show();
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                            showMessageOKCancel("Please allow access to Device location and Storage to use this app.",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            ActivityCompat.requestPermissions(ConnectionNearby.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 200);
                                        }
                                    });

                    }

                }
            }
        } else if(requestCode == 103){
            if(!isLocationEnabled()) {
                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 103);
            } else {
                if(!checkPermission()){
                    requestPermission();
                }
            }
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        return LocationManagerCompat.isLocationEnabled(locationManager);
    }
}