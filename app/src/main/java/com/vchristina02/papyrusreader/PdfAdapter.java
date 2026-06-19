package com.vchristina02.papyrusreader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.Objects;

public class PdfAdapter extends ListAdapter<PdfContent, PdfAdapter.ViewHolder> {

    private final Context context;
    private OnPdfClickListener onPdfClickListener;

    public PdfAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pdf, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PdfContent currentItem = getItem(position);
        holder.textViewPdfName.setText(currentItem.title);
        holder.seekBar.setProgress(currentItem.progress);
        holder.seekBar.setClickable(false);
        holder.seekBar.setEnabled(false);

        File imageFile = new File(currentItem.imagePath != null ? currentItem.imagePath : "");

        if (imageFile.exists()) {
            // Se a imagem da capa (criada pela MainActivity) existe, carrega com Glide.
            Glide.with(context)
                    .load(imageFile)
                    .placeholder(R.drawable.ic_pdf_placeholder) // Imagem temporária durante o carregamento
                    .error(R.drawable.ic_error_placeholder)       // Imagem se o Glide falhar ao carregar o arquivo
                    .into(holder.imageViewPdf);
        } else {
            // Se o arquivo não existe, mostra o placeholder padrão.
            holder.imageViewPdf.setImageResource(R.drawable.ic_no_pdf_cover);
        }

        holder.itemView.setOnClickListener(view -> {
            if (onPdfClickListener != null) {
                onPdfClickListener.onPdfClick(holder.getAdapterPosition());
            }
        });
    }

    public PdfContent getPdfContentAt(int position) {
        return getItem(position);
    }

    public interface OnPdfClickListener {
        void onPdfClick(int position);
    }

    public void setOnPdfClickListener(OnPdfClickListener listener) {
        this.onPdfClickListener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewPdfName;
        ImageView imageViewPdf;
        SeekBar seekBar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewPdfName = itemView.findViewById(R.id.textViewPdfName);
            imageViewPdf = itemView.findViewById(R.id.imageViewPdf);
            seekBar = itemView.findViewById(R.id.seekBarMain);
        }
    }

    private static final DiffUtil.ItemCallback<PdfContent> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull PdfContent oldItem, @NonNull PdfContent newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull PdfContent oldItem, @NonNull PdfContent newItem) {
                    return oldItem.progress == newItem.progress &&
                            Objects.equals(oldItem.title, newItem.title) &&
                            Objects.equals(oldItem.imagePath, newItem.imagePath);
                }
            };
}