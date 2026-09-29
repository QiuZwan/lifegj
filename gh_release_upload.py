# -*- coding: utf-8 -*-
"""创建 GitHub Release v2.21 并上传签名 APK。"""
import io, sys, json, subprocess, urllib.request, urllib.error, os

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
ROOT = os.path.dirname(os.path.abspath(__file__))

inp = subprocess.run(["git", "credential", "fill"],
                     input="protocol=https\nhost=github.com\n\n",
                     capture_output=True, text=True).stdout
tok = None
for line in inp.splitlines():
    if line.startswith("password="):
        tok = line[9:]
assert tok, "no stored git credential"

def api(url, data=None, raw=None, ctype=None):
    h = {"Authorization": "token " + tok, "Accept": "application/vnd.github+json",
         "User-Agent": "lifebutler-release"}
    body = None
    if data is not None:
        body = json.dumps(data).encode("utf-8"); h["Content-Type"] = "application/json"
    if raw is not None:
        body = raw; h["Content-Type"] = ctype
    req = urllib.request.Request(url, data=body, headers=h, method="POST" if body else "GET")
    try:
        r = urllib.request.urlopen(req, timeout=300)
        return json.loads(r.read().decode("utf-8")) if raw is None else None
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read().decode("utf-8")[:500]); raise

body = """v2.21 · 2026-09-29

自动导航（支付宝/微信替你翻进去）第二轮修复：

- 修「打开就没下文」的第二种死法：开屏广告是一扇没有文字的图片窗（实测 pageTexts 报「没读到文本」），原来重试 6 次约 5 秒就收工，广告还没播完。现在按时间预算等，每步 20 秒内持续等真页面出来。
- 失败诊断增强：当页可见文本为空时，会报「扫过 N 个节点」，能区分「整窗无文字（广告图）」和「真的没这个入口」。

注：如果上一版（2.20）装完后没有重开过无障碍服务，请务必到系统设置把「自动扣款读取」关一次再开 —— 否则新配置（不过滤不重要节点等）不生效，支付宝的齿轮图标读不到。"""

rel = api("https://api.github.com/repos/QiuZwan/lifegj/releases",
          data={"tag_name": "v2.21", "target_commitish": "main",
                "name": "v2.21 自动导航修复二轮（时间预算等待）", "body": body})
up = rel["upload_url"].split("{")[0]
apk = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
api(up + "?name=lifebutler-v2.21.apk", raw=open(apk, "rb").read(),
    ctype="application/vnd.android.package-archive")
print("release ok:", rel["html_url"])
