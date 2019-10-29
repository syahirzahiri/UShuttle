package com.molly.bustracker.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.molly.bustracker.R;
import com.molly.bustracker.passenger.LoginPassenger;


public class StartUp extends AppCompatActivity {

    private String TAG = "startup";
    private Button passenger_btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);

        passenger_btn = findViewById(R.id.passengerbtn);


        passenger_btn.setOnClickListener(view -> {
            Intent toLogin = new Intent(StartUp.this, LoginPassenger.class);
            startActivity(toLogin);
        });

    }
}
