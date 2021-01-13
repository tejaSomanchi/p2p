package com.appyhigh.p2pfiletransfer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

public class ServerActivity extends Activity {

    TextView welcomeMsg;
    TextView infoIp;
    TextView infoMsg;
    String msgLog = "";
    WebServer webServer;
    int port;
    Button send;
    ProgressBar progressBar;
    String ipAdd = "";

    public final String TAG = "ServerActivity";

    ServerSocket httpServerSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.server_layout);
        port = 9005;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        welcomeMsg = (TextView) findViewById(R.id.welcomemsg);
        infoIp = (TextView) findViewById(R.id.infoip);
        infoMsg = (TextView) findViewById(R.id.msg);
        send = findViewById(R.id.send);
        infoIp.setText("Site Address - http://"+getIpAddress() + ":" + port + "\n");
        send.setEnabled(true);
        progressBar = findViewById(R.id.progress);
        progressBar.setVisibility(View.GONE);
//        HttpServerThread httpServerThread = new HttpServerThread();
//        httpServerThread.start();
        webServer = new WebServer(ipAdd,port, this);
        try {
            webServer.start();

        } catch (IOException e) {
            e.printStackTrace();
            send.setEnabled(false);
            Toast.makeText(this, "Web Server not started", Toast.LENGTH_LONG).show();
        }
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

                ArrayList<FileObject> fileObjects = new ArrayList<>();

                for(int i=0;i<uris.size();i++){
                    Uri currentUri = uris.get(i);
                    long size = getFileSize(currentUri);
                    FileObject fileObject = new FileObject(currentUri.toString(), getDisplayNameFromUri(currentUri), getCurrentBytes(size), size, false);
                    fileObjects.add(fileObject);
                }
                WebServer.isReceived = true;
                WebServer.receivedFiles.addAll(fileObjects);
            }


        }
    }






    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (httpServerSocket != null) {
            try {
                httpServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        webServer.stop();
    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip = inetAddress.getHostAddress();
                        ipAdd = inetAddress.getHostAddress();
                        return ip;
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }


    public void uploadFile(Uri sourceFileUri, String fileName, String fileSize) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                DataOutputStream dos = null;
                String lineEnd = "\r\n";
                String twoHyphens = "--";
                String boundary = "*****";


                int serverResponseCode;
                try {
                    // open a URL connection to the Servlet
                    URL url = new URL("http://"+ipAdd+":"+port+"/receive");

                    Log.d(TAG, "run: "+url);
                    // Open a HTTP  connection to  the URL
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true); // Allow Inputs
                    conn.setDoOutput(true); // Allow Outputs
                    conn.setUseCaches(false); // Don't use a Cached Copy
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("file", fileName);
                    conn.addRequestProperty("size", fileSize);

                    // Responses from the server (code and message)
                    serverResponseCode = conn.getResponseCode();
                    String serverResponseMessage = conn.getResponseMessage();

                    Log.i("uploadFile", "HTTP Response is : "
                            + serverResponseMessage + ": " + serverResponseCode);

                    if(serverResponseCode == 200){

                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(ServerActivity.this, "File Upload Complete.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                }  catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();



    }


    private class HttpServerThread extends Thread {

        static final int HttpServerPORT = 9006;

        @Override
        public void run() {
            Socket socket = null;

            try {
                httpServerSocket = new ServerSocket(HttpServerPORT);

                while (true) {
                    socket = httpServerSocket.accept();

                    HttpResponseThread httpResponseThread =
                            new HttpResponseThread(
                                    socket,
                                    welcomeMsg.getText().toString());
                    httpResponseThread.start();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }


    }

    private class HttpResponseThread extends Thread {

        Socket socket;
        String h1;

        HttpResponseThread(Socket socket, String msg) {
            this.socket = socket;
            h1 = msg;
        }

        @Override
        public void run() {
            BufferedReader is;
            PrintWriter os;
            String request;


            try {
                is = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                request = is.readLine();

                os = new PrintWriter(socket.getOutputStream(), true);

                String response =
                        "<html><head></head>" +
                                "<body>" +
                                "<h1>" + h1 + "</h1>" +
                                "</body></html>";

                os.print("HTTP/1.0 200" + "\r\n");
                os.print("Content type: text/html" + "\r\n");
                os.print("Content length: " + response.length() + "\r\n");
                os.print("\r\n");
                os.print(response + "\r\n");
                os.flush();
                socket.close();


                msgLog += "Request of " + request
                        + " from " + socket.getInetAddress().toString() + "\n";
                ServerActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        infoMsg.setText(msgLog);
                    }
                });

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            return;
        }
    }

    public static void sendToast(Context context,String msg){
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    String getDisplayNameFromUri(Uri uri) {
        String displayName = "";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
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


    private long getFileSize(Uri fileUri) {
        Cursor returnCursor = getContentResolver().
                query(fileUri, null, null, null, null);
        try {
            int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
            returnCursor.moveToFirst();

            return returnCursor.getLong(sizeIndex);
        }
        catch (Exception e){
            Toast.makeText(this,"Could not share this file, try again later!",Toast.LENGTH_SHORT).show();
            finish();
            return 0;
        }
    }


    private String getCurrentBytes(long bytes) {
        double bytesValue = (double) bytes;
        String bytesUnit = "B";

        if (bytes > 1000000000) {
            bytesValue = (double) bytes / 1073741824;
            bytesUnit = "GB";
        } else if (bytes > 1000000) {
            bytesValue = (double) bytes / 1048576;
            bytesUnit = "MB";
        } else if (bytes > 1000) {
            bytesValue = (double) bytes / 1024;
            bytesUnit = "KB";
        }
        DecimalFormat numberFormat = new DecimalFormat("#.00");
        return numberFormat.format(bytesValue)+" "+bytesUnit;
    }


}
