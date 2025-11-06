package com.example.circularslider;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private CircularSlider circularSlider;
    private TextView valueText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        circularSlider = findViewById(R.id.myCircularSlider);
        valueText = findViewById(R.id.valueText);

        valueText.setText("Valeur : 0%");

        circularSlider.setOnValueChangeListener(new CircularSlider.OnValueChangeListener() {
            @Override
            public void onValueChanged(float newValue) {
                valueText.setText(String.format("Valeur : %.0f%%", newValue));
            }
        });
    }
}
