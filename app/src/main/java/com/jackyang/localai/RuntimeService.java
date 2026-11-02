package com.jackyang.localai;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import com.termux.terminal.*;
import java.io.*;
import java.lang.Process;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RuntimeService extends Service implements TerminalSessionClient {
    static RuntimeService instance;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler main=new Handler(Looper.getMainLooper());
    RuntimeFiles runtime;
    volatile Process model;
    volatile int operation=0;
    TerminalSession terminal;
    String terminalMode="";
    String status="準備就緒";
    int activeCtx=71680,activeThreads=4;
    String activeModel="LFM3-1.7B-Uncensored-AD-Q6_K.gguf";
    MainActivity activity;
    PowerManager.WakeLock wakeLock;
    public class LocalBinder extends Binder { RuntimeService service(){return RuntimeService.this;} }
    public IBinder onBind(Intent intent){return new LocalBinder();}
    public void onCreate(){
        super.onCreate();instance=this;runtime=new RuntimeFiles(this);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("runtime","本機 AI",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RuntimeService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"runtime").setSmallIcon(R.drawable.ic_local_ai).setContentTitle("Local AI")
            .setContentText("本機模型與終端機執行環境").setContentIntent(pi).addAction(new Notification.Action.Builder(null,"全部停止",stop).build()).build());
        wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LocalAI:runtime");
    }
    public int onStartCommand(Intent intent,int flags,int id){if(intent!=null&&"stop".equals(intent.getAction())){stopRuntime();stopSelf();}return START_NOT_STICKY;}
    void report(String value){status=value;Log.i("LocalAI",value);main.post(()->{if(activity!=null)activity.refresh();});}
    void launch(String mode){
        final int requested=operation;
        worker.execute(()->{
            try{
                if(requested!=operation)return;
                runtime.install(this::report);
                if(mode.equals("smoke")){
                    File log=new File(getFilesDir(),"smoke.log");
                    Process p=runtime.process("export LD_LIBRARY_PATH=$PREFIX/lib; echo LOCAL_AI_ROOT_OK; pwd; bash --version; ls -ld $HOME",log);
                    if(!p.waitFor(30,TimeUnit.SECONDS)){p.destroy();throw new IOException("執行環境測試逾時");}
                    report("環境測試結束，代碼 "+p.exitValue()+"。詳細結果請開啟記錄。");return;
                }
                if(requested!=operation)return;
                if(mode.equals("web")||mode.equals("hermes"))startModel(requested);
                if(requested!=operation)return;
                main.post(()->{if(mode.equals("web")){if(activity!=null)activity.showWeb();}else showTerminal(mode);});
            }catch(Exception ex){Log.e("LocalAI","啟動失敗",ex);report("啟動失敗："+ex.getMessage());}
        });
    }
    boolean healthy(){
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:8081/health").openConnection();
            c.setConnectTimeout(1000);c.setReadTimeout(1000);int code=c.getResponseCode();c.disconnect();return code==200;
        }catch(IOException e){return false;}
    }
    void startModel(int requested) throws Exception {
        if(model!=null&&model.isAlive()&&healthy())return;
        if(healthy())throw new IOException("8081 連接埠已由其他程式使用，請先停止該服務");
        SharedPreferences prefs=getSharedPreferences("model",MODE_PRIVATE);
        activeCtx=prefs.getInt("ctx",71680);activeThreads=prefs.getInt("threads",Math.min(4,Runtime.getRuntime().availableProcessors()));
        activeModel=prefs.getString("modelFile",SettingsActivity.DEFAULT_MODEL);
        File models=runtime.guest(RuntimeFiles.HOME+"/models");File selected=new File(models,activeModel);
        if(!selected.isFile()||!selected.getCanonicalPath().startsWith(models.getCanonicalPath()+"/"))throw new IOException("找不到選擇的模型，請到設定重新選取");
        File config=runtime.guest(RuntimeFiles.HOME+"/.localai.env");
        try(FileWriter out=new FileWriter(config)){out.write("export CTX="+activeCtx+"\nexport THREADS="+activeThreads+"\nexport MODEL_NAME="+shellQuote(activeModel)+"\nexport MODEL_FILE="+shellQuote(RuntimeFiles.HOME+"/models/"+activeModel)+"\n");}
        report("正在載入 "+activeModel+" · CTX "+activeCtx+" · "+activeThreads+" 執行緒…");
        if(!wakeLock.isHeld())wakeLock.acquire();
        model=runtime.process("export LD_LIBRARY_PATH=$PREFIX/lib; echo $$ > $HOME/.localai-model.pid; exec bash $HOME/start-model.sh",new File(getFilesDir(),"model.log"));
        for(int i=0;i<180;i++){
            if(requested!=operation){model.destroy();return;}
            if(!model.isAlive())throw new IOException("模型程序已結束，請查看記錄");
            if(healthy()){report(activeModel+" · CTX "+activeCtx+" · "+activeThreads+" 執行緒");return;}
            Thread.sleep(1000);
        }
        model.destroy();throw new IOException("模型載入逾時");
    }
    static String shellQuote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}
    void showTerminal(String mode){
        if(terminal==null||!terminal.isRunning()||!terminalMode.equals(mode)){
            if(terminal!=null&&terminal.isRunning()){report("請先結束目前的終端機工作，再切換模式");if(activity!=null)activity.showTerminal();return;}
            String script="export LD_LIBRARY_PATH=$PREFIX/lib; "+(mode.equals("hermes")?"printf '正在啟動 Hermes，請等候輸入提示出現…\\n'; exec bash $HOME/start-hermes.sh":"source $HOME/.bashrc; exec bash --noprofile --norc -i");
            String[] command=runtime.command(script);
            ArrayList<String> env=new ArrayList<>();for(Map.Entry<String,String> e:runtime.environment().entrySet())env.add(e.getKey()+"="+e.getValue());
            terminal=new TerminalSession(command[0],runtime.root.getAbsolutePath(),command,env.toArray(new String[0]),2000,this);
            terminalMode=mode;
        }
        if(activity!=null)activity.showTerminal();
        report((mode.equals("hermes")?"Hermes · ":"終端機 · ")+activeModel+" · CTX "+activeCtx+" · "+activeThreads+" 執行緒");
    }
    void stopRuntime(){
        operation++;
        try {stopOwnedProcess(Integer.parseInt(new String(java.nio.file.Files.readAllBytes(runtime.guest(RuntimeFiles.HOME+"/.localai-model.pid").toPath()),"UTF-8").trim()),"llama-server");}catch(Exception ignored){}
        try {org.json.JSONObject pid=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(runtime.guest(RuntimeFiles.HOME+"/.hermes/gateway.pid").toPath()),"UTF-8"));if("hermes-gateway".equals(pid.optString("kind")))stopOwnedProcess(pid.getInt("pid"),"hermes_cli.main");}catch(Exception ignored){}
        if(terminal!=null&&terminal.isRunning())terminal.finishIfRunning();
        if(model!=null)model.destroy();
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();report("已停止模型與終端機");
    }
    String processIdentity(int pid,String command) throws Exception {
        if(pid<=1||pid==android.os.Process.myPid()||android.system.Os.stat("/proc/"+pid).st_uid!=android.os.Process.myUid())return null;
        String cmd=new String(java.nio.file.Files.readAllBytes(new File("/proc/"+pid+"/cmdline").toPath()),"UTF-8");
        if(!cmd.contains(command))return null;
        String stat=new String(java.nio.file.Files.readAllBytes(new File("/proc/"+pid+"/stat").toPath()),"UTF-8");
        return stat.substring(stat.lastIndexOf(')')+2).split(" ")[19];
    }
    void stopOwnedProcess(int pid,String command) throws Exception {
        String identity=processIdentity(pid,command);if(identity==null)return;
        android.system.Os.kill(pid,15);
        main.postDelayed(()->{try{if(identity.equals(processIdentity(pid,command)))android.system.Os.kill(pid,9);}catch(Exception ignored){}},1500);
    }
    void restartModel(){
        Process previous=model;
        stopRuntime();
        worker.execute(()->{
            try {
                if(previous!=null&&!previous.waitFor(10,TimeUnit.SECONDS)){
                    previous.destroyForcibly();previous.waitFor(5,TimeUnit.SECONDS);
                }
                for(int i=0;i<20&&healthy();i++)Thread.sleep(250);
                model=null;
                main.post(()->launch("web"));
            } catch(InterruptedException e){Thread.currentThread().interrupt();}
        });
    }
    public void onDestroy(){stopRuntime();worker.shutdownNow();instance=null;super.onDestroy();}
    public void onTextChanged(TerminalSession s){if(activity!=null)activity.terminalChanged();}
    public void onTitleChanged(TerminalSession s){}
    public void onSessionFinished(TerminalSession s){if(s==terminal)report("終端機已結束："+s.getExitStatus());}
    public void onCopyTextToClipboard(TerminalSession s,String text){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Terminal",text));}
    public void onPasteTextFromClipboard(TerminalSession s){android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(c.hasPrimaryClip())s.write(c.getPrimaryClip().getItemAt(0).coerceToText(this).toString());}
    public void onBell(TerminalSession s){}
    public void onColorsChanged(TerminalSession s){onTextChanged(s);}
    public void onTerminalCursorStateChange(boolean state){}
    public Integer getTerminalCursorStyle(){return 0;}
    public void logError(String t,String m){Log.e(t,m);}public void logWarn(String t,String m){Log.w(t,m);}
    public void logInfo(String t,String m){Log.i(t,m);}public void logDebug(String t,String m){Log.d(t,m);}public void logVerbose(String t,String m){Log.v(t,m);}
    public void logStackTraceWithMessage(String t,String m,Exception e){Log.e(t,m,e);}public void logStackTrace(String t,Exception e){Log.e(t,"terminal",e);}
}
