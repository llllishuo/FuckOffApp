# FuckOff 介绍视频脚本

- 版本：FuckOff v1.7.0
- 成片：`FuckOff-intro-v1.7.0.mp4`
- 画幅：720 × 1280，9:16 竖屏
- 时长：49.9 秒
- 帧率：30 fps
- 视频：H.264 / yuv420p
- 音频：AAC 48 kHz 立体声
- 旁白：Microsoft Edge TTS · zh-CN-liaoning-XiaobeiNeural（东北话·小北，幽默风格）
- 字幕：`FuckOff-intro-v1.7.0.srt`
- 封面：`FuckOff-intro-v1.7.0-thumbnail.png`

## 分镜

| 时间 | 画面 | 旁白 | 屏幕文案 |
| --- | --- | --- | --- |
| 0–6s | 标题卡 | FuckOff，专治那些赖在手机里不走的 App。 | FuckOff / 黑名单应用清理器 |
| 6–11s | 首页 | 装上就删不掉？给它拉黑，立马给你盯着。 | 01 / 黑名单：应用出现，立即处理 |
| 11–20s | 清理方式弹窗 | 手动、Device Owner、Shizuku、无障碍，四种清理方式，随便挑。 | 02 / 清理方式：四种清理方式 |
| 20–25s | 通知与灵动岛弹窗 | 通知和灵动岛，各管各的，谁也不耽误谁。 | 03 / 通知与灵动岛：两个开关，独立管理 |
| 25–31s | 通知栏汇总卡片 | 多个 App 一起蹦出来？一键批量安排。 | 04 / 一键卸载：多个应用，一次发起 |
| 31–37s | 后台守护设置 | 后台三秒一瞅，十分钟一个看门狗，跑不了。 | 05 / 后台守护：不打开 App 也能检测 |
| 37–44s | 安全审查规则 | 系统关键角色默认保护，卸载之前还得过安检。 | 06 / 安全边界：关键角色，默认保护 |
| 44–50s | 结尾卡 | 纯本地运行，不联网。GitHub 搜 llllishuo，FuckOffApp。 | GitHub / llllishuo/FuckOffApp |

## 制作说明

- 截图来自 FuckOff v1.7.0（MEmu Android 9 真机界面捕获），界面与应用内实际文案一致。
- 旁白使用 Edge TTS 的东北话·小北神经网络语音生成；屏幕字幕保持标准普通话文案，背景氛围音由本地脚本合成，没有使用第三方音乐或素材。
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
