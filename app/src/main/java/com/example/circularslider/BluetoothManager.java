package com.example.circularslider;
// 确保包名与您的项目一致

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 实现 Transceiver 抽象类的蓝牙版本。
 * 负责蓝牙连接、线程管理和数据读写。
 * (Q.3.5-2, Q.3.5-4, Q.3.5-6, Q.3.7-3 要求)
 */
public class BluetoothManager extends Transceiver {

    private static final String TAG = "BluetoothManager";

    // SPP UUID (Serial Port Profile): "00001101-0000-1000-8000-00805F9B34FB"
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;

    // 线程引用
    private ConnectThread connectThread;
    private ReadingThread readingThread;
    private WritingThread writingThread;

    // 发送 (Tx) 缓冲区 (Q.3.5-5)。容量 2K 字节。
    private ByteRingBuffer txBuffer = new ByteRingBuffer(2048);

    public BluetoothManager() {
        // 获取默认蓝牙适配器
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // ----------------------------------------------------------------------
    // --- 内部线程类 ---
    // ----------------------------------------------------------------------

    /**
     * ConnectThread (Q.3.5-3): 负责处理蓝牙连接的线程。
     */
    private class ConnectThread extends Thread {
        private final BluetoothDevice device;

        // 压制权限警告：假设 BTSetup 已在 connect() 调用前处理了 BLUETOOTH_SCAN 权限
        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            // 确保连接前取消任何正在进行的设备发现
            if (btAdapter != null && btAdapter.isDiscovering()) {
                btAdapter.cancelDiscovery();
            }
        }

        public void run() {
            Log.i(TAG, "ConnectThread started for " + device.getAddress());
            updateConnectionStatus(STATE_CONNECTING);

            try {
                // 1. 创建 RFCOMM Socket
                btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);

                // 2. 连接到 Socket (阻塞调用)
                btSocket.connect();

                // 3. 连接成功，启动数据传输线程
                Log.i(TAG, "Connection successful. Starting data threads.");

                startConnectedThreads(btSocket);

                updateConnectionStatus(STATE_CONNECTED);
            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                // 无法连接，报告断开
                updateConnectionStatus(STATE_NOT_CONNECTED);
                closeSocket();
            } finally {
                connectThread = null;
            }
        }

