package com.example.circularslider;

/**
 * 接口：用于 BTSetup 类通知 Activity 权限/蓝牙适配器是否已准备就绪。
 */
public interface BTSetupCallback {

    /**
     * 当所有蓝牙设置（权限和适配器）都通过时调用。
     */
    void onBTSetupReady();

    /**
     * 当设置失败，需要用户采取行动（例如拒绝权限）时调用。
     * @param reason 失败原因描述。
     */
    void onBTSetupFailed(String reason);
}