package com.fixer.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Redirect to UserDashboardActivity
        Intent intent = new Intent(this, UserDashboardActivity.class);
        startActivity(intent);
        finish();
    }
}