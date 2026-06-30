package com.github.tvbox.osc.server;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action":
                if (params.get("do") != null) {
                    String action = params.get("do");
                    if ("setting".equals(action)) {
                        String settingKey = params.get("key");
                        String settingValue = params.get("value");
                        if (settingValue == null) {
                            settingValue = "";
                        }
                        return RemoteServer.createJSONResponse(
                                NanoHTTPD.Response.Status.OK,
                                RemoteSettingsHelper.apply(settingKey, settingValue).toString()
                        );
                    }
                    if (mDataReceiver != null) {
                        switch (action) {
                        case "search": {
                            mDataReceiver.onTextReceived(params.get("word").trim());
                            break;
                        }
                        case "api": {
                            mDataReceiver.onApiReceived(params.get("url").trim());
                            break;
                        }
                        case "liveApi": {
                            mDataReceiver.onLiveApiReceived(params.get("url").trim());
                            break;
                        }
                        case "danmuApi": {
                            mDataReceiver.onDanmuApiReceived(params.get("url").trim());
                            break;
                        }
                        case "push": {
                            mDataReceiver.onPushReceived(params.get("url").trim());
                            break;
                        }
                        case "key": {
                            String key = params.get("key");
                            if (key != null) {
                                mDataReceiver.onKeyReceived(key.trim());
                            }
                            break;
                        }
                        }
                    }
                }
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }
}
