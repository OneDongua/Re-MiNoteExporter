package com.onedongua.reminoteexporter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.onedongua.reminoteexporter.databinding.ActivityMainBinding;
import com.onedongua.reminoteexporter.databinding.DialogProgressBinding;
import com.onedongua.reminoteexporter.databinding.DialogSettingsBinding;
import com.onedongua.reminoteexporter.model.ExtraInfo;
import com.onedongua.reminoteexporter.model.NoteDetail;
import com.onedongua.reminoteexporter.model.NoteEntry;
import com.onedongua.reminoteexporter.model.NoteFolder;
import com.onedongua.reminoteexporter.model.NoteResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@SuppressLint({"SetTextI18n", "SimpleDateFormat", "SetJavaScriptEnabled"})
public class MainActivity extends AppCompatActivity {

    private enum OutMode {
        HTML, TXT
    }

    private final int REQUEST_CODE_DIRECTORY = 1;
    private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(5);
    private Uri uri;
    private DocumentFile folder;
    private DocumentFile newFolder;
    private DocumentFile imageFolder;
    private OkHttpClient client;
    private CookieManager cookieManager;
    private String cookie;
    private OutMode outMode = OutMode.HTML;
    private boolean downloadImages = false;
    private boolean useFolders = true;
    private final List<String> noteUrls = new ArrayList<>();
    private final Map<String, String> folderNames = new HashMap<>();
    private final Map<String, DocumentFile> folderDirs = new HashMap<>();
    private final Map<String, DocumentFile> folderImageDirs = new HashMap<>();
    private Button checkButton;
    private Button selectButton;
    private Button viewLastButton;
    private WebView webView;
    private String folderName;
    private final Map<Integer, Integer> handledImages = new ConcurrentHashMap<>();
    private int completedRequests = 0;
    private int totalImages = 0;
    private int completedImages = 0;
    private BottomSheetDialog progressDialog;
    private DialogProgressBinding progressBinding;
    private Toast currentToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        client = new OkHttpClient();
        webView = binding.webView;

        // 设置 WebView 配置
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://i.mi.com/note/h5#/");
        cookieManager = CookieManager.getInstance();

        Button refreshButton = binding.refresh;
        checkButton = binding.check;
        Button settingButton = binding.setting;
        selectButton = binding.select;
        viewLastButton = binding.viewLastRecord;

        refreshButton.setOnClickListener(v -> refreshWebView());
        settingButton.setOnClickListener(v -> showSettingsPopup());
        checkButton.setOnClickListener(v -> check());
        selectButton.setOnClickListener(v -> openDirectory());
        viewLastButton.setOnClickListener(v -> {
            if (progressDialog != null) {
                progressDialog.show();
            }
        });

        // 线程池的生命周期管理
        Runtime.getRuntime().addShutdownHook(new Thread(scheduledExecutorService::shutdown));

