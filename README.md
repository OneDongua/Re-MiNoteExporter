# Re-MiNoteExporter

## 项目简介

Re: MiNoteExporter 是一个用于导出小米笔记数据的 Android 应用程序（重构版）。

本软件仅供学习交流使用，请在24小时内删除本应用，一切因使用本软件产生的后果与原作者无关。

可在 [Release](https://github.com/OneDongua/Re-MiNoteExporter/releases) 下载

### 主要功能

- 使用小米官方云服务 API 获取数据，安全可靠
- 支持多种导出格式：

  | 格式           | 说明                            |
  |--------------|-------------------------------|
  | **XML**      | 保留富文本格式，内嵌图片链接                |
  | **TXT**      | 纯文本格式，自动去除 HTML 标签            |
  | **JSON**     | 结构化数据导出，便于程序处理                |
  | **Markdown** | 支持标准 Markdown 格式，可选 YAML 元数据头 |

- 支持笔记图片的下载与转换（Markdown 格式可选远程链接或本地下载）
- 支持按文件夹分类导出笔记
- Markdown 导出可包含 YAML 元数据（标题、创建时间、修改时间）
- 实时导出进度显示
- 详细的导出日志查看功能

### 使用的开源库

- [**Material Components for Android**](https://github.com/material-components/material-components-android): 用户界面组件，Apache 2.0 许可证
- [**Gson**](https://github.com/google/gson): JSON 解析库，Apache 2.0 许可证
- [**OkHttp**](https://github.com/square/okhttp): 网络请求库，Apache 2.0 许可证
- [**jsoup**](https://github.com/jhy/jsoup/): HTML 解析库，MIT 许可证

## 构建

### 克隆仓库

```bash
git clone https://github.com/OneDongua/Re-MiNoteExporter.git
```

使用最新版 [Android Studio](https://developer.android.com/studio) 导入即可

### 使用说明

1. 打开应用后，通过 WebView 登录小米账号（i.mi.com）
2. 点击「设置」按钮选择导出格式、是否下载图片等选项
3. 点击「选择目录」选择笔记的保存位置
4. 点击「开始检查」开始导出笔记
5. 导出过程中可查看详细进度和日志

## 注意事项

1. 本软件仅供学习交流使用，请在24小时内删除本应用，一切因使用本软件产生的后果与原作者无关。
2. 请勿从除酷安和 Github Release 以外的渠道获取本软件。
3. 本软件尊重您的隐私，仅需要最基础的网络权限即可正常使用。
4. 本软件永远不会也不能收集或修改您的账号、密码、隐私信息等数据，您可以自行查阅软件源代码验证。

## 联系方式

如果有任何问题或建议，请联系作者：[一個冬瓜](http://www.coolapk.com/u/4239876)

## 许可证

本项目使用 [Apache 2.0](LICENSE) 许可证。