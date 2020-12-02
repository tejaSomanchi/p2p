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
            if (manager != null) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                manager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
                    @Override
                    public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
                        activity.notifyWiFiDirectPeerListDiscoveryFinished(wifiP2pDeviceList);
                    };
                });
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Respond to new connection or disconnections
            WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            WifiP2pGroup wifiP2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
            Log.d(TAG, "WiFi Direct Connection Status changed:\n" +
                    "WiFiDirectInfo: "+wifiP2pInfo+"\n" +
                    "NetworkInfo: "+networkInfo+"\n" +
                    "WiFi Direct Group: "+wifiP2pGroup);
            TextView connectedTo = ((Activity) context).findViewById(R.id.connectedTo);
            SharedPreferences preferences = context.getSharedPreferences("MyPrefs", 0);
            SharedPreferences.Editor editor = preferences.edit();
            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner){
                ArrayList<WifiP2pDevice> wifiP2pDevices = new ArrayList<>(Collections.unmodifiableCollection(wifiP2pGroup.getClientList()));
                connectedTo.setText(" Receiver "+ wifiP2pDevices.get(0).deviceName);
                editor.putString("host", wifiP2pInfo.groupOwnerAddress.getHostAddress());
                editor.apply();
            }else if(wifiP2pInfo.groupFormed){
                connectedTo.setText(" Sender "+wifiP2pGroup.getOwner().deviceName);
                new ReceiverClass(context);
                editor.putString("host", "");
                editor.apply();
            }
            else {
                connectedTo.setText("Not connected to any device");
                editor.putString("host", "");
                editor.apply();
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // Respond to this device's wifi state changing
        }
    }

    public class ReceiverClass extends Thread{
        Socket socket;
        ServerSocket serverSocket;
        Context context;
        ReceiverClass(Context context){
            this.context = context;
        }

        @Override
        public void run() {
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(8888);
                Socket client = serverSocket.accept();

                /**
                 * If this code is reached, a client has connected and transferred data
                 * Save the input stream from the client as a JPEG file
                 */
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + f.getAbsolutePath()), "image/*");
                context.startActivity(intent);

            } catch (Exception e) {
                Log.d(TAG, "Exception "+e);
                e.printStackTrace();
            }

        }
    }

    public void copyFile(InputStream is, FileOutputStream os){
        byte[] buffer = new byte[1024];
        int length;
        try{
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            is.close();
            os.close();
        } catch (Exception e){
            Log.d(TAG, "Exception "+e);
            e.printStackTrace();
        }
    }
}
