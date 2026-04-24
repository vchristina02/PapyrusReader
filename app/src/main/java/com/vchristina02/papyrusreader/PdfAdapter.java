package com.vchristina02.papyrusreader;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PdfAdapter extends RecyclerView.Adapter<PdfAdapter.ViewHolder> {

    private final Context context;
    private final List<String> pdfNames;
    private OnPdfClickListener onPdfClickListener;

    public PdfAdapter(Context context, List<String> pdfNames) {
        this.context = context;
        this.pdfNames = pdfNames;
    }

    public interface OnPdfClickListener {
        void onPdfClick(int position);
    }

    public void setOnPdfClickListener(OnPdfClickListener listener) {
        this.onPdfClickListener = listener;
    }

    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pdf, parent, false);
        return new ViewHolder(view);
    }

    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Verifica se a posição está dentro dos limites da lista pdfNames
        if (position >= 0 && position < pdfNames.size()) {
            // Obtém o nome do PDF
            String pdfName = pdfNames.get(position);
            holder.textViewPdfName.setText(pdfName);

            // Define um ouvinte de clique para o item da lista
            holder.itemView.setOnClickListener(view -> {
                if (onPdfClickListener != null) {
                    onPdfClickListener.onPdfClick(holder.getAdapterPosition());
                }
            });
        }
    }

    public int getItemCount() {
        return pdfNames.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewPdfName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewPdfName = itemView.findViewById(R.id.textViewPdfName);
        }
    }
}
