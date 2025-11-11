package com.example.circularslider;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private TextView valueText;
    private CircularSlider circularSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 绑定 XML

        valueText = findViewById(R.id.valueText);
        circularSlider = findViewById(R.id.circularSlider);

        // 设置监听器
        circularSlider.setOnValueChangeListener(new CircularSlider.OnValueChangeListener() {
            @Override
            public void onValueChanged(float newValue) {
                valueText.setText("Valeur : " + Math.round(newValue) + "%");
            }
        });
    }
}
