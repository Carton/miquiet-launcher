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
