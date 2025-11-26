package com.example.circularslider;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

/**
 * 主活动：处理 UI 交互、菜单操作、蓝牙权限及适配器激活，并与 OscilloManager 交互。
 * 实现了 BTSetupCallback 来接收权限和状态通知。
 */
public class MainActivity extends AppCompatActivity implements BTSetupCallback {

    private static final String TAG = "MainActivity";

    // UI 元素
    private TextView valueText;
    private TextView statusText; // 用于显示连接状态的 TextView (Q.3.6-1)
    private CircularSlider circularSlider; // 假设您已定义此自定义 View

    // 业务逻辑和通信管理
    private OscilloManager oscilloManager;
    private BTSetup btSetup;

    // Q.3.6-2: 抽象类实现的具体实例，用于附加到 OscilloManager
    private OscilloEventsListener oscilloEventsListener;

    // --- ActivityResultLaunchers (用于处理异步结果) ---

    // 1. 权限请求启动器
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (Boolean isGranted : result.values()) {
                    if (!isGranted) {
                        granted = false;
                        break;
                    }
                }
                if (granted) {
                    // 权限已授予，检查适配器启用状态
                    if (!btSetup.isAdapterEnabled()) {
                        requestEnableBT();
                    } else {
                        onBTSetupReady();
                    }
                } else {
                    onBTSetupFailed("用户拒绝了必要的蓝牙权限。");
                }
            });

    // 2. 启用蓝牙适配器启动器
    private final ActivityResultLauncher<Intent> requestEnableBTLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    onBTSetupReady();
                } else {
                    onBTSetupFailed("用户拒绝启用蓝牙，连接无法建立。");
                }
            });

    // 3. 启动 BluetoothConnectActivity 的 Launcher (Q.3.4-7)
    private final ActivityResultLauncher<Intent> startConnectActivityLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    // 从 BluetoothConnectActivity 接收设备地址
                    String deviceAddress = result.getData().getStringExtra("DEVICE_ADDRESS");
                    if (deviceAddress != null) {
                        // 启动连接过程
                        oscilloManager.connect(deviceAddress);
                    }
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 BTSetup
        btSetup = new BTSetup(this, this);

        // Q.3.6-1 初始化 OscilloManager (ViewModel)
        oscilloManager = new ViewModelProvider(this).get(OscilloManager.class);

        // Q.3.7-2: 附加 FrameProcessor
        if (oscilloManager.getTransceiver() instanceof BluetoothManager) {
            BluetoothManager bm = (BluetoothManager) oscilloManager.getTransceiver();
            // 确保 FrameProcessor 实例被附加
            bm.attachFrameProcessor(new FrameProcessor());
        }

        // 初始化 UI 元素
        valueText = findViewById(R.id.valueText);
        circularSlider = findViewById(R.id.circularSlider);
        statusText = findViewById(R.id.statusText);

        if (circularSlider != null && valueText != null) {
            // 监听 Slider 变化
            circularSlider.setOnValueChangeListener(new CircularSlider.OnValueChangeListener() {
                @Override
                public void onValueChanged(float newValue) {
                    valueText.setText("Valeur : " + Math.round(newValue) + "%");

                    // Q.3.6-4: 调用业务逻辑发送命令
                    float dutyCycle = newValue / 100f;
                    oscilloManager.setCalibrationDutyCyle(dutyCycle);
                }
            });
            circularSlider.setEnabled(false); // Q.3.8-1: 初始禁用 Slider
        }

        // Q.3.6-2: 实例化 OscilloEventsListener 的抽象类
        oscilloEventsListener = new OscilloEventsListener() {
            @Override
            public void onConnectionStatusChange(final int status) {
                // Q.3.6-3 关键：必须在 UI 线程上更新 UI 元素
                runOnUiThread(() -> {
                    String statusMsg;
                    boolean isConnected = false;

                    switch (status) {
                        case Transceiver.STATE_NOT_CONNECTED:
                            statusMsg = "未连接";
                            break;
                        case Transceiver.STATE_CONNECTING:
                            statusMsg = "连接中...";
                            break;
                        case Transceiver.STATE_CONNECTED:
                            statusMsg = "已连接";
                            isConnected = true;
                            break;
                        default:
                            statusMsg = "未知状态";
                    }

                    if (statusText != null) {
                        statusText.setText("状态: " + statusMsg);
                    }
                    if (circularSlider != null) {
                        circularSlider.setEnabled(isConnected); // Q.3.8-1
                    }

                    Toast.makeText(MainActivity.this, statusMsg, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onDataReceived(FrameProcessor.Data data) {
                // Q.3.6-3: 接收数据，必须在 UI 线程上更新 UI 元素
                runOnUiThread(() -> {
                    if (!data.getFrameValidity()) {
                        Toast.makeText(MainActivity.this, "警告: 收到无效帧 (Checksum 错误)", Toast.LENGTH_SHORT).show();
                    }
                    // TODO: 在这里处理收到的数据
                });
            }
        };

        // 注意：第一次 addListener 在 onStart 中执行
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Q.3.6-2: 确保 Activity 启动时监听器是活动的
        if (oscilloEventsListener != null) {
            oscilloManager.addListener(oscilloEventsListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Q.3.6-2: 移除监听器以避免内存泄漏 (Q.3.8-2 提及的内存泄漏防止)
        if (oscilloEventsListener != null) {
            oscilloManager.removeListener(oscilloEventsListener);
        }
    }


    // --------------------------------------------------------
    // --- Q.3.1: 菜单创建与点击逻辑 (已修复) ---
    // --------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Q.3.1: 加载 menu.xml 资源 (确保 R.menu.menu 存在)
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Q.3.1: 处理菜单点击事件
        // 假设连接菜单项 ID 是 R.id.menu_connect
        if (item.getItemId() == R.id.menu_connect) {
            // 每次点击连接菜单时，调用 BTReadyBT 检查状态和权限 (Q.3.1 和 Q.3.3)
            btSetup.BTReadyBT(requestPermissionLauncher, requestEnableBTLauncher);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --------------------------------------------------------
    // --- BTSetupCallback 实现 (Q.3.3-4) ---
    // --------------------------------------------------------

    @Override
    public void onBTSetupReady() {
        Log.i(TAG, "蓝牙设置全部就绪。启动连接活动。");
        Toast.makeText(this, "蓝牙已就绪，启动设备列表...", Toast.LENGTH_SHORT).show();

        // Q.3.2-1 & Q.3.4-7: 启动 BluetoothConnectActivity
        Intent intent = new Intent(MainActivity.this, BluetoothConnectActivity.class);
        startConnectActivityLauncher.launch(intent);
    }

    @Override
    public void onBTSetupFailed(String reason) {
        Log.e(TAG, "蓝牙设置失败: " + reason);
        Toast.makeText(this, "蓝牙设置失败: " + reason, Toast.LENGTH_LONG).show();
        if (circularSlider != null) {
            circularSlider.setEnabled(false);
        }
    }

    // --------------------------------------------------------
    // --- Q.3.8-2: 生命周期管理 ---
    // --------------------------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Q.3.8-2: 确保连接在 Activity 销毁时中断
        if (oscilloManager != null) {
            // Note: disconnect() 也会在 ViewModel 的 onCleared() 中调用
            // 确保在 Activity 销毁时，如果 ViewModel 也要被销毁，连接能被终止。
            // 如果 Activity 只是配置更改，ViewModel 会存活，连接保持。
            // 这里不需要显式调用 disconnect()，因为 onCleared() 会处理，
            // 但为安全起见，通常在 Activity/Fragment 中不持有 ViewModel 的引用。
            // 由于 TP 要求确保中断，我们依赖 ViewModel 的 onCleared()。
        }
    }

    /** 用于封装启动蓝牙适配器 Intent 的逻辑，解决向前引用问题。*/
    private void requestEnableBT() {
        Intent enableBtIntent = new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE);
        requestEnableBTLauncher.launch(enableBtIntent);
    }
}