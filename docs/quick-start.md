# 普通用户上手：开启无线 ADB，交给 agent

你只需要在电视上做两步，其余连接、备份、安装和接管都由电脑上的 AI agent（ZCode、Claude Code、Codex 等）按[部署指南](agent-deployment.md)完成。

## 准备

- 一台与电视连着同一个 Wi-Fi 的电脑。
- 在电脑上打开你的 AI 编程助手。

## 第 1 步：在电视上开启无线 ADB

1. 用遥控器进入电视「设置」，找到「ADB调试」并打开；它一般在安全或「关于」相关的分类里，不同固件菜单位置可能不同。
2. 打开后，电视会在屏幕上显示一个由数字和冒号组成的地址（形如 `192.168.x.x:端口`），记下来或拍张照，端口以屏幕显示为准。

## 第 2 步：把地址交给 agent

在电脑上对 agent 说，示例：

> 请部署简净桌面，仓库是 https://github.com/Carton/mitv-quiet-launcher ，
> 电视的 ADB 地址是（第 1 步屏幕上那串），电视型号是（设置 → 关于 里可查）。

之后安装、接管 Home 键、验收都由 agent 完成。让 agent 优先从 [Releases](https://github.com/Carton/mitv-quiet-launcher/releases) 下载已构建的 `quiet-tv.apk` 直接安装；Releases 里没有时它才从源码构建。过程中电视可能弹出「允许调试」等确认框，用遥控器选「允许／确定」即可。

## 之后

- 想回原厂桌面：在简净桌面底部选「原厂桌面」，或让 agent 运行仓库中的恢复脚本。
- 连接不上或状态显示 offline：把电视上的 ADB 调试关掉再打开，让 agent 重试。
