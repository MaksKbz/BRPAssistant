#!/usr/bin/env python3
"""
Скрипт для скачивания последнего собранного APK из GitHub Actions в корень воркспейса.
"""
import urllib.request
import json
import zipfile
import os
import shutil

PAT = os.environ.get("GITHUB_TOKEN", os.environ.get("GITHUB_PAT", ""))
REPO = "MaksKbz/BRPAssistant"
API_URL = f"https://api.github.com/repos/{REPO}/actions/runs?per_page=5"

headers = {
    "Authorization": f"token {PAT}",
    "Accept": "application/vnd.github.v3+json"
}

class NoAuthRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req and 'Authorization' in new_req.headers:
            del new_req.headers['Authorization']
        return new_req

opener = urllib.request.build_opener(NoAuthRedirectHandler())
urllib.request.install_opener(opener)

req = urllib.request.Request(API_URL, headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())

runs = [r for r in data.get('workflow_runs', []) if r.get('status') == 'completed' and r.get('conclusion') == 'success']
if not runs:
    print("❌ Нет завершенных успешных сборок")
    exit(1)

latest_run = runs[0]
run_id = latest_run['id']
html_url = latest_run['html_url']
print(f"📦 Найдена успешная сборка Run ID: {run_id}")

art_url = f"https://api.github.com/repos/{REPO}/actions/runs/{run_id}/artifacts"
req_art = urllib.request.Request(art_url, headers=headers)
with urllib.request.urlopen(req_art) as resp:
    arts = json.loads(resp.read().decode()).get('artifacts', [])

apk_art = next((a for a in arts if 'debug' in a['name'].lower() or 'brpassistant' in a['name'].lower() and 'schema' not in a['name'].lower()), None)
if not apk_art:
    print("❌ Артефакт с APK не найден")
    exit(1)

download_url = apk_art['archive_download_url']
art_name = apk_art['name']
print(f"⬇️ Скачивание артефакта {art_name}...")

zip_path = "/tmp/apk_artifact.zip"
req_dl = urllib.request.Request(download_url, headers=headers)
with urllib.request.urlopen(req_dl) as resp, open(zip_path, "wb") as f:
    shutil.copyfileobj(resp, f)

extract_dir = "/tmp/apk_extracted"
shutil.rmtree(extract_dir, ignore_errors=True)
os.makedirs(extract_dir, exist_ok=True)

with zipfile.ZipFile(zip_path, 'r') as zip_ref:
    zip_ref.extractall(extract_dir)

apk_files = [os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.apk')]
if not apk_files:
    print("❌ Внутри архива нет .apk файлов")
    exit(1)

src_apk = apk_files[0]
dest_apk = "/home/user/BRPAssistant-latest.apk"
shutil.copy(src_apk, dest_apk)
print(f"✅ Готово! APK сохранен в: {dest_apk}")
print(f"🔗 Страница сборки на GitHub: {html_url}")
