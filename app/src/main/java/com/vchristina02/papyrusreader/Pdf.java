package com.vchristina02.papyrusreader;

import android.os.Bundle;
import android.util.TypedValue;
import android.webkit.WebView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class Pdf extends AppCompatActivity {
    // Variáveis para salvar a posição de rolagem
    private int scrollX = 0;
    private int scrollY = 0;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);
        WebView webView = findViewById(R.id.webview);
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        @ColorInt int backgroundColor = typedValue.data;
        webView.setBackgroundColor(backgroundColor);
        String pdfContent = getIntent().getStringExtra("pdfContent");
        String hexBackgroundColor = String.format("#%06X", (0xFFFFFF & backgroundColor));
        boolean isBackgroundBeige = (hexBackgroundColor.equals("#FFFFDF"));
        String textColor = isBackgroundBeige ? "black" : "white";
        String htmlText = "<html><head><style>body {text-align: justify; word-wrap: break-word; font-size: 20px; color: %s;}</style></head><body>%s</body></html>";
        String data = String.format(htmlText, textColor, pdfContent);
        webView.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null);
    }

    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Salvando a posição de rolagem da WebView
        WebView webView = findViewById(R.id.webview);
        if (webView != null) {
            scrollX = webView.getScrollX();
            scrollY = webView.getScrollY();
            outState.putInt("scrollX", scrollX);
            outState.putInt("scrollY", scrollY);
        }
    }

    protected void onResume() {
        super.onResume();
        // Restaurando a posição de rolagem da WebView
        WebView webView = findViewById(R.id.webview);
        if (webView != null) {
            webView.post(() -> webView.scrollTo(scrollX, scrollY));
        }
    }
}