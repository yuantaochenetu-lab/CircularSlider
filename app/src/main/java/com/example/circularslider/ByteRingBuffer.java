package com.example.circularslider;

import java.io.IOException;

/**
 * ByteRingBuffer 类：用于管理循环字节缓冲区，以实现非阻塞数据传输 (Q.3.5-5)。
 * 这是一个线程安全的实现，所有存取方法都是同步的。
 *
 * 【线程安全解释】：
 * 缓冲区(buffer)、读索引(readIndex)、写索引(writeIndex)和计数(count)都是共享资源。
 * 通过在 put() 和 get() 方法上使用 synchronized 关键字，我们确保了在任何给定时间，
 * 只有一个线程可以执行这些方法，从而避免了数据竞争和不一致的状态。
 * * 问：为什么需要线程安全？
 * 答：因为主线程（调用send）和WritingThread（读取并发送）会同时访问和修改这个buffer。
 */
public class ByteRingBuffer {

    private final byte[] buffer;
    private final int capacity;

    // 读写指针
    private int readIndex = 0;
    private int writeIndex = 0;

    // 当前缓冲区中存储的字节数
    private int count = 0;

    /**
     * 构造函数。
     * @param size 缓冲区的大小。
     */
    public ByteRingBuffer(int size) {
        this.capacity = size;
        this.buffer = new byte[size];
    }

    /**
     * 向缓冲区中放入一个字节 (put byte)。
     * @param b 要放入的字节。
     * @return true 如果成功放入，false 如果缓冲区已满。
     */
    public synchronized boolean put(byte b) {
        if (count == capacity) {
            // 缓冲区已满
            return false;
        }

        buffer[writeIndex] = b;
        writeIndex = (writeIndex + 1) % capacity;
        count++;

        // 唤醒等待的线程 (例如 WritingThread 的 wait() 调用)
        notify();

        return true;
    }

    /**
     * 向缓冲区中放入一个字节数组 (put byte[])。
     * @param data 要放入的字节数组。
     * @return 实际放入的字节数。
     */
    public synchronized int put(byte[] data) {
        int bytesPut = 0;
        for (byte b : data) {
            if (count < capacity) {
                // 使用 put(byte) 的逻辑，但避免重复调用 synchronized
                buffer[writeIndex] = b;
                writeIndex = (writeIndex + 1) % capacity;
                count++;
                bytesPut++;
            } else {
                // 缓冲区满了
                break;
            }
        }

        // 只有当有数据被放入时才通知
        if (bytesPut > 0) {
            notify();
        }

        return bytesPut;
    }

    /**
     * 从缓冲区中取出一个字节 (get)。
     * 【注意】此方法在缓冲区为空时会抛出异常。
     * WritingThread 在调用此方法前必须确保 count > 0。
     * @return 取出的字节。
     * @throws IOException 如果缓冲区为空。
     */
    public synchronized byte get() throws IOException {
        if (count == 0) {
            // 缓冲区为空，这是不应该发生的情况，因为 WritingThread 在调用前会检查并等待
            throw new IOException("Buffer is empty.");
        }

        byte b = buffer[readIndex];
        readIndex = (readIndex + 1) % capacity;
        count--;

        // 如果 put() 也使用了 wait()，这里可以 notify() 唤醒生产者，但通常只唤醒消费者。

        return b;
    }

    /**
     * 返回缓冲区中可读取的字节数 (bytesToRead)。
     */
    public synchronized int bytesToRead() {
        return count;
    }

    /**
     * 返回缓冲区中可用的空闲空间。
     */
    public synchronized int availableSpace() {
        return capacity - count;
    }
}