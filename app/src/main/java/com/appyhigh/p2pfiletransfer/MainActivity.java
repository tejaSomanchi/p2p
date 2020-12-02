package com.appyhigh.p2pfiletransfer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.PermissionRequest;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;
    IntentFilter intentFilter;
    WiFiDirectPeerDevicesRecyclerAdapter adapter;
    RecyclerView recyclerView;
    WifiP2pManager.ConnectionInfoListener connectionInfoListener;
    WifiP2pInfo wifiP2pInfo;
    private final String TAG = "MainActivity";

    public Context getContext(){
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WiFiBroadCastReceiver(manager, channel, this);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        recyclerView = findViewById(R.id.device_list);
        adapter = new WiFiDirectPeerDevicesRecyclerAdapter(manager, channel);
        recyclerView.setAdapter(adapter);
        Button discover = findViewById(R.id.discover);
        Button send = findViewById(R.id.send);
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION },1);
        discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onFailure(int i) {
                        Log.d(TAG, "onFailure: "+i);
                    }
                });
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onFailure(int i) {
                        Log.d(TAG, "onFailure: "+i);
                    }
                });
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences preferences = getContext().getSharedPreferences("MyPrefs", 0);
                if(preferences.contains("host") && !preferences.getString("host","").equals("")){
                    new SenderClass(preferences.getString("host",""));
                } else {
                    Toast.makeText(getContext(), "Receiver is not selected", Toast.LENGTH_SHORT).show();
                }
            }
        });

        connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
                InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
                if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner){
                    Log.d(TAG, "onConnectionInfoAvailable: Host "+groupOwnerAddress);
                }
                else {
                    Log.d(TAG, "onConnectionInfoAvailable: Client "+groupOwnerAddress);
                }
            }
        };

    }




    public class ReceiverClass extends Thread{
        Socket socket;
        ServerSocket serverSocket;

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
                        + getContext().getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
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
                getContext().startActivity(intent);

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


    public class SenderClass extends Thread{
        Socket socket;
        String  hostAddress;
        int len;
        byte buf[]  = new byte[1024];

        public SenderClass(String hostAddress){
            this.hostAddress = hostAddress;
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAddress, 8888), 5000);
                OutputStream outputStream = socket.getOutputStream();
                ContentResolver cr = getContext().getContentResolver();
                InputStream inputStream = null;
                inputStream = cr.openInputStream(Uri.parse("/home/somanchi/Downloads/ic_launcher.png"));
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                outputStream.close();
                inputStream.close();
            } catch (Exception e) {
                Log.d(TAG, "Exception "+e);
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (Exception e) {
                            Log.d(TAG, "Exception "+e);
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }



    void notifyWiFiDirectPeerListDiscoveryFinished(WifiP2pDeviceList discoveredPeers) {
        adapter.setmValues(new ArrayList<WifiP2pDevice>(Collections.unmodifiableCollection(discoveredPeers.getDeviceList())));
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }
    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
}