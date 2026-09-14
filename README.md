# 手机相册整理（Photo Organizer）

像刷抖音一样上下滑刷自己的照片和视频，顺手把相册整理干净的 **Android 原生 App**。

- 工程规范（目录结构 / 构建 / 代码约定）：见 [`AGENTS.md`](./AGENTS.md)
- 产品需求细节：见 [`docs/手机相册整理产品PRD.md`](./docs/%E6%89%8B%E6%9C%BA%E7%9B%B8%E5%86%8C%E6%95%B4%E7%90%86%E4%BA%A7%E5%93%81PRD.md)

## 为什么做

手机相册长期积累后有几个老大难：

- 照片、截图、视频越来越多，占用大量本机空间
- 手动整理成本高，很难一次性处理几千到几万张
- **删除决策成本高**：担心误删，也难判断哪些值得留
- 网盘偏"存储"、相册 App 偏"浏览"，都不聚焦"顺手清理"

所以本产品的核心思路是：**把整理变成低压力的刷卡动作**（借鉴 Slidebox），而不是让用户面对一整屏缩略图做决策。

## 需求范围

### 当前做：本地 MVP（PRD 阶段 1）

- 读取本地照片和视频
- 随机整理卡片流 / 按月份、截图、大视频、相册筛选
- 保留、删除、稍后、永久保留、加入相册
- 删除前二次确认，且优先移入系统「最近删除」
- 整理进度、已释放空间、今日整理数量统计
- 本地规则队列与增量扫描

### 明确暂不做

- AI 自动整理队列（PRD 阶段 2）
- 家庭主机备份（PRD 阶段 3）
- iOS / 纯 HarmonyOS NEXT 版本
- 完全自动删除（信任成本过高，且违背"删除必须可解释"）

## 功能现状

| 模块 | 状态 | 说明 |
|---|---|---|
| 媒体扫描 | ✅ | 全量 + 增量（WorkManager 每日一次），结果存 Room 索引 |
| 刷照片流 | ✅ | 全屏竖向 Pager，上下滑切换，右滑保留 / 左滑稍后 |
| 整理队列 | ✅ | 随机、截图、大视频、最近 30 天、按月份、未整理、收藏 |
| 筛选 | ✅ | 按媒体类型、按系统相册 |
| 删除 | ✅ | 二次确认 + 系统确认，**移入系统最近删除**（非永久删除） |
| 恢复 | ✅ | App 内「最近删除」列表一键恢复 |
| 批量确认 | ✅ | 多选 + 显示数量/预计释放空间，按安全策略剔除高风险项 |
| 视频播放 | ✅ | 自动播放、暂停、可拖动进度条 |
| 应用内相册 | ✅ | 自定义相册分组、加入、查看、移除 |
| 设置 | ✅ | 删除安全策略、每日整理目标 |
| 每日整理任务 | ✅ | 目标进度条，跨天自动清零 |
| 操作日志 | ✅ | 记录每次决策的动作/来源/前后状态 |

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| 阶段 1 | 本地刷照片整理 | ✅ 已完成 |
| 阶段 2 | AI 辅助整理（重复/相似/模糊检测） | 暂不做 |
| 阶段 3 | 家庭主机备份 | 暂不做 |
| 体验增强 | 每日提醒通知、连续打卡、大相册分页 | 待评估 |

## 开发环境

| 项 | 要求 |
|---|---|
| IDE | Android Studio（最新稳定版） |
| JDK | 17+ |
| Android SDK | Platform 37、Build-Tools、Platform-Tools、cmdline-tools |
| Gradle | 8.13（已配腾讯镜像，无需翻墙） |
| 调试设备 | Android 8.0（API 26）以上 |

环境变量已配置（**新开终端生效**）：

```
ANDROID_HOME = C:\Users\wanghj\AppData\Local\Android\Sdk
Path += %ANDROID_HOME%\platform-tools
Path += %ANDROID_HOME%\cmdline-tools\latest\bin
```

## 构建与运行

**方式一：Android Studio**
打开项目 → 顶部选设备 → 点 ▶ Run

**方式二：命令行**

```powershell
cd d:\personal_items\photo-organizer

# 构建
.\gradlew.bat assembleDebug

# 安装（只连一台设备）
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 连了多台设备时指定序列号
adb devices
adb -s <序列号> install -r app\build\outputs\apk\debug\app-debug.apk
```

> 华为手机需额外开启：开发者选项 → **USB 调试** + **「允许通过 USB 安装应用」**。

## 测试约定

| 设备 | 序列号 | 谁测 |
|---|---|---|
| 模拟器（API 37） | `emulator-5554` | 可自动化 |
| 真机 Mate 70 Pro（HarmonyOS 4.3） | `6EN0225729004566` | **人工测**（含真实照片，勿跑自动化删除） |

授权时请在系统弹窗选 **「Allow all」**，选「Allow limited access」会让 App 只能看到部分照片。

## 文档索引

| 文件 | 内容 |
|---|---|
| [`AGENTS.md`](./AGENTS.md) | 工程规范：目录结构、构建、代码约定、已知坑 |
| `docs/手机相册整理产品PRD.md` | 完整产品需求文档 |
| `docs/Slidebox刷卡式相册整理交互研究.md` | 交互设计参考 |
| `docs/Slidebox功能级UI界面拆解.md` / `docs/Slidebox界面设计与交互拆解.md` | 界面拆解 |
| `docs/assets/*.svg` | 界面与交互流程图 |
