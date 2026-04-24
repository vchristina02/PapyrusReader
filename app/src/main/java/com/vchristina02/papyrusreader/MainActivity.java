package com.vchristina02.papyrusreader;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    public Button buttonOpenPdf;
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    Intent intent = new Intent(MainActivity.this, Pdf.class);
                    intent.putExtra("pdfUri", uri.toString());
                    startActivity(intent);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonOpenPdf = findViewById(R.id.buttonOpenPdf);

        buttonOpenPdf.setOnClickListener(v -> mGetContent.launch("application/pdf"));
    }
}
