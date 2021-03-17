package com.appyhigh.p2pfiletransfer;

import android.annotation.SuppressLint;
import android.os.FileUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;


import java.util.ArrayList;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.collection.SimpleArrayMap;
import androidx.recyclerview.widget.RecyclerView;

public class FilesListAdapter extends RecyclerView.Adapter<FilesListAdapter.ViewHolder> {

    String TAG = "FilesListAdapter";
    ArrayList<FileObject> filesList = new ArrayList<>();

    void addFile(FileObject fileObject){
        Log.d(TAG, "addFile: "+fileObject.filename);
        int oldPos = this.filesList.size();
        Log.d("oldSize", oldPos+"");
        filesList.add(fileObject);
        notifyItemRangeInserted(oldPos, oldPos+1);
    }

    void setData(ArrayList<FileObject> filesList){
        this.filesList = filesList;
        notifyDataSetChanged();
    }

    public void updateTransfer(int pos, long totalSize, long currentSize, int percentTransferred) {
        filesList.get(pos).fileSize = totalSize;
        filesList.get(pos).currentSize = currentSize;
        filesList.get(pos).percent = percentTransferred;
        notifyItemChanged(pos);
    }

    void update(int pos){
        filesList.get(pos).isCompleted = true;
        notifyItemChanged(pos);
    }

    @NonNull
    @Override
    public FilesListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemview = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(itemview);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull FilesListAdapter.ViewHolder holder, int position) {
        FileObject fileObject = filesList.get(position);
        holder.title.setText(fileObject.filename);
        if(fileObject.percent>0){
            holder.received.setText(sizeExpression(fileObject.currentSize, false));
            Log.d(TAG, "onBindViewHolder: "+fileObject.fileSize+"  "+sizeExpression(fileObject.fileSize,false));
            holder.total.setText("/"+(sizeExpression(fileObject.fileSize,false)));
            holder.progressBar.setProgress(fileObject.percent);
        }
        if(fileObject.isCompleted){
            holder.received.setText(sizeExpression(fileObject.fileSize, false));
            holder.total.setText("/"+sizeExpression(fileObject.fileSize,false));
            holder.progressBar.setProgress(100);
        }

        Log.d(TAG, "onBindViewHolder: "+position+" "+fileObject.filename+"  "+fileObject.isCompleted);
    }


    @Override
    public int getItemCount() {
        return filesList.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {

        public TextView title;
        public TextView total;
        public TextView received;
        public ProgressBar progressBar;
        public View progressLayout;

        public ViewHolder(View view) {
            super(view);
            title = (TextView) view.findViewById(R.id.title);
            total = (TextView) view.findViewById(R.id.total);
            progressBar = view.findViewById(R.id.progressBar);
            received = view.findViewById(R.id.received);
            progressLayout = view.findViewById(R.id.progressLayout);

        }

    }

    public static String sizeExpression(long bytes, boolean notUseByte)
    {
        int unit = notUseByte ? 1000 : 1024;

        if (bytes < unit)
            return bytes + " B";

        int expression = (int) (Math.log(bytes) / Math.log(unit));
        String prefix = (notUseByte ? "kMGTPE" : "KMGTPE").charAt(expression - 1) + (notUseByte ? "i" : "");

        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(unit, expression), prefix);
    }
}
