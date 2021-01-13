package com.appyhigh.p2pfiletransfer;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.StringRes;
import fi.iki.elonen.NanoHTTPD;

public class WebServer extends NanoHTTPD {
    Context mContext;
    AssetManager mAssetManager;
    public final String TAG = "WebServer";
    String hostname;
    public static boolean isReceived = false;
    public static ArrayList<FileObject> receivedFiles = new ArrayList<>();

    public WebServer(String hostname, int port, Context context) {
        super(port);
        mContext = context;
        this.hostname = hostname;
        mAssetManager = mContext.getAssets();
    }



    @Override
    public Response serve(IHTTPSession session) {
        String[] args = new String[]{};

        if (session.getUri().length() > 1) {
            args = session.getUri().substring(1).split("/");
        }

        String search = "default";
        if(args.length>=1){
            search = args[0];
        }

        try {
            switch (search) {
                case "getFilesLength":
                    return newFixedLengthResponse(""+receivedFiles.size());
                case "sendToOther":
                    return sendToOther(session);
                case "getReceived":
                    String json = new Gson().toJson(receivedFiles);
                    return newFixedLengthResponse(Response.Status.ACCEPTED, "text/json", json);
                case "download":
                    return fileDownload(session);
                default:
                    return newFixedLengthResponse(Response.Status.ACCEPTED, "text/html",
                            homePage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.NOT_ACCEPTABLE, "text/plain",
                    e.toString());
        }
    }


    private Response sendToOther(IHTTPSession session){
        try {
            Map<String, String> files = new HashMap<String, String>();
            session.parseBody(files);
            String msg = "";
            ArrayList<String> filePaths = new ArrayList<>();
            String[] fileNames = session.getParms().get("fileNames").split(",");
            String key = "file";
            int noOfFiles = 1;
            while (files.containsKey(key)) {
                filePaths.add(files.get(key));
                noOfFiles++;
                key = "file" + noOfFiles;
            }
            for (int i = 0; i < filePaths.size(); i++) {
                String filePath = filePaths.get(i);
                String fileName = fileNames[i];
                File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + "p2pFileTransfer" + "/" + fileName);
                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                try {
                    ContentResolver resolver = mContext.getContentResolver();

                    InputStream inputStream = resolver.openInputStream(Uri.fromFile(new File(filePath)));
                    OutputStream outputStream = resolver.openOutputStream(Uri.fromFile(f));

                    byte[] buffer = new byte[1024];
                    long totalRead = 0;
                    int len;
                    while ((len = inputStream.read(buffer)) > 0) {

                        outputStream.write(buffer, 0, len);
                        outputStream.flush();
                        totalRead += len;

                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            ServerActivity.sendToast(mContext, "File Received " + fileName);
                        }
                    });
                    outputStream.close();
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Response r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
            r.addHeader("Location", "/");
            return r;
        }catch (Exception e){
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.NOT_ACCEPTABLE, "text/plain",
                    e.toString());
        }
    }


    private Response fileDownload(IHTTPSession session) {

        try{
            FileObject fileObject = receivedFiles.get(Integer.parseInt(session.getHeaders().get("id")));
            FileInputStream fis = null;
            fileObject.setDownloaded(true);
            Uri uri = Uri.parse(fileObject.uri);
            Log.d(TAG, "fileDownload: "+uri.getPath());
            InputStream inputStream = mContext.getContentResolver().openInputStream(uri);
            Response res = newFixedLengthResponse(Response.Status.ACCEPTED, "application/octet-stream", inputStream, fileObject.fileSize);
            res.addHeader("name", fileObject.filename);
            return res;

        } catch (Exception e) {
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.NOT_ACCEPTABLE, "text/plain",
                    e.toString());
        }
    }

    private String homePage() {
        StringBuilder contentBuilder = new StringBuilder();
        if (contentBuilder.length() == 0)
            contentBuilder.append(makeNotFoundTemplate("Use 'Send to PC' option to share files to PC", ""));
        return makePage("Transfers", contentBuilder.toString());
    }


    private String makePage(String title, String content)
    {
        String appName = mContext.getString(R.string.app_name);

        Map<String, String> values = new HashMap<>();
        values.put("title", String.format("%s - %s", title, appName));
        values.put("header_logo", "/image/applogo");
        values.put("header", mContext.getString(R.string.app_name));
        values.put("title_header", title);
        values.put("main_content", content);
        values.put("help_icon", "/image/help-circle.svg");
        values.put("help_alt", "Help");
        values.put("username", "P2PFileTransfer");
        values.put("footer_text", "This is developed by p2pfiletransfer ");

        return applyPattern(getFieldPattern(), readPage("home.html"), values);
    }

    private String makeNotFoundTemplate(String msg, String detail)
    {
        Map<String, String> values = new HashMap<>();
        values.put("content", msg);
        values.put("detail", detail);

        return applyPattern(getFieldPattern(), readPage("layout_not_found.html"),
                values);
    }

    private static String applyPattern(Pattern pattern, String template, Map<String, String> values)
    {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = pattern.matcher(template);
        int previousLocation = 0;

        while (matcher.find()) {
            builder.append(template, previousLocation, matcher.start());
            builder.append(values.get(matcher.group(1)));

            previousLocation = matcher.end();
        }

        if (previousLocation > -1 && previousLocation < template.length())
            builder.append(template, previousLocation, template.length());

        return builder.toString();
    }

    private static Pattern getFieldPattern()
    {
        // Android Studio may say the escape characters at the end are redundant.
        // They are not in Java 1.7.
        return Pattern.compile("\\$\\{([a-zA-Z_]+)\\}");
    }


    private InputStream openFile(String fileName) throws IOException
    {
        return mAssetManager.open("webshare" + File.separator + fileName);
    }

    private String readPage(String pageName)
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        try {
            InputStream inputStream = openFile(pageName);
            int len;

            while ((len = inputStream.read()) != -1) {
                stream.write(len);
                stream.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stream.toString();
    }


    String getDisplayNameFromUri(Uri uri) {
        String displayName = "";
        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null, null);
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



}
