# 构建环境现状与结论（阶段 0 体检记录）

记录时间：2026-10-25（Asia/Shanghai）。**只读体检，未安装任何东西。**

## 一、主机事实

| 检查项 | 结果 |
|---|---|
| 工作目录 | `C:\Users\19853\Documents\deepseek-harness\default-workspace` |
| 磁盘可用 | C: 约 169 GB / D: 约 57 GB |
| JDK | **无**（`java` 不存在，`JAVA_HOME` 为空） |
| Android SDK | **无**（`ANDROID_HOME` / `ANDROID_SDK_ROOT` 为空，`%LOCALAPPDATA%\Android\Sdk` 不存在） |
| Gradle | **无**（`gradle` 不存在） |
| adb / platform-tools | **无**（`adb` 不在 PATH，常见目录均不存在） |
| gh CLI | 无 |
| git | 有，2.56.0.windows.2；**本地仓库可用**（已验证 init/add/commit） |
| SSH key | 无 `~/.ssh`（且沙箱禁止在工作区外创建） |

## 二、网络事实（关键）

沙箱内的进程级出网是**分栈**的，不能一概而论：

| 出网方式 | 结果 | 说明 |
|---|---|---|
| `curl`（Windows schannel 栈） | **失败** | TLS 在 step 1/3 即断，`-k` 也无用 → 不是证书问题 |
| PowerShell `Invoke-WebRequest`（.NET 栈） | **失败** | 连接被关闭 |
| 裸 TCP 443 | 通 | `Test-NetConnection repo1.maven.org:443` → True |
| 明文 HTTP | 通 | `http://repo1.maven.org/maven2/` → 301 |
| **Python 3.10（自带 OpenSSL 栈）** | **成功** | 取回 Apache-2.0 全文 11358 字节 |
| `git ls-remote`（HTTPS） | **失败** | `cannot create standard input pipe for remote-https: Permission denied` —— 沙箱禁止子进程管道，不是网络问题 |
| `ssh -T git@github.com` | 到达服务器 | 返回 `Permission denied (publickey)`，即链路通、缺密钥 |
| harness 侧 `web_fetch` | 成功 | 可读 GitHub API 与 Maven Central |

**推论：**

1. 依赖下载（Gradle 走 Java 自己的 TLS 栈）**可能**可行，需在装好 JDK 后实测，不能提前保证。
2. `git push` / `git fetch` 在本会话沙箱内**不可行**（管道限制），与网络无关。
3. Python 能出网，意味着**理论上可以自行下载 JDK 与 Android SDK 到工作区**（无需管理员权限），但这属于「改环境」，需明确授权。

## 三、验收门禁的可行性

| 门禁 | 当前可行? |
|---|---|
| `assembleDebug` 构建通过 | 否（缺工具链） |
| 单元测试 | 否 |
| Lint | 否 |
| 真机 / 模拟器安装运行 | 否（缺 adb 与设备） |
| 首屏、ANR、内存实测 | 否 |
| `git push` 到 GitHub | 否（沙箱管道限制 + 无凭证） |

## 四、解除阻塞的最短路径

**方案 A（推荐，一步到位）**：安装 Android Studio。它同时带来 JDK 21、Android SDK（platform / build-tools / platform-tools / adb）、模拟器与系统镜像、Gradle 兼容环境。装完把 `ADB` 环境变量指到 `...\Sdk\platform-tools\adb.exe`，本仓库的 Android 设备工具链即可工作。

**方案 B（轻量，不装 IDE）**：自行下载并解压 Temurin JDK 17/21 + Android `commandlinetools`，用 `sdkmanager` 安装 `platform-tools` / `platforms;android-<N>` / `build-tools;<N>.x`，并接受许可。体积小于 A，但没有图形化设备管理。

**方案 C**：把本会话沙箱提升到 `danger-full-access`，由我在工作区内的 `.toolchain/` 自行下载 JDK + SDK 并跑构建。需要先明确授权（下载量约 2–3 GB）。

无论哪个方案，`git push` 都需要：提供凭证（HTTPS token / SSH key）或由你在本机终端执行推送。
