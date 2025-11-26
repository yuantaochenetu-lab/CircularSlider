package com.example.circularslider;
// 确保包名与您的项目一致

/**
 * 抽象类：定义 Transceiver 的通用接口和连接状态。
 * (参考 UML 图 5)
 */
public abstract class Transceiver {

    // --- 连接状态常量 (来自 UML 图 5) ---
    public static final int STATE_NOT_CONNECTED = 0; // 设备未连接 [cite: 287]
    public static final int STATE_CONNECTING = 1;    // 设备正在连接 [cite: 288]
    public static final int STATE_CONNECTED = 2;     // 设备已连接 [cite: 289]

    protected int state = STATE_NOT_CONNECTED;
    protected TransceiverListener listener;
    protected FrameProcessor frameProcessor;

    // --- 抽象方法：必须由子类（如 BluetoothManager）实现 ---

    /**
     * 抽象方法：启动连接过程。
     * @param id 要连接的设备标识符（例如蓝牙地址）。
     */
    public abstract void connect(String id); // 对应 UML 图中的 connect(String id) [cite: 224]

    /**
     * 抽象方法：断开连接。
     */
    public abstract void disconnect(); // 对应 UML 图中的 disconnect() [cite: 224]

    /**
     * 抽象方法：发送数据。
     * @param data 要发送的原始字节数据（Payload）。
     */
    public abstract void send(byte[] data); // 对应 UML 图中的 send(byte[] data) [cite: 224]

    // --- 具体方法 ---

    /**
     * 设置监听器，用于报告连接事件和接收到的数据。
     */
    public void setTransceiverListener(TransceiverListener listener) { // 对应 UML 图中的 setTransceiverListener()
        this.listener = listener;
    }

    /**
     * 返回当前连接状态。
     */
    public int getStatus() { // 对应 UML 图中的 getStatus()
        return state;
    }

    /**
     * 附加 FrameProcessor 实例，用于编码和解码。
     * (Q.3.7-2 要求)
     */
    public void attachFrameProcessor(FrameProcessor fp) { // 对应 UML 图中的 attachFrameProcessor() [cite: 307]
        this.frameProcessor = fp;
    }

    /**
     * 分离 FrameProcessor 实例。
     * (Q.3.7-2 要求)
     */
    public void detachFrameProcessor() { // 对应 UML 图中的 detachFrameProcessor() [cite: 307]
        this.frameProcessor = null;
    }

    /**
     * 更改状态并通知监听器。
     * @param newState 新状态。
     */
    protected void updateConnectionStatus(int newState) {
        this.state = newState;
        if (listener != null) {
            listener.onTransceiverConnectionStatusChanged(newState);
        }
    }
}