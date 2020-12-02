package com.appyhigh.p2pfiletransfer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

public class WiFiDirectPeerDevicesRecyclerAdapter extends RecyclerView.Adapter<WiFiDirectPeerDevicesRecyclerAdapter.DeviceViewHolder> {

    private ArrayList<WifiP2pDevice> mValues = new ArrayList<>();
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private final String TAG = "Adapter";

    public WiFiDirectPeerDevicesRecyclerAdapter(WifiP2pManager manager, WifiP2pManager.Channel channel) {
        this.manager = manager;
        this.channel = channel;
        setHasStableIds(true);
    }


    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View singleDeviceView = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_device, parent, false);
        return new DeviceViewHolder(singleDeviceView);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {

        WifiP2pDevice wifiP2pDevice = mValues.get(position);
        holder.mIdView.setText((CharSequence) wifiP2pDevice.deviceAddress);
        holder.mContentView.setText((CharSequence) wifiP2pDevice.deviceName);
        Log.d("devices", "device : " + wifiP2pDevice.deviceName);
        holder.itemView.setTag(wifiP2pDevice);
    }

    public void setmValues(ArrayList<WifiP2pDevice> mValues) {
        this.mValues.clear();
        this.mValues = mValues;
        notifyDataSetChanged();
    }


    @Override
    public int getItemCount() {
        return mValues.size();
    }


    @Override
    public long getItemId(int position) {
        String wifiP2pDevice = mValues.get(position).deviceAddress;
        String macAddressHexStringWithoutColons = wifiP2pDevice.replace(":", "");
        return Long.parseLong(macAddressHexStringWithoutColons, 16);
    }


    public class DeviceViewHolder extends RecyclerView.ViewHolder {

        private TextView mIdView, mContentView;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            mIdView = itemView.findViewById(R.id.item_number);
            mContentView = itemView.findViewById(R.id.content);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    WifiP2pDevice wifiP2pDevice = mValues.get(getAdapterPosition());
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = wifiP2pDevice.deviceAddress;
                    if (ActivityCompat.checkSelfPermission(itemView.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    manager.connect(channel, config, new WifiP2pManager.ActionListener() {

                        @Override
                        public void onSuccess() {
                            //success logic
                        }

                        @Override
                        public void onFailure(int reason) {
                            //failure logic
                            Log.d(TAG, "onFailure: "+wifiP2pDevice.deviceName+" "+reason);
                        }
                    });
                }
            });
        }

        @Override
        public String toString() {
            return super.toString()+" '" + mContentView.getText() + "'";
        }
    }
}
