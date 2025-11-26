package com.example.circularslider;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 活动：显示已配对和已发现的蓝牙设备列表 (Q.3.2)。
 * 包含调试日志以排查扫描问题。
 */
public class BluetoothConnectActivity extends AppCompatActivity implements BTAdapter.OnItemClickListener {

    private static final String TAG = "BTConnectActivity";

    private BluetoothAdapter btAdapter;

    // 数据源
    private List<DeviceItem> pairedDevicesList = new ArrayList<>();
    private List<DeviceItem> discoveredDevicesList = new ArrayList<>();

    // 适配器
    private BTAdapter pairedAdapter;
    private BTAdapter discoveredAdapter;

    // UI 元素
    private Button scanButton;
    private ProgressBar discoveryProgress;
    private TextView titleDiscoveredDevices;

    // 广播接收器：接收设备发现事件
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // 从 Intent 中获取设备对象
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device != null) {
                    String deviceName = device.getName();
                    String deviceAddress = device.getAddress();

                    // 调试日志：打印所有发现的原始设备信息
                    Log.d(TAG, "RAW Discovery: Found device: " + deviceName + " [" + deviceAddress + "]");

                    // 如果名称为空，显示为 "未知设备"
                    if (deviceName == null || deviceName.isEmpty()) {
                        deviceName = "未知设备";
                    }

                    // 检查设备是否已经在“已发现”列表中，避免重复添加
                    if (!isDeviceInList(discoveredDevicesList, deviceAddress)) {
                        // 还要检查是否已经在“已配对”列表中（可选，为了不重复显示）
                        if (!isDeviceInList(pairedDevicesList, deviceAddress)) {
                            Log.i(TAG, "Adding to UI: " + deviceName + " [" + deviceAddress + "]");

                            DeviceItem newItem = new DeviceItem(deviceName, deviceAddress);
                            discoveredAdapter.addDevice(newItem);

                            // 更新标题
                            titleDiscoveredDevices.setText("已发现设备 (" + discoveredAdapter.getItemCount() + ")");
                        } else {
                            Log.d(TAG, "Device is already paired, skipping: " + deviceName);
                        }
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.i(TAG, "Discovery Finished.");
                setScanning(false);
                if (discoveredAdapter.getItemCount() == 0) {
                    titleDiscoveredDevices.setText("已发现设备 (未找到)");
                    Toast.makeText(context, "扫描结束，未发现新设备", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "扫描结束", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    @SuppressLint("MissingPermission")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_connect);

        // 1. 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar_connect);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("连接蓝牙设备");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // 添加返回按钮
        }
        toolbar.setNavigationOnClickListener(v -> finish()); // 点击返回关闭 Activity

        // 2. 初始化 UI
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        scanButton = findViewById(R.id.btn_scan_devices);
        discoveryProgress = findViewById(R.id.progress_discovery);
        titleDiscoveredDevices = findViewById(R.id.title_discovered_devices);

        RecyclerView pairedRecyclerView = findViewById(R.id.list_paired_devices);
        RecyclerView discoveredRecyclerView = findViewById(R.id.list_discovered_devices);

        // 3. 配置 RecyclerView
        pairedRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        discoveredRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 4. 实例化 Adapter
        pairedAdapter = new BTAdapter(pairedDevicesList, this);
        discoveredAdapter = new BTAdapter(discoveredDevicesList, this);

        pairedRecyclerView.setAdapter(pairedAdapter);
        discoveredRecyclerView.setAdapter(discoveredAdapter);

        // 5. 注册 BroadcastReceiver
        // 必须注册 ACTION_FOUND 和 ACTION_DISCOVERY_FINISHED
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(btReceiver, filter);
        Log.d(TAG, "BroadcastReceiver Registered");

        // 6. 绑定按钮事件
        scanButton.setOnClickListener(v -> startDiscovery());

        // 7. 加载已配对设备
        loadPairedDevices();
    }

    @Override
    @SuppressLint("MissingPermission")
    protected void onDestroy() {
        super.onDestroy();
        // 停止扫描并注销接收器
        if (btAdapter != null) {
            btAdapter.cancelDiscovery();
        }
        try {
            unregisterReceiver(btReceiver);
            Log.d(TAG, "BroadcastReceiver Unregistered");
        } catch (IllegalArgumentException e) {
            // 忽略接收器未注册的异常
        }
    }

    /** 加载已配对设备列表 */
    @SuppressLint("MissingPermission")
    private void loadPairedDevices() {
        pairedDevicesList.clear();
        if (btAdapter != null && btAdapter.isEnabled()) {
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    String name = device.getName() != null ? device.getName() : "未知设备";
                    pairedDevicesList.add(new DeviceItem(name, device.getAddress()));
                    Log.d(TAG, "Loaded Paired Device: " + name);
                }
            } else {
                Log.d(TAG, "No paired devices found.");
                // 可以选择在这里显示一个“无配对设备”的占位符
            }
        } else {
            Log.e(TAG, "Bluetooth Adapter is null or disabled during loadPairedDevices");
        }
        pairedAdapter.notifyDataSetChanged();
    }

    /** 启动设备发现 */
    @SuppressLint("MissingPermission")
    private void startDiscovery() {
        if (btAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!btAdapter.isEnabled()) {
            Toast.makeText(this, "请先开启蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        // 如果正在发现，先取消
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }

        // 清空旧列表
        discoveredAdapter.clear();
        titleDiscoveredDevices.setText("正在扫描...");
        setScanning(true);

        // 启动发现
        boolean started = btAdapter.startDiscovery();
        Log.i(TAG, "startDiscovery called. Result: " + started);

        if (started) {
            Toast.makeText(this, "开始扫描...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "无法启动扫描 (系统限制或未授权)", Toast.LENGTH_LONG).show();
            setScanning(false);
        }
    }

    /** 停止发现 (辅助方法) */
    @SuppressLint("MissingPermission")
    private void stopDiscovery() {
        if (btAdapter != null && btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }
    }

    /** 更新 UI 扫描状态 */
    private void setScanning(boolean scanning) {
        scanButton.setEnabled(!scanning);
        discoveryProgress.setVisibility(scanning ? View.VISIBLE : View.GONE);
        scanButton.setText(scanning ? "扫描中..." : "重新扫描");
    }

    /** 检查设备是否已存在于列表中 */
    private boolean isDeviceInList(List<DeviceItem> list, String address) {
        for (DeviceItem item : list) {
            if (item.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    // --- 点击事件 ---
    @Override
    public void onItemClick(DeviceItem item) {
        stopDiscovery(); // 连接前必须停止扫描，否则会严重影响连接速度

        Intent intent = new Intent();
        intent.putExtra("DEVICE_ADDRESS", item.getAddress());
        setResult(Activity.RESULT_OK, intent);

        Log.i(TAG, "Selected device: " + item.getName() + " [" + item.getAddress() + "]");
        finish();
    }
}