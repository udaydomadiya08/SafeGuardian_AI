"""
Fast parallel downloader for remaining ESC-50 files.
"""
import csv
import os
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE_URL = "https://raw.githubusercontent.com/karolpiczak/ESC-50/master/audio/"
AUDIO_DIR = "ml/data/esc50/audio"
META_FILE = "ml/data/esc50/esc50.csv"

def download_one(fn):
    dest = os.path.join(AUDIO_DIR, fn)
    if os.path.exists(dest) and os.path.getsize(dest) > 1000:
        return
    url = BASE_URL + fn
    try:
        urllib.request.urlretrieve(url, dest)
    except Exception as e:
        pass

def main():
    with open(META_FILE, 'r') as f:
        rows = list(csv.DictReader(f))
    missing = [r['filename'] for r in rows if not os.path.exists(os.path.join(AUDIO_DIR, r['filename']))]
    print(f"Starting parallel download for {len(missing)} files with 16 workers...")
    with ThreadPoolExecutor(max_workers=16) as pool:
        list(pool.map(download_one, missing))
    print("Done! Total files:", len(os.listdir(AUDIO_DIR)))

if __name__ == "__main__":
    main()
