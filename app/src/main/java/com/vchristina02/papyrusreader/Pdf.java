package com.vchristina02.papyrusreader;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_pdf);

        // Inicializa o banco de dados
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "pdf-content-database").build();

        // Obtenhe o nome do PDF do Intent
        pdfName = getIntent().getStringExtra("pdfName");

        WebView webView = findViewById(R.id.webview);
        seekBar = findViewById(R.id.seekBar);
        toolbar = findViewById(R.id.toolbar);

        TextView pdfNameTextView = findViewById(R.id.pdfNameTextView);
        pdfNameTextView.setText(pdfName);

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // Inicializa o GestureDetector
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
            int webViewHeight = webView.getContentHeight() - webView.getHeight();
            int progress = (int) (((float) scrollY / webViewHeight) * 100);
            seekBar.setProgress(progress);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int webViewHeight = webView.getContentHeight() - webView.getHeight();
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

                // Recupere o PdfContent do banco de dados usando o nome
                executor.execute(() -> {
                    PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                    if (pdfContent != null) {
                        scrollY = pdfContent.scrollPosition;

                        // Defina a posição de rolagem depois que o conteúdo da WebView for totalmente carregado
                        handler.postDelayed(() -> {
                            webView.scrollTo(0, scrollY);

                            // Atualize a posição da SeekBar com base na posição de rolagem
                            int webViewHeight = webView.getContentHeight() - webView.getHeight();
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

        executor.execute(() -> {
            // Recupere o PdfContent do banco de dados usando o nome
            PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
            if (pdfContent != null) {
                handler.post(() -> {
                    // Use o conteúdo do PdfContent
                    String pdfContentString = pdfContent.content;

                    String hexBackgroundColor = String.format("#%06X", (0xFFFFFF & backgroundColor));
                    boolean isBackgroundBeige = (hexBackgroundColor.equals("#FFFFDF"));
                    String textColor = isBackgroundBeige ? "black" : "white";
                    String htmlText = "<html><head><style>body {text-align: justify; word-wrap: break-word; font-size: 20px; color: %s;}</style></head><body>%s</body></html>";
                    String data = String.format(htmlText, textColor, pdfContentString);
                    webView.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null);
                });
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        WebView webView = findViewById(R.id.webview);
        if (webView != null) {
            scrollY = webView.getScrollY();
            outState.putInt("scrollY", scrollY);

            // Salva a posição de rolagem no banco de dados
            executor.execute(() -> {
                PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                if (pdfContent != null) {
                    pdfContent.scrollPosition = scrollY;
                    db.pdfContentDao().update(pdfContent);
                }
            });
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

            // Salva a posição de rolagem no banco de dados
            executor.execute(() -> {
                PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                if (pdfContent != null) {
                    pdfContent.scrollPosition = scrollY;
                    db.pdfContentDao().update(pdfContent);
                }
            });
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        // Mantém o layout estável para não redimensionar a tela quando a barra aparecer
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Esconde a Navigation Bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        // Esconde a Status Bar
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