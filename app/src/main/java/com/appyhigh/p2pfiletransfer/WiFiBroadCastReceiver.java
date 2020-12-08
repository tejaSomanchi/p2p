package com.appyhigh.p2pfiletransfer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;

import androidx.core.app.ActivityCompat;

public class WiFiBroadCastReceiver extends BroadcastReceiver {

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private MainActivity activity;
    private final String TAG = "WiFiBroadCastReceiver";

    public WiFiBroadCastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, MainActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: ");

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi is enabled and notify appropriate activity

        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // Call WifiP2pManager.requestPeers() to get a list of current peers
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Respond to new connection or disconnections
            WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            WifiP2pGroup wifiP2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
            Log.d(TAG, "WiFi Direct Connection Status changed:\n" +
                    "WiFiDirectInfo: "+wifiP2pInfo+"\n" +
                    "NetworkInfo: "+networkInfo+"\n" +
                    "WiFi Direct Group: "+wifiP2pGroup);
            if(networkInfo.isConnected()){
                Log.d(TAG, "Network is connected");
                TextView connectedTo = ((Activity) context).findViewById(R.id.connectedTo);
                if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner){
                    ArrayList<WifiP2pDevice> wifiP2pDevices = new ArrayList<>(Collections.unmodifiableCollection(wifiP2pGroup.getClientList()));
                    if(wifiP2pGroup.getClientList()!=null && wifiP2pGroup.getClientList().size()>0){
                        connectedTo.setText("Connected to "+ wifiP2pDevices.get(0).deviceName);
                        activity.decideDevice(wifiP2pInfo, networkInfo);
                    }
                }else if(wifiP2pInfo.groupFormed){
                    connectedTo.setText("Connected to "+wifiP2pGroup.getOwner().deviceName);
                    activity.decideDevice(wifiP2pInfo, networkInfo);
                }
                else {
                    connectedTo.setText("Not connected to any device");
                }
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // Respond to this device's wifi state changing
        }
    }
}
