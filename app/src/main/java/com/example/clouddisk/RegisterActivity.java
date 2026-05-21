package com.example.clouddisk;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {
    // 移除本地 db 引用

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        EditText etUser = findViewById(R.id.re_username);
        EditText etPass = findViewById(R.id.re_password);
        EditText etConfirm = findViewById(R.id.re_confirm_password);
        EditText etInvite = findViewById(R.id.re_invite_code);
        android.widget.CheckBox cb2fa = findViewById(R.id.cb_use_2fa);
        Button btnReg = findViewById(R.id.btn_do_register);

        btnReg.setOnClickListener(v -> {
            String name = etUser.getText().toString().trim();
            String pass = etPass.getText().toString().trim();
            String confirm = etConfirm.getText().toString().trim();
            String invite = etInvite.getText().toString().trim();
            boolean use2FA = cb2fa.isChecked();

            if (name.isEmpty() || pass.isEmpty() || invite.isEmpty()) {
                Toast.makeText(this, "用户名、密码或邀请码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!pass.equals(confirm)) {
                Toast.makeText(this, "两次密码输入不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                try {
                    OkHttpClient client = new OkHttpClient();
                    JSONObject json = new JSONObject();
                    json.put("username", name);
                    json.put("password", pass);
                    json.put("inviteCode", invite);
                    json.put("use2FA", use2FA);

                    RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
                    Request request = new Request.Builder()
                            .url(Config.getBackendUrl() + "/register")
                            .post(body)
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        String respStr = response.body().string();
                        if (response.isSuccessful()) {
                            if (use2FA) {
                                try {
                                    JSONObject respJson = new JSONObject(respStr);
                                    org.json.JSONArray secret = respJson.getJSONArray("secret");
                                    
                                    JSONObject qrJson = new JSONObject();
                                    qrJson.put("issuer", "CloudDisk");
                                    qrJson.put("account", name);
                                    qrJson.put("secret", secret);
                                    
                                    // 自动下载 CDAuthy
                                    downloadCDAuthy();
                                    
                                    runOnUiThread(() -> show2FAQRCode(name, qrJson.toString()));
                                } catch (org.json.JSONException e) {
                                    runOnUiThread(() -> Toast.makeText(this, "2FA数据解析失败，请检查服务器版本。返回内容: " + respStr, Toast.LENGTH_LONG).show());
                                }
                            } else {
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "注册成功，云端已同步！", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            }
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "注册失败: " + respStr, Toast.LENGTH_SHORT).show());
                        }
                    }
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    runOnUiThread(() -> Toast.makeText(this, "异常: " + errorMsg, Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }

    private void downloadCDAuthy() {
        String url = "https://gh-proxy.org/https://github.com/SmartZWZ/CDAuthy/releases/download/V1.0/CDAuthy-V1.0.apk";
        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
        request.setTitle("下载 CDAuthy 验证器");
        request.setDescription("配套 CloudDisk 安全盾使用");
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "CDAuthy-V1.0.apk");

        android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
            runOnUiThread(() -> android.widget.Toast.makeText(this, "正在为您下载配套验证器 CDAuthy...", android.widget.Toast.LENGTH_LONG).show());
        }
    }

    private void show2FAQRCode(String username, String secretJson) {
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=500x500&data=" + android.net.Uri.encode(secretJson);

        android.widget.ImageView imageView = new android.widget.ImageView(this);
        // 设置固定大小，防止在某些机型上显示为 0
        int size = (int) (250 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(size, size);
        lp.gravity = android.view.Gravity.CENTER;
        imageView.setLayoutParams(lp);

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        imageView.setPadding(padding, padding, padding, padding);

        // 使用 Glide 加载并强制显示
        com.bumptech.glide.Glide.with(this)
                .load(qrUrl)
                .placeholder(android.R.drawable.progress_indeterminate_horizontal) // 加载时占位
                .error(android.R.drawable.stat_notify_error) // 失败时图标
                .into(imageView);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("开启安全盾")
                .setMessage("账号: " + username + "\n请使用 CDAuthy 扫描二维码绑定。")
                .setView(imageView)
                .setCancelable(false)
                .setPositiveButton("我已完成扫码并保存", (dialog, which) -> finish())
                .show();
    }

    private boolean createFolderForUser(String username) {
        try {
            OkHttpClient client = new OkHttpClient();
            // 直接在 WebDAV 根目录下建立同名文件夹
            String url = Config.getWebDavUrl() + username;
            Request request = new Request.Builder()
                    .url(url)
                    .method("MKCOL", null)
                    .header("Authorization", okhttp3.Credentials.basic(Config.WEBDAV_USER, Config.WEBDAV_PASS))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                // 201 创建成功，405 已存在（也视为成功）
                return response.isSuccessful() || response.code() == 405;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
