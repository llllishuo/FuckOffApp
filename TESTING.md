# 测试记录

## 环境

- 构建：Windows 10、JDK 17、Android SDK 35、Build-Tools 35.0.0、Gradle 8.9
- 运行：MEmu 逍遥模拟器，Android 9（API 28），x86_64，900×1600（竖屏）
- 权限：通过 `adb shell dpm set-device-owner` 配置为 Device Owner
- 应用显示名：FuckOff（包名仍为 `com.menghanstudio.gooutpls`）
- 测试对象：FuckOff v1.7.0 release APK（debug key 签名）
- 最终 APK：`FuckOff-v1.7.0.apk`，1,027,144 bytes，SHA-256 `59cae1602154030fee6873a36ba604bf38a04380790afefbe4900d46479bbcbb`
- 测试用“黑名单应用”：`...blacklist.victim`、`...blacklist.victim2`
- Lua 运行时：LuaJ 3.0.1，仅加载 Base/Package/Table/String/Math 库
- Shizuku 依赖：Shizuku API/AIDL/Provider 13.1.5；测试机未安装 Shizuku
- v1.7.0 已在 MEmu Android 9 安装；验证了首页、后台守护设置、Alarm 调度、应用切后台后的 3 秒级轮询检测；荣耀设备仍未连接

## 已验证

| 项目 | 结果 |
| --- | --- |
| Debug / Release 构建 | 通过 |
| 荣耀 Magic8 Pro（BKQ-AN90，Android 16 / MagicOS 10）冷启动 | v1.4.0 通过；v1.5.0/v1.6.0 构建后荣耀设备断开；v1.7.0 已用 MEmu Android 9 启动验证 |
| 清理方式选择 | v1.4.0 通过三种；v1.5.0 新增无障碍、v1.6.0 新增灵动岛汇总，均未在真机验证 |
| Shizuku 未安装时的引导 | 通过；显示安装、无线调试和授权说明，不崩溃 |
| Shizuku 实际卸载 | 未验证；测试机未安装/启动 Shizuku，代码路径已编译，需实机授权后验证 |
| 无障碍模拟确认（v1.5.0） | 未验证；服务、权限入口和模式 UI 已编译，设备断开后未安装；需真机开启无障碍并测试 |
| 应用列表扫描优化（v1.5.0） | 代码审查通过；图标改为按需加载、减少一次 PackageManager 查询；未做设备基准 |
| v1.5.0/v1.6.0/v1.7.0 安装包 | v1.7.0 在 MEmu Android 9 安装启动通过；荣耀未安装 |
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
| 关于信息 | v1.4.0 通过；v1.5.0/v1.6.0/v1.7.0 版本号构建正确，未做真机关于页回归 |
| 通知/灵动岛（v1.6.0） | MEmu 通过：双开关、普通汇总通知（1 个应用、2001 通知、2 个 action）无崩溃；灵动胶囊真机渲染未验证 |
| 后台守护设置（v1.7.0） | MEmu 通过；荣耀步骤、电池优化状态、应用设置入口均显示 |
| 10 分钟 Alarm 看门狗（v1.7.0） | MEmu 通过；`dumpsys alarm` 可见 ALARM_SCAN，下一轮约 10 分钟 |
| 后台轮询检测（v1.7.0） | MEmu 通过；应用切后台后安装/移除黑名单应用，日志在约 3 秒轮询周期内记录 add/remove；手动项正确进入待确认 |
| Android 16 promoted ongoing（v1.6.0） | 未验证；MEmu Android 9 仅验证普通通知回退 |
| 批量队列（v1.6.0） | 未验证；需在 MEmu/真机点汇总通知 action 或直接启动 BatchUninstallActivity |
| 手动检测快速路径（v1.6.0） | 代码审查通过；跳过通知前的完整 Snapshot，卸载入口仍做完整安全检查 |

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

## 通知/灵动岛说明

- 「检测通知」和「灵动岛/实况通知」两个开关分别对应 `detection_notifications_enabled` 和 `island_enabled`，互不影响。
- 汇总卡片使用 Android 16 promoted ongoing notification 的公开 API；系统不支持时回退普通通知。
- 荣耀 Magic Capsule 没有开放的通用第三方按钮 SDK；实际是否显示为胶囊由荣耀/YOYO 配置和系统版本决定，不能用私有接口保证。
- 多个应用时，汇总卡片的「全部卸载」会启动顺序队列；手动模式仍需逐项确认，无障碍模式由服务自动确认。
- 本次设备断开，未做通知/灵动岛/批量队列真机验证。

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
