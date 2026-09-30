# -*- coding: utf-8 -*-
"""GitHub Release 发布小工具（本机没有 gh CLI 时的替代）。
用法:
  python gh_rel.py list                      # 列出已有 release(tag/draft/assets)
  python gh_rel.py create <notes.json>       # 用 JSON(tag_name/name/body)建正式 release
  python gh_rel.py upload <release_id> <本地apk> <资产名>
Token 从 git credential fill 取,绝不打印。
"""
import sys, json, urllib.request

REPO = "QiuZwan/lifegj"

def token():
    import subprocess
    inp = "protocol=https\nhost=github.com\n\n".encode()
    out = subprocess.run(["git", "credential", "fill"], input=inp,
                         capture_output=True).stdout.decode("utf-8", "replace")
    for line in out.splitlines():
        if line.startswith("password="):
            return line[len("password="):].strip()
    raise SystemExit("no github token in credential manager")

def api(url, tok, data=None, method=None, ctype="application/json"):
    req = urllib.request.Request(url, method=method)
    req.add_header("Authorization", "Bearer " + tok)
    if data is not None:
        req.add_header("Content-Type", ctype)
        if isinstance(data, str):
            data = data.encode("utf-8")
        req.add_header("Content-Length", str(len(data)))
    req.data = data
    with urllib.request.urlopen(req) as r:
        body = r.read().decode("utf-8")
        return json.loads(body) if body.strip() else {}

def main():
    tok = token()
    cmd = sys.argv[1]
    if cmd == "list":
        rels = api(f"https://api.github.com/repos/{REPO}/releases", tok)
        for r in rels:
            assets = ", ".join(f"{a['name']}({a['size']}B)" for a in r["assets"])
            print(r["tag_name"], "draft" if r["draft"] else "published", "|", assets)
    elif cmd == "create":
        payload = open(sys.argv[2], "rb").read().decode("utf-8")
        r = api(f"https://api.github.com/repos/{REPO}/releases", tok, data=payload, method="POST")
        print("release_id=" + str(r["id"]), r["tag_name"], r["html_url"])
    elif cmd == "delasset":
        rel_tag, asset_name = sys.argv[2], sys.argv[3]
        rels = api(f"https://api.github.com/repos/{REPO}/releases", tok)
        rel = next(r for r in rels if r["tag_name"] == rel_tag)
        for a in rel["assets"]:
            if a["name"] == asset_name:
                api(f"https://api.github.com/repos/{REPO}/releases/assets/{a['id']}",
                    tok, method="DELETE")
                print("deleted", asset_name)
                return
        print("no asset named", asset_name)
    elif cmd == "upload":
        rel_id, apk, name = sys.argv[2], sys.argv[3], sys.argv[4]
        data = open(apk, "rb").read()
        r = api(f"https://uploads.github.com/repos/{REPO}/releases/{rel_id}/assets?name={name}",
                tok, data=data, method="POST", ctype="application/vnd.android.package-archive")
        print("asset:", r["name"], r["size"], r["state"])

if __name__ == "__main__":
    main()
