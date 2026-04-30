package com.vchristina02.papyrusreader;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PdfContentDao {
    @Query("SELECT * FROM pdfcontent WHERE id = :id")
    PdfContent getById(long id);
    @Query("SELECT * FROM pdfcontent")
    List<PdfContent> getAll();

    @Query("SELECT * FROM pdfcontent WHERE title = :title")
    PdfContent getByTitle(String title);

    @Insert
    void insert(PdfContent pdfContent);

    @Update
    void update(PdfContent pdfContent);

    @Delete
    void delete(PdfContent pdfContent);
}


