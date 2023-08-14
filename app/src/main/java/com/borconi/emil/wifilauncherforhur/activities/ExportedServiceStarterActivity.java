package com.borconi.emil.wifilauncherforhur.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.borconi.emil.wifilauncherforhur.services.WifiService;

public class ExportedServiceStarterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent wifiServiceIntent = new Intent(this, WifiService.class);
        startForegroundService(wifiServiceIntent);
        finish();
    }
}
