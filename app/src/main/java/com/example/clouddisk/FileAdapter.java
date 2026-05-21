package com.example.clouddisk;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private List<CloudFile> fileList;
    private boolean isSelectionMode = false;
    private Set<CloudFile> selectedFiles = new HashSet<>();

    // --- 新增：定义点击回调接口 ---
    public interface OnItemClickListener {
        void onItemClick(CloudFile file);
        void onSelectionModeChanged(boolean enabled);
    }

    private OnItemClickListener listener;

    // --- 新增：暴露给 Activity 设置监听的方法 ---
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public FileAdapter(List<CloudFile> fileList) {
        this.fileList = fileList;
    }

    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) selectedFiles.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionModeChanged(enabled);
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public Set<CloudFile> getSelectedFiles() {
        return selectedFiles;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CloudFile file = fileList.get(position);
        holder.tvName.setText(file.getName());
        holder.tvSize.setText(file.getSizeText());

        if (file.isDirectory()) {
            holder.ivIcon.setImageResource(R.drawable.ic_file_folder);
            holder.checkBox.setVisibility(View.GONE);
        } else {
            String name = file.getName().toLowerCase();
            if (name.matches(".*\\.(jpg|jpeg|png|gif|webp)$")) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_pic);
            } else if (name.matches(".*\\.(mp4|mkv|mov|avi)$")) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_video);
            } else if (name.matches(".*\\.(mp3|wav|flac|aac|m4a)$")) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_audio);
            } else if (name.matches(".*\\.(doc|docx|pdf|xls|xlsx|ppt|pptx|txt)$")) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_docx);
            } else {
                // 默认图标
                holder.ivIcon.setImageResource(R.drawable.ic_file_unknown);
            }
            holder.checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(selectedFiles.contains(file));
        }

        // --- 设置点击事件 ---
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode && !file.isDirectory()) {
                if (selectedFiles.contains(file)) {
                    selectedFiles.remove(file);
                } else {
                    selectedFiles.add(file);
                }
                notifyItemChanged(position);
            } else if (listener != null) {
                listener.onItemClick(file);
            }
        });

        // 长按进入多选模式
        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode && !file.isDirectory()) {
                setSelectionMode(true);
                selectedFiles.add(file);
                notifyItemChanged(position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvSize;
        ImageView ivIcon;
        CheckBox checkBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_file_name);
            tvSize = itemView.findViewById(R.id.tv_file_size);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            checkBox = itemView.findViewById(R.id.checkbox);
        }
    }
}
