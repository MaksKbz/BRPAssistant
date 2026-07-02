#!/usr/bin/env python3
"""
Скрипт для скачивания последнего стабильно подписанного релизного APK из GitHub Releases в корень воркспейса.
"""
import urllib.request
import json
import os
import shutil

class NoAuthRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req and 'Authorization' in new_req.headers:
            del new_req.headers['Authorization']
        return new_req

opener = urllib.request.build_opener(NoAuthRedirectHandler())
urllib.request.install_opener(opener)

PAT = os.environ.get("GITHUB_TOKEN", os.environ.get("GITHUB_PAT", ""))
REPO = "MaksKbz/BRPAssistant"
API_URL = f"https://api.github.com/repos/{REPO}/releases/tags/latest"

headers = {
    "Accept": "application/vnd.github.v3+json"
}
if PAT:
    headers["Authorization"] = f"token {PAT}"

req = urllib.request.Request(API_URL, headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())

assets = sorted(data.get('assets', []), key=lambda a: a['id'], reverse=True)
if not assets:
    print("❌ В GitHub Releases нет доступных APK артефактов")
    exit(1)

latest_asset = assets[0]
asset_name = latest_asset['name']
asset_url = latest_asset['url']
html_url = data.get('html_url', f"https://github.com/{REPO}/releases/latest")

print(f"📦 Найдена стабильная релизная сборка: {asset_name}")
print(f"⬇️ Скачивание стабильно подписанного APK {asset_name}...")

req_dl = urllib.request.Request(asset_url, headers={"Accept": "application/octet-stream", **({"Authorization": f"token {PAT}"} if PAT else {})})
dest_apk = "/home/user/BRPAssistant-latest.apk"
with urllib.request.urlopen(req_dl) as resp, open(dest_apk, "wb") as f:
    shutil.copyfileobj(resp, f)

print(f"✅ Готово! Стабильно подписанный APK сохранен в: {dest_apk}")
print(f"🔗 Страница релиза на GitHub: {html_url}")
