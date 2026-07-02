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

/**
 * Activity principal do Papyrus Reader.
 * Responsável por gerenciar a biblioteca de PDFs, exibir a lista de leitura,
 * lidar com a importação de novos arquivos e orquestrar a exclusão de itens (Swipe to Delete).
 */
public class MainActivity extends AppCompatActivity {

    /** Banco de dados local Room para armazenar os metadados e conteúdo extraído dos PDFs. */
    private AppDatabase db;

    /** Lista visual que exibe os cards dos livros na tela. */
    private RecyclerView recyclerViewPdf;

    /** Tela de carregamento exibida durante a extração de um novo PDF. */
    private LinearLayout loadingLayout;

    /** Mensagem exibida quando a biblioteca está vazia (Empty State). */
    private TextView textViewEmpty;

    /**
     * Executor para processamento em background (Multithreading).
     * Evita que o aplicativo trave (ANR) enquanto faz operações pesadas no banco de dados
     * ou extrai o texto de PDFs grandes.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Handler vinculado à Main Thread (UI Thread).
     * Usado para atualizar a interface gráfica (esconder loading, atualizar listas)
     * após as tarefas de background serem concluídas.
     */
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Adapter responsável por conectar os dados do banco à RecyclerView. */
    private PdfAdapter pdfAdapter;

