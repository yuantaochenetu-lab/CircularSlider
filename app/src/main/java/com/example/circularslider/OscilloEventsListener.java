package com.example.circularslider;

/**
 * 抽象类：用于 OscilloManager 通知 UI (MainActivity) 连接状态的变化和接收到的数据。
 * (替代之前的接口，以满足使用“类”的需求，但仍保留抽象方法实现解耦。Q.3.6-2 要求)
 */
public abstract class OscilloEventsListener {

    /**
     * 抽象方法：当连接状态发生变化时调用。
     * @param status BluetoothManager.STATE_CONNECTED 等状态常量。
     */
    public abstract void onConnectionStatusChange(int status);

    /**
     * 抽象方法：当收到并处理完有效数据帧时调用。
     * @param data 从 FrameProcessor 接收的已处理数据 (例如，采集缓冲区)。
     */
    public abstract void onDataReceived(FrameProcessor.Data data);
}