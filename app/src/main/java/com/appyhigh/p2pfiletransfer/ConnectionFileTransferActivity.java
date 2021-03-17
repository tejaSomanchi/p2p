package com.appyhigh.p2pfiletransfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.SimpleArrayMap;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ConnectionFileTransferActivity extends AppCompatActivity {

    String TAG = "ConnectionFileTransferActivity";
    RecyclerView mRecyclerView;
    String endpointId = "";

    Gson gson;
    public static FilesListAdapter filesListAdapter;
    public static int fileNumber = 0;


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_file_transfer);
        gson = new Gson();
        TextView connectedTo = findViewById(R.id.connectedTo);
        Button send = findViewById(R.id.send);

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
        connectedTo.setText("Connected To "+getIntent().getStringExtra("deviceName"));
        endpointId = getIntent().getStringExtra("endpointId");
        mRecyclerView = findViewById(R.id.recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        filesListAdapter = new FilesListAdapter();
        mRecyclerView.setAdapter(filesListAdapter);

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
                for (Uri uri : uris) {
                    FileObject fileObject = new FileObject();
                    fileObject.filename = getDisplayNameFromUri(uri);
                    fileObject.fileSize = getFileSize(uri);
                    String fileObjectString = gson.toJson(fileObject);
                    ParcelFileDescriptor fileDescriptor = null;
                    InputStream inputStream = null;
                    try {
                        fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
                        inputStream = getContentResolver().openInputStream(uri);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    Payload filePayload = Payload.fromFile(fileDescriptor);
                    Payload streamPayload = Payload.fromStream(inputStream);

                    String filenameMessage = filePayload.getId() + ":" + fileObject.filename;
//                    String filenameMessage = streamPayload.getId() + ":" + fileObject.filename;
                    Payload fileObjectPayload =
                            Payload.fromBytes(filenameMessage.getBytes(StandardCharsets.UTF_8));
                    Log.d(TAG, "onActivityResult: "+fileObject.filename);
                    filesListAdapter.addFile(fileObject);
                    Nearby.getConnectionsClient(this).sendPayload(endpointId, fileObjectPayload).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Log.d(TAG, "onComplete: "+fileNumber);
                            filesListAdapter.update(fileNumber);
                            fileNumber++;
                            Toast.makeText(ConnectionFileTransferActivity.this," File Sent", Toast.LENGTH_SHORT).show();
                        }
                    });
                    Nearby.getConnectionsClient(this).sendPayload(endpointId, filePayload);
