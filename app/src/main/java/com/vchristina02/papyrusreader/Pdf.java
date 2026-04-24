package com.vchristina02.papyrusreader;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.webkit.WebView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.io.InputStream;
import android.net.Uri;
public class Pdf extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);

        // Referenciando o WebView
        WebView webView = findViewById(R.id.webview);

        // Definindo a cor de fundo da WebView
        //webView.setBackgroundColor(getResources().getColor(R.color.beige));

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        @ColorInt int backgroundColor = typedValue.data;

        // Definindo a cor de fundo da WebView para a cor do tema
        webView.setBackgroundColor(backgroundColor);

        StringBuilder parsedText = new StringBuilder();

        try {
            // Carregando o documento PDF
            Uri uri = Uri.parse(getIntent().getStringExtra("pdfUri"));
            InputStream inputStream = getContentResolver().openInputStream(uri);

            if (inputStream != null) {
                PdfReader reader = new PdfReader(inputStream);

                // Extraindo o texto do PDF
                int numberOfPages = reader.getNumberOfPages();
                for (int i = 1; i <= numberOfPages; i++) {
                    String pageText = PdfTextExtractor.getTextFromPage(reader, i); // Extrai o texto da página i
                    // Adicionando a tag <p> para cada parágrafo
                    pageText = pageText.replaceAll("(?m)(^\\s*$\\n)", "<p>");
                    parsedText.append(pageText);
                }

                reader.close();
            } else {
                Log.e("PdfBox-Android-Sample", "InputStream is null");
            }
        } catch (IOException e) {
            Log.e("PdfBox-Android-Sample", "Exception thrown while loading or reading PDF", e);
        }

        String hexBackgroundColor = String.format("#%06X", (0xFFFFFF & backgroundColor));

        // Verificando se a cor de fundo da WebView é #808080
        boolean isBackgroundBeige = (hexBackgroundColor.equals("#FFFFDF"));

        // Definindo a cor do texto com base na cor de fundo da WebView
        String textColor = isBackgroundBeige ? "black" : "white"; // Branco se o fundo for cinza, preto caso contrário

        // Criando uma string HTML para justificar o texto e permitir que as palavras sejam quebradas em várias linhas
        String htmlText = "<html><head><style>body {text-align: justify; word-wrap: break-word; font-size: 20px; color: %s;}</style></head><body>%s</body></html>";
        String data = String.format(htmlText, textColor, parsedText);

        // Carregando o texto justificado no WebView
        webView.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null);
    }
}