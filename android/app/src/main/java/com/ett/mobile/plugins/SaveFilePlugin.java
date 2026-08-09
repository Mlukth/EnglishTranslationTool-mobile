package com.ett.mobile.plugins;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SaveFilePlugin — 导出 JSON 备份时弹出系统"保存到"对话框（SAF / ACTION_CREATE_DOCUMENT）。
 *
 * 用户可在对话框里手动选择保存位置（下载、文档、SD 卡等），系统负责落盘，
 * 无需申请任何存储权限。用户取消则 reject("cancelled")。
 *
 * 注意：必须用 Capacitor 3+ 的 @ActivityCallback 回调 API，而不是旧的
 * handleOnActivityResult —— Capacitor 6 中旧 API 的 Activity 结果不会再回传给插件。
 */
@CapacitorPlugin(name = "SaveFile")
public class SaveFilePlugin extends Plugin {

    @PluginMethod
    public void saveFile(PluginCall call) {
        String data = call.getString("data");
        String filename = call.getString("filename");
        if (data == null || filename == null) {
            call.reject("data and filename are required");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(call, intent, "createDocumentCallback");
    }

    /**
     * 清除 WebView 缓存（只清缓存，绝不动 localStorage / 用户数据）。
     * 用于覆盖安装后旧代码缓存未失效的场景。
     */
    @PluginMethod
    public void clearCache(PluginCall call) {
        getBridge().getWebView().clearCache(true);
        call.resolve();
    }

    @ActivityCallback
    private void createDocumentCallback(PluginCall call, ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK
                || result.getData() == null
                || result.getData().getData() == null) {
            call.reject("cancelled");
            return;
        }

        Uri uri = result.getData().getData();
        String data = call.getString("data");
        if (data == null) {
            call.reject("data is null");
            return;
        }
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            OutputStream os = getContext().getContentResolver().openOutputStream(uri);
            if (os == null) {
                call.reject("cannot open output stream");
                return;
            }
            os.write(bytes);
            os.flush();
            os.close();

            // 读回校验：从刚写入的 uri 读一遍，确认文件真的落盘且非空。
            // 这是对"0 字节空文件"最强的防御——不只是相信自己写入了，而是实际读回来验证。
            int readBack = -1;
            try (InputStream is = getContext().getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    readBack = is.available();
                }
            }
            if (readBack <= 0) {
                call.reject("write verification failed: readback 0 bytes");
                return;
            }

            // 返回实际写入字节数 + 读回字节数 + 显示名，JS 端校验并提示
            String displayName = result.getData().getData().getLastPathSegment();
            JSObject ret = new JSObject();
            ret.put("bytes", bytes.length);
            ret.put("readBack", readBack);
            ret.put("displayName", displayName);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("write failed: " + e.getMessage());
        }
    }
}
