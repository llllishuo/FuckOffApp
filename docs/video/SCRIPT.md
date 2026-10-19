# FuckOff 介绍视频脚本

- 版本：FuckOff v1.7.0
- 成片：`FuckOff-intro-v1.7.0.mp4`
- 画幅：720 × 1280，9:16 竖屏
- 时长：45.5 秒
- 帧率：30 fps
- 视频：H.264 / yuv420p
- 音频：AAC 48 kHz 立体声
- 旁白：Windows Huihui 中文语音（本地生成）
- 字幕：`FuckOff-intro-v1.7.0.srt`
- 封面：`FuckOff-intro-v1.7.0-thumbnail.png`

## 分镜

| 时间 | 画面 | 旁白 | 屏幕文案 |
| --- | --- | --- | --- |
| 0–5s | 标题卡 | FuckOff，把黑名单应用请出手机。 | FuckOff / 黑名单应用清理器 |
| 5–11s | 首页 | 应用装了又删不掉？加入黑名单，持续检测。 | 01 / 黑名单：应用出现，立即处理 |
| 11–18s | 清理方式弹窗 | 支持手动、Device Owner、Shizuku、无障碍四种方式。 | 02 / 清理方式：四种清理方式 |
| 18–22s | 通知与灵动岛弹窗 | 通知和灵动岛，独立管理。 | 03 / 通知与灵动岛：两个开关，独立管理 |
| 22–27s | 通知栏汇总卡片 | 多个应用，一键发起批量卸载。 | 04 / 一键卸载：多个应用，一次发起 |
| 27–32s | 后台守护设置 | 后台三秒轮询，十分钟看门狗兜底。 | 05 / 后台守护：不打开 App 也能检测 |
| 32–38s | 安全审查规则 | 关键系统角色默认保护，卸载前仍会检查。 | 06 / 安全边界：关键角色，默认保护 |
| 38–45s | 结尾卡 | 本地运行，无网络权限。GitHub 搜索 llllishuo，FuckOffApp。 | GitHub / llllishuo/FuckOffApp |

## 制作说明

- 截图来自 FuckOff v1.7.0（MEmu Android 9 真机界面捕获），界面与应用内实际文案一致。
- 旁白、背景氛围音均由本地脚本生成，没有使用第三方音乐或素材。
- 视频对“灵动岛”的表述保持边界：若系统支持 Android 16 promoted ongoing notification 并映射到胶囊，则可能显示为灵动胶囊；否则显示普通汇总通知。视频不声称荣耀 MagicOS 一定会显示胶囊。
- 需要重新生成时：

```powershell
python video/make_video.py
```

重新生成需要：
- `video/assets/` 中的界面截图
- `video/audio/` 中的旁白 WAV
- `imageio-ffmpeg` 提供的 ffmpeg 可执行文件

## 发布文案（可选）

```text
FuckOff：把黑名单应用请出手机。
本地运行，无网络权限；支持手动、Device Owner、Shizuku 和无障碍模拟清理。
后台 3 秒轮询 + 10 分钟看门狗，通知与灵动岛独立管理。
GitHub: llllishuo/FuckOffApp
```
