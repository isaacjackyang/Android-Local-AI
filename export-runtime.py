"""Run inside Termux: export only software and fresh defaults, never personal state."""
import json, os, pathlib, subprocess, zipfile, hashlib

HOME=pathlib.Path('/data/data/com.termux/files/home')
PREFIX=pathlib.Path('/data/data/com.termux/files/usr')
SOURCE=HOME/'.hermes/hermes-agent'
EXPORT=HOME/'local-ai-export'
OUT=pathlib.Path('/sdcard/Download')
files={}

def add(source,destination=None):
    source=pathlib.Path(source)
    if not source.exists() and not source.is_symlink():return
    dest=str(destination or source).lstrip('/')
    if source.is_dir() and not source.is_symlink():
        for child in source.iterdir():
            if child.name in {'__pycache__','.DS_Store'}:continue
            add(child,pathlib.PurePosixPath(dest)/child.name)
    else:files[dest]=source

# Deliberate executable allowlist; no SSH server, credentials, caches or compiler tree.
commands='bash sh env ls cat head tail pwd mkdir rmdir rm cp mv touch chmod ln readlink realpath find grep egrep fgrep sed awk curl git tar gzip gunzip unzip zip tput less which timeout whoami id uname sleep stat date du df printf xargs sort uniq wc cut tr base64 tee busybox tmux rg file diff patch patchelf readelf ssh scp sftp xz bzip2 zstd kill pkill pgrep getconf nproc'.split()
for name in commands:
    p=PREFIX/'bin'/name;add(p)
    if p.is_symlink():add(p.resolve())
for p in (PREFIX/'lib').iterdir():
    if '.so' in p.name and not p.name.startswith(('libLLVM','libclang','libvulkan_lvp','libgallium')):
        add(p)
for rel in ['libexec/git-core','share/git-core','share/terminfo','etc/tls','etc/ssl','share/termux/licenses','share/doc']:
    add(PREFIX/rel)

add(HOME/'llama-b12052')
add(HOME/'.local/opt/hermes-native')
add(HOME/'.local/share/hermes-termux-env.sh')
add(HOME/'.local/bin/hermes')
add(EXPORT/'hermes-source',SOURCE)
for rel in ['hermes_cli/update_lock.py','hermes_cli/_launchers.py','pm/environment.py','pm/android_link.py','.hermes/bin']:
    add(SOURCE/rel)
add(HOME/'.hermes/tools')
install=HOME/'.hermes/installs/07e6df5db080b527'
facts=json.loads((install/'facts.json').read_text())
add(install/'facts.json')
add(install/'pm-runtime')
add(pathlib.Path(facts['packages']['venv']['environment']).parent)

meta={'expandedBytes':1587969920,'model':'LFM3-1.7B-Uncensored-AD-Q6_K.gguf',
      'modelBytes':1587969920,'modelSha256':'4a7d91c094b9a0f8ed094093f3a0eb3cfeeb46b9bd491e33115689fc5d658b22',
      'executables':[],'symlinks':[],'components':{'llama.cpp':'b12052','Hermes':'v0.22.6+Termux-fixes'}}
generated={
    str(HOME/'start-model.sh'):'''#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
[[ ! -f "$HOME/.localai.env" ]] || source "$HOME/.localai.env"
export LD_LIBRARY_PATH="$HOME/llama-b12052:$PREFIX/lib"
cd "$HOME/llama-b12052"
exec ./llama-server --model "${MODEL_FILE:-$HOME/models/LFM3-1.7B-Uncensored-AD-Q6_K.gguf}" --alias "${MODEL_NAME:-LFM3-1.7B-Uncensored-AD-Q6_K.gguf}" \\
  --host 127.0.0.1 --port 8081 --ctx-size "${CTX:-71680}" \\
  --threads "${THREADS:-4}" --threads-batch "${THREADS:-4}" \\
  --n-gpu-layers 0 --parallel 1 --batch-size 512 --ubatch-size 128 \\
  --flash-attn on --cache-type-k q8_0 --cache-type-v q8_0 \\
  --predict 8192 --temp 0.1 --top-k 50 --top-p 1 --min-p 0 --repeat-penalty 1.1 --jinja
''',
    str(HOME/'start-hermes.sh'):'''#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
export PATH="$HOME/.local/bin:$HOME/.hermes/hermes-agent/.hermes/bin:$PREFIX/bin:/system/bin"
source "$HOME/.local/share/hermes-termux-env.sh"
[[ ! -f "$HOME/.localai.env" ]] || source "$HOME/.localai.env"
export OPENAI_API_KEY=sk-local OPENAI_BASE_URL=http://127.0.0.1:8081/v1
cd "$HOME"
hermes config set model.context_length "${CTX:-71680}"
hermes config set model.default "${MODEL_NAME:-LFM3-1.7B-Uncensored-AD-Q6_K.gguf}"
exec hermes --cli --provider custom --model "${MODEL_NAME:-LFM3-1.7B-Uncensored-AD-Q6_K.gguf}" "$@"
''',
    str(HOME/'.hermes/config.yaml'):'''model:
  default: LFM3-1.7B-Uncensored-AD-Q6_K.gguf
  provider: custom
  base_url: http://127.0.0.1:8081/v1
  context_length: 71680
display:
  interface: cli
terminal:
  backend: local
''',
    str(HOME/'.bashrc'):'''export PATH="$HOME/.local/bin:$HOME/.hermes/hermes-agent/.hermes/bin:$PREFIX/bin:/system/bin"
source "$HOME/.local/share/hermes-termux-env.sh"
PS1='LocalAI \\w $ '
''',
    '/linkerconfig/ld.config.txt':'''dir.default = /
[default]
namespace.default.isolated = false
namespace.default.search.paths = /system/${LIB}:/apex/com.android.runtime/${LIB}/bionic
''',
}
with zipfile.ZipFile(OUT/'codex-localai-runtime.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=False) as z:
    for name,path in sorted(files.items()):
        if path.is_symlink():meta['symlinks'].append({'path':name,'target':os.readlink(path)})
        elif path.is_file():
            z.write(path,name);meta['expandedBytes']+=path.stat().st_size
            if path.stat().st_mode&0o111:meta['executables'].append(name)
    for name,content in generated.items():
        z.writestr(name.lstrip('/'),content);meta['expandedBytes']+=len(content.encode())
        if name.endswith('.sh'):meta['executables'].append(name.lstrip('/'))
(OUT/'codex-localai-payload.json').write_text(json.dumps(meta))
print('Files',len(files),'expanded bytes',meta['expandedBytes'],'runtime archive bytes',(OUT/'codex-localai-runtime.zip').stat().st_size)
