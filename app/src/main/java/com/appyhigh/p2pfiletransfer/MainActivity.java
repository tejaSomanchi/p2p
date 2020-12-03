package com.appyhigh.p2pfiletransfer;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.webkit.PermissionRequest;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    NetworkInfo networkInfo;
    private final String TAG = "MainActivity";
    ReceiverClass receiverClass;
    SenderClass senderClass;

    public Context getContext() {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        if (receiverClass != null) {
            receiverClass.closeSocket();
        }
        if (senderClass != null) {
            senderClass.closeSocket();
        }
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                if (wifiP2pGroup != null) {
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {

                        }

                        @Override
                        public void onFailure(int i) {
                            Log.d(TAG, "onFailure: " + i);
                        }
                    });
                }
            }
        });
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                if (receiverClass != null) {
                    receiverClass.closeSocket();
                }
                if (senderClass != null) {
                    senderClass.closeSocket();
                }
                TextView isConnectedTo = findViewById(R.id.connectedTo);
                isConnectedTo.setText("Not connected to any device");
                manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                        if (wifiP2pGroup != null) {
                            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                        return;
                                    }
                                    Log.d(TAG, "Started discover Peers inside ...");
                                    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                                        @Override
                                        public void onSuccess() {

                                        }

                                        @Override
                                        public void onFailure(int i) {
                                            Log.d(TAG, "discover Peers onFailure: " + i);
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(int i) {
                                    Log.d(TAG, "onFailure: " + i);
                                }
                            });
                        } else{
                            Log.d(TAG, "Started discover Peers ...");
                            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {

                                }

                                @Override
                                public void onFailure(int i) {
                                    Log.d(TAG, "discover Peers onFailure: " + i);
                                }
                            });
                        }
                    }
                });

            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, 42);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult called");
        if (requestCode == 42 && resultCode == Activity.RESULT_OK) {
            if(data!=null){
                Uri uri = data.getData();
                Log.d(TAG, "Uri of file to send, chosen by user: "+uri);
                if(wifiP2pInfo!=null && wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                    senderClass = new SenderClass(wifiP2pInfo.groupOwnerAddress.getHostAddress(), uri);
                    senderClass.start();
                } else {
                    Toast.makeText(getContext(), "Receiver is not selected", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    public void decideDevice(WifiP2pInfo wifiP2pInfo, NetworkInfo networkInfo){
        this.wifiP2pInfo = wifiP2pInfo;
        this.networkInfo = networkInfo;
        if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner){
            receiverClass = new ReceiverClass();
            receiverClass.start();
        }
//        else if(wifiP2pInfo.groupFormed){
//            senderClass = new SenderClass(wifiP2pInfo.groupOwnerAddress.getHostAddress(), null);
//            senderClass.start();
//        }

    }

    public class ReceiverClass extends Thread{
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(8889));
                Socket client = serverSocket.accept();

                /**
                 * If this code is reached, a client has connected and transferred data
                 * Save the input stream from the client as a JPEG file
                 */

//                ObjectInputStream objectInputStream = new ObjectInputStream(client.getInputStream());
//                Object object = objectInputStream.readObject();
//                if (object.getClass().equals(String.class) && ((String) object).equals("BROFIST")) {
//                    Log.d(TAG, "Client IP address: "+client.getInetAddress());
//                }
                InputStream inputstream = client.getInputStream();
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputstream);
                DataInputStream dataInputStream = new DataInputStream(bufferedInputStream);
                String filename = dataInputStream.readUTF();
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + "p2pFileTransfer" + "/" + filename);
                File dirs = new File(f.getParent());
                Log.d(TAG, "run: file resumed "+f.getParent()+" "+dirs.exists());
                if (!dirs.exists())
                    dirs.mkdirs();
                Log.d(TAG, "run: file resumed "+f.getParent()+" "+dirs.exists());
                f.createNewFile();
                copyFile(inputstream, new FileOutputStream(f));
                Log.d(TAG, "run: file sent");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getContext(),"File received",Toast.LENGTH_SHORT).show();
                    }
                });
                serverSocket.close();
//                Intent intent = new Intent();
//                intent.setAction(android.content.Intent.ACTION_VIEW);
//                intent.setDataAndType(Uri.parse("file://" + f.getAbsolutePath()), "image/*");
//                getContext().startActivity(intent);

            } catch (Exception e) {
                Log.d(TAG, "Exception "+e);
                e.printStackTrace();
            }

        }

        void closeSocket(){
            if(serverSocket!=null && serverSocket.isBound()){
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
        Uri uri;
        int len;
        byte buf[]  = new byte[1024];

        public SenderClass(String hostAddress, Uri uri){
            this.hostAddress = hostAddress;
            this.uri = uri;
        }

        @Override
        public void run() {
            try {
                socket = new Socket();
                socket.setReuseAddress(true);
                socket.connect(new InetSocketAddress(hostAddress, 8889), 500);
//                OutputStream os = socket.getOutputStream();
//                ObjectOutputStream oos = new ObjectOutputStream(os);
//                oos.writeObject(new String("BROFIST"));
//                oos.close();
//                os.close();
//                socket.close();
                OutputStream outputStream = socket.getOutputStream();
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
                String displayName = getDisplayNameFromUri(uri);
                Log.d(TAG, "run: Sending with displayname "+displayName);
                dataOutputStream.writeUTF(displayName);
                dataOutputStream.flush();

                ContentResolver cr = getContext().getContentResolver();
                InputStream inputStream = null;
                inputStream = cr.openInputStream(uri);
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getContext(),"File sent",Toast.LENGTH_SHORT).show();
                    }
                });
                dataOutputStream.close();
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

        void closeSocket(){
            if(socket!=null && socket.isBound()){
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    String getDisplayNameFromUri(Uri uri){
        String displayName = "";
        Cursor cursor = this.getContentResolver().query(uri, null, null, null, null, null);
        if(cursor!=null && cursor.moveToFirst()){
            int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if(columnIndex != -1){
                displayName = cursor.getString(columnIndex);
            }
        }
        cursor.close();
        if(displayName.equals("")){
            displayName = uri.getLastPathSegment();
            if(displayName == null){
                displayName = uri.getEncodedPath();
            }
            if(displayName == null){
                displayName = uri.toString();
            }
        }
        return displayName;
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