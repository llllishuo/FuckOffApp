# GitHub Release 发布模板

每次版本变更后复制本模板，填写并保存为 `release-notes/vX.Y.Z.md`。

## 发布信息

- Tag：`vX.Y.Z`
- Target：`main`
- Release 标题：`FuckOff vX.Y.Z`
- 类型：`Pre-release`（测试版）/ `Latest`（稳定版）
- APK：`FuckOff-vX.Y.Z.apk`
- 文件大小：`...` bytes
- SHA-256：`...`

## Release 说明（可直接粘贴）

```markdown
## FuckOff vX.Y.Z

### 更新内容
- ...

### 安装
1. 下载下方 APK。
2. 允许浏览器安装未知应用。
3. 安装并打开。

### 校验
SHA-256：`...`

### 反馈
如遇问题，请附上手机型号、Android/MagicOS 版本和崩溃日志。
```

## 发布步骤

1. 更新 `versionCode` 和 `versionName`，构建 APK。
2. 提交并推送代码，创建 tag：`vX.Y.Z`。
3. 打开 GitHub → Releases → Draft a new release。
4. 选择 tag，填写标题和 release notes。
5. 上传 `FuckOff-vX.Y.Z.apk`，不要上传 keystore。
6. 测试版勾选 Pre-release；确认稳定后再设为 Latest。
