# 发布前检查

## 每次发布前

```powershell
git status --short
git diff --cached --check
git diff --cached
python -B tools/audit_public.py --history
```

`audit_public.py` 扫描所有已跟踪文件和本地Git全部对象（含不可达对象）中的私网IPv4、电脑绝对路径、常见令牌和私钥头；新文件要先加入暂存区才会被扫描。它是模式检查，读不出截图画面里的文字，新增截图和文档仍需人工过目。

## 截图与公开资料

放入 `docs/images/` 的截图须人工检查画面和元数据，排除账户、IP／MAC、Wi-Fi名称、位置和私人照片，并注明版本及实机／设计预览属性；原始取证留在 `nor/`。

## 历史注意

公开前已重写历史，去除原设备地址和电脑路径，保留提交作者。不要把旧本地克隆的分支合并回来，也不要用 `git add -f` 提交 `nor/` 中清理前的历史恢复副本。

## 发布新版本

一次性准备：把签名密钥写入仓库 Secret `QUIET_TV_KEYSTORE`（Release 与已安装版本同签名，才能覆盖升级）：

```powershell
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("nor\.local\launcher.keystore"))
gh secret set QUIET_TV_KEYSTORE -R Carton/mitv-quiet-launcher --body "$b64"
```

之后每个版本一条命令：`python -B tools/release.py 0.8.7`。发布前先提交并同步 master 与 origin/master；可用 `--dry-run` 检查版本。若目标 versionName 尚未写入，脚本递增 versionCode；若已在开发阶段准备好同一 versionName，则沿用当前 versionCode。Manifest 和 Gradle 元数据会同步，有实际修改才创建版本提交，最后推送 `v0.8.7` tag。GitHub Actions（`.github/workflows/build.yml`）自动构建并创建带 `quiet-tv.apk` 的 Release。Secret 未设置时只构建不发 Release。也可在 Actions 页 Run workflow 手动填 tag 发布。