        /**
         * 外部调用：取消正在进行的连接，关闭 Socket。
         */
        public void cancel() {
            closeSocket();
        }
    }

    /**
     * ReadingThread (UML 图 5): 负责从 InputStream 异步读取数据并传递给 FrameProcessor。
     */
    private class ReadingThread extends Thread {
        private final InputStream mmInStream;
        private volatile boolean running = true;

        public ReadingThread(BluetoothSocket socket) throws IOException {
            mmInStream = socket.getInputStream();
        }

        public void run() {
            Log.i(TAG, "ReadingThread started.");

            while (running) {
                try {
                    // 读取单个字节 (阻塞调用)
                    byte data = (byte) mmInStream.read();

                    if (frameProcessor != null) {
                        // 传递给 FrameProcessor 进行解码
                        frameProcessor.fromFrame(data);

                        // 检查是否有完整帧等待读取
                        if (frameProcessor.framePending()) {
                            FrameProcessor.Data decoded = frameProcessor.getData();
                            if (listener != null) {
                                // 传递给监听器 (OscilloManager 将处理线程切换到 UI)
                                listener.onTransceiverDataReceived(decoded);
                            }
                        }
                    }
                } catch (IOException e) {
                    // 连接丢失或 Socket 关闭
                    Log.e(TAG, "Input stream was disconnected or closed.", e);
                    if(running) {
                        // 只有在非手动断开时才报告连接丢失
                        updateConnectionStatus(STATE_NOT_CONNECTED);
                    }
                    running = false;
                    break;
                }
            }
        }

        public void cancel() {
            running = false;
        }
    }

    /**
     * WritingThread (Q.3.5-6): 负责从 ByteRingBuffer 中读取数据并写入 OutputStream。
     */
    private class WritingThread extends Thread {
        private final OutputStream mmOutStream;
        private volatile boolean running = true;

        public WritingThread(BluetoothSocket socket) throws IOException {
            mmOutStream = socket.getOutputStream();
        }

        public void run() {
            Log.i(TAG, "WritingThread started.");

            while (running) {
                try {
                    byte dataToSend;

                    // 使用 txBuffer 作为锁对象
                    synchronized (txBuffer) {
                        // 1. 等待数据进入 Tx Buffer
                        while (txBuffer.bytesToRead() == 0 && running) {
                            // 使用 wait() 释放锁，进入等待状态，高效地等待 notify() 唤醒
                            txBuffer.wait();
                        }

                        if (!running) break;

                        // 2. 从 Buffer 中提取数据
                        dataToSend = txBuffer.get();
                    }

                    // 3. 写入 OutputStream (阻塞调用)
                    mmOutStream.write(dataToSend);
                    mmOutStream.flush();

                } catch (IOException e) {
                    Log.e(TAG, "Output stream write error.", e);
                    if(running) {
                        updateConnectionStatus(STATE_NOT_CONNECTED);
                    }
                    running = false;
                    break;
                } catch (InterruptedException e) {
                    Log.w(TAG, "WritingThread interrupted.", e);
                    running = false;
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void cancel() {
            running = false;
        }
    }

    // ----------------------------------------------------------------------
    // --- 抽象方法实现 (Transceiver) ---
    // ----------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    @Override
    public void connect(String deviceAddress) { // Q.3.5-4
        if (state == STATE_CONNECTED || state == STATE_CONNECTING) {
            Log.w(TAG, "Already connected or connecting.");
            return;
        }

        if (btAdapter == null || !btAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth adapter not available or not enabled.");
            updateConnectionStatus(STATE_NOT_CONNECTED);
            return;
        }

        // 1. 停止所有现有连接/线程
        disconnect();

        // 2. 获取目标设备
        BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);

        // 3. 启动新的连接线程
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    @Override
    public void disconnect() { // Q.3.5-4
        Log.i(TAG, "Disconnecting Bluetooth connection.");

        // 1. 停止所有线程
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (readingThread != null) {
            readingThread.cancel();
            readingThread = null;
        }
        if (writingThread != null) {
            writingThread.cancel();
            writingThread = null;
        }

        // 2. 关闭 Socket
        closeSocket();

        // 3. 报告断开连接
        updateConnectionStatus(STATE_NOT_CONNECTED);
    }

    @Override
    public void send(byte[] data) { // Q.3.7-3
        if (frameProcessor == null) {
            Log.e(TAG, "FrameProcessor is not attached. Cannot send data.");
            return;
        }
        if (state != STATE_CONNECTED || writingThread == null) {
            Log.e(TAG, "Not connected or WritingThread not running. Cannot send data.");
            return;
        }

        // 1. 使用 FrameProcessor 编码数据 (Q.3.7-1)
        byte[] frameToSend = frameProcessor.toFrame(data);

        // 2. 将 frameToSend 放入 Tx Buffer
        synchronized (txBuffer) {
            int bytesPushed = txBuffer.put(frameToSend);

            if (bytesPushed < frameToSend.length) {
                Log.e(TAG, "Tx Buffer overflow. Dropping " + (frameToSend.length - bytesPushed) + " bytes.");
            }

            // 3. 通知 WritingThread 有新数据
            txBuffer.notify();
        }

        Log.d(TAG, "Frame encoded and pushed to Tx buffer. Size: " + frameToSend.length);
    }

    // ----------------------------------------------------------------------
    // --- 实用方法 ---
    // ----------------------------------------------------------------------

    /**
     * 启动连接成功后必须运行的读写线程。
     */
    private synchronized void startConnectedThreads(BluetoothSocket socket) throws IOException {
        // 取消所有旧的连接/读写线程
        if (readingThread != null) readingThread.cancel();
        if (writingThread != null) writingThread.cancel();

        // 启动新的读写线程
        readingThread = new ReadingThread(socket);
        readingThread.start();

        writingThread = new WritingThread(socket);
        writingThread.start();
    }

    /**
     * 关闭蓝牙 Socket。
     */
    private void closeSocket() {
        if (btSocket != null) {
            try {
                btSocket.close();
                btSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Failed to close socket", e);
            }
        }
    }
}