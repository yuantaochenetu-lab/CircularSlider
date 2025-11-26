package com.example.circularslider;

/**
 * 用于表示一个蓝牙设备的数据模型。
 */
public class DeviceItem {
    private final String name;
    private final String address;

    public DeviceItem(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }
}