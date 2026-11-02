"""Restore build inputs from the matching separately obtained offline APK."""
import argparse
import hashlib
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent
EXPECTED_SHA256 = '09b955776af35e0f4ffc5d0ec198617b78c9e4208bdb9cd327c2544a825ba88f'
FILES = {
    'assets/model.gguf': 'app/src/main/assets/model.gguf',
    'assets/runtime.zip': 'app/src/main/assets/runtime.zip',
    'assets/payload.json': 'app/src/main/assets/payload.json',
    **{f'lib/arm64-v8a/{name}': f'app/src/main/jniLibs/arm64-v8a/{name}'
       for name in ('libandroid-shmem.so', 'libproot-loader.so', 'libproot.so', 'libtalloc.so')},
    'lib/arm64-v8a/libtermux.so': 'libraries/terminal-emulator/src/main/jniLibs/arm64-v8a/libtermux.so',
}

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--check', action='store_true', help='Verify without writing files')
args = parser.parse_args()
digest = hashlib.sha256()
with args.apk.open('rb') as source:
    for chunk in iter(lambda: source.read(4 * 1024 * 1024), b''):
        digest.update(chunk)
if digest.hexdigest() != EXPECTED_SHA256:
    raise SystemExit('APK SHA-256 does not match the tested 1.0.0 build.')
with zipfile.ZipFile(args.apk) as archive:
    missing = set(FILES) - set(archive.namelist())
    if missing:
        raise SystemExit('Missing APK inputs: ' + ', '.join(sorted(missing)))
    if not args.check:
        for member, destination in FILES.items():
            target = ROOT / destination
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(member) as source, target.open('wb') as output:
                shutil.copyfileobj(source, output, 4 * 1024 * 1024)
print('APK verified.' if args.check else 'Build inputs restored from the verified APK.')
