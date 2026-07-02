package com.vchristina02.papyrusreader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.room.Room;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

/**
 * Activity principal de Leitura (Core do Aplicativo).
 * Exibe o conteúdo do PDF extraído renderizando-o como HTML em uma WebView.
 * Gerencia a barra de progresso, motor de busca de palavras, modo imersivo (tela cheia)
 * e personalização avançada de leitura (cores, tipografia e tamanho da fonte).
 */
public class Pdf extends AppCompatActivity {

    /** Posição inicial de rolagem recuperada do banco de dados para continuar a leitura. */
    private int initialScrollY = 0;

    /** Instância do banco de dados local (Room). */
    private AppDatabase db;

    /** Executor para garantir que consultas ao banco ocorram fora da Thread Principal (evitando travamentos). */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Handler para postar atualizações visuais de volta na Main Thread. */
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String pdfName;
    private WebView webView;
    private SeekBar seekBar;
    private GestureDetector gestureDetector;
    private Toolbar toolbar;

    /** Componentes da barra de pesquisa flutuante. */
    private LinearLayout floatButtonBar;
    private SearchView searchViewFloating;
    private int currentMatchIndex = -1;
    private int totalMatches = 0;
    private TextView itemCountTextView;

    /** Componentes do painel de Configurações de Leitura. */
    private SharedPreferences readingPrefs;
    private LinearLayout readingSettingsPanel;
    private ImageButton readingSettingsButton;
    private View colorWhite, colorBlack, colorSepia;
    private LinearLayout colorOptions;
    private MaterialButton btnDecreaseFont, btnIncreaseFont, btnToggleFontFamily;
    private SwitchMaterial switchFollowSystemTheme;
    private TextView labelFollowSystemTheme, labelPageColor, labelFont;

    /** Variáveis de estado das preferências de leitura. */
    private int currentFontSize;
    private String currentTextColor;
    private String currentBackgroundColor;
    private boolean isSerifFont;

    /** Controle para recálculo de progresso quando a fonte ou layout é alterado. */
    private float lastKnownProgressPercentage = -1f;
    private boolean isInitialLoad = true;

