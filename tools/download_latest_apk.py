#!/usr/bin/env python3
"""
Скрипт для скачивания последнего стабильно подписанного релизного APK из GitHub Releases в корень воркспейса.
Теперь использует /releases/latest (по флагу make_latest), а не тег latest.
Поддерживает versioned релизы v2.9.22+.
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
# Используем /releases/latest — возвращает релиз с флагом make_latest=true (v2.9.24), а не тег latest
API_URL = f"https://api.github.com/repos/{REPO}/releases/latest"

headers = {
    "Accept": "application/vnd.github.v3+json",
    "User-Agent": "BRPAssistant-Downloader"
}
if PAT:
    headers["Authorization"] = f"token {PAT}"

req = urllib.request.Request(API_URL, headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())

assets = [a for a in data.get('assets', []) if a['name'].endswith('.apk')]
if not assets:
    print("❌ В последнем релизе нет APK артефактов, пробуем список всех релизов...")
    # Fallback: перебираем последние 5 релизов
    list_url = f"https://api.github.com/repos/{REPO}/releases?per_page=5"
    req_list = urllib.request.Request(list_url, headers=headers)
    with urllib.request.urlopen(req_list) as resp2:
        releases = json.loads(resp2.read().decode())
        for rel in releases:
            apk_assets = [a for a in rel.get('assets', []) if a['name'].endswith('.apk')]
            if apk_assets:
                data = rel
                assets = apk_assets
                break
    if not assets:
        print("❌ В GitHub Releases нет доступных APK артефактов")
        exit(1)

# Сортируем по размеру/id, берём первый
assets_sorted = sorted(assets, key=lambda a: a['size'], reverse=True)
latest_asset = assets_sorted[0]
asset_name = latest_asset['name']
asset_url = latest_asset['url']
html_url = data.get('html_url', f"https://github.com/{REPO}/releases/latest")
tag = data.get('tag_name', 'unknown')

print(f"📦 Найдена стабильная релизная сборка: {asset_name} (tag {tag})")
print(f"⬇️ Скачивание стабильно подписанного APK {asset_name}...")

req_dl = urllib.request.Request(asset_url, headers={"Accept": "application/octet-stream", **({"Authorization": f"token {PAT}"} if PAT else {}), "User-Agent": "BRPAssistant-Downloader"})
dest_apk = "/home/user/BRPAssistant-latest.apk"
with urllib.request.urlopen(req_dl) as resp, open(dest_apk, "wb") as f:
    shutil.copyfileobj(resp, f)

print(f"✅ Готово! Стабильно подписанный APK сохранен в: {dest_apk}")
print(f"🔗 Страница релиза на GitHub: {html_url}")
print(f"🏷️ Тег: {tag} | Размер: {latest_asset['size']} bytes")
