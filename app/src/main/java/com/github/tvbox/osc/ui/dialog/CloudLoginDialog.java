package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.cloud.CloudRemoteClient;
import com.github.tvbox.osc.util.FastClickCheckUtil;

import org.jetbrains.annotations.NotNull;

public class CloudLoginDialog extends BaseDialog {

    private TextView tvCloudStatus;
    private EditText inputCloudServer;
    private EditText inputCloudUser;
    private EditText inputCloudPass;

    public CloudLoginDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_cloud_login);
        setCanceledOnTouchOutside(false);
        tvCloudStatus = findViewById(R.id.tvCloudStatus);
        inputCloudServer = findViewById(R.id.inputCloudServer);
        inputCloudUser = findViewById(R.id.inputCloudUser);
        inputCloudPass = findViewById(R.id.inputCloudPass);
        refreshStatus();

        inputCloudServer.setText(CloudRemoteClient.get().getServerUrl());
        if (CloudRemoteClient.get().isLoggedIn()) {
            inputCloudUser.setText(CloudRemoteClient.get().getUsername());
        }

        findViewById(R.id.btnCloudLogin).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            String server = inputCloudServer.getText().toString().trim();
            String user = inputCloudUser.getText().toString().trim();
            String pass = inputCloudPass.getText().toString();
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(getContext(), "请输入用户名和密码", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(getContext(), "正在连接云后端…", Toast.LENGTH_SHORT).show();
            CloudRemoteClient.get().login(server, user, pass, new CloudRemoteClient.LoginCallback() {
                @Override
                public void onSuccess(String username) {
                    Toast.makeText(getContext(), "云遥控已连接", Toast.LENGTH_SHORT).show();
                    refreshStatus();
                    dismiss();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                    refreshStatus();
                }
            });
        });

        findViewById(R.id.btnCloudLogout).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            CloudRemoteClient.get().logout();
            inputCloudPass.setText("");
            Toast.makeText(getContext(), "已退出云遥控", Toast.LENGTH_SHORT).show();
            refreshStatus();
        });
    }

    private void refreshStatus() {
        if (CloudRemoteClient.get().isLoggedIn()) {
            tvCloudStatus.setText("已登录：" + CloudRemoteClient.get().getUsername() + "\n手机登录同一账号即可遥控");
        } else {
            tvCloudStatus.setText("未登录。模拟器后端默认：http://10.0.2.2:3080");
        }
    }
}
