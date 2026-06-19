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

public class Pdf extends AppCompatActivity {
    private int initialScrollY = 0;
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pdfName;
    private WebView webView;
    private SeekBar seekBar;
    private GestureDetector gestureDetector;
    private Toolbar toolbar;
    private LinearLayout floatButtonBar;
    private SearchView searchViewFloating;
    private int currentMatchIndex = -1;
    private int totalMatches = 0;
    private TextView itemCountTextView;

    private SharedPreferences readingPrefs;
    private LinearLayout readingSettingsPanel;
    private ImageButton readingSettingsButton;
    private View colorWhite, colorBlack, colorSepia;
    private LinearLayout colorOptions;
    private MaterialButton btnDecreaseFont, btnIncreaseFont, btnToggleFontFamily;
    private SwitchMaterial switchFollowSystemTheme;
    private int currentFontSize;
    private String currentTextColor;
    private String currentBackgroundColor;
    private boolean isSerifFont;

    // NOVAS VARIÁVEIS PARA OS TEXTOS DO PAINEL
    private TextView labelFollowSystemTheme, labelPageColor, labelFont;

    private float lastKnownProgressPercentage = -1f;
    private boolean isInitialLoad = true;

    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // hideSystemUI();

        setContentView(R.layout.activity_pdf);

        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "pdf-content-database").fallbackToDestructiveMigration().build();
        pdfName = getIntent().getStringExtra("pdfName");

        readingPrefs = getSharedPreferences("ReadingPreferences", MODE_PRIVATE);
        initializeReadingSettingsViews();
        loadReadingPreferences();
        setupReadingSettingsListeners();

        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        seekBar = findViewById(R.id.seekBar);
        toolbar = findViewById(R.id.toolbar);
        TextView pdfNameTextView = findViewById(R.id.pdfNameTextView);
        pdfNameTextView.setText(pdfName);
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> handleBackPressed());
        itemCountTextView = findViewById(R.id.itemCountTextView);

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

        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float scale = webView.getResources().getDisplayMetrics().density;
            int webViewHeight = (int) (webView.getContentHeight() * scale);
            if (webViewHeight > webView.getHeight()) {
                int progress = (int) (100 * (float) scrollY / (webViewHeight - webView.getHeight()));
                seekBar.setProgress(progress);
            }
        });

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
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBackPressed(); }
        });
        setupSearchFunctionality();
    }

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

        // INICIALIZAÇÃO DOS NOVOS TEXTVIEWS
        labelFollowSystemTheme = findViewById(R.id.labelFollowSystemTheme);
        labelPageColor = findViewById(R.id.labelPageColor);
        labelFont = findViewById(R.id.labelFont);
    }

    private void loadReadingPreferences() {
        currentFontSize = readingPrefs.getInt("fontSize", 20);
        isSerifFont = readingPrefs.getBoolean("isSerif", false);
        boolean followSystem = readingPrefs.getBoolean("followSystemTheme", true);

        switchFollowSystemTheme.setChecked(followSystem);
        updateColorsBasedOnSwitchState(followSystem);

        updateUIState();
        // CHAMA A NOVA FUNÇÃO PARA DEFINIR A APARÊNCIA INICIAL DO PAINEL
        updatePanelAppearance(isBackgroundColorLight(currentBackgroundColor));
    }

    // NOVA FUNÇÃO para verificar se uma cor é "clara"
    private boolean isBackgroundColorLight(String color) {
        return color.equalsIgnoreCase("#FFFFFF") || color.equalsIgnoreCase("#FBF0D9");
    }

    // NOVA FUNÇÃO para atualizar a aparência de todo o painel de configurações
    private void updatePanelAppearance(boolean isLight) {
        int panelColor;
        int textColor;
        int strokeColor;

        if (isLight) {
            panelColor = Color.parseColor("#F5F5F5"); // Um branco suave
            textColor = Color.parseColor("#000000");  // Preto
            strokeColor = Color.parseColor("#BDBDBD"); // Cinza para a borda do botão
        } else {
            panelColor = Color.parseColor("#212121"); // Cinza escuro
            textColor = Color.parseColor("#FFFFFF");  // Branco
            strokeColor = Color.parseColor("#616161"); // Cinza para a borda do botão
        }

        readingSettingsPanel.setBackgroundColor(panelColor);

        // Atualiza a cor de todos os textos
        labelFollowSystemTheme.setTextColor(textColor);
        labelPageColor.setTextColor(textColor);
        labelFont.setTextColor(textColor);
        switchFollowSystemTheme.setTextColor(textColor);

        // Atualiza a cor do texto e da borda dos botões
        btnDecreaseFont.setTextColor(textColor);
        btnDecreaseFont.setStrokeColor(ColorStateList.valueOf(strokeColor));
        btnIncreaseFont.setTextColor(textColor);
        btnIncreaseFont.setStrokeColor(ColorStateList.valueOf(strokeColor));
        btnToggleFontFamily.setTextColor(textColor);
        btnToggleFontFamily.setStrokeColor(ColorStateList.valueOf(strokeColor));
    }

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

    private void setupReadingSettingsListeners() {
        // ... (seu listener do readingSettingsButton, etc. continuam iguais)
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
            // ATUALIZA A APARÊNCIA DO PAINEL
            updatePanelAppearance(isLight);
            reloadWebViewWithNewPreferences();
        };

        colorWhite.setOnClickListener(colorClickListener);
        colorBlack.setOnClickListener(colorClickListener);
        colorSepia.setOnClickListener(colorClickListener);

        switchFollowSystemTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateColorsBasedOnSwitchState(isChecked);
            updateUIState();
            // ATUALIZA A APARÊNCIA DO PAINEL
            updatePanelAppearance(isBackgroundColorLight(currentBackgroundColor));
            reloadWebViewWithNewPreferences();
        });
    }

    // O resto do seu código (applyPreferences, savePreferences, handleBackPressed, etc.)
    // permanece exatamente o mesmo da versão anterior.
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

    private void loadContentFromDatabase() {
        executor.execute(() -> {
            PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
            if (pdfContent != null) {
                initialScrollY = pdfContent.scrollPosition;
                handler.post(this::applyPreferencesToWebView);
            }
        });
    }

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

    private void updateUIState() {
        btnToggleFontFamily.setText(isSerifFont ? getString(R.string.sans_serif_font) : getString(R.string.serif_font));
        boolean enabled = !switchFollowSystemTheme.isChecked();
        colorOptions.setAlpha(enabled ? 1.0f : 0.5f);
        colorWhite.setEnabled(enabled);
        colorBlack.setEnabled(enabled);
        colorSepia.setEnabled(enabled);
    }

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

    @SuppressLint("DefaultLocale")
    private void updateItemCountDisplay() {
        if (totalMatches > 0) {
            itemCountTextView.setText(String.format("%d/%d", currentMatchIndex + 1, totalMatches));
            itemCountTextView.setVisibility(View.VISIBLE);
        } else {
            itemCountTextView.setVisibility(View.GONE);
        }
    }

    private void clearHighlights() {
        webView.clearMatches();
        totalMatches = 0;
        currentMatchIndex = -1;
        updateItemCountDisplay();
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
        //Toda vez que o usuário focar no PDF, força a imersão
        if (hasFocus) {
            hideSystemUI();
        }
    }
}