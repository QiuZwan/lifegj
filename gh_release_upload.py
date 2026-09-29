# -*- coding: utf-8 -*-
"""创建 GitHub Release v2.20 并上传签名 APK（用本机 git 凭据）。"""
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

body = """v2.20 · 2026-09-29

一、全应用文案改口（交互逻辑未动）
- 「守护」页更名「订阅管理」；「义务」→「到期事项」、「待认领线索」→「待确认」、「认得」→「确认」、「以后别再提」→「忽略此商户」、「关闭中」→「关闭复核中」、「代扣协议读取」→「自动扣款读取」、「每日简报」→「今日概要」。
- 确认框只说一个后果，界面撤掉免责声明与原理讲解；管家人格只留在对话页。

二、自动导航（支付宝/微信替你翻进去）
- 修「打开应用就没下文」：开屏/广告页上一次没看到入口就收工，现在会等真页面出来（最多再看 6 次）。
- 每一步在对方 App 里实时弹进度；失败当场弹原因并附「当页可见」的实际文字。
- 无障碍配置加不过滤“不重要节点”，节点扫描预算提升。

升级后请到系统设置把「自动扣款读取」关一次再打开，否则新配置不生效。"""

rel = api("https://api.github.com/repos/QiuZwan/lifegj/releases",
          data={"tag_name": "v2.20", "target_commitish": "main",
                "name": "v2.20 文案改口 + 自动导航修复", "body": body})
up = rel["upload_url"].split("{")[0]
apk = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
api(up + "?name=lifebutler-v2.20.apk", raw=open(apk, "rb").read(),
    ctype="application/vnd.android.package-archive")
print("release ok:", rel["html_url"])
