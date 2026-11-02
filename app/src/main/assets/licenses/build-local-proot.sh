#!/data/data/com.termux/files/usr/bin/bash
# Reproduce the packaged native binaries inside ARM64 Termux.
# Prerequisites: clang, make, libtalloc, libandroid-shmem, patchelf.
set -euo pipefail
cd "$(dirname "$0")/../vendor/proot-5.1.107.102"
PROOT_UNBUNDLE_LOADER=/data/data/com.jackyang.localai/files \
  make -C src -j2 CC=clang PROOT_WITH_LIBANDROID_SHMEM=true
patchelf --replace-needed libtalloc.so.2 libtalloc.so src/proot
patchelf --set-rpath '$ORIGIN' src/proot
echo 'Package src/proot as libproot.so; src/loader/loader as libproot-loader.so.'