        showInfoDialog();
    }

    private void showInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_app)
                .setMessage("""
                        作者：一個冬瓜
                        1. 本软件仅供学习交流使用，请在 24 小时内删除本应用，一切因使用本软件产生的后果与原作者无关
                        2. 本软件均使用小米官方云服务(i.mi.com)的 API 获取数据，不存在第三方 API 的使用
                        3. 请勿从除 酷安 和 Github Release 以外的渠道获取本软件
                        4. 本软件尊重您的隐私，仅需要最基础的网络权限即可正常使用
                        5. 本软件永远不会也不能收集或修改您的账号、密码、隐私信息等数据，您可以自行查阅软件源代码验证
                        6. 本软件已开源，项目地址: https://github.com/OneDongua/Re-MiNoteExporter
                        7. 本软件使用了以下开源项目，在此鸣谢:
                        Material Components for Android: https://github.com/material-components/material-components-android
                         Licence: Apache 2.0
                        Gson: https://github.com/google/gson
                         Licence: Apache 2.0
                        OkHttp: https://github.com/square/okhttp
                         Licence: Apache 2.0
                        """)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    public void openDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_DIRECTORY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQUEST_CODE_DIRECTORY
                && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                uri = resultData.getData();
                if (uri != null) {
                    showToast("你已选择: " + uri.getPath());
                    selectButton.setText("重新选择目录");
                } else {
                    showToast("选择失败");
                }
            }
        }
    }

    private void showToast(String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
    }

    private void showProgressDialog() {
        progressDialog = new BottomSheetDialog(this);
        progressBinding = DialogProgressBinding.inflate(getLayoutInflater());
        progressDialog.setContentView(progressBinding.getRoot());
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);

        progressBinding.layoutImageProgress.setVisibility(downloadImages ? View.VISIBLE : View.GONE);

        progressBinding.textLogToggle.setOnClickListener(v -> {
            if (progressBinding.scrollLog.getVisibility() == View.VISIBLE) {
                progressBinding.scrollLog.setVisibility(View.GONE);
                progressBinding.textLogToggle.setText(R.string.view_log);
            } else {
                progressBinding.scrollLog.setVisibility(View.VISIBLE);
                progressBinding.textLogToggle.setText(R.string.hide_log);
            }
        });

        progressDialog.show();
    }

    private void updateLog(String message) {
        runOnUiThread(() -> {
            if (progressBinding != null) {
                progressBinding.textLog.append(message + "\n");
                progressBinding.scrollLog.post(() -> progressBinding.scrollLog.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private synchronized void updateNoteProgress() {
        runOnUiThread(() -> {
            if (progressBinding != null) {
                progressBinding.progressNote.setMax(noteUrls.size());
                progressBinding.progressNote.setProgress(completedRequests);
                progressBinding.textNoteProgress.setText(getString(R.string.note_progress, completedRequests, noteUrls.size()));
            }
        });
    }

    private synchronized void updateImageProgress() {
        runOnUiThread(() -> {
            if (progressBinding != null && downloadImages) {
                progressBinding.progressImage.setMax(totalImages);
                progressBinding.progressImage.setProgress(completedImages);
                progressBinding.textImageProgress.setText(getString(R.string.image_progress, completedImages, totalImages));
            }
        });
    }

    private void refreshWebView() {
        webView.reload();
        cookie = cookieManager.getCookie("https://i.mi.com/note/h5#/");
    }

    private void showSettingsPopup() {
        DialogSettingsBinding binding = DialogSettingsBinding.inflate(getLayoutInflater());
        View dialogView = binding.getRoot();

        RadioButton radioHtml = binding.radioHtml;
        RadioButton radioTxt = binding.radioTxt;
        CheckBox checkBox = binding.checkBox;
        CheckBox checkFolder = binding.checkFolder;
        TextView textView = binding.textView;

        // 设置初始选中状态
        if (outMode == OutMode.HTML) {
            radioHtml.setChecked(true);
        } else if (outMode == OutMode.TXT) {
            radioTxt.setChecked(true);
        }
        checkBox.setChecked(downloadImages);
        checkFolder.setChecked(useFolders);

        textView.setOnClickListener(v -> showInfoDialog());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView)
                .setTitle(R.string.settings)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (radioHtml.isChecked()) {
                        outMode = OutMode.HTML;
                    } else if (radioTxt.isChecked()) {
                        outMode = OutMode.TXT;
                    }
                    downloadImages = checkBox.isChecked();
                    useFolders = checkFolder.isChecked();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void check() {
        if (uri == null) {
            showToast("请先选择导出目录");
            return;
        }

        String settingsSummary =
                "导出模式: " + (outMode == OutMode.HTML ? getString(R.string.export_html) : getString(R.string.export_txt)) + "\n" +
                "下载图片: " + (downloadImages ? getString(R.string.yes) : getString(R.string.no)) + "\n" +
                "按文件夹分类: " + (useFolders ? getString(R.string.yes) : getString(R.string.no)) + "\n\n" +
                "点击确定则会开始导出";

        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_settings_title)
                .setMessage(getString(R.string.confirm_settings_message, settingsSummary))
                .setPositiveButton(R.string.ok, (dialog, which) -> startExport())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startExport() {
        showToast("开始获取…");
        checkButton.setEnabled(false);
        viewLastButton.setVisibility(View.GONE);

        cookie = cookieManager.getCookie("https://i.mi.com/note/h5#/");
        noteUrls.clear(); // 清空上一次的记录
        folderNames.clear();
        folderDirs.clear();
        folderImageDirs.clear();
        completedRequests = 0;
        totalImages = 0;
        completedImages = 0;

        showProgressDialog();
        updateLog("正在初始化导出目录...");

        folder = DocumentFile.fromTreeUri(this, uri);
        if (folder == null || !folder.canWrite()) {
            showToast("导出目录初始化失败");
            checkButton.setEnabled(true);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        folderName = sdf.format(new Date());
        newFolder = folder.findFile(folderName);
        if (newFolder == null)
            newFolder = folder.createDirectory(folderName);
        if (downloadImages) {
            if (newFolder != null) {
                imageFolder = newFolder.createDirectory("图片");
            } else {
                showToast("导出目录初始化失败");
                checkButton.setEnabled(true);
                return;
            }
        }

        fetchNotes(null); // 从第一页开始获取
    }

    private void fetchNotes(String syncTag) {
        String url = "https://i.mi.com/note/full/page/?limit=200";
        if (syncTag != null) {
            url += "&syncTag=" + syncTag;
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", cookie)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> showToast("步骤1失败: 请求失败"));
                updateLog("步骤1失败: 请求列表失败");
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
                        updateLog("步骤1失败: 数据解析错误");
                    }
                } else {
                    runOnUiThread(() -> showToast("步骤1失败: 请求失败 (" + response.code() + ")"));
                    updateLog("步骤1失败: 请求列表失败 (" + response.code() + ")");
                }
            }
        });
    }

    private void parseNotesJson(String json) {
        Gson gson = new Gson();
        NoteResponse noteResponse = gson.fromJson(json, NoteResponse.class);
        if (noteResponse.code == 0) {
            NoteResponse.Data data = noteResponse.data;
            List<NoteEntry> entries = data.entries;

            if (data.folders != null) {
                for (NoteFolder f : data.folders) {
                    folderNames.put(f.id, f.subject);
                }
            }

            // 如果没有更多数据，结束请求
            if (entries.isEmpty()) {
                runOnUiThread(() -> {
                    showToast("步骤1完成: 已读取到共" + noteUrls.size() + "条笔记列表");
                    updateLog("步骤1完成: 已读取到共" + noteUrls.size() + "条笔记列表");
                    if (useFolders) {
                        createCategoryFolders();
                    }
                });
                updateNoteProgress();
                fetchNoteDetails();
                return;
            }

            // 保存笔记 ID 到列表中
            for (NoteEntry entry : entries) {
                noteUrls.add("https://i.mi.com/note/note/" + entry.id);
            }

            // 获取新的 syncTag
            String nextSyncTag = data.syncTag;

            // 递归调用以请求下一页数据
            fetchNotes(nextSyncTag);
        } else {
            runOnUiThread(() -> showToast("步骤1失败: 数据解析错误"));
        }
    }

    private void createCategoryFolders() {
        if (newFolder == null) return;
        for (Map.Entry<String, String> entry : folderNames.entrySet()) {
            String name = entry.getValue();
            if (name == null || name.isEmpty()) name = "未命名文件夹";
            // 过滤文件名非法字符
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            DocumentFile targetFolder = newFolder.createDirectory(name);
            if (targetFolder != null) {
                folderDirs.put(entry.getKey(), targetFolder);
                if (downloadImages) {
                    DocumentFile imgFolder = targetFolder.createDirectory("图片");
                    if (imgFolder != null) {
                        folderImageDirs.put(entry.getKey(), imgFolder);
                    }
                }
            }
        }
    }

    private void fetchNoteDetails() {
        completedRequests = 0; // 重置计数器
        for (int i = 0; i < noteUrls.size(); i++) {
            String url = noteUrls.get(i);
            final int index = i;

            // 提交任务到线程池
            scheduledExecutorService.schedule(() -> {
                updateLog("正在获取笔记详情 (" + (index + 1) + "/" + noteUrls.size() + ")");
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Cookie", cookie)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        runOnUiThread(() -> showToast("步骤2失败: 请求失败"));
                        updateLog("步骤2失败: 详情请求失败");
                        increaseCompletedRequests(); // 计数
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            ResponseBody responseBody = response.body();
                            if (responseBody != null) {
                                String content = responseBody.string();
                                fetchImages(content, index);
                            }
                        } else {
                            runOnUiThread(() -> showToast("步骤2失败: 请求失败 (" + response.code() + ")"));
                            updateLog("步骤2失败: 详情请求失败 (" + response.code() + ")");
                        }
                        increaseCompletedRequests(); // 计数
                    }

                });
            }, 500, TimeUnit.MILLISECONDS);
        }
    }

    private void fetchImages(String content, int index) {
        Gson gson = new Gson();
        NoteDetail noteDetail = gson.fromJson(content, NoteDetail.class);

        String note = noteDetail.data.entry.content;

        if (outMode == OutMode.TXT && !downloadImages) {
            finalStep(note, index, noteDetail, null);
        } else {
            Pattern pattern = Pattern.compile("☺ (.*?)<0/></>");
            Matcher matcher = pattern.matcher(note);

            Map<String, String> imageUrls = new HashMap<>();

            while (matcher.find()) {
                String fileId = matcher.group(1);
                imageUrls.put(fileId, null);
                handledImages.merge(index, 1, Integer::sum);
                if (downloadImages) {
                    synchronized (this) {
                        totalImages++;
                    }
                    updateImageProgress();
                }
            }

            if (!imageUrls.isEmpty()) {
                for (String fileId : imageUrls.keySet()) {
                    Request request = new Request.Builder()
                            .url("https://i.mi.com/file/full?type=note_img&fileid=" + fileId)
                            .addHeader("Cookie", cookie)
                            .build();

                    new OkHttpClient.Builder().followRedirects(false).build()
                            .newCall(request).enqueue(new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    runOnUiThread(() -> showToast("步骤2失败: 请求图片失败"));
                                    finalStep(note, index, noteDetail, imageUrls);
                                }

                                @Override
                                public void onResponse(@NonNull Call call, @NonNull Response response) {
                                    if (response.isRedirect()) {
                                        String location = response.header("Location");
                                        if (location != null) {
                                            imageUrls.put(fileId, location);
                                        } else {
                                            runOnUiThread(() -> showToast("步骤2失败: 请求图片真实地址失败"));
                                        }
                                    } else {
                                        runOnUiThread(() -> showToast("步骤2失败: 请求图片失败 (" + response.code() + ")"));
                                    }
                                    finalStep(note, index, noteDetail, imageUrls);
                                }
                            });
                }
            } else {
                finalStep(note, index, noteDetail, null);
            }
        }

    }

    /**
     * 处理笔记文本、下载图片、保存文件
     */
    private synchronized void finalStep(String note, int index, NoteDetail noteDetail, Map<String, String> imageUrls) {
        handledImages.merge(index, -1, Integer::sum);
        if (imageUrls != null && handledImages.get(index) != 0) return;

        Gson gson = new Gson();
        String title = gson.fromJson(noteDetail.data.entry.extraInfo, ExtraInfo.class).title;
        if (title == null || title.isEmpty()) {
            title = "无标题";
        }

        String folderId = noteDetail.data.entry.folderId;
        DocumentFile targetFolder = newFolder;
        DocumentFile targetImageFolder = imageFolder;

        if (useFolders && folderDirs.containsKey(folderId)) {
            targetFolder = folderDirs.get(folderId);
            if (downloadImages) {
                targetImageFolder = folderImageDirs.get(folderId);
            }
        }

        if (imageUrls != null) {
            if (outMode == OutMode.HTML) {
                for (String fileId : imageUrls.keySet()) {
                    String url = imageUrls.get(fileId);
                    if (url == null) continue;

                    note = note.replaceAll("☺ " + fileId + "<0/></>",
                            "<img src=\"" + url + "\">");
                }
            } else if (outMode == OutMode.TXT) {
                note = note.replaceAll("☺ .*?<0/></>",
                        "[图片]");
            }

            if (downloadImages && targetImageFolder != null) {
                for (String fileId : imageUrls.keySet()) {
                    String url = imageUrls.get(fileId);
                    if (url == null) {
                        increaseCompletedImages();
                        continue;
                    }

                    final DocumentFile finalImageFolder = targetImageFolder;
                    scheduledExecutorService.schedule(() -> {
                        Request request = new Request.Builder()
                                .url(url)
                                .addHeader("Cookie", cookie)
                                .build();

                        client.newCall(request).enqueue(new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                runOnUiThread(() -> showToast("图片获取失败"));
                                updateLog("图片获取失败: " + fileId);
                                increaseCompletedImages();
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) {
                                ResponseBody body = response.body();
                                if (body != null) {
                                    String contentType = response.header("Content-Type");
                                    DocumentFile imageFile = finalImageFolder.createFile(
                                            contentType != null ? contentType : "image/jpeg",
                                            fileId);
                                    if (imageFile != null) {
                                        try (InputStream inputStream = body.byteStream();
                                             OutputStream outputStream = getContentResolver().openOutputStream(imageFile.getUri())) {
                                            if (outputStream != null) {
                                                byte[] buffer = new byte[8192];
                                                int bytesRead;

                                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                                    outputStream.write(buffer, 0, bytesRead);
                                                }

                                                outputStream.flush();
                                                runOnUiThread(() -> showToast("图片下载成功"));
                                                updateLog("图片下载成功: " + fileId);
                                            }
                                        } catch (IOException e) {
                                            runOnUiThread(() -> showToast("图片保存失败"));
                                            updateLog("图片保存失败: " + fileId);
                                        }
                                    } else {
                                        runOnUiThread(() -> showToast("图片文件创建失败"));
                                        updateLog("图片文件创建失败: " + fileId);
                                    }
                                } else {
                                    updateLog("图片下载失败 (body empty): " + fileId);
                                }
                                increaseCompletedImages();
                            }
                        });
                    }, 500, TimeUnit.MILLISECONDS);
                }
            }
        }

        String fileExt;

        if (outMode == OutMode.HTML) {
            fileExt = ".html";
            note = note.replace("\n", "<br>");
        } else if (outMode == OutMode.TXT) {
            fileExt = ".txt";
            note = note.replaceAll("<.*?>", "");
            note = note.replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
        } else {
            runOnUiThread(() -> showToast("步骤2失败: 未知输出模式"));
            return;
        }

        try {
            if (targetFolder != null) {
                // 过滤文件名非法字符
                title = title.replaceAll("[\\\\/:*?\"<>|]", "_");
                String name = index + "." + title + fileExt;
                DocumentFile newFile = targetFolder.createFile("*/*", name);
                if (newFile != null) {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(newFile.getUri())) {
                        if (outputStream != null) {
                            outputStream.write(note.getBytes());
                            outputStream.flush();
                            runOnUiThread(() -> showToast("已导出: " + name));
                            updateLog("已导出: " + name);
                        }
                    } catch (IOException e) {
                        runOnUiThread(() -> showToast("文件写入失败"));
                    }
                }
            }
        } catch (Exception e) {
            runOnUiThread(() -> showToast("文件创建失败"));
        }
    }

    private synchronized void increaseCompletedRequests() {
        completedRequests++;
        updateNoteProgress();
        if (completedRequests == noteUrls.size()) {
            runOnUiThread(() -> {
                showToast("所有笔记已导出");
                updateLog("所有笔记已导出");
                checkButton.setEnabled(true);
                viewLastButton.setVisibility(View.VISIBLE);
                if (progressDialog != null) {
                    progressDialog.setCancelable(true);
                    progressDialog.setCanceledOnTouchOutside(true);
                }
            });
        }
    }

    private synchronized void increaseCompletedImages() {
        completedImages++;
        updateImageProgress();
    }
}
