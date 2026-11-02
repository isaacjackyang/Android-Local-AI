import os, pathlib, zipfile, json, subprocess
prefix=pathlib.Path('/data/data/com.termux/files/usr')
dest=pathlib.Path('/sdcard/Download')
files=set()
def add(path):
    path=pathlib.Path(path)
    if path in files or not path.exists(): return
    files.add(path)
    if path.is_symlink(): add(path.resolve())
    if path.is_file() and path.read_bytes()[:4]==b'\x7fELF':
        result=subprocess.run(['patchelf','--print-needed',str(path)],capture_output=True,text=True)
        for name in result.stdout.splitlines(): add(prefix/'lib'/name)
for name in ['bash','ls']: add(prefix/'bin'/name)
meta={'expandedBytes':0,'model':'','executables':[],'symlinks':[]}
with zipfile.ZipFile(dest/'codex-smoke-runtime.zip','w',zipfile.ZIP_DEFLATED) as z:
    for path in sorted(files):
        rel=str(path)[1:]
        if path.is_symlink():meta['symlinks'].append({'path':rel,'target':os.readlink(path)})
        else:
            z.write(path,rel);meta['expandedBytes']+=path.stat().st_size
            if path.stat().st_mode&0o111:meta['executables'].append(rel)
(dest/'codex-smoke-payload.json').write_text(json.dumps(meta))
print(len(files),meta['expandedBytes'])