    /**
     * Supressão de ClickableViewAccessibility e SetJavaScriptEnabled, pois
     * tratamos o toque via GestureDetector e precisamos do JS para navegação interna na WebView.
     */
    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);

        // Inicializa o banco com migração destrutiva por segurança de esquema
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "pdf-content-database").fallbackToDestructiveMigration(true).build();
        pdfName = getIntent().getStringExtra("pdfName");

        // Inicializa SharedPreferences e painel de leitura
        readingPrefs = getSharedPreferences("ReadingPreferences", MODE_PRIVATE);
        initializeReadingSettingsViews();
        loadReadingPreferences();
        setupReadingSettingsListeners();

        // Configuração da WebView
        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        seekBar = findViewById(R.id.seekBar);
        toolbar = findViewById(R.id.toolbar);
        TextView pdfNameTextView = findViewById(R.id.pdfNameTextView);
        pdfNameTextView.setText(pdfName);

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> handleBackPressed());
        itemCountTextView = findViewById(R.id.itemCountTextView);

        // Configura o detector de toques para mostrar/esconder as barras (Modo Imersivo)
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (readingSettingsPanel.getVisibility() == View.VISIBLE) {
                    readingSettingsPanel.setVisibility(View.GONE);
                } else if (seekBar.getVisibility() == View.VISIBLE) {
                    seekBar.setVisibility(View.GONE);
                    toolbar.setVisibility(View.GONE);
                } else {
                    seekBar.setVisibility(View.VISIBLE);
                    toolbar.setVisibility(View.VISIBLE);
                }
                return super.onSingleTapConfirmed(e);
            }
        });

        webView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });

        // Sincroniza a rolagem da página HTML com o progresso da SeekBar
        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float scale = webView.getResources().getDisplayMetrics().density;
            int webViewHeight = (int) (webView.getContentHeight() * scale);
            if (webViewHeight > webView.getHeight()) {
                int progress = (int) (100 * (float) scrollY / (webViewHeight - webView.getHeight()));
                seekBar.setProgress(progress);
            }
        });

        // Sincroniza o arraste da SeekBar com a rolagem da WebView
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float scale = webView.getResources().getDisplayMetrics().density;
                    int webViewHeight = (int) (webView.getContentHeight() * scale);
                    int scrollTo = (int) ((webViewHeight - webView.getHeight()) * (progress / 100.0));
                    webView.scrollTo(0, scrollTo);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Aguarda a WebView terminar de desenhar o HTML para rolar até a última posição salva
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.postDelayed(() -> {
                    if (isInitialLoad) {
                        view.scrollTo(0, initialScrollY);
                        isInitialLoad = false;
                    } else if (lastKnownProgressPercentage >= 0) {
                        float scale = webView.getResources().getDisplayMetrics().density;
                        int contentHeight = (int) (view.getContentHeight() * scale);
                        int newScrollY = (int) (contentHeight * lastKnownProgressPercentage);
                        view.scrollTo(0, newScrollY);
                        lastKnownProgressPercentage = -1f;
                    }
                }, 200);
            }
        });

        loadContentFromDatabase();

        // Tratamento moderno do botão nativo de "Voltar" do Android
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBackPressed(); }
        });

        setupSearchFunctionality();
    }

    /** Vincula os elementos de interface do menu de configurações visuais. */
    private void initializeReadingSettingsViews() {
        readingSettingsPanel = findViewById(R.id.readingSettingsPanel);
        readingSettingsButton = findViewById(R.id.readingSettingsButton);
        colorWhite = findViewById(R.id.colorWhite);
        colorBlack = findViewById(R.id.colorBlack);
        colorSepia = findViewById(R.id.colorSepia);
        colorOptions = findViewById(R.id.colorOptions);
        btnDecreaseFont = findViewById(R.id.buttonDecreaseFont);
        btnIncreaseFont = findViewById(R.id.buttonIncreaseFont);
        btnToggleFontFamily = findViewById(R.id.buttonToggleFontFamily);
        switchFollowSystemTheme = findViewById(R.id.switchFollowSystemTheme);

        labelFollowSystemTheme = findViewById(R.id.labelFollowSystemTheme);
        labelPageColor = findViewById(R.id.labelPageColor);
        labelFont = findViewById(R.id.labelFont);
    }

    /** Carrega as configurações de leitura salvas no dispositivo via SharedPreferences. */
    private void loadReadingPreferences() {
        currentFontSize = readingPrefs.getInt("fontSize", 20);
        isSerifFont = readingPrefs.getBoolean("isSerif", false);
        boolean followSystem = readingPrefs.getBoolean("followSystemTheme", true);

        switchFollowSystemTheme.setChecked(followSystem);
        updateColorsBasedOnSwitchState(followSystem);

        updateUIState();
        updatePanelAppearance(isBackgroundColorLight(currentBackgroundColor));
    }

    /** Verifica se a cor de fundo escolhida é clara para ajustar a cor das fontes do painel. */
    private boolean isBackgroundColorLight(String color) {
        return color.equalsIgnoreCase("#FFFFFF") || color.equalsIgnoreCase("#FBF0D9");
    }

    /** * Atualiza o design do painel flutuante (dark/light) de acordo com a cor do papel (fundo) selecionada.
     */
    private void updatePanelAppearance(boolean isLight) {
        int panelColor;
        int textColor;
        int strokeColor;

        if (isLight) {
            panelColor = Color.parseColor("#F5F5F5"); // Branco suave
            textColor = Color.parseColor("#000000");  // Preto
            strokeColor = Color.parseColor("#BDBDBD"); // Cinza para a borda do botão
        } else {
            panelColor = Color.parseColor("#212121"); // Cinza-escuro
            textColor = Color.parseColor("#FFFFFF");  // Branco
            strokeColor = Color.parseColor("#616161"); // Cinza para a borda do botão
        }

        readingSettingsPanel.setBackgroundColor(panelColor);

        labelFollowSystemTheme.setTextColor(textColor);
        labelPageColor.setTextColor(textColor);
        labelFont.setTextColor(textColor);
        switchFollowSystemTheme.setTextColor(textColor);

        btnDecreaseFont.setTextColor(textColor);
        btnDecreaseFont.setStrokeColor(ColorStateList.valueOf(strokeColor));
        btnIncreaseFont.setTextColor(textColor);
        btnIncreaseFont.setStrokeColor(ColorStateList.valueOf(strokeColor));
        btnToggleFontFamily.setTextColor(textColor);
        btnToggleFontFamily.setStrokeColor(ColorStateList.valueOf(strokeColor));
    }

    /** Ajusta as cores ativas caso o Switch de seguir o tema do aparelho seja alterado. */
    private void updateColorsBasedOnSwitchState(boolean followSystem) {
        if (followSystem) {
            int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                currentBackgroundColor = "#121212";
                currentTextColor = "#E0E0E0";
            } else {
                currentBackgroundColor = "#FFFFFF";
                currentTextColor = "#000000";
            }
        } else {
            currentBackgroundColor = readingPrefs.getString("backgroundColor", "#FFFFFF");
            currentTextColor = readingPrefs.getString("textColor", "#000000");
        }
    }

    /** Configura os ouvintes de clique para os botões do painel de leitura (zoom, fonte, paleta). */
    private void setupReadingSettingsListeners() {
        readingSettingsButton.setOnClickListener(v -> {
            if (readingSettingsPanel.getVisibility() == View.VISIBLE) {
                readingSettingsPanel.setVisibility(View.GONE);
            } else {
                readingSettingsPanel.setVisibility(View.VISIBLE);
                toolbar.setVisibility(View.VISIBLE);
                seekBar.setVisibility(View.VISIBLE);
            }
        });

        btnIncreaseFont.setOnClickListener(v -> {
            if (currentFontSize < 32) {
                currentFontSize += 2;
                reloadWebViewWithNewPreferences();
            }
        });

        btnDecreaseFont.setOnClickListener(v -> {
            if (currentFontSize > 14) {
                currentFontSize -= 2;
                reloadWebViewWithNewPreferences();
            }
        });

        btnToggleFontFamily.setOnClickListener(v -> {
            isSerifFont = !isSerifFont;
            updateUIState();
            reloadWebViewWithNewPreferences();
        });


        View.OnClickListener colorClickListener = v -> {
            switchFollowSystemTheme.setChecked(false);
            boolean isLight = false;

            if (v.getId() == R.id.colorWhite) {
                currentBackgroundColor = "#FFFFFF";
                currentTextColor = "#000000";
                isLight = true;
            } else if (v.getId() == R.id.colorBlack) {
                currentBackgroundColor = "#121212";
                currentTextColor = "#E0E0E0";
            } else if (v.getId() == R.id.colorSepia) {
                currentBackgroundColor = "#FBF0D9";
                currentTextColor = "#5B4636";
                isLight = true;
            }

            updatePanelAppearance(isLight);
            reloadWebViewWithNewPreferences();
        };

        colorWhite.setOnClickListener(colorClickListener);
        colorBlack.setOnClickListener(colorClickListener);
        colorSepia.setOnClickListener(colorClickListener);

        switchFollowSystemTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateColorsBasedOnSwitchState(isChecked);
            updateUIState();
            updatePanelAppearance(isBackgroundColorLight(currentBackgroundColor));
            reloadWebViewWithNewPreferences();
        });
    }

    /** * Recarrega o conteúdo HTML na WebView aplicando as novas configurações visuais.
     * Salva a porcentagem atual da leitura para não perder a posição ao recriar o layout.
     */
    private void reloadWebViewWithNewPreferences() {
        float scale = webView.getResources().getDisplayMetrics().density;
        int contentHeight = (int) (webView.getContentHeight() * scale);
        if (contentHeight > 0) {
            lastKnownProgressPercentage = (float) webView.getScrollY() / contentHeight;
        } else {
            lastKnownProgressPercentage = 0f;
        }
        applyPreferencesToWebView();
    }

    /** Busca o texto puro armazenado no banco de dados. */
    private void loadContentFromDatabase() {
        executor.execute(() -> {
            PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
            if (pdfContent != null) {
                initialScrollY = pdfContent.scrollPosition;
                handler.post(this::applyPreferencesToWebView);
            }
        });
    }

    /** Salva as opções visuais atuais nas Preferências do Aparelho. */
    private void saveReadingPreferences() {
        SharedPreferences.Editor editor = readingPrefs.edit();
        editor.putInt("fontSize", currentFontSize);
        editor.putBoolean("isSerif", isSerifFont);
        if (!switchFollowSystemTheme.isChecked()) {
            editor.putString("backgroundColor", currentBackgroundColor);
            editor.putString("textColor", currentTextColor);
        }
        editor.putBoolean("followSystemTheme", switchFollowSystemTheme.isChecked());
        editor.apply();
    }

    /** * Envolve o texto puro do banco em uma casca HTML com CSS dinâmico
     * e o envia para ser renderizado pela WebView.
     */
    private void applyPreferencesToWebView() {
        executor.execute(() -> {
            PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
            if (pdfContent != null) {
                String pdfContentString = pdfContent.content;
                String fontFamily = isSerifFont ? "serif" : "sans-serif";

                String html = "<html><head><style>body { background-color: %s; color: %s; font-family: %s; font-size: %dpx; text-align: justify; word-wrap: break-word; padding: 10px; }</style></head><body>%s</body></html>";
                String formattedHtml = String.format(Locale.US, html, currentBackgroundColor, currentTextColor, fontFamily, currentFontSize, pdfContentString);

                handler.post(() -> {
                    webView.setBackgroundColor(Color.parseColor(currentBackgroundColor));
                    webView.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null);
                });
            }
        });
    }

    /** Ativa ou desativa a paleta de cores manual dependendo do Switch. */
    private void updateUIState() {
        btnToggleFontFamily.setText(isSerifFont ? getString(R.string.sans_serif_font) : getString(R.string.serif_font));
        boolean enabled = !switchFollowSystemTheme.isChecked();
        colorOptions.setAlpha(enabled ? 1.0f : 0.5f);
        colorWhite.setEnabled(enabled);
        colorBlack.setEnabled(enabled);
        colorSepia.setEnabled(enabled);
    }

    /** * Chamado ao fechar a tela.
     * Salva a posição final (scrollY) e o progresso em % no banco de dados.
     */
    private void handleBackPressed() {
        saveReadingPreferences();
        if (webView != null) {
            int finalScrollY = webView.getScrollY();
            float scale = webView.getResources().getDisplayMetrics().density;
            int contentHeight = (int) (webView.getContentHeight() * scale);
            int progress = (contentHeight > webView.getHeight()) ? (int) (100 * (float) finalScrollY / (contentHeight - webView.getHeight())) : 0;

            executor.execute(() -> {
                PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                if (pdfContent != null) {
                    pdfContent.scrollPosition = finalScrollY;
                    pdfContent.progress = Math.max(0, Math.min(100, progress));
                    pdfContent.lastTimeOpened = System.currentTimeMillis();
                    db.pdfContentDao().update(pdfContent);
                }
            });
            Intent resultIntent = new Intent();
            setResult(Activity.RESULT_OK, resultIntent);
        }
        finish();
    }

    /** Garante que o progresso é salvo mesmo se o aplicativo for minimizado (Background). */
    @Override
    protected void onPause() {
        super.onPause();
        saveReadingPreferences();
        if (webView != null && webView.getContentHeight() > 0) {
            int finalScrollY = webView.getScrollY();
            float scale = webView.getResources().getDisplayMetrics().density;
            int contentHeight = (int) (webView.getContentHeight() * scale);
            int progress = (contentHeight > webView.getHeight()) ? (int) (100 * (float) finalScrollY / (contentHeight - webView.getHeight())) : 0;

            executor.execute(() -> {
                PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                if (pdfContent != null) {
                    pdfContent.scrollPosition = finalScrollY;
                    pdfContent.progress = Math.max(0, Math.min(100, progress));
                    db.pdfContentDao().update(pdfContent);
                }
            });
        }
    }

    /** Configura os atalhos e ouvintes de pesquisa da barra de pesquisa flutuante. */
    private void setupSearchFunctionality() {
        floatButtonBar = findViewById(R.id.floatButtonBar);
        searchViewFloating = findViewById(R.id.searchViewFloating);
        ImageButton upButton = findViewById(R.id.upButton);
        ImageButton downButton = findViewById(R.id.downButton);
        ImageButton closeButton = findViewById(R.id.closeButton);
        ImageButton searchViewButton = findViewById(R.id.searchView);

        searchViewButton.setOnClickListener(v -> {
            floatButtonBar.setVisibility(View.VISIBLE);
            searchViewFloating.requestFocus();
            searchViewFloating.setIconified(false);
        });

        searchViewFloating.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { highlightText(query); return true; }
            @Override
            public boolean onQueryTextChange(String newText) { highlightText(newText); return true; }
        });

        upButton.setOnClickListener(v -> webView.findNext(false));
        downButton.setOnClickListener(v -> webView.findNext(true));

        closeButton.setOnClickListener(v -> {
            floatButtonBar.setVisibility(View.GONE);
            searchViewFloating.setQuery("", false);
            clearHighlights();
        });
    }

    /** Encontra todas as ocorrências de um termo no DOM do HTML renderizado. */
    private void highlightText(String keyword) {
        webView.findAllAsync(keyword);
        webView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            if (isDoneCounting) {
                totalMatches = numberOfMatches;
                currentMatchIndex = (numberOfMatches > 0) ? activeMatchOrdinal : -1;
                updateItemCountDisplay();
            }
        });
    }

    /** Atualiza o contador de resultados encontrados na pesquisa (ex: 2/5). */
    @SuppressLint("DefaultLocale")
    private void updateItemCountDisplay() {
        if (totalMatches > 0) {
            itemCountTextView.setText(String.format("%d/%d", currentMatchIndex + 1, totalMatches));
            itemCountTextView.setVisibility(View.VISIBLE);
        } else {
            itemCountTextView.setVisibility(View.GONE);
        }
    }

    /** Limpa os textos marcados na tela de pintura (Canvas) após fechar a busca. */
    private void clearHighlights() {
        webView.clearMatches();
        totalMatches = 0;
        currentMatchIndex = -1;
        updateItemCountDisplay();
    }

    /** Método moderno do AndroidX para esconder botões nativos e status bar (Modo Imersivo). */
    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /** Trava dupla: Força a reinserção do modo imersivo sempre que o usuário retornar o foco à tela. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
}