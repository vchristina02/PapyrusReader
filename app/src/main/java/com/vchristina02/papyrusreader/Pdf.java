package com.vchristina02.papyrusreader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
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
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.room.Room;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Pdf extends AppCompatActivity {
    private int scrollY = 0;
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pdfName;
    private SeekBar seekBar;
    private GestureDetector gestureDetector;
    private Toolbar toolbar;

    private LinearLayout floatButtonBar;
    private SearchView searchViewFloating;
    private ImageButton upButton;
    private ImageButton downButton;
    private int currentMatchIndex = -1;
    private int totalMatches = 0;
    private TextView itemCountTextView;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideSystemUI();

        setContentView(R.layout.activity_pdf);

        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "pdf-content-database").fallbackToDestructiveMigration().build();
        pdfName = getIntent().getStringExtra("pdfName");

        WebView webView = findViewById(R.id.webview);
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
                if (seekBar.getVisibility() == View.VISIBLE) {
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
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        });

        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float scale = webView.getResources().getDisplayMetrics().density;
            int webViewHeight = (int) (webView.getContentHeight() * scale);
            int progress = (int) (((float) scrollY / webViewHeight) * 100);
            seekBar.setProgress(progress);
            Log.d("progress pdf", "O progresso pdf é: " + progress);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float scale = webView.getResources().getDisplayMetrics().density;
                    int webViewHeight = (int) (webView.getContentHeight() * scale);
                    int scrollY = (int) ((progress / 100.0) * webViewHeight);
                    webView.scrollTo(0, scrollY);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.postDelayed(() -> view.scrollTo(0, scrollY), 100);
                executor.execute(() -> {
                    PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                    if (pdfContent != null) {
                        scrollY = pdfContent.scrollPosition;
                        handler.postDelayed(() -> {
                            webView.scrollTo(0, scrollY);
                            float scale = webView.getResources().getDisplayMetrics().density;
                            int webViewHeight = (int) (webView.getContentHeight() * scale);
                            int progress = (int) (((float) scrollY / webViewHeight) * 100);
                            seekBar.setProgress(progress);
                        }, 100);
                    }
                });
            }
        });

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        @ColorInt int backgroundColor = typedValue.data;
        webView.setBackgroundColor(backgroundColor);
        getTheme().resolveAttribute(R.attr.pdfTextColor, typedValue, true);
        String textColor = String.format("#%06X", (0xFFFFFF & typedValue.data));

        executor.execute(() -> {
            PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
            if (pdfContent != null) {
                handler.post(() -> {
                    String pdfContentString = pdfContent.content;
                    String htmlText = "<html><head><style>body {text-align: justify; word-wrap: break-word; font-size: 20px; color: %s;}</style></head><body>%s</body></html>";
                    String data = String.format(htmlText, textColor, pdfContentString);
                    webView.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null);
                });
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });

        floatButtonBar = findViewById(R.id.floatButtonBar);
        searchViewFloating = findViewById(R.id.searchViewFloating);
        upButton = findViewById(R.id.upButton);
        downButton = findViewById(R.id.downButton);
        // Adicionado para o botão X
        ImageButton closeButton = findViewById(R.id.closeButton); // Inicialização do botão X

        ImageButton searchView = findViewById(R.id.searchView);
        searchView.setOnClickListener(v -> {
            floatButtonBar.setVisibility(View.VISIBLE);
            searchViewFloating.requestFocus();
            searchViewFloating.setIconified(false);
        });

        searchViewFloating.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Ao submeter, pesquisa e destaca o texto
                highlightText(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Atualiza os resultados conforme o texto é alterado
                highlightText(newText);
                return true;
            }
        });

        upButton.setOnClickListener(v -> {
            if (totalMatches > 0) {
                currentMatchIndex--;
                if (currentMatchIndex < 0) {
                    currentMatchIndex = totalMatches - 1; // Volta para o último item se passar do primeiro
                }
                webView.findNext(false);
                updateNavigationButtons();
                updateItemCountText();
            }
        });

        downButton.setOnClickListener(v -> {
            if (totalMatches > 0) {
                currentMatchIndex++;
                if (currentMatchIndex >= totalMatches) {
                    currentMatchIndex = 0; // Volta para o primeiro item se passar do último
                }
                webView.findNext(true);
                updateNavigationButtons();
                updateItemCountText();
            }
        });

        // Lógica para o botão X
        closeButton.setOnClickListener(v -> {
            floatButtonBar.setVisibility(View.GONE);
            searchViewFloating.setQuery("", false);
            clearHighlights();
        });
    }

    private void highlightText(String keyword) {
        WebView webView = findViewById(R.id.webview);
        webView.findAllAsync(keyword); // Encontra todos os itens enquanto o usuário digita
        webView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            if (isDoneCounting) {
                totalMatches = numberOfMatches;
                currentMatchIndex = (numberOfMatches > 0) ? activeMatchOrdinal : -1;
                updateItemCountDisplay();
                updateNavigationButtons();
            }
        });
    }

    @SuppressLint("DefaultLocale")
    private void updateItemCountDisplay() {
        TextView itemCountTextView = findViewById(R.id.itemCountTextView);
        itemCountTextView.setText(String.format("%d/%d", currentMatchIndex + 1, totalMatches));
    }

    private void clearHighlights() {
        WebView webView = findViewById(R.id.webview);
        webView.clearMatches();
        totalMatches = 0;
        currentMatchIndex = -1;
        updateNavigationButtons();
        updateItemCountDisplay();
    }

    private void updateNavigationButtons() {
        upButton.setEnabled(currentMatchIndex > 0);
        downButton.setEnabled(currentMatchIndex < totalMatches - 1);
    }

    @SuppressLint("SetTextI18n")
    private void updateItemCountText() {
        itemCountTextView.setText((currentMatchIndex + 1) + "/" + totalMatches);
    }

    private void handleBackPressed() {
        WebView webView = findViewById(R.id.webview);
        if (webView != null) {
            scrollY = webView.getScrollY();
            int position = seekBar.getProgress();
            // Salve a posição de rolagem no banco de dados
            executor.execute(() -> {
                PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                if (pdfContent != null) {
                    pdfContent.scrollPosition = scrollY;
                    pdfContent.progress = position;
                    db.pdfContentDao().update(pdfContent);
                }
            });
        }
        Intent resultIntent = new Intent();
        resultIntent.putExtra("pdfName", pdfName);
        resultIntent.putExtra("pdfProgress", seekBar.getProgress());
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        WebView webView = findViewById(R.id.webview);
        SeekBar seekBar = findViewById(R.id.seekBar);
        if (webView != null && seekBar != null) {
            int scrollY = webView.getScrollY();
            int progressBarPosition = seekBar.getProgress();
            outState.putInt("scrollY", scrollY);
            outState.putInt("progressBarPosition", progressBarPosition);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebView webView = findViewById(R.id.webview);
        if (webView != null) {
            executor.execute(() -> {
                PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                if (pdfContent != null) {
                    scrollY = pdfContent.scrollPosition;
                    handler.post(() -> webView.scrollTo(0, scrollY));
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebView webView = findViewById(R.id.webview);
        if (webView != null) {
            scrollY = webView.getScrollY();
            int position = seekBar.getProgress();
            // Salve a posição de rolagem no banco de dados
            executor.execute(() -> {
                PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                if (pdfContent != null) {
                    pdfContent.scrollPosition = scrollY;
                    pdfContent.progress = position;
                    db.pdfContentDao().update(pdfContent);
                }
            });
        }
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