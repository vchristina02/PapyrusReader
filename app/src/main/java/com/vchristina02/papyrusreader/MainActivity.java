package com.vchristina02.papyrusreader;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button buttonOpenPdf = findViewById(R.id.buttonOpenPdf);

        buttonOpenPdf.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Pdf.class);
            startActivity(intent);
        });
    }
}