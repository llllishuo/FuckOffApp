# 测试记录

## 环境

- 构建：Windows 10、JDK 17、Android SDK 35、Build-Tools 35.0.0、Gradle 8.9
- 运行：MEmu 逍遥模拟器，Android 9（API 28），x86_64，900×1600（竖屏）
- 权限：通过 `adb shell dpm set-device-owner` 配置为 Device Owner
- 应用显示名：FuckOff（包名仍为 `com.menghanstudio.gooutpls`）
- 测试对象：FuckOff v1.5.0 release APK（debug key 签名）
- 最终 APK：`FuckOff-v1.5.0.apk`，1,019,036 bytes，SHA-256 `84d4fd9af006cf6f3298d37e370a09e2a7ed5d0c277e1f0dcbe578bfa8f09fa4`
- 测试用“黑名单应用”：`...blacklist.victim`、`...blacklist.victim2`
- Lua 运行时：LuaJ 3.0.1，仅加载 Base/Package/Table/String/Math 库
- Shizuku 依赖：Shizuku API/AIDL/Provider 13.1.5；测试机未安装 Shizuku
- v1.5.0 构建时荣耀设备已断开，未能安装/启动新 APK；无障碍实际点击和性能改善未做真机回归

## 已验证

| 项目 | 结果 |
| --- | --- |
| Debug / Release 构建 | 通过 |
| 荣耀 Magic8 Pro（BKQ-AN90，Android 16 / MagicOS 10）冷启动 | v1.4.0 通过；v1.5.0 构建后设备断开，未安装 |
| 清理方式选择 | v1.4.0 通过三种；v1.5.0 新增无障碍第四种，未在真机验证 |
| Shizuku 未安装时的引导 | 通过；显示安装、无线调试和授权说明，不崩溃 |
| Shizuku 实际卸载 | 未验证；测试机未安装/启动 Shizuku，代码路径已编译，需实机授权后验证 |
| 无障碍模拟确认（v1.5.0） | 未验证；服务、权限入口和模式 UI 已编译，设备断开后未安装；需真机开启无障碍并测试 |
| 应用列表扫描优化（v1.5.0） | 代码审查通过；图标改为按需加载、减少一次 PackageManager 查询；未做设备基准 |
| v1.5.0 安装包 | 构建通过，APK 未在设备安装（设备断开） |
| Android Lint | 0 errors |
| APK 安装并通过 v2 签名校验 | 通过 |
| Device Owner 配置 | 通过 |
| 单项默认「跟随系统」/「仅手动」覆盖 | 通过 |
| 单项「跟随系统」：下一次扫描静默卸载 | 通过 |
| JSON 导入：全部强制 `manual`，不立即清理 | 通过 |
| 自定义规则：UI 添加、保存、开关、删除 | 通过；规则持久化到本机 `gooutpls_rules.xml` |
| 自定义规则：`pkg:find("victim") ~= nil` | 通过；`Blacklist Victim` 显示「受保护」，待清理数降为 0，主页卸载按钮关闭；自动/手动路径共用同一安全 Snapshot（代码审查） |
| 自定义规则：删除后恢复 | 通过；基础审查仍在，目标重新显示「待清理」，卸载按钮恢复 |
| 自定义规则：非法语句 `while` | 通过；保存被拒绝，提示「不支持语句关键字：while」，App 不崩溃 |
| 自定义规则：规则引擎在 API 28 初始化 | 通过，LuaJ 表达式可执行 |
| 基础审查不能关闭 | 通过；规则只做 OR 保护，无关闭/放行开关 |
| 应用选择器安全过滤 | 通过；基础受保护角色不出现；自定义规则由同一 Snapshot 过滤（代码审查） |
| 前台服务通知 / 检测通知 | 通过 |
| App 更新后守护服务恢复 | 通过（`MY_PACKAGE_REPLACED`） |
| 应用内取消 Device Owner | 通过，同时移除 Active Admin |
| 无 `INTERNET` 权限 | 通过 |
| 开源许可文件 | 通过；根目录 `LICENSE`/`NOTICE` 与 APK `assets/LICENSE.txt`/`NOTICE.txt` 一致 |
| 关于信息 | v1.4.0 通过；v1.5.0 版本号构建正确，未做真机关于页回归 |

## 自定义规则安全边界

- 自定义规则只允许**单个 Lua 表达式**，必须返回真值才会保护应用。
- 规则不能修改基础审查结果，只能增加「受保护」判断；没有 allow/unprotect 类型。
- 保存时拒绝：循环、函数定义、`return`、条件语句关键字、注释、长字符串、分号。
- Lua 环境移除了 `os`、`io`、`package`、`load`、`loadfile`、`dofile`、`require`、`debug`、`luajava`；字符串库移除了 `rep`、`char`、`format`、`dump`。
- 规则数量上限 50，表达式上限 800 字符；规则读取/编译/执行异常只跳过该条，不影响 App 或基础审查。
- 规则只保存在本机，**不随黑名单 JSON 导出/导入**。

## Shizuku 高级清理说明

- 清理方式在首页「自动处理」卡片中选择；Shizuku 模式不需要 Device Owner。
- Shizuku 服务未运行、未授权或命令失败时，GuardEngine 会自动回退到手动通知。
- 实际命令为固定的 `pm uninstall --user 0 <包名>`，包名经过字符校验，不执行任意用户输入的命令。
- 本次测试环境未安装 Shizuku，因此只验证了 UI 引导、模式切换、权限请求入口和回退路径；实际 shell 卸载需要在安装 Shizuku 后复测。

## 无障碍模拟确认说明

- 该模式是**半自动**：仍需用户点通知或列表「卸载」，系统卸载界面出现后由无障碍服务点击确认。
- 服务只接受 `ManualUninstallActivity` 入队的包，且只在包安装器/卸载器窗口内查找「卸载 / 确定 / OK」等确认按钮。
- Android 13+ 侧载应用可能需要在 应用信息 → FuckOff → ⋮ →「允许受限设置」中放开开关。
- 本次设备断开，未做真机点击验证；不同 OEM 的按钮文案/窗口包名可能不同，失败时应回退手动确认。

## 荣耀 Android 16 启动问题

- 实际设备：HONOR BKQ-AN90，Android 16，MagicOS 10.0.0.170。
- v1.3.3 及之前版本在 `BaseActivity.onCreate()` 中、`setContentView()` 之前调用 `window.getInsetsController()`。
- 荣耀 Android 16 的 `PhoneWindow` 此时 DecorView 为空，触发：
  `NullPointerException: Attempt to invoke ... DecorView.getWindowInsetsController() on a null object reference`
- v1.3.4 把系统栏外观设置移到 `onPostCreate()` 并增加空值/异常保护；已在上述荣耀设备冷启动验证可进入首页。

## 说明

- 测试使用的 Device Owner 配置命令与 README 一致。
- 自动卸载使用 `PackageInstaller.uninstall()` 的设备所有者能力。
- Android 13 及以上才需要的 `POST_NOTIFICATIONS` 运行时权限、Android 14 的 `specialUse` 前台服务类型，代码与清单均已按 API 35 实现，但本次运行时环境为 API 28，未能覆盖对应版本真机。
- 默认角色保护逻辑属于代码级审查与静态路径验证，未在当前模拟器上构造独立的默认桌面/输入法应用做破坏性测试。
- 截图中的「保护测试应用」规则是测试规则，最终 APK 不预置任何规则。
- 部分品牌 ROM 的后台限制或 Device Owner 策略可能影响自动执行，手动模式可作为回退。
