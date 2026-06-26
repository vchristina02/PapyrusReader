package com.vchristina02.papyrusreader;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * Classe principal do Banco de Dados local (Room).
 * Serve como o ponto de acesso principal para a conexão com o SQLite mantido pelo Android.
 * * A anotação @Database define as entidades (tabelas) que pertencem a este banco
 * e a versão atual do esquema estrutural.
 */
@Database(entities = {PdfContent.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    /**
     * Fornece o DAO (Data Access Object) para a tabela de PDFs.
     * É através deste método que a MainActivity e a Pdf activity conseguem
     * chamar os comandos de Insert, Update, Delete e Select.
     *
     * @return A interface de acesso aos dados do PdfContent.
     */
    public abstract PdfContentDao pdfContentDao();
}