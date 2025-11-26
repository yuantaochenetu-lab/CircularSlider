package com.example.circularslider;
// 确保包名与您的项目一致

/**
 * 接口：用于将 Transceiver 的事件和接收到的数据传递给监听者（如 OscilloManager）。
 * (参考 UML 图 5 中的 TransceiverListener 接口)
 */
public interface TransceiverListener {

    /**
     * 当 Transceiver 收到完整帧并解码后调用，将解码后的数据传递给业务逻辑层。
     * @param data FrameProcessor.Data 对象，包含命令ID、参数和有效性。
     */
    void onTransceiverDataReceived(FrameProcessor.Data data); // 对应 UML 图中的 onTransceiverDataReceived()

    /**
     * 当连接建立、断开或丢失时，报告连接状态的变化。
     * @param status 当前的连接状态常量（例如 STATE_CONNECTED）。
     */
    void onTransceiverConnectionStatusChanged(int status); // 对应 UML 图中的 onTransceiverConnectionLost() 和 onTransceiverUnableToConnect() 的通用处理

    // TODO: 您可以根据需要添加更多特定方法，例如 onTransceiverError(String error)。
}