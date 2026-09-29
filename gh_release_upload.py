# -*- coding: utf-8 -*-
"""创建 GitHub Release v2.22 并上传签名 APK。"""
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

body = """v2.22 · 2026-09-29

自动导航（支付宝/微信替你翻进去）第三轮修复：

- 配置自检：无障碍服务若还在用旧配置跑（更新后没重开过，系统会一直用启用那一刻缓存的 flags），点「自动导航并读取」会直接提示去重开，不再黑盒失败。支付宝的设置齿轮图标能否读到，取决于这一条。
- 广告窗兜底：开屏广告是一扇无文字的图片窗，且可能恰好是系统认定的活动窗口（实测微信报「当页可见：没读到文本」）。现在自动换成同包名下有内容的窗口接着判断。
- 找不到入口按时间预算等（每步 20 秒），不再被闪屏页误判。

判定方法：装好 v2.22 后到系统设置把「自动扣款读取」关一次再开。若没重开，App 会拦下导航并明说；若重开了还失败，失败提示里的「当页可见」会给出那页的真实文字，照它改候选词即可。"""

rel = api("https://api.github.com/repos/QiuZwan/lifegj/releases",
          data={"tag_name": "v2.22", "target_commitish": "main",
                "name": "v2.22 自动导航三轮：配置自检 + 广告窗兜底", "body": body})
up = rel["upload_url"].split("{")[0]
apk = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
api(up + "?name=lifebutler-v2.22.apk", raw=open(apk, "rb").read(),
    ctype="application/vnd.android.package-archive")
print("release ok:", rel["html_url"])
