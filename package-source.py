"""Make a redistributable source archive without user data or signing material."""
from pathlib import Path
import zipfile
root=Path(__file__).resolve().parent
target=root/'app/src/main/assets/source/local-ai-source.zip'
target.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for path in root.rglob('*'):
        if not path.is_file():continue
        rel=path.relative_to(root)
        if any(p in {'.git','.gradle','build','payload','downloads','source'} for p in rel.parts):continue
        if path.suffix in {'.jks','.apk','.gguf','.log'}:continue
        if path.name in {'runtime.zip','runtime.new.zip','signing.properties','local.properties','native.tar.gz'}:continue
        if path.name.endswith('-ui.xml') or path.name=='ui.xml':continue
        if rel.parts[0] not in {'app','libraries','vendor','licenses'} and path.suffix not in {'.py','.gradle','.properties','.md'} and path.name!='LICENSE':continue
        z.write(path,rel.as_posix())
print('Source archive bytes:',target.stat().st_size)
