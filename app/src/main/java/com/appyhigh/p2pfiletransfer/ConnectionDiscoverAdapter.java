package com.appyhigh.p2pfiletransfer;

import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ConnectionDiscoverAdapter extends RecyclerView.Adapter<ConnectionDiscoverAdapter.ViewHolder> {

    ArrayList<String> deviceEndPointList = new ArrayList<>();
    ArrayList<String> deviceNameList = new ArrayList<>();
    ConnectionLifecycleCallback connectionLifecycleCallback;
    String TAG = "ConnectionDiscoverAdapter";

    ConnectionDiscoverAdapter(ConnectionLifecycleCallback connectionLifecycleCallback){
        super();
        this.connectionLifecycleCallback = connectionLifecycleCallback;
    }



    void addItem(String endpointId, String name){
        deviceEndPointList.add(endpointId);
        deviceNameList.add(name);
        notifyDataSetChanged();
    }

    void removeItem(String endpointId){
        int ind = deviceEndPointList.indexOf(endpointId);
        deviceEndPointList.remove(ind);
        deviceNameList.remove(ind);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConnectionDiscoverAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemview = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_connection, parent, false);
        return new ViewHolder(itemview);
    }

    @Override
    public void onBindViewHolder(@NonNull ConnectionDiscoverAdapter.ViewHolder holder, int position) {
        String name = deviceNameList.get(position);
        String endpointId = deviceEndPointList.get(position);
        holder.title.setText(name);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Nearby.getConnectionsClient(holder.itemView.getContext())
                .requestConnection(Build.MODEL, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We successfully requested a connection. Now both sides
                            // must accept before the connection is established.
                            Log.d(TAG, "onClick: connectionRequested");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // Nearby Connections failed to request the connection.
                            Log.d(TAG, "onClick: connectionn failed");
                            e.printStackTrace();
                        });
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceNameList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public TextView title;

        public ViewHolder(View view) {
            super(view);
            title = (TextView) view.findViewById(R.id.title);
        }

    }
}
