package com.vchristina02.papyrusreader;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {PdfContent.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract PdfContentDao pdfContentDao();
}