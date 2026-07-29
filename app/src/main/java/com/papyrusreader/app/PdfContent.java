package com.papyrusreader.app;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entidade que representa a tabela "PdfContent" no banco de dados SQLite.
 * Cada instância desta classe corresponde a um livro/PDF importado pelo usuário,
 * guardando não apenas o conteúdo, mas todo o estado de leitura e metadados.
 */
@Entity
public class PdfContent {

    /** Chave primária única gerada automaticamente pelo Room para cada novo livro. */
    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Título original do arquivo PDF (usado como identificador de busca na interface). */
    @ColumnInfo(name = "title")
    public String title;

    /** Caminho absoluto no armazenamento interno onde a miniatura (capa) foi salva em formato PNG. */
    @ColumnInfo(name = "image_path")
    public String imagePath;

    /** Todo o conteúdo textual do PDF, já processado, formatado em HTML e separado por parágrafos. */
    @ColumnInfo(name = "content")
    public String content;

    /** * Posição absoluta (em píxeis) do eixo Y na WebView.
     * Usada para carregar a página exatamente onde o usuário parou de ler.
     */
    @ColumnInfo(name = "scroll_position")
    public int scrollPosition;

    /** * Progresso relativo da leitura (de 0 a 100).
     * Usado exclusivamente para preencher a SeekBar na interface gráfica.
     */
    @ColumnInfo(name = "progress")
    public int progress;

    /** * Timestamp (em milissegundos) da última vez que o arquivo foi aberto ou importado.
     * Crucial para a ordenação LIFO (Last-In, First-Out) na tela inicial.
     */
    @ColumnInfo(name = "last_time_opened")
    public Long lastTimeOpened;
}