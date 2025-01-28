package com.onedongua.reminoteexporter;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.onedongua.reminoteexporter.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {
    private enum OutMode {
        HTML, TXT
    }

    private OkHttpClient client;
    private String cookie;
    private OutMode outMode = OutMode.HTML;
    private final List<String> noteUrls = new ArrayList<>();
    private String exportDir = "/sdcard/小米便签导出器/";
    private WebView webView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.onedongua.reminoteexporter.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        client = new OkHttpClient();
        webView = binding.webView;

        // 设置 WebView 配置
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://i.mi.com/note/h5#/");

        Button refreshButton = binding.refresh;
        Button checkButton = binding.check;
        Button settingButton = binding.setting;

        refreshButton.setOnClickListener(v -> refreshWebView());
        settingButton.setOnClickListener(v -> showSettingsPopup());
        checkButton.setOnClickListener(v -> checkNotes());

        CookieManager cookieManager = CookieManager.getInstance();
        cookie = cookieManager.getCookie("https://i.mi.com/note/h5#/");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void refreshWebView() {
        webView.reload();
    }

    private void showSettingsPopup() {
        LinearLayout settingLayout = new LinearLayout(this);
        settingLayout.setOrientation(LinearLayout.VERTICAL);

        CheckBox cbHtml = new CheckBox(this);
        cbHtml.setText("导出为 HTML");
        cbHtml.setChecked(true);

        CheckBox cbTxt = new CheckBox(this);
        cbTxt.setText("导出为 TXT");

        settingLayout.addView(cbHtml);
        settingLayout.addView(cbTxt);

        PopupWindow popup = new PopupWindow(settingLayout, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        popup.setFocusable(true);
        popup.showAtLocation(findViewById(android.R.id.content), 0, 0, 0);

        cbHtml.setOnClickListener(v -> {
            if (cbHtml.isChecked()) {
                cbTxt.setChecked(false);
                outMode = OutMode.HTML;
            }
        });

        cbTxt.setOnClickListener(v -> {
            if (cbTxt.isChecked()) {
                cbHtml.setChecked(false);
                outMode = OutMode.TXT;
            }
        });
    }

    private void checkNotes() {
        new File(exportDir).mkdirs();

        String url = "https://i.mi.com/note/full/page/?limit=200";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", cookie)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> showToast("步骤1失败: 请求失败"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        String responseData = responseBody.string();
                        parseNotesJson(responseData);
                    } else {
                        runOnUiThread(() -> showToast("步骤1失败: 数据解析错误"));
                    }
                } else {
                    runOnUiThread(() -> showToast("步骤1失败: 请求失败 (" + response.code() + ")"));
                }
            }
        });
    }

    private void parseNotesJson(String json) {
        Gson gson = new Gson();
        NoteResponse noteResponse = gson.fromJson(json, NoteResponse.class);
        if (noteResponse.code == 0) {
            for (NoteResponse.NoteEntry entry : noteResponse.data.entries) {
                noteUrls.add("https://i.mi.com/note/note/" + entry.id);
            }
            fetchNoteDetails();
        } else {
            runOnUiThread(() -> showToast("步骤1失败: 数据解析错误"));
        }
    }

    private void fetchNoteDetails() {
        for (int i = 0; i < noteUrls.size(); i++) {
            String url = noteUrls.get(i);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookie)
                    .build();

            final int index = i;
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> showToast("步骤2失败: 请求失败"));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        ResponseBody responseBody = response.body();
                        if (responseBody != null) {
                            String content = responseBody.string();
                            try {
                                processNoteContent(content, index);
                                runOnUiThread(() -> showToast("已导出至 " + exportDir));
                            } catch (IOException e) {
                                runOnUiThread(() -> showToast("文件保存失败"));
                            }
                        }

                    } else {
                        runOnUiThread(() -> showToast("步骤2失败: 请求失败 (" + response.code() + ")"));
                    }
                }
            });
        }
    }

    private void processNoteContent(String content, int index) throws IOException {
        Gson gson = new Gson();
        NoteDetail noteDetail = gson.fromJson(content, NoteDetail.class);

        String note = noteDetail.data.entry.content;
        note = note.replaceAll("☺ (.*?)\\n", "<img src=\"https://i.mi.com/file/full?type=note_img&fileid=$1\">");

        String fileExt;

        if (outMode == OutMode.HTML) {
            fileExt = ".html";
            note = note.replaceAll("\n", "<br>");
        } else if (outMode == OutMode.TXT) {
            fileExt = ".txt";
            note = note.replaceAll("<.*?>", "");
            note = note.replaceAll("&amp;", "&")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">");
        } else {
            runOnUiThread(() -> showToast("步骤2失败: 未知输出模式"));
            return;
        }

        String title = noteDetail.data.entry.extraInfo.title;
        if (title == null || title.isEmpty()) {
            title = "无标题";
        }

        File file = new File(exportDir, index + "." + title + fileExt);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(note);
        }
    }

    private static class NoteResponse {
        int code;
        Data data;

        static class Data {
            List<NoteEntry> entries;
        }

        static class NoteEntry {
            String id;
        }
    }

    private static class NoteDetail {
        Data data;

        static class Data {
            Entry entry;

            static class Entry {
                String content;
                ExtraInfo extraInfo;
            }

            static class ExtraInfo {
                String title;
            }
        }
    }
}