//                    Nearby.getConnectionsClient(this).sendPayload(endpointId, streamPayload);
                }

            }
        }
    }

    static class StreamFilePayloadCallback extends PayloadCallback{
        Context context;
        String TAG = "StreamFilePayloadCallback";
        private final SimpleArrayMap<Long, Payload> incomingPayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();

        StreamFilePayloadCallback(Context context){
            this.context = context;
        }

        @Override
        public void onPayloadReceived(@NonNull String s, @NonNull Payload payload) {
            Log.d(TAG, "onPayloadReceived: "+payload.getType());
            if (payload.getType() == Payload.Type.BYTES) {
                String payloadFilenameMessage = new String(payload.asBytes(), StandardCharsets.UTF_8);
                long payloadId = addPayloadFilename(payloadFilenameMessage);
            } else if (payload.getType() == Payload.Type.STREAM) {
                // Add this to our tracking map, so that we can retrieve the payload later.
                incomingPayloads.put(payload.getId(), payload);
            }
        }

        private long addPayloadFilename(String payloadFilenameMessage) {
            String[] parts = payloadFilenameMessage.split(":");
            long payloadId = Long.parseLong(parts[0]);
            String filename = parts[1];
            Log.d("TAG", "addPayloadFilename: "+filename);
            filePayloadFilenames.put(payloadId, filename);
            FileObject fileObject = new FileObject();
            fileObject.filename = filename;
            filesListAdapter.addFile(fileObject);
            return payloadId;
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            Log.d(TAG, "onPayloadTransferUpdate: "+update.getStatus()+"   "+ PayloadTransferUpdate.Status.SUCCESS);
            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                Payload payload = incomingPayloads.get(update.getPayloadId());
                if(payload!=null) {
                    Log.d(TAG, "onPayloadTransferUpdate: " + incomingPayloads.size() + " " + payload);
                    InputStream in = payload.asStream().asInputStream();
                    File dest = new File(Environment.getExternalStorageDirectory() + "/"
                            + context.getString(R.string.app_name) + "/" + filePayloadFilenames.get(update.getPayloadId()));
                    try {
                        copyStream(in, new FileOutputStream(dest));
                        filesListAdapter.update(fileNumber);
                        fileNumber++;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        /** Copies a stream from one location to another. */
        private void copyStream(InputStream in, FileOutputStream out) throws IOException {
            try {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            } finally {
                in.close();
                out.close();
            }
        }
    }

    static class ReceiveFilePayloadCallback extends PayloadCallback {
        Context context;
        private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();


         ReceiveFilePayloadCallback(Context context){
             this.context = context;
         }

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                String payloadFilenameMessage = new String(payload.asBytes(), StandardCharsets.UTF_8);
                long payloadId = addPayloadFilename(payloadFilenameMessage);
                processFilePayload(payloadId);
            } else if (payload.getType() == Payload.Type.FILE) {
                // Add this to our tracking map, so that we can retrieve the payload later.
                incomingFilePayloads.put(payload.getId(), payload);
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is payloadId:filename.
         */
        private long addPayloadFilename(String payloadFilenameMessage) {
            String[] parts = payloadFilenameMessage.split(":");
            long payloadId = Long.parseLong(parts[0]);
            String filename = parts[1];
            Log.d("TAG", "addPayloadFilename: "+filename);
            filePayloadFilenames.put(payloadId, filename);
            FileObject fileObject = new FileObject();
            fileObject.filename = filename;
            filesListAdapter.addFile(fileObject);
            return payloadId;
        }

        private void processFilePayload(long payloadId) {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            Payload filePayload = completedFilePayloads.get(payloadId);
            String filename = filePayloadFilenames.get(payloadId);
            Log.d("TAG", "processFilePayload: "+filename);
            if (filePayload != null && filename != null) {
                completedFilePayloads.remove(payloadId);

                File parentFolder = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getString(R.string.app_name));
                if(!parentFolder.exists()){
                    parentFolder.mkdir();
                }
                // Get the received file (which will be in the Downloads folder)
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    // Because of https://developer.android.com/preview/privacy/scoped-storage, we are not
                    // allowed to access filepaths from another process directly. Instead, we must open the
                    // uri using our ContentResolver.
                    try {
                        Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID+".fileprovider", filePayload.asFile().asJavaFile());
                        // Copy the file to a new location.
                        InputStream in = context.getContentResolver().openInputStream(uri);
                        File dest = new File(Environment.getExternalStorageDirectory() + "/"
                                + context.getString(R.string.app_name) + "/" + filename);
                        copyStream(in, new FileOutputStream(dest));
                        Log.d("TAG", "processFilePayload: "+fileNumber);
                        filesListAdapter.update(fileNumber);
                        fileNumber++;
                        context.getContentResolver().delete(uri, null, null);
                    } catch (Exception e) {
                        // Log the error.
                        Log.d("TAG", "processFilePayload: "+e.getMessage());
                        e.printStackTrace();
                    } finally {
                        // Delete the original file.
                    }
                } else {
                    File payloadFile = filePayload.asFile().asJavaFile();

                    File dest = new File(Environment.getExternalStorageDirectory() + "/"
                            + context.getString(R.string.app_name) + "/" + filename);
                    payloadFile.renameTo(dest);
                    // Rename the file.
//                    payloadFile.renameTo(new File(payloadFile.getParentFile(), filename));
                    Log.d("TAG", "processFilePayload: "+fileNumber);
                    filesListAdapter.update(fileNumber);
                    fileNumber++;

                }
                Toast.makeText(context," File Received", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                long payloadId = update.getPayloadId();
                Payload payload = incomingFilePayloads.remove(payloadId);
                if(payload!=null) {
                    Log.d("TAG", "onPayloadTransferUpdate: "+payload.getId());
                    completedFilePayloads.put(payloadId, payload);
                    if (payload.getType() == Payload.Type.FILE) {
                        processFilePayload(payloadId);
                    }
                }
            } else if(update.getStatus() == PayloadTransferUpdate.Status.IN_PROGRESS){
                long payloadId = update.getPayloadId();
                Payload payload = incomingFilePayloads.get(payloadId);
                if(payload!=null) {
                    long totalSize = update.getTotalBytes();
                    long currentSize = update.getBytesTransferred();
                    if (totalSize == -1) {
                        return;
                    }
                    int percentTransferred = (int) (100.0 * (currentSize / (double) totalSize));
                    filesListAdapter.updateTransfer(fileNumber, totalSize, currentSize, percentTransferred);
                }
            }
        }



        /** Copies a stream from one location to another. */
        private void copyStream(InputStream in, FileOutputStream out) throws IOException {
            try {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            } finally {
                in.close();
                out.close();
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

}