package com.vchristina02.papyrusreader;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Pdf extends AppCompatActivity {
    private int scrollY = 0;
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pdfName;


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

        // Obtem o nome do PDF do Intent
        pdfName = getIntent().getStringExtra("pdfName");

        WebView webView = findViewById(R.id.webview);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                executor.execute(() -> {
                    // Recupere o PdfContent do banco de dados usando o nome
                    PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
                    if (pdfContent != null) {
                        scrollY = pdfContent.scrollPosition;
                        handler.postDelayed(() -> webView.scrollTo(0, scrollY), 100);
                    }
                });
            }
        });

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        @ColorInt int backgroundColor = typedValue.data;
        webView.setBackgroundColor(backgroundColor);

        executor.execute(() -> {
            // Recupera o PdfContent do banco de dados usando o nome
            PdfContent pdfContent = db.pdfContentDao().getByTitle(pdfName);
            if (pdfContent != null) {
                handler.post(() -> {
                    // Usa o conteúdo do PdfContent
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

            // Salve a posição de rolagem no banco de dados
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
}
