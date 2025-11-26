package com.example.circularslider;

import android.util.Log;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.MutableLiveData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * OscilloManager 类：实现业务逻辑 (Model)，并继承 ViewModel 保持连接状态 (Q.3.6-1)。
 * 它作为 TransceiverListener 接收数据和状态变化。
 */
public class OscilloManager extends ViewModel implements TransceiverListener {

    private static final String TAG = "OscilloManager";

    // Q.3.6-2: 用于通知 UI 层的监听器列表 (现在是抽象类类型)
    private final List<OscilloEventsListener> listeners = new ArrayList<>();

    // Q.3.6-1: 持有 Transceiver 实例 (这里是 BluetoothManager)
    private final Transceiver transceiver;

    /**
     * 构造函数：初始化通信管理器。
     * OscilloManager 在这里创建并配置 BluetoothManager。
     */
    public OscilloManager() {
        // 使用具体的实现类：BluetoothManager
        this.transceiver = new BluetoothManager();

        // 注册自身作为 Transceiver 的监听器
        this.transceiver.setTransceiverListener(this);
    }

    // --------------------------------------------------------
    // --- 外部访问方法 ---
    // --------------------------------------------------------

    /**
     * 返回 Transceiver 实例。用于 MainActivity 附加 FrameProcessor。
     */
    public Transceiver getTransceiver() {
        return transceiver;
    }

    /**
     * Q.3.6-4: 设置校准信号的占空比 (Calibration Duty Cycle)。
     * ID: 0x0A (Table 1)
     * 参数: dutycycle(1 byte) 0-100.
     * @param alpha 占空比值 (0.0 到 1.0)。
     */
    public void setCalibrationDutyCyle(float alpha) { // Q.3.6-4
        if (transceiver.getStatus() != Transceiver.STATE_CONNECTED) {
            Log.w(TAG, "Not connected. Command setCalibrationDutyCyle ignored.");
            return;
        }

        // 将 float (0.0-1.0) 转换为 int (0-100)
        int dutyCycleInt = (int) (alpha * 100);
        if (dutyCycleInt < 0) dutyCycleInt = 0;
        if (dutyCycleInt > 100) dutyCycleInt = 100;

        // 1. 构建 Payload (命令ID 0x0A + 参数 dutycycle)
        byte commandId = (byte) 0x0A;
        byte dutyCycleByte = (byte) dutyCycleInt;

        byte[] payload = new byte[2]; // 1 byte ID + 1 byte Param
        payload[0] = commandId;
        payload[1] = dutyCycleByte;

        // 2. 通过 Transceiver 发送 Payload
        transceiver.send(payload);
        Log.i(TAG, "Sending setCalibrationDutyCyle: " + dutyCycleInt + "%");
    }

    /**
     * Q.3.6-2: 附加 OscilloEventsListener (UI)。
     */
    public void addListener(OscilloEventsListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        // 立即报告当前状态给新监听器
        listener.onConnectionStatusChange(transceiver.getStatus());
    }

    /**
     * Q.3.6-2: 分离 OscilloEventsListener。
     */
    public void removeListener(OscilloEventsListener listener) {
        listeners.remove(listener);
    }

    /**
     * 启动连接。
     * @param deviceAddress 目标设备的蓝牙地址。
     */
    public void connect(String deviceAddress) {
        transceiver.connect(deviceAddress);
    }

    /**
     * Q.3.8-2: 断开连接 (由 onDestroy 调用)。
     */
    public void disconnect() {
        transceiver.disconnect();
    }

    // --------------------------------------------------------
    // --- TransceiverListener 实现 (接收来自 BluetoothManager 的事件) ---
    // --------------------------------------------------------

    /**
     * 当 Transceiver 收到完整帧数据时调用。
     */
    @Override
    public void onTransceiverDataReceived(FrameProcessor.Data data) {
        // OscilloManager 处理接收到的数据，例如：
        // 1. 检查数据有效性：data.getFrameValidity()
        // 2. 根据 Command ID (data.getId()) 处理不同的命令，例如 dataTransfert (0x8F)

        Log.d(TAG, "Data Received. ID: 0x" + String.format("%02X", data.getId()) +
                ", Valid: " + data.getFrameValidity());

        // 通知所有 UI 监听器 (Q.3.6-2)
        for (OscilloEventsListener listener : listeners) {
            listener.onDataReceived(data);
        }
    }

    /**
     * 当连接状态发生变化时调用。
     */
    @Override
    public void onTransceiverConnectionStatusChanged(final int status) {
        Log.i(TAG, "Connection Status Changed to: " + status);

        // Q.3.6-3: 必须将 UI 更新推送到 UI 线程。
        for (OscilloEventsListener listener : listeners) {
            // 假设 listener (MainActivity) 会处理线程切换
            listener.onConnectionStatusChange(status);
        }
    }

    // --------------------------------------------------------
    // --- ViewModel 生命周期 ---
    // --------------------------------------------------------

    @Override
    protected void onCleared() {
        super.onCleared();
        // 当 ViewModel 被销毁时，确保断开连接以释放资源
        transceiver.disconnect();
        listeners.clear();
        Log.d(TAG, "OscilloManager onCleared. Disconnected.");
    }
}