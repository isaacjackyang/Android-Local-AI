package com.jackyang.localai;

import android.content.Context;
import android.system.Os;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

final class RuntimeFiles {
    static final String PREFIX = "/data/data/com.termux/files/usr";
    static final String HOME = "/data/data/com.termux/files/home";
    interface Progress { void update(String value); }
    final Context context;
    final File root;
    RuntimeFiles(Context c) { context=c; root=new File(c.getFilesDir(),"rootfs"); }
    File guest(String path) { return new File(root,path.substring(1)); }
    boolean ready() { return new File(context.getFilesDir(),"runtime-ready-v1").exists(); }
    void install(Progress progress) throws Exception {
        if (ready()) { syncLinkerConfig(); return; }
        JSONObject meta=new JSONObject(readAsset("payload.json"));
        long required=meta.getLong("expandedBytes")+256L*1024*1024;
        if (context.getFilesDir().getUsableSpace()<required)
            throw new IOException("內部儲存空間不足，需要至少 "+(required/1024/1024)+" MB 可用空間");
        root.mkdirs();
        String base=root.getCanonicalPath()+"/";
        progress.update("正在解開 Termux 與 Hermes 執行環境…");
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(context.getAssets().open("runtime.zip")))) {
            ZipEntry entry; byte[] buffer=new byte[1024*1024]; int count=0;
            while((entry=zip.getNextEntry())!=null) {
                File target=new File(root,entry.getName());
                if(!target.getCanonicalPath().startsWith(base)) throw new IOException("不合法的封裝路徑");
                if(entry.isDirectory()) target.mkdirs();
                else {
                    target.getParentFile().mkdirs();
                    try(OutputStream out=new FileOutputStream(target)) { int n; while((n=zip.read(buffer))!=-1) out.write(buffer,0,n); }
                    target.setReadable(true,true); target.setWritable(true,true);
                }
                if(++count%300==0) progress.update("正在安裝執行環境："+count+" 個檔案");
            }
        }
        JSONArray executables=meta.getJSONArray("executables");
        for(int i=0;i<executables.length();i++) {
            File f=new File(root,executables.getString(i));
            if(!f.getCanonicalPath().startsWith(base)) throw new IOException("不合法的權限路徑");
            Os.chmod(f.getAbsolutePath(),0700);
        }
        JSONArray links=meta.getJSONArray("symlinks");
        for(int i=0;i<links.length();i++) {
            JSONObject link=links.getJSONObject(i);
            File f=new File(root,link.getString("path"));
            if(!f.getParentFile().getCanonicalPath().startsWith(base)) throw new IOException("不合法的連結路徑");
            f.getParentFile().mkdirs();
            if(f.exists() || java.nio.file.Files.isSymbolicLink(f.toPath())) f.delete();
            Os.symlink(link.getString("target"),f.getAbsolutePath());
        }
        String model=meta.optString("model","");
        if(!model.isEmpty()) {
            File destination=guest(HOME+"/models/"+model);
            destination.getParentFile().mkdirs();
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long total=0, size=meta.getLong("modelBytes");
            try(InputStream in=context.getAssets().open("model.gguf");OutputStream out=new FileOutputStream(destination)) {
                byte[] buf=new byte[4*1024*1024]; int n,last=-1;
                while((n=in.read(buf))!=-1) {
                    out.write(buf,0,n); digest.update(buf,0,n); total+=n;
                    int percent=(int)(100*total/size);
                    if(percent!=last) { progress.update("正在安裝模型："+percent+"%"); last=percent; }
                }
            }
            StringBuilder hash=new StringBuilder(); for(byte b:digest.digest()) hash.append(String.format("%02x",b&255));
            if(!hash.toString().equals(meta.getString("modelSha256"))) { destination.delete(); throw new IOException("模型完整性檢查失敗"); }
        }
        for(String d:new String[]{"/dev","/proc","/sys","/system","/apex","/vendor","/linkerconfig",PREFIX+"/tmp",HOME}) guest(d).mkdirs();
        for(String name:new String[]{"bin","lib","etc","tmp"}) {
            File link=guest("/"+name);
            if(!java.nio.file.Files.exists(link.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))Os.symlink(PREFIX+"/"+name,link.getAbsolutePath());
        }
        File usr=guest("/usr");if(!java.nio.file.Files.exists(usr.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))Os.symlink(PREFIX,usr.getAbsolutePath());
        try(FileWriter out=new FileWriter(guest(HOME+"/.hermes/config.yaml"))){out.write(readAsset("defaults/config.yaml"));}
        syncLinkerConfig();
        new File(context.getFilesDir(),"runtime-ready-v1").createNewFile();
        progress.update("安裝完成。模型保存在此 App 的內部儲存空間。");
    }
    void syncLinkerConfig() throws IOException {
        File config=guest("/linkerconfig/ld.config.txt");config.getParentFile().mkdirs();
        try(FileWriter out=new FileWriter(config)){out.write(readAsset("defaults/ld.config.txt"));}
    }
    String readAsset(String path) throws IOException {
        try(InputStream in=context.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");
        }
    }
    String executable() { return context.getApplicationInfo().nativeLibraryDir+"/libproot.so"; }
    String[] command(String script) {
        File loader=new File(context.getFilesDir(),"loader");
        try { String target=context.getApplicationInfo().nativeLibraryDir+"/libproot-loader.so";if(!java.nio.file.Files.isSymbolicLink(loader.toPath())||!java.nio.file.Files.readSymbolicLink(loader.toPath()).toString().equals(target)){java.nio.file.Files.deleteIfExists(loader.toPath());Os.symlink(target,loader.getAbsolutePath());} }
        catch(Exception ex) { throw new IllegalStateException("無法準備啟動程式",ex); }
        ArrayList<String> args=new ArrayList<>(Arrays.asList(executable(),"--kill-on-exit","-r",root.getAbsolutePath()));
        for(String bind:new String[]{"/dev","/proc","/sys","/system","/apex","/vendor",context.getApplicationInfo().nativeLibraryDir,context.getCacheDir().getAbsolutePath()})
            if(new File(bind).exists()) { args.add("-b"); args.add(bind); }
        args.addAll(Arrays.asList("-w",HOME,PREFIX+"/bin/bash","--noprofile","--norc","-c",script));
        return args.toArray(new String[0]);
    }
    Map<String,String> environment() {
        HashMap<String,String> env=new HashMap<>();
        env.put("HOME",HOME);env.put("PREFIX",PREFIX);env.put("PATH",PREFIX+"/bin:/system/bin");
        env.put("TERM","xterm-256color");env.put("COLORTERM","truecolor");env.put("LANG","C.UTF-8");
        env.put("TMPDIR",PREFIX+"/tmp");env.put("PROOT_TMP_DIR",context.getCacheDir().getAbsolutePath());
        env.put("PROOT_LOADER",context.getApplicationInfo().nativeLibraryDir+"/libproot-loader.so");
        env.put("LD_LIBRARY_PATH",context.getApplicationInfo().nativeLibraryDir);
        env.put("ANDROID_ROOT","/system");env.put("ANDROID_DATA","/data");
        return env;
    }
    Process process(String script, File log) throws IOException {
        ProcessBuilder p=new ProcessBuilder(command(script));p.environment().clear();p.environment().putAll(environment());
        p.directory(root);p.redirectErrorStream(true);p.redirectOutput(ProcessBuilder.Redirect.appendTo(log));return p.start();
    }
}
