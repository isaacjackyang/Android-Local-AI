"""Apply the current generated launcher texts to an already exported runtime."""
import ast, pathlib, zipfile, shutil
root=pathlib.Path(__file__).resolve().parent
tree=ast.parse((root/'export-runtime.py').read_text())
assignment=next(n for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='generated' for t in n.targets))
values=eval(compile(ast.Expression(assignment.value),'<generated>','eval'),{'HOME':pathlib.PurePosixPath('/data/data/com.termux/files/home')})
replace={k.lstrip('/'):v for k,v in values.items()}
path=root/'app/src/main/assets/runtime.zip';temp=path.with_suffix('.new.zip')
with zipfile.ZipFile(path) as src,zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=False) as dst:
    for info in src.infolist():
        if info.filename in replace:continue
        with src.open(info) as reader,dst.open(info,'w') as writer:shutil.copyfileobj(reader,writer,4*1024*1024)
    for name,value in replace.items():dst.writestr(name,value)
temp.replace(path)
print('Updated runtime launchers and linker configuration:',path.stat().st_size)
