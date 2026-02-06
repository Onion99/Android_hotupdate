package com.orange.update;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class TestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView textView = new TextView(this);
        textView.setText("Test Plugin Usage - Version 1.6");
        textView.setTextSize(20);
        textView.setPadding(50, 50, 50, 50);
        
        setContentView(textView);
    }
}
