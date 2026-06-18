package com.vchristina02.papyrusreader;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

public class MainActivity extends AppCompatActivity {
    private List<String> pdfNames;
    private PdfAdapter pdfAdapter;
    private List<Integer> pdfScrollPosition;
    private List<String> pdfImagePaths;
    private List<Integer> pdfProgress;
    private com.vchristina02.papyrusreader.AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RecyclerView recyclerViewPdf;
    private LinearLayout loadingLayout;
    private TextView textViewEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        setContentView(R.layout.activity_main);

        initializeDatabase();
        initializeViews();
        loadPdfContentsFromDatabase();
        setButtonClickListeners();
        setupItemTouchHelper();
    }

    private void initializeDatabase() {
        db = Room.databaseBuilder(getApplicationContext(),
                com.vchristina02.papyrusreader.AppDatabase.class, "pdf-content-database").fallbackToDestructiveMigration().build();
    }

    private void initializeViews() {
        pdfNames = new ArrayList<>();
        pdfImagePaths = new ArrayList<>();
        pdfScrollPosition = new ArrayList<>();
        pdfProgress = new ArrayList<>();
        pdfAdapter = new PdfAdapter(this, pdfNames, pdfImagePaths, pdfScrollPosition, pdfProgress);
        recyclerViewPdf = findViewById(R.id.recyclerViewPdf);
        recyclerViewPdf.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPdf.setAdapter(pdfAdapter);
        Button buttonOpenPdf = findViewById(R.id.buttonOpenPdf);
        buttonOpenPdf.setOnClickListener(v -> mGetContent.launch("application/pdf"));
        loadingLayout = findViewById(R.id.loadingLayout);
        textViewEmpty = findViewById(R.id.textViewEmpty);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadPdfContentsFromDatabase() {
        executor.execute(() -> {
            List<PdfContent> pdfContentsList = db.pdfContentDao().getAll();

            if (pdfContentsList != null && !pdfContentsList.isEmpty()) {
                for (PdfContent pdfContent : pdfContentsList) {
                    pdfNames.add(pdfContent.title);
                    pdfImagePaths.add(pdfContent.imagePath);
                    pdfScrollPosition.add(pdfContent.scrollPosition);
                    pdfProgress.add(pdfContent.progress);
                }

                // Atualização da UI na thread principal após o carregamento
                handler.post(() -> {
                    pdfAdapter.notifyDataSetChanged();
                    textViewEmpty.setVisibility(pdfNames.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } else {
                handler.post(() -> textViewEmpty.setVisibility(View.VISIBLE));
            }
        });
    }

    private void setButtonClickListeners() {
        pdfAdapter.setOnPdfClickListener(this::openPdfAndMoveToTop);
    }

    private void openPdfAndMoveToTop(int position) {
        if (position >= 0 && position < pdfNames.size()) {
            String pdfName = pdfNames.get(position);
            int progress = pdfProgress.get(position);

            updateLastTimeOpened(pdfName);

            Intent intent = new Intent(MainActivity.this, com.vchristina02.papyrusreader.Pdf.class);
            intent.putExtra("pdfName", pdfName);
            intent.putExtra("pdfProgress", progress);
            mStartForResult.launch(intent);

            handler.postDelayed(() -> moveItemToTop(position), 1000);
        } else {
            Toast.makeText(MainActivity.this, "Conteúdo do PDF inválido", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateLastTimeOpened(String pdfName) {
        executor.execute(() -> {
            PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
            if (pdfContent != null) {
                pdfContent.lastTimeOpened = System.currentTimeMillis();
                db.pdfContentDao().update(pdfContent);
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void moveItemToTop(int position) {
        if (position >= 0 && position < pdfNames.size()) {
            String itemClicked = pdfNames.remove(position);
            String imagePath = pdfImagePaths.remove(position);
            int scrollPosition = pdfScrollPosition.remove(position);
            int progress = pdfProgress.remove(position);

            pdfNames.add(0, itemClicked);
            pdfImagePaths.add(0, imagePath);
            pdfScrollPosition.add(0, scrollPosition);
            pdfProgress.add(0, progress);

            recyclerViewPdf.scrollToPosition(0);
            pdfAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        String pdfName = data.getStringExtra("pdfName");
                        int progress = data.getIntExtra("pdfProgress", 0);
                        int position = findItemByName(pdfName);
                        pdfProgress.set(position, progress);
                        updateLastTimeOpened(pdfName);
                        pdfAdapter.notifyDataSetChanged();
                    }
                }
            });

    public int findItemByName(String name) {
        for (int i = 0; i < pdfNames.size(); i++) {
            if (pdfNames.get(i).equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int position = viewHolder.getAdapterPosition();
                        String deletedPdfName = pdfNames.get(position);

                        pdfNames.remove(position);
                        pdfImagePaths.remove(position);
                        pdfScrollPosition.remove(position);
                        pdfProgress.remove(position);

                        executor.execute(() -> {
                            PdfContent deletedPdfContent = db.pdfContentDao().getByTitle(deletedPdfName);
                            if (deletedPdfContent != null) {
                                db.pdfContentDao().delete(deletedPdfContent);
                                File imageFile = new File(deletedPdfContent.imagePath);
                                if (imageFile.exists()) {
                                    boolean deleted = imageFile.delete();
                                    Log.d("MainActivity", "File deleted: " + deleted); // Verifica se a exclusão foi bem-sucedida
                                }
                            }
                            handler.post(() -> {
                                textViewEmpty.setVisibility(pdfNames.isEmpty() ? View.VISIBLE : View.GONE);
                                pdfAdapter.notifyItemRemoved(position); // Usar notifyItemRemoved
                            });
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
                        pdfNames.add(0, pdfName);
                        executor.execute(() -> processPdfContent(uri, pdfName));
                    } else {
                        Toast.makeText(MainActivity.this, "Este PDF já foi adicionado", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri != null && "content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    result = cursor.getString(nameIndex);
                }
            }
        }
        return result != null ? result : (uri != null ? uri.getLastPathSegment() : null);
    }

    private void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void processPdfContent(Uri uri, String pdfName) {
        executor.execute(() -> {
            StringBuilder parsedText = new StringBuilder();
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    PdfReader reader = new PdfReader(inputStream);
                    int numberOfPages = reader.getNumberOfPages();
                    ExecutorService executorService = Executors.newFixedThreadPool(4); // Cria um pool de threads
                    List<Future<String>> futures = new ArrayList<>();
                    for (int i = 1; i <= numberOfPages; i++) {
                        final int pageNumber = i;
                        futures.add(executorService.submit(() -> {
                            StringBuilder pageTextBuilder = new StringBuilder();
                            String pageText = PdfTextExtractor.getTextFromPage(reader, pageNumber);
                            String[] lines = pageText.split("\\n");
                            StringBuilder paragraph = new StringBuilder();
                            for (String line : lines) {
                                line = line.trim();
                                if (!line.isEmpty()) {
                                    paragraph.append(line).append(" ");
                                    if (line.endsWith(".") || line.length() < 40) {
                                        pageTextBuilder.append("<p>").append(paragraph).append("</p>");
                                        paragraph.delete(0, paragraph.length());
                                    }
                                }
                            }
                            if (paragraph.length() > 0) {
                                pageTextBuilder.append("<p>").append(paragraph).append("</p>");
                            }
                            return pageTextBuilder.toString();
                        }));
                    }
                    for (Future<String> future : futures) {
                        parsedText.append(future.get());
                    }
                    reader.close();
                    executorService.shutdown();
                } else {
                    Log.e("PdfBox-Android-Sample", "InputStream is null");
                }
            } catch (IOException | InterruptedException | ExecutionException e) {
                Log.e("PdfBox-Android-Sample", "Exception thrown while loading or reading PDF", e);
            }
            // Renderiza a primeira página em um Bitmap
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd != null) {
                    PdfRenderer renderer = new PdfRenderer(pfd);
                    PdfRenderer.Page page = renderer.openPage(0);
                    Bitmap bitmap = Bitmap.createBitmap(212, 300, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    renderer.close();

                    // Salva o Bitmap como uma imagem
                    File imageFile = new File(getFilesDir(), pdfName + ".png");  // Defina imageFile aqui
                    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        Log.d("PdfBox-Android-Sample", "Image saved at: " + imageFile.getAbsolutePath());
                    }
                    // Cria uma nova instância de PdfContent
                    PdfContent pdfContent = new PdfContent();

                    // Salva os itens no banco de dados
                    pdfContent.imagePath = imageFile.getAbsolutePath();
                    pdfContent.title = pdfName;
                    pdfContent.content = parsedText.toString();
                    db.pdfContentDao().insert(pdfContent);

                    // Atualiza a UI na thread principal
                    handler.post(() -> {
                        pdfImagePaths.add(0, imageFile.getAbsolutePath());
                        pdfScrollPosition.add(0, 0); // Defina a posição de rolagem inicial como 0
                        pdfProgress.add(0, 0);
                        pdfAdapter.notifyItemInserted(0); // Usar notifyItemInserted
                        textViewEmpty.setVisibility(pdfNames.isEmpty() ? View.VISIBLE : View.GONE);
                        hideLoading();
                    });
                    // Rolando para o topo da lista
                    recyclerViewPdf.smoothScrollToPosition(0);
                }
            } catch (IOException e) {
                Log.e("PdfBox-Android-Sample", "Exception thrown while rendering PDF", e);
            }
        });
    }

    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
}