package com.papyrusreader.app;

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

/**
 * Adapter responsável por conectar os dados dos PDFs (PdfContent) à interface (RecyclerView).
 * Utiliza o ListAdapter com DiffUtil para calcular atualizações de tela de forma otimizada,
 * redesenhando apenas os itens que realmente sofreram alterações no banco de dados.
 */
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
        // Infla o layout XML de cada item da lista (o "card" do livro)
        View view = LayoutInflater.from(context).inflate(R.layout.item_pdf, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PdfContent currentItem = getItem(position);

        // Preenche os textos e o progresso da barra de leitura
        holder.textViewPdfName.setText(currentItem.title);
        holder.seekBar.setProgress(calculateDisplayProgress(currentItem));

        // Desativa a interação manual na miniatura da SeekBar da tela inicial
        holder.seekBar.setClickable(false);
        holder.seekBar.setEnabled(false);

        // Verifica se a imagem da capa do PDF foi gerada com sucesso
        File imageFile = new File(currentItem.imagePath != null ? currentItem.imagePath : "");

        if (imageFile.exists()) {
            // Carrega a imagem da capa de forma assíncrona usando a biblioteca Glide
            Glide.with(context)
                    .load(imageFile)
                    .placeholder(R.drawable.ic_pdf_placeholder) // Mostra enquanto carrega
                    .error(R.drawable.ic_error_placeholder)     // Mostra se houver falha
                    .into(holder.imageViewPdf);
        } else {
            holder.imageViewPdf.setImageResource(R.drawable.ic_no_pdf_cover);
        }

        // Configura o clique no livro para abrir a tela de leitura
        holder.itemView.setOnClickListener(view -> {
            if (onPdfClickListener != null) {
                // Garante a posição exata do item na lista atual
                int currentPosition = holder.getBindingAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onPdfClickListener.onPdfClick(currentPosition);
                }
            }
        });
    }

    /**
     * Calcula o progresso (0-100) a ser exibido no card, de acordo com o modo de
     * visualização preferido para este livro:
     * - "Modo PDF Original": calculado a partir da página atual / total de páginas.
     * - "Modo Texto" (padrão): usa o progresso de rolagem da WebView já salvo em `progress`.
     */
    private int calculateDisplayProgress(PdfContent pdfContent) {
        if (pdfContent.isPdfModePreferred && pdfContent.pdfTotalPages > 0) {
            float pagesRead = pdfContent.pdfPageNumber + 1;
            return (int) Math.min(100, (100 * pagesRead / pdfContent.pdfTotalPages));
        }
        return pdfContent.progress;
    }

    /**
     * Retorna o objeto PdfContent da posição clicada.
     */
    public PdfContent getPdfContentAt(int position) {
        return getItem(position);
    }

    /**
     * Interface para gerenciar os eventos de clique fora do Adapter.
     */
    public interface OnPdfClickListener {
        void onPdfClick(int position);
    }

    public void setOnPdfClickListener(OnPdfClickListener listener) {
        this.onPdfClickListener = listener;
    }

    /**
     * Classe interna que "segura" as referências dos componentes visuais do XML,
     * evitando chamadas excessivas de findViewById.
     */
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

    /**
     * Algoritmo de comparação do ListAdapter.
     * Ele avalia se a lista sofreu mudanças para animar a entrada/saída de itens.
     */
    private static final DiffUtil.ItemCallback<PdfContent> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull PdfContent oldItem, @NonNull PdfContent newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull PdfContent oldItem, @NonNull PdfContent newItem) {
                    return oldItem.progress == newItem.progress &&
                            oldItem.pdfPageNumber == newItem.pdfPageNumber &&
                            oldItem.pdfTotalPages == newItem.pdfTotalPages &&
                            oldItem.isPdfModePreferred == newItem.isPdfModePreferred &&
                            Objects.equals(oldItem.title, newItem.title) &&
                            Objects.equals(oldItem.imagePath, newItem.imagePath);
                }
            };
}