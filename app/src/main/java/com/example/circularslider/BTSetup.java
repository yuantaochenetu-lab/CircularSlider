package com.example.circularslider;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;

/**
 * 集中管理蓝牙权限和适配器激活的类 (Q.3.3-1)。
 * 目标：在调用蓝牙方法前（最接近调用点），确保所有条件就绪。
 */
public class BTSetup {

    private static final String TAG = "BTSetup";
    private final Activity activity;
    private final BTSetupCallback callback;
    private final BluetoothAdapter btAdapter;

    // 运行时权限数组 (API 31+)
    private final String[] BT_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
            // ACCESS_COARSE_LOCATION 被 Manifest 中的 usesPermissionFlags 规避，
            // 且 API 31+ 仅需 CONNECT/SCAN
    };

    /**
     * 构造函数。
     * @param activity 宿主 Activity。
     * @param callback 结果回调接口。
     */
    public BTSetup(Activity activity, BTSetupCallback callback) {
        this.activity = activity;
        this.callback = callback;

        // 尝试获取 BluetoothAdapter
        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            this.btAdapter = null;
        } else {
            this.btAdapter = bluetoothManager.getAdapter();
        }
    }

    /**
     * 核心方法：检查蓝牙适配器和所有必要的权限是否就绪 (BTReadyBT)。
     * 权限请求和适配器激活必须由调用 Activity 提供的 Launcher 处理。
     * @param requestPermissionLauncher 用于启动权限请求的 Launcher。
     * @param requestEnableBTLauncher 用于启动蓝牙激活 Intent 的 Launcher。
     * @return true 如果所有条件立即满足，false 如果需要异步操作（权限请求或启用适配器）。
     */
    public boolean BTReadyBT(
            ActivityResultLauncher<String[]> requestPermissionLauncher,
            ActivityResultLauncher<Intent> requestEnableBTLauncher)
    {
        // 1. 检查硬件支持
        if (btAdapter == null) {
            callback.onBTSetupFailed("设备不支持蓝牙。");
            return false;
        }

        // 2. 检查运行时权限 (API 23+)
        if (!checkPermissions()) {
            Log.d(TAG, "权限不足，请求权限...");
            // 请求权限 (Launcher 会处理结果并回调 Activity)
            requestPermissionLauncher.launch(BT_PERMISSIONS);
            return false;
        }

        // 3. 检查适配器是否启用
        if (!btAdapter.isEnabled()) {
            Log.d(TAG, "蓝牙未启用，请求启用...");
            // 请求启用蓝牙
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            requestEnableBTLauncher.launch(enableBtIntent);
            return false;
        }

        // 4. 所有条件满足
        Log.d(TAG, "蓝牙已就绪。");
        callback.onBTSetupReady();
        return true;
    }

    /**
     * 检查所有必需的运行时权限是否已被授予。
     */
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // API < 31，只需检查位置权限（如果有）和 BLUETOOTH/BLUETOOTH_ADMIN
            return true; // 假设 Manifest 中的 BLUETOOTH/BLUETOOTH_ADMIN 已处理
        }

        for (String permission : BT_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查蓝牙适配器是否仍然存在且启用。
     */
    public boolean isAdapterEnabled() {
        return btAdapter != null && btAdapter.isEnabled();
    }
}