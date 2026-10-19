# 荣耀灵动胶囊适配边界

FuckOff v1.6.0 增加了“通知与灵动岛”设置和汇总通知，但需要明确一个平台限制：

## 荣耀没有开放通用第三方胶囊按钮 API

- 荣耀 MagicOS 的灵动胶囊/灵动岛，第三方业务入口主要走 **YOYO 建议 - 全局触达**，需要荣耀开发者平台的业务配置/准入，并不是像普通通知一样任意 App 都能直接显示任意按钮。
- 目前没有找到荣耀公开的、可由个人开发者直接调用的“在胶囊中放两个按钮”的稳定 SDK。
- FuckOff **不会**使用 `com.hihonor.*` 私有接口、反射系统服务或伪造系统胶囊；这些做法在系统更新后容易失效，也可能违反平台规则。

参考：

- [荣耀俱乐部：灵动胶囊开放第三方接口建议](https://club-api.c.hihonor.com/cn/thread-29515612-1-1.html)
- [荣耀俱乐部：灵动胶囊适配个人开发者](https://club-api.c.hihonor.com/cn/thread-29986666-1-1.html)
- [荣耀 YOYO 建议介绍（官方文档入口）](https://developer.honor.com/en/doc/guides/101368)
- [荣耀 YOYO 建议 - 全局触达配置指南（官方文档入口）](https://developer.honor.com/cn/doc/guides/101455)

## FuckOff 当前实现

使用 **Android 16 标准 promoted ongoing notification / Live Updates** 路径：

- 汇总显示当前已安装的黑名单应用数量与名称；
- 提供「全部卸载」按钮；
- 多应用时先启动批量队列，由用户确认（无障碍模式可自动点击确认）；
- 通知与灵动岛开关独立管理；
- 如果系统不支持 promoted ongoing notification，则回退为普通汇总通知。

这意味着：

- 在支持 Android 16 Live Updates 且厂商将其映射到顶部胶囊的机型上，有机会显示为胶囊；
- MagicOS 版本、YOYO 配置、用户开关和业务准入都会影响实际呈现；
- **不能保证所有荣耀机型/系统版本都显示成灵动胶囊**；
- 如果没有显示，通知栏中的汇总通知和「全部卸载」按钮仍然可用。

## 未来适配其他机型

- 公共层使用 Android 标准通知/Live Updates，不绑定荣耀私有协议；
- 若小米超级岛、OPPO 流体云、vivo 原子通知等提供公开 SDK，可在 `IslandNotificationManager` 外增加厂商 adapter；
- 厂商 adapter 只负责“把同一个汇总卡片提交给系统”，清理逻辑和安全检查保持不变；
- 没有公开接口的厂商继续使用普通汇总通知，不伪造系统 UI。

## 如果需要真正的荣耀胶囊

需要走荣耀 YOYO 建议/全局触达的正式接入流程，提交业务场景和资质，由荣耀配置后才能在系统胶囊中展示。FuckOff 作为本地工具类应用是否符合其业务范围，需要荣耀侧审核。