    /** Variáveis para controle do recurso "Desfazer" na exclusão de livros. */
    private PdfContent recentlyDeletedItem;
    private int recentlyDeletedItemPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Orquestração inicial da tela
        initializeDatabase();
        initializeViews();
        loadPdfContentsFromDatabase();
        setButtonClickListeners();
        setupItemTouchHelper();
    }

    /**
     * Recarrega os dados toda vez que o usuário volta para a tela inicial.
     * Garante que o último livro lido suba para o topo da lista (ordenação LIFO).
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadPdfContentsFromDatabase();
    }

    /**
     * Inicializa a conexão com o banco de dados Room.
     * Utiliza fallbackToDestructiveMigration para evitar crashes caso o esquema mude
     * durante o desenvolvimento (ex: adição de novas colunas).
     */
    private void initializeDatabase() {
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "pdf-content-database").fallbackToDestructiveMigration(true).build();
    }

    /**
     * Vincula os componentes visuais do XML (RecyclerView, Botões, Loading) às variáveis
     * e inicializa o Adapter da lista.
     */
    private void initializeViews() {
        pdfAdapter = new PdfAdapter(this);

        recyclerViewPdf = findViewById(R.id.recyclerViewPdf);
        recyclerViewPdf.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPdf.setAdapter(pdfAdapter);

        // Configura o botão para acionar o seletor nativo de arquivos do Android
        Button buttonOpenPdf = findViewById(R.id.buttonOpenPdf);
        buttonOpenPdf.setOnClickListener(v -> mGetContent.launch("application/pdf"));

        loadingLayout = findViewById(R.id.loadingLayout);
        textViewEmpty = findViewById(R.id.textViewEmpty);
    }

    /**
     * Busca todos os PDFs salvos no banco de dados rodando em background (Executor).
     * Em seguida, atualiza a interface gráfica na Thread principal (Handler).
     */
    private void loadPdfContentsFromDatabase() {
        executor.execute(() -> {
            List<PdfContent> loadedItems = db.pdfContentDao().getAll();
            handler.post(() -> {
                // Controla a exibição do estado vazio (Empty State)
                textViewEmpty.setVisibility(loadedItems.isEmpty() ? View.VISIBLE : View.GONE);

                // Envia a lista atualizada para o Adapter calcular as diferenças (DiffUtil)
                pdfAdapter.submitList(loadedItems, () -> {
                    // Garante que a lista role para o topo após a atualização
                    recyclerViewPdf.scrollToPosition(0);
                });
            });
        });
    }

    /**
     * Configura o ouvinte de cliques nos cards dos livros gerados pelo Adapter.
     */
    private void setButtonClickListeners() {
        pdfAdapter.setOnPdfClickListener(this::openPdfAndMoveToTop);
    }

    /**
     * Acionado quando o usuário clica em um livro na biblioteca.
     * Atualiza o horário de abertura (para ordenação) e abre a tela de leitura.
     *
     * @param position Posição do item clicado na RecyclerView.
     */
    private void openPdfAndMoveToTop(int position) {
        PdfContent pdfContent = pdfAdapter.getPdfContentAt(position);
        if (pdfContent == null) {
            Toast.makeText(MainActivity.this, "Erro ao abrir o PDF.", Toast.LENGTH_SHORT).show();
            return;
        }

        updateLastTimeOpened(pdfContent.title);

        // Prepara a intenção para abrir a tela de leitura (Pdf.java) com os dados necessários
        Intent intent = new Intent(MainActivity.this, Pdf.class);
        intent.putExtra("pdfName", pdfContent.title);
        intent.putExtra("pdfProgress", pdfContent.progress);
        mStartForResult.launch(intent);
    }

    /**
     * Atualiza a coluna lastTimeOpened no banco de dados para o momento atual.
     * Utilizado para garantir que os livros recentes fiquem no topo (LIFO).
     */
    private void updateLastTimeOpened(String pdfName) {
        executor.execute(() -> {
            PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
            if (pdfContent != null) {
                pdfContent.lastTimeOpened = System.currentTimeMillis();
                db.pdfContentDao().update(pdfContent);
            }
        });
    }

    /**
     * Contrato para receber o resultado da tela de leitura (Pdf.java) quando ela for fechada.
     * Atualiza visualmente a barra de progresso do livro na tela inicial sem piscar a lista.
     */
    ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String pdfName = data.getStringExtra("pdfName");
                    int progress = data.getIntExtra("pdfProgress", 0);

                    int position = findItemByName(pdfName);

                    if (position != -1) {
                        PdfContent item = pdfAdapter.getPdfContentAt(position);
                        if (item != null) {
                            item.progress = progress;
                            // Notifica o adapter para redesenhar APENAS aquele item modificado
                            pdfAdapter.notifyItemChanged(position);
                        }
                    }
                }
            });

    /**
     * Busca a posição de um livro específico na lista atual do Adapter.
     */
    public int findItemByName(String name) {
        List<PdfContent> currentList = pdfAdapter.getCurrentList();
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).title.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Configura o gesto de deslizar para excluir (Swipe to Delete).
     * Inclui uma camada de segurança com um Snackbar que permite "Desfazer" a ação.
     */
    private void setupItemTouchHelper() {
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                        return false; // Não suportamos arrastar para reordenar
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        // RESOLUÇÃO DO WARNING: Usando getBindingAdapterPosition()
                        int position = viewHolder.getBindingAdapterPosition();

                        if (position == RecyclerView.NO_POSITION) return;

                        // Salva o item temporariamente para caso o usuário queira desfazer
                        recentlyDeletedItem = pdfAdapter.getPdfContentAt(position);
                        recentlyDeletedItemPosition = position;

                        // Remove da interface visual
                        List<PdfContent> currentList = new ArrayList<>(pdfAdapter.getCurrentList());
                        currentList.remove(recentlyDeletedItem);
                        pdfAdapter.submitList(currentList);

                        // Exibe a notificação na base da tela
                        Snackbar snackbar = Snackbar.make(recyclerViewPdf, "Livro excluído", Snackbar.LENGTH_LONG);
                        snackbar.setBackgroundTint(ContextCompat.getColor(MainActivity.this, R.color.snackbar_background_color));
                        snackbar.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.snackbar_text_color));
                        snackbar.setActionTextColor(ContextCompat.getColor(MainActivity.this, R.color.snackbar_action_color));

                        // Configura a ação de Desfazer: recoloca o item na lista
                        snackbar.setAction("DESFAZER", view -> {
                            List<PdfContent> undoList = new ArrayList<>(pdfAdapter.getCurrentList());
                            undoList.add(recentlyDeletedItemPosition, recentlyDeletedItem);
                            pdfAdapter.submitList(undoList);
                        });

                        // Se a notificação sumir naturalmente, exclui permanentemente do Banco e do Disco
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
                                            Log.d("MainActivity", "Arquivo da capa deletado: " + deleted);
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

    /**
     * Contrato para abrir o seletor nativo de arquivos do sistema (SAF).
     * Filtra apenas por arquivos "application/pdf".
     */
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    String pdfName = getFileNameFromUri(uri);

                    // Verifica duplicidade antes de importar
                    if (findItemByName(pdfName) == -1) {
                        showLoading();
                        processPdfContent(uri, pdfName);
                    } else {
                        Toast.makeText(MainActivity.this, "Este PDF já foi adicionado", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    /**
     * Resolve a URI retornada pelo sistema para extrair o nome real do arquivo.
     */
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

    /**
     * Orquestra o processamento completo de um novo PDF importado.
     * Extrai texto, gera capa, salva no banco e atualiza a UI. Tudo em background.
     */
    private void processPdfContent(Uri uri, String pdfName) {
        executor.execute(() -> {
            // Passo 1: Extrai e formata o texto para HTML
            String formattedHtml = extractAndFormatText(uri);

            // Passo 2: Renderiza a primeira página como imagem (Capa/Miniatura)
            File imageFile = renderFirstPageAsImage(uri, pdfName);

            // Passo 3: Salva os dados processados no Room Database
            if (formattedHtml != null) {
                saveNewPdfToDatabase(pdfName, formattedHtml, imageFile);
            }

            // Passo 4: Retorna para a Main Thread para fechar o loading e recarregar a lista
            handler.post(() -> {
                hideLoading();
                loadPdfContentsFromDatabase();
            });
        });
    }

    /**
     * Utiliza o iTextG para varrer as páginas do PDF e extrair o texto.
     * Implementa FixedThreadPool para dividir a carga de leitura entre várias threads,
     * otimizando a performance em arquivos grandes.
     */
    private String extractAndFormatText(Uri uri) {
        StringBuilder parsedText = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;

            PdfReader reader = new PdfReader(inputStream);
            int numberOfPages = reader.getNumberOfPages();

            // Cria um pool de 4 threads simultâneas para acelerar a extração
            ExecutorService textExtractorExecutor = Executors.newFixedThreadPool(4);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 1; i <= numberOfPages; i++) {
                final int pageNumber = i;

                // Submete cada página para ser processada pelas threads do pool
                futures.add(textExtractorExecutor.submit(() -> {
                    StringBuilder pageTextBuilder = new StringBuilder();
                    String pageText = PdfTextExtractor.getTextFromPage(reader, pageNumber);
                    String[] lines = pageText.split("\\n");
                    StringBuilder paragraph = new StringBuilder();

                    // Lógica de heurística para agrupar quebras de linha em parágrafos HTML (<p>)
                    for (String line : lines) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            paragraph.append(line).append(" ");
                            if (line.endsWith(".") || line.length() < 40) {
                                pageTextBuilder.append("<p>").append(paragraph).append("</p>");
                                paragraph.setLength(0); // Mais eficiente para limpar o buffer
                            }
                        }
                    }
                    if (paragraph.length() > 0) {
                        pageTextBuilder.append("<p>").append(paragraph).append("</p>");
                    }
                    return pageTextBuilder.toString();
                }));
            }

            // Aguarda a conclusão de todas as threads e junta os textos na ordem correta
            for (Future<String> future : futures) {
                parsedText.append(future.get());
            }

            reader.close();
            textExtractorExecutor.shutdown();
            return parsedText.toString();

        } catch (IOException | ExecutionException | InterruptedException e) {
            Log.e("MainActivity", "Erro ao extrair texto do PDF", e);
            return null;
        }
    }

    /**
     * Utiliza a API nativa PdfRenderer para transformar a primeira página do arquivo
     * em um Bitmap e salvá-lo no armazenamento interno do aplicativo para ser usado como miniatura.
     */
    private File renderFirstPageAsImage(Uri uri, String pdfName) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd != null) {
                PdfRenderer renderer = new PdfRenderer(pfd);
                PdfRenderer.Page page = renderer.openPage(0);

                Bitmap bitmap = Bitmap.createBitmap(212, 300, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                // Salva a imagem PNG no diretório interno seguro do aplicativo
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

    /**
     * Persiste os dados extraídos do novo PDF (texto bruto e caminho da imagem) no banco local.
     */
    private void saveNewPdfToDatabase(String pdfName, String formattedHtml, File imageFile) {
        PdfContent pdfContent = new PdfContent();
        pdfContent.title = pdfName;
        pdfContent.content = formattedHtml;
        pdfContent.imagePath = (imageFile != null) ? imageFile.getAbsolutePath() : "";
        pdfContent.lastTimeOpened = System.currentTimeMillis();

        db.pdfContentDao().insert(pdfContent);
    }
}