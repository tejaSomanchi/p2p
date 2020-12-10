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
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.webkit.PermissionRequest;
import android.widget.Button;
import android.widget.ProgressBar;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;
    WifiP2pDnsSdServiceRequest serviceRequest;
    IntentFilter intentFilter;
    WiFiDirectPeerDevicesRecyclerAdapter adapter;
    RecyclerView recyclerView;
    WifiP2pManager.ConnectionInfoListener connectionInfoListener;
    WifiP2pInfo wifiP2pInfo;
    NetworkInfo networkInfo;
    private final String TAG = "MainActivity";
    ReceiverClass receiverClass;
    SenderClass senderClass;
    InetAddress nonGroupOwnerAddress;
    ProgressBar progressBar;

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
        registerReceiver(receiver, intentFilter);
        recyclerView = findViewById(R.id.device_list);
        adapter = new WiFiDirectPeerDevicesRecyclerAdapter(manager, channel);
        recyclerView.setAdapter(adapter);
        Button discover = findViewById(R.id.discover);
        Button send = findViewById(R.id.send);
        progressBar = findViewById(R.id.progress);
        progressBar.setVisibility(View.GONE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        if (receiverClass != null) {
            receiverClass.closeSocket();
        }
        if (senderClass != null) {
            senderClass.closeSocket();
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        startRegistration();
        discoverServices();
        discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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

                Log.d(TAG, "Started discover Peers ...");
                serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
                manager.addServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                // Success!
                            }

                            @Override
                            public void onFailure(int code) {
                                Log.d(TAG, "onFailure: " + code);
                            }
                        });
                    }

                    @Override
                    public void onFailure(int code) {
                        Log.d(TAG, "onFailure: " + code);
                    }
                });
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, 42);
            }
        });
    }


    private void startRegistration() {

        Map record = new HashMap();
        record.put("version", "" + android.os.Build.VERSION.SDK_INT);
        record.put("buddyname", "device" + (int) (Math.random() * 1000));
        record.put("available", "visible");

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onFailure(int arg0) {
                Log.d(TAG, "onFailure: registration " + arg0);
            }
        });
    }


    private void discoverServices() {
        final HashMap<String, String> buddies = new HashMap<String, String>();
        WifiP2pManager.DnsSdTxtRecordListener recordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String fullDomain, Map record, WifiP2pDevice device) {
                Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());
                buddies.put(device.deviceAddress, (String) record.get("buddyname"));
            }
        };

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice resourceType) {
//                resourceType.deviceName = buddies.containsKey(resourceType.deviceAddress) ? buddies.get(resourceType.deviceAddress) : resourceType.deviceName;
                if (buddies.containsKey(resourceType.deviceAddress)) {
                    adapter.add(resourceType);
                    Log.d(TAG, "onServiceAvailable " + instanceName + " " + resourceType.deviceName + " " + buddies.get(resourceType.deviceAddress));
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, recordListener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult called");
        if (requestCode == 42 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                ArrayList<Uri> uris = new ArrayList<>();
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        uris.add(uri);
                        Log.d(TAG, "Uri of file to send, chosen by user: " + uri);
                    }
                } else {
                    Uri uri = data.getData();
                    uris.add(uri);
                    Log.d(TAG, "Uri of file to send, chosen by user: " + uri);
                }

                if (wifiP2pInfo != null && wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                    senderClass = new SenderClass(wifiP2pInfo.groupOwnerAddress.getHostAddress(), uris, 8887);
                    senderClass.start();
                } else if (wifiP2pInfo != null && wifiP2pInfo.groupFormed && nonGroupOwnerAddress != null) {
                    senderClass = new SenderClass(nonGroupOwnerAddress.getHostAddress(), uris, 8888);
                    senderClass.start();
                } else {
                    Toast.makeText(getContext(), "Receiver is not selected", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    public void decideDevice(WifiP2pInfo wifiP2pInfo, NetworkInfo networkInfo) {
        this.wifiP2pInfo = wifiP2pInfo;
        this.networkInfo = networkInfo;
        if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
            ReceiveIpAddressThread receiveIpAddressThread = new ReceiveIpAddressThread();
            receiveIpAddressThread.start();
            receiverClass = new ReceiverClass(8887);
            receiverClass.start();
        } else if (wifiP2pInfo.groupFormed) {
            SendIpAddressThread sendIpAddressThread = new SendIpAddressThread(wifiP2pInfo.groupOwnerAddress.getHostAddress());
            sendIpAddressThread.start();
            receiverClass = new ReceiverClass(8888);
            receiverClass.start();
        }

    }

    public class ReceiverClass extends Thread {
        Socket socket;
        ServerSocket serverSocket;
        int port;
        byte[] buffer = new byte[1024];
        int length;

        ReceiverClass(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(port));
                Socket client = serverSocket.accept();

                /**
                 * If this code is reached, a client has connected and transferred data
                 * Save the input stream from the client as a JPEG file
                 */

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                });
                InputStream inputstream = client.getInputStream();
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputstream);
                DataInputStream dataInputStream = new DataInputStream(bufferedInputStream);
                int noOfFiles = dataInputStream.readInt();
                Log.d(TAG, "run: receiving " + noOfFiles);
                ArrayList<File> files = new ArrayList<>();
                ArrayList<Long> fileSizes = new ArrayList<>();
                for (int i = 0; i < noOfFiles; i++) {
                    String filename = dataInputStream.readUTF();
                    long fileSize = dataInputStream.readLong();
                    File f = new File(Environment.getExternalStorageDirectory() + "/"
                            + "p2pFileTransfer" + "/" + filename);
                    Log.d(TAG, "run: " + filename + " " + fileSize);
                    files.add(f);
                    fileSizes.add(fileSize);
                }
                for (int i = 0; i < noOfFiles; i++) {
                    File f = files.get(i);
                    File dirs = new File(f.getParent());
                    if (!dirs.exists())
                        dirs.mkdirs();
                    f.createNewFile();
                    long fileSize = fileSizes.get(i);
                    long total = 0;
                    FileOutputStream os = new FileOutputStream(f);
                    while (total < fileSize && (length = inputstream.read(buffer, 0, fileSize - total > buffer.length ? buffer.length : (int) (fileSize - total))) > 0) {
                        os.write(buffer, 0, length);
                        total += length;
                    }
                    os.close();
                    Log.d(TAG, "run: file received " + f.getName() + " ");
                }
                inputstream.close();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "File received", Toast.LENGTH_SHORT).show();
                    }
                });
                serverSocket.close();
                receiverClass = new ReceiverClass(port);
                receiverClass.start();

            } catch (Exception e) {
                Log.d(TAG, "Exception " + e);
                e.printStackTrace();
            }

        }

        void closeSocket() {
            if (serverSocket != null && serverSocket.isBound()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public class SenderClass extends Thread {
        Socket socket;
        String hostAddress;
        int port;
        ArrayList<Uri> uris;
        int len;
        byte buf[] = new byte[1024];

        public SenderClass(String hostAddress, ArrayList<Uri> uris, int port) {
            this.hostAddress = hostAddress;
            this.uris = uris;
            this.port = port;
        }

        @Override
        public void run() {
            try {
                socket = new Socket();
                socket.setReuseAddress(true);
                socket.connect(new InetSocketAddress(hostAddress, port), 500);
                OutputStream outputStream = socket.getOutputStream();
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                });
                DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
                dataOutputStream.writeInt(uris.size());
                dataOutputStream.flush();
                for (Uri uri : uris) {
                    String displayName = getDisplayNameFromUri(uri);
                    File f = new File(uri.getPath());
                    AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r");
                    long fileSize = afd.getLength();
                    Log.d(TAG, "run: Sending with displayname " + displayName + " " + fileSize);
                    afd.close();
                    dataOutputStream.writeUTF(displayName);
                    dataOutputStream.writeLong(fileSize);
                    dataOutputStream.flush();
                }
                for (Uri uri : uris) {
                    ContentResolver cr = getContext().getContentResolver();
                    InputStream inputStream = null;
                    inputStream = cr.openInputStream(uri);
                    while ((len = inputStream.read(buf)) != -1) {
                        outputStream.write(buf, 0, len);
                    }
                    dataOutputStream.flush();
                    inputStream.close();
                }
                dataOutputStream.close();
                outputStream.close();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "File sent", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Exception " + e);
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (Exception e) {
                            Log.d(TAG, "Exception " + e);
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        void closeSocket() {
            if (socket != null && socket.isBound()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public class SendIpAddressThread extends Thread {
        Socket socket;
        String hostAddress;

        public SendIpAddressThread(String hostAddress) {
            this.hostAddress = hostAddress;
        }

        @Override
        public void run() {
            try {
                socket = new Socket();
                socket.setReuseAddress(true);
                socket.connect(new InetSocketAddress(hostAddress, 9000), 500);
                OutputStream os = socket.getOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(os);
                oos.writeObject(new String("BROFIST"));
                oos.close();
                os.close();
            } catch (Exception e) {
                Log.d(TAG, "Exception " + e);
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (Exception e) {
                            Log.d(TAG, "Exception " + e);
                            e.printStackTrace();
                        }
                    }
                }

            }
        }
    }


    public class ReceiveIpAddressThread extends Thread {
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(9000));
                Socket client = serverSocket.accept();

                /**
                 * If this code is reached, a client has connected and transferred data
                 * Save the input stream from the client as a JPEG file
                 */

                ObjectInputStream objectInputStream = new ObjectInputStream(client.getInputStream());
                Object object = objectInputStream.readObject();
                if (object.getClass().equals(String.class) && ((String) object).equals("BROFIST")) {
                    nonGroupOwnerAddress = client.getInetAddress();
                    Log.d(TAG, "Client IP address: " + client.getInetAddress());
                }
                serverSocket.close();

            } catch (Exception e) {
                Log.d(TAG, "Exception " + e);
                e.printStackTrace();
            }

        }
    }

    String getDisplayNameFromUri(Uri uri) {
        String displayName = "";
        Cursor cursor = this.getContentResolver().query(uri, null, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (columnIndex != -1) {
                displayName = cursor.getString(columnIndex);
            }
        }
        cursor.close();
        if (displayName.equals("")) {
            displayName = uri.getLastPathSegment();
            if (displayName == null) {
                displayName = uri.getEncodedPath();
            }
            if (displayName == null) {
                displayName = uri.toString();
            }
        }
        return displayName;
    }

    void notifyWiFiDirectPeerListDiscoveryFinished(WifiP2pDeviceList discoveredPeers) {
        adapter.setmValues(new ArrayList<WifiP2pDevice>(Collections.unmodifiableCollection(discoveredPeers.getDeviceList())));
    }


//    @Override
//    protected void onResume() {
//        super.onResume();
//        registerReceiver(receiver, intentFilter);
//    }
//    /* unregister the broadcast receiver */
//    @Override
//    protected void onPause() {
//        super.onPause();
//        unregisterReceiver(receiver);
//    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
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

                        }
                    });
                }
            }
        });
        unregisterReceiver(receiver);
    }
}