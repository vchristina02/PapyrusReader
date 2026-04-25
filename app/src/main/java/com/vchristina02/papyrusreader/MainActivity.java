package com.vchristina02.papyrusreader;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

public class MainActivity extends AppCompatActivity {
    private List<String> pdfNames;
    private PdfAdapter pdfAdapter;
    private List<Uri> pdfUris;
    private final List<String> pdfContents = new ArrayList<>();
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the database
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "pdf-content-database").build();

        pdfNames = new ArrayList<>();
        pdfUris = new ArrayList<>();
        pdfAdapter = new PdfAdapter(this, pdfNames);
        RecyclerView recyclerViewPdf = findViewById(R.id.recyclerViewPdf);
        recyclerViewPdf.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPdf.setAdapter(pdfAdapter);
        Button buttonOpenPdf = findViewById(R.id.buttonOpenPdf);
        buttonOpenPdf.setOnClickListener(v -> mGetContent.launch("application/pdf"));
        pdfAdapter.setOnPdfClickListener(position -> {
            if (position >= 0 && position < pdfContents.size()) {
                String pdfContent = pdfContents.get(position);
                Intent intent = new Intent(MainActivity.this, Pdf.class);
                intent.putExtra("pdfContent", pdfContent);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Conteúdo do PDF inválido", Toast.LENGTH_SHORT).show();
            }
        });

        executor.execute(() -> {

            // Retrieve the titles and contents from the database
            List<PdfContent> pdfContentsList = db.pdfContentDao().getAll();
            for (PdfContent pdfContent : pdfContentsList) {
                pdfNames.add(pdfContent.title);
                pdfContents.add(pdfContent.content);
            }
            handler.post(() -> pdfAdapter.notifyDataSetChanged());
        });
    }

    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    String pdfName = getFileNameFromUri(uri);
                    if (!pdfNames.contains(pdfName)) {
                        pdfUris.add(uri);
                        pdfNames.add(pdfName);
                        StringBuilder parsedText = new StringBuilder();
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(uri);
                            if (inputStream != null) {
                                PdfReader reader = new PdfReader(inputStream);
                                int numberOfPages = reader.getNumberOfPages();
                                for (int i = 1; i <= numberOfPages; i++) {
                                    String pageText = PdfTextExtractor.getTextFromPage(reader, i);
                                    String[] lines = pageText.split("\\n"); // divide o texto em linhas
                                    StringBuilder paragraph = new StringBuilder();
                                    for (String s : lines) {
                                        String line = s.trim(); // remove espaços em branco no início e no fim
                                        if (!line.isEmpty()) {
                                            paragraph.append(line).append(" "); // adiciona um espaço ao final de cada linha
                                            if (line.endsWith(".")) {
                                                // Se a linha termina com um ponto e a primeira letra da próxima linha é maiúscula, consideramos que é o final de um parágrafo
                                                parsedText.append("<p>").append(paragraph).append("</p>");
                                                paragraph = new StringBuilder();
                                            } else if (line.length() < 40) {
                                                // Se a linha tem menos de 40 caracteres, consideramos que é o final de um parágrafo
                                                parsedText.append("<p>").append(paragraph).append("</p>");
                                                paragraph = new StringBuilder();
                                            }
                                        }
                                    }
                                    // Adiciona o último parágrafo
                                    if (paragraph.length() > 0) {
                                        parsedText.append("<p>").append(paragraph).append("</p>");
                                    }
                                }
                                reader.close();
                            } else {
                                Log.e("PdfBox-Android-Sample", "InputStream is null");
                            }
                        } catch (IOException e) {
                            Log.e("PdfBox-Android-Sample", "Exception thrown while loading or reading PDF", e);
                        }
                        pdfContents.add(parsedText.toString());
                        pdfAdapter.notifyDataSetChanged();

                        executor.execute(new Runnable() {
                            public void run() {
                                // Save the title and content to the database
                                PdfContent pdfContent = new PdfContent();
                                pdfContent.title = pdfName;
                                pdfContent.content = parsedText.toString();
                                db.pdfContentDao().insert(pdfContent);
                            }
                        });

                        Intent intent = new Intent(MainActivity.this, Pdf.class);
                        intent.putExtra("pdfContent", parsedText.toString());
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, "Este PDF já foi adicionado", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    result = cursor.getString(nameIndex);
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }

        if (result != null) {
            result = result.substring(0, result.lastIndexOf("."));
        }

        return result;
    }

    protected void onStop() {
        super.onStop();
        SharedPreferences sharedPreferences = getSharedPreferences("pdf_list", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("pdf_names", TextUtils.join(",", pdfNames));
        editor.putString("pdf_contents", TextUtils.join(";", pdfContents));
        editor.apply();
    }
}