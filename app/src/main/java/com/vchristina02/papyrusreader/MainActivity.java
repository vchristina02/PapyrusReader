package com.vchristina02.papyrusreader;

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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.android.material.snackbar.Snackbar;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

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

    public class MainActivity extends AppCompatActivity {
        private AppDatabase db;
        private RecyclerView recyclerViewPdf;
        private LinearLayout loadingLayout;
        private TextView textViewEmpty;

        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Handler handler = new Handler(Looper.getMainLooper());

        private PdfAdapter pdfAdapter;

        private PdfContent recentlyDeletedItem;
        private int recentlyDeletedItemPosition;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // hideSystemUI();
            setContentView(R.layout.activity_main);

            initializeDatabase();
            initializeViews();
            loadPdfContentsFromDatabase();
            setButtonClickListeners();
            setupItemTouchHelper();
        }

        @Override
        protected void onResume() {
            super.onResume();
            // Sempre que a tela se torna visível, recarrega os dados para garantir a ordem correta.
            loadPdfContentsFromDatabase();
        }

        private void initializeDatabase() {
            db = Room.databaseBuilder(getApplicationContext(),
                    AppDatabase.class, "pdf-content-database").fallbackToDestructiveMigration().build();
        }

        private void initializeViews() {
            pdfAdapter = new PdfAdapter(this);

            recyclerViewPdf = findViewById(R.id.recyclerViewPdf);
            recyclerViewPdf.setLayoutManager(new LinearLayoutManager(this));
            recyclerViewPdf.setAdapter(pdfAdapter);

            Button buttonOpenPdf = findViewById(R.id.buttonOpenPdf);
            buttonOpenPdf.setOnClickListener(v -> mGetContent.launch("application/pdf"));

            loadingLayout = findViewById(R.id.loadingLayout);
            textViewEmpty = findViewById(R.id.textViewEmpty);
        }

        private void loadPdfContentsFromDatabase() {
            executor.execute(() -> {
                List<PdfContent> loadedItems = db.pdfContentDao().getAll();
                handler.post(() -> {
                    textViewEmpty.setVisibility(loadedItems.isEmpty() ? View.VISIBLE : View.GONE);
                    pdfAdapter.submitList(loadedItems, () -> {
                        // Este código só roda depois que a lista for atualizada na tela
                        recyclerViewPdf.scrollToPosition(0);
                    });
                });
            });
        }

        private void setButtonClickListeners() {
            pdfAdapter.setOnPdfClickListener(this::openPdfAndMoveToTop);
        }

        private void openPdfAndMoveToTop(int position) {
            PdfContent pdfContent = pdfAdapter.getPdfContentAt(position);
            if (pdfContent == null) {
                Toast.makeText(MainActivity.this, "Erro ao abrir o PDF.", Toast.LENGTH_SHORT).show();
                return;
            }

            updateLastTimeOpened(pdfContent.title);

            Intent intent = new Intent(MainActivity.this, Pdf.class);
            intent.putExtra("pdfName", pdfContent.title);
            intent.putExtra("pdfProgress", pdfContent.progress);
            mStartForResult.launch(intent);
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

        ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String pdfName = data.getStringExtra("pdfName");
                        int progress = data.getIntExtra("pdfProgress", 0);

                        // 1. Encontra a posição do item na lista atual do adapter
                        int position = findItemByName(pdfName);

                        if (position != -1) {
                            // 2. Pega o item diretamente do adapter e atualiza seu progresso
                            PdfContent item = pdfAdapter.getPdfContentAt(position);
                            if (item != null) {
                                item.progress = progress;
                                // 3. Notifica o adapter para redesenhar APENAS aquele item
                                // É muito mais eficiente que recarregar a lista inteira.
                                pdfAdapter.notifyItemChanged(position);
                            }
                        }
                    }
                });

        public int findItemByName(String name) {
            List<PdfContent> currentList = pdfAdapter.getCurrentList();
            for (int i = 0; i < currentList.size(); i++) {
                if (currentList.get(i).title.equals(name)) {
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
                            recentlyDeletedItem = pdfAdapter.getPdfContentAt(position);
                            recentlyDeletedItemPosition = position;

                            List<PdfContent> currentList = new ArrayList<>(pdfAdapter.getCurrentList());
                            currentList.remove(recentlyDeletedItem);
                            pdfAdapter.submitList(currentList);

                            Snackbar snackbar = Snackbar.make(recyclerViewPdf, "Livro excluído", Snackbar.LENGTH_LONG);

                            snackbar.setBackgroundTint(ContextCompat.getColor(MainActivity.this, R.color.snackbar_background_color));
                            snackbar.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.snackbar_text_color));
                            snackbar.setActionTextColor(ContextCompat.getColor(MainActivity.this, R.color.snackbar_action_color));

                            snackbar.setAction("DESFAZER", view -> {
                                List<PdfContent> undoList = new ArrayList<>(pdfAdapter.getCurrentList());
                                undoList.add(recentlyDeletedItemPosition, recentlyDeletedItem);
                                pdfAdapter.submitList(undoList);
                            });

                            snackbar.addCallback(new Snackbar.Callback() {
                                @Override
                                public void onDismissed(Snackbar transientBottomBar, int event) {
                                    super.onDismissed(transientBottomBar, event);
                                    if (event != DISMISS_EVENT_ACTION) {
                                        executor.execute(() -> {
                                            db.pdfContentDao().delete(recentlyDeletedItem);
                                            File imageFile = new File(recentlyDeletedItem.imagePath);
                                            if (imageFile.exists()) {
                                                boolean deleted = imageFile.delete();
                                                Log.d("MainActivity", "Arquivo da capa deletado com sucesso: " + deleted);
                                            }
                                        });
                                    }
                                }
                            });

                            snackbar.show();
                        }
                    };

            new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerViewPdf);
        }

        private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        String pdfName = getFileNameFromUri(uri);
                        if (findItemByName(pdfName) == -1) {
                            showLoading();
                            processPdfContent(uri, pdfName);
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
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex);
                        }
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

        // O metodo principal que agora apenas organiza as tarefas
        private void processPdfContent(Uri uri, String pdfName) {
            executor.execute(() -> {
                // Passo 1: Extrai o texto.
                String formattedHtml = extractAndFormatText(uri);

                // Passo 2: Renderiza a imagem da capa.
                File imageFile = renderFirstPageAsImage(uri, pdfName);

                // Passo 3: Salva ambos no banco de dados.
                if (formattedHtml != null) {
                    saveNewPdfToDatabase(pdfName, formattedHtml, imageFile);
                }

                // Passo 4: Atualiza a interface do usuário.
                handler.post(() -> {
                    hideLoading();
                    loadPdfContentsFromDatabase();
                });
            });
        }

        private String extractAndFormatText(Uri uri) {
            StringBuilder parsedText = new StringBuilder();
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream == null) return null;

                PdfReader reader = new PdfReader(inputStream);
                int numberOfPages = reader.getNumberOfPages();
                ExecutorService textExtractorExecutor = Executors.newFixedThreadPool(4);
                List<Future<String>> futures = new ArrayList<>();

                for (int i = 1; i <= numberOfPages; i++) {
                    final int pageNumber = i;
                    futures.add(textExtractorExecutor.submit(() -> {
                        // SUA LÓGICA DE CRIAÇÃO DE PARÁGRAFOS (INTACTA)
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
                                    paragraph.setLength(0); // Mais eficiente que .delete()
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
                textExtractorExecutor.shutdown();
                return parsedText.toString();

            } catch (IOException | ExecutionException | InterruptedException e) {
                Log.e("MainActivity", "Erro ao extrair texto do PDF", e);
                return null; // Retorna nulo para indicar que a extração falhou.
            }
        }

        private File renderFirstPageAsImage(Uri uri, String pdfName) {
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd != null) {
                    PdfRenderer renderer = new PdfRenderer(pfd);
                    PdfRenderer.Page page = renderer.openPage(0);

                    Bitmap bitmap = Bitmap.createBitmap(212, 300, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    File imageFile = new File(getFilesDir(), pdfName + ".png");
                    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }

                    page.close();
                    renderer.close();
                    return imageFile;
                }
            } catch (IOException e) {
                Log.e("MainActivity", "Erro ao gerar imagem da capa do PDF", e);
            }
            return null;
        }

        private void saveNewPdfToDatabase(String pdfName, String formattedHtml, File imageFile) {
            PdfContent pdfContent = new PdfContent();
            pdfContent.title = pdfName;
            pdfContent.content = formattedHtml;
            // Salva o caminho da imagem que foi gerada (ou vazio se a geração falhou).
            pdfContent.imagePath = (imageFile != null) ? imageFile.getAbsolutePath() : "";
            pdfContent.lastTimeOpened = System.currentTimeMillis();
            db.pdfContentDao().insert(pdfContent);
        }
}