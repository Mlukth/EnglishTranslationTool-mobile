package com.ett.mobile;

import android.os.Bundle;

import com.ett.mobile.plugins.SaveFilePlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 必须在 super.onCreate 之前注册：registerPlugin 只是加入待注册列表，
        // 真正生效发生在 super.onCreate 内部的 load() 消费 initialPlugins 时。
        // 放后面会导致插件不进 bridge，JS 端调用报 "Plugin not found"。
        registerPlugin(SaveFilePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
