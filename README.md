# N_m3u8DL-RE TUI for Android

把 [N_m3u8DL-RE](https://github.com/nilaoda/N_m3u8DL-RE) 做成安卓上的图形化小工具：粘贴链接、挑画质、等下载完。不需要 Termux，不需要敲命令。

## 下载

每次推代码都会自动打包，去 [Actions](https://github.com/wumingzhinu/nm3u8dl-re-tui-android/actions) 最新一次绿色构建，展开 **Collect APKs** 步骤下载 `nm3u8dl-re-tui-android-release.apk`。

也可以从 [Releases](https://github.com/wumingzhinu/nm3u8dl-re-tui-android/releases) 下。

- 架构：arm64-v8a（绝大多数安卓手机）
- 最低系统：Android 8.0
- 安装：直接装 APK，首次打开会让你允许"安装未知来源应用"

## 怎么用（三步）

**第 1 步 粘贴链接**

拿到 `.m3u8` 或 `.mpd` 链接，粘贴进去，点「开始解析」。

> 怎么找链接：浏览器打开视频页 → 按 F12 → 切到 Network/网络 → 筛选框输 `m3u8` 或 `mpd` → 右键复制链接

**第 2 步 选画质**

顶部一排预设直接点：**最佳 / 4K / 1080P / 720P / 480P / 只要音频**。

想手动挑就点轨道卡片切换勾选，卡片上会显示分辨率、码率、语言、编码、分片数。

**第 3 步 下载**

点底部「下载」，看进度条、等完成。文件存在 App 专属目录：

```
/storage/emulated/0/Android/data/com.nm3u8dl.tui/files/downloads/
```

用文件管理器打开"内部存储 → Android → data → com.nm3u8dl.tui → files → downloads"就能看到。

## 两套界面

右上角可以切换，选择会记住：

- **TERMINAL** — 深色等宽字体，程序员看着舒服
- **MODERN** — 浅色圆角卡片，更适合不熟悉终端的人

## 加密视频怎么办

先搞清楚是哪种加密：

| 情况 | 要不要填密钥 |
|---|---|
| 普通未加密视频 | 什么都不用填 |
| HLS 标准 AES-128，清单里写了 key | **不用填**，程序自己从清单取 |
| HLS AES-128，清单里没写 key | 展开「加密视频？」，填 **HLS AES-128 密钥** |
| DASH / MP4 CENC 加密 | 展开「加密视频？」，填 **DASH/CENC 密钥** |

**关于 IV**：一般不用填。HLS 标准规定 IV 默认就是分片序号，程序会自动推导。只有服务端用了非标准 IV 且没写进清单时才需要手动填。

**密钥格式**：

- CENC：`KID:KEY`（KID 和 KEY 都是 32 位十六进制），或者所有轨道共用一个 KEY 时直接写 `KEY`
- 多个密钥用逗号或空格分隔

密钥从哪来：流媒体平台的会员/付费链接通常在网络请求里以 `license` 或 `key` 请求出现，抓包能看到；或者看平台的说明。

## 它是怎么工作的

App 本身只是个界面，真正的下载由内置的 [N_m3u8DL-RE](https://github.com/nilaoda/N_m3u8DL-RE) 完成：

- **N_m3u8DL-RE**（MIT，bionic arm64 NativeAOT）解析清单、下载分片
- **ffmpeg**（GPL-3.0，静态编译）合并分片成 mp4
- **libssl / libcrypto**（Termux OpenSSL 3）—— 因为安卓系统的 `libssl.so` 只是链接用的空壳库，程序实际需要真库

这几个文件打包在 APK 的 `assets/native/` 里，首次运行时自动释放到 App 私有目录。

下载分两阶段：先 `--skip-download` 只解析拿到轨道清单给你选，确定后再带 `--select-video`/`--select-audio`/`--select-subtitle` 精确下载（按 GroupId 匹配，N_m3u8DL-RE 的 `id=` 正则）。

## 自己构建

推 tag 或手动触发 Actions 即可，不需要本地装 Android SDK。

```bash
gh workflow run build-apk.yml
```

native 资产从 [`vendor-v1` release](https://github.com/wumingzhinu/nm3u8dl-re-tui-android/releases/tag/vendor-v1) 拉取，构建时校验 sha256，所以仓库本身不存大文件。

## 许可

- App 代码：本仓库
- N_m3u8DL-RE：MIT
- ffmpeg：GPL-3.0（静态构建含 x264/x265）
- OpenSSL：Apache-2.0

## 已知限制

- 只有 arm64-v8a
- 直播流只按当前播放列表下载，不追直播
- 需要 MP4 解密（CENC）时用 ffmpeg 引擎，不含 mp4decrypt