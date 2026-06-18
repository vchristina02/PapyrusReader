package com.vchristina02.papyrusreader;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private List<String> pdfNames;
    private PdfAdapter pdfAdapter;
    private List<String> pdfImagePaths = new ArrayList<>();
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProgressBar progressBar;
    private RecyclerView recyclerViewPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeDatabase();
        initializeViews();
        loadPdfContentsFromDatabase();
        setButtonClickListeners();
        setupItemTouchHelper();
    }

    private void initializeDatabase() {
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "pdf-content-database").build();
    }

    private void initializeViews() {
        pdfNames = new ArrayList<>();
        pdfImagePaths = new ArrayList<>();
        pdfAdapter = new PdfAdapter(this, pdfNames, pdfImagePaths);
        recyclerViewPdf = findViewById(R.id.recyclerViewPdf); // Corrigido para atribuir à variável de classe
        recyclerViewPdf.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPdf.setAdapter(pdfAdapter);
        Button buttonOpenPdf = findViewById(R.id.buttonOpenPdf);
        buttonOpenPdf.setOnClickListener(v -> mGetContent.launch("application/pdf"));
        progressBar = findViewById(R.id.progressBar);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadPdfContentsFromDatabase() {
        executor.execute(() -> {
            List<PdfContent> pdfContentsList = db.pdfContentDao().getAll();
            for (PdfContent pdfContent : pdfContentsList) {
                pdfNames.add(pdfContent.title);
                pdfImagePaths.add(pdfContent.imagePath);
            }
            // Invertendo a ordem da lista pdfNames
            Collections.reverse(pdfNames);
            Collections.reverse(pdfImagePaths);

            handler.post(() -> pdfAdapter.notifyDataSetChanged());
        });
    }

    private void setButtonClickListeners() {
        pdfAdapter.setOnPdfClickListener(position -> {
            if (position >= 0 && position < pdfNames.size()) {
                String pdfName = pdfNames.get(position);
                Intent intent = new Intent(MainActivity.this, Pdf.class);
                intent.putExtra("pdfName", pdfName);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Conteúdo do PDF inválido", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        // Remove o item deslizado
                        int position = viewHolder.getAdapterPosition();
                        String deletedPdfName = pdfNames.get(position);
                        pdfNames.remove(position);
                        pdfImagePaths.remove(position);
                        pdfAdapter.notifyItemRemoved(position);
                        // Realiza a exclusão no banco de dados e no sistema de arquivos
                        executor.execute(() -> {
                            PdfContent deletedPdfContent = db.pdfContentDao().getByTitle(deletedPdfName);
                            if (deletedPdfContent != null) {
                                db.pdfContentDao().delete(deletedPdfContent);
                                File imageFile = new File(deletedPdfContent.imagePath);
                                if (imageFile.exists()) {
                                    boolean isDeleted = imageFile.delete();
                                    if (!isDeleted) {
                                        Log.e("PapyrusApp", "Falha ao deletar a capa física: " + imageFile.getAbsolutePath());
                                    } else {
                                        Log.d("PapyrusApp", "Capa deletada com sucesso!");
                                    }
                                }
                            }
                        });
                    }
                };

        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerViewPdf);
    }

    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    String pdfName = getFileNameFromUri(uri);
                    if (!pdfNames.contains(pdfName)) {
                        showLoading();
                        pdfNames.add(0,pdfName);
                        executor.execute(() -> processPdfContent(uri, pdfName));
                    } else {
                        Toast.makeText(MainActivity.this, "Este PDF já foi adicionado", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri != null && uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    result = cursor.getString(nameIndex);
                }
            }
        }
        if (result == null) {
            assert uri != null;
            result = uri.getLastPathSegment();
        }
        if (result != null && result.contains(".")) {
            result = result.substring(0, result.lastIndexOf("."));
        }
        return result;
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    private void processPdfContent(Uri uri, String pdfName) {
        executor.execute(() -> {
            StringBuilder parsedText = new StringBuilder();
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    PdfReader reader = new PdfReader(inputStream);
                    int numberOfPages = reader.getNumberOfPages();
                    for (int i = 1; i <= numberOfPages; i++) {
                        String pageText = PdfTextExtractor.getTextFromPage(reader, i);
                        String[] lines = pageText.split("\\n");
                        StringBuilder paragraph = new StringBuilder();
                        for (String line : lines) {
                            line = line.trim();
                            if (!line.isEmpty()) {
                                paragraph.append(line).append(" ");
                                if (line.endsWith(".")) {
                                    parsedText.append("<p>").append(paragraph).append("</p>");
                                    paragraph = new StringBuilder();
                                } else if (line.length() < 40) {
                                    parsedText.append("<p>").append(paragraph).append("</p>");
                                    paragraph = new StringBuilder();
                                }
                            }
                        }
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
            // Renderiza a primeira página em um Bitmap
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd != null) {
                    PdfRenderer renderer = new PdfRenderer(pfd);
                    PdfRenderer.Page page = renderer.openPage(0);
                    Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    renderer.close();

                    // Salva o Bitmap como uma imagem
                    File imageFile = new File(getFilesDir(), pdfName + ".png");
                    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        Log.d("PdfBox-Android-Sample", "Image saved at: " + imageFile.getAbsolutePath());
                    }
                    // Cria uma nova instância de PdfContent
                    PdfContent pdfContent = new PdfContent();

                    // Salva o caminho da imagem no banco de dados
                    pdfContent.imagePath = imageFile.getAbsolutePath();

                    // Salva o título e o conteúdo no banco de dados
                    pdfContent.title = pdfName;
                    pdfContent.content = parsedText.toString();
                    db.pdfContentDao().insert(pdfContent);

                    // Atualiza a UI na thread principal
                    handler.post(() -> {
                        pdfImagePaths.add(0, imageFile.getAbsolutePath());
                        pdfAdapter.notifyItemInserted(0);
                        hideLoading();
                    });
                }
            } catch (IOException e) {
                Log.e("PdfBox-Android-Sample", "Exception thrown while rendering PDF", e);
            }
        });
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
}