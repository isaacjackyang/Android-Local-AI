package com.jackyang.localai;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import com.termux.terminal.*;
import com.termux.view.*;
import java.io.*;

public class MainActivity extends Activity implements TerminalViewClient {
    RuntimeService service;LinearLayout layout;FrameLayout content;TextView status;TerminalView terminal;WebView web;
    boolean bound=false;String pending="";boolean control=false;
    final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder b){service=((RuntimeService.LocalBinder)b).service();service.activity=MainActivity.this;bound=true;refresh();if(!pending.isEmpty()){launch(pending);pending="";}}
        public void onServiceDisconnected(ComponentName n){service=null;bound=false;}
    };
    public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(0xff11251f);getWindow().setNavigationBarColor(0xff11251f);
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(0xff11251f);setContentView(layout);
        TextView title=new TextView(this);title.setText("Local AI");title.setTextColor(0xffc9ffe2);title.setTextSize(22);title.setPadding(20,15,15,8);layout.addView(title);
        LinearLayout bar=new LinearLayout(this);layout.addView(bar);
        button(bar,"llama.cpp",()->launch("web"));button(bar,"Hermes",()->launch("hermes"));button(bar,"終端機",()->launch("terminal"));
        LinearLayout more=new LinearLayout(this);layout.addView(more);
        button(more,"設定",()->startActivity(new Intent(this,SettingsActivity.class)));button(more,"停止",()->{if(service!=null)service.stopRuntime();});button(more,"記錄",this::logs);button(more,"捷徑",this::shortcuts);
        status=new TextView(this);status.setTextColor(0xffd0ddd7);status.setPadding(16,6,16,10);status.setTextSize(13);layout.addView(status);
        content=new FrameLayout(this);layout.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        TextView welcome=new TextView(this);welcome.setText("選擇 llama.cpp 開啟聊天網頁，或選擇 Hermes 啟動代理。\n\n首次啟動會離線安裝模型與執行環境，需要幾分鐘。\n\nContext：70K（71680 tokens）\n所有資料均保存在 Local AI 的獨立內部儲存空間。");welcome.setTextSize(18);welcome.setTextColor(0xffe0eee7);welcome.setPadding(24,30,24,10);content.addView(welcome);
        pending=getIntent().getStringExtra("mode");if(pending==null)pending="";
        Intent intent=new Intent(this,RuntimeService.class);startForegroundService(intent);bindService(intent,connection,BIND_AUTO_CREATE);
    }
    void button(LinearLayout bar,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);bar.addView(b,new LinearLayout.LayoutParams(0,48*getResources().getDisplayMetrics().densityDpi/160,1));b.setOnClickListener(v->action.run());}
    void launch(String mode){if(service!=null){if(mode.equals("restart"))service.restartModel();else service.launch(mode);}else pending=mode;}
    void refresh(){if(service!=null)status.setText(service.status);}
    void showWeb(){
        content.removeAllViews();if(web==null){web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.setWebViewClient(new WebViewClient(){public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"127.0.0.1".equals(request.getUrl().getHost());}});web.loadUrl("http://127.0.0.1:8081");}
        content.addView(web);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(content.getWindowToken(),0);
    }
    void showTerminal(){
        content.removeAllViews();LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);content.addView(panel);
        terminal=new TerminalView(this,null);terminal.setFocusable(true);terminal.setFocusableInTouchMode(true);terminal.setTerminalViewClient(this);terminal.setTextSize((int)(12*getResources().getDisplayMetrics().scaledDensity));terminal.setTypeface(Typeface.MONOSPACE);
        panel.addView(terminal,new LinearLayout.LayoutParams(-1,0,1));terminal.attachSession(service.terminal);
        LinearLayout keys=new LinearLayout(this);panel.addView(keys);button(keys,"ESC",()->service.terminal.write("\u001b"));button(keys,"TAB",()->service.terminal.write("\t"));button(keys,"CTRL-C",()->service.terminal.write("\u0003"));button(keys,"鍵盤",this::showKeyboard);terminal.requestFocus();
    }
    void showKeyboard(){if(terminal!=null){terminal.requestFocus();terminal.post(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(terminal,InputMethodManager.SHOW_IMPLICIT));}}
    void terminalChanged(){if(terminal!=null)terminal.onScreenUpdated();}
    void logs(){
        StringBuilder text=new StringBuilder();for(String name:new String[]{"model.log","smoke.log"})try{File file=new File(getFilesDir(),name);if(file.exists()){byte[] data=java.nio.file.Files.readAllBytes(file.toPath());text.append(name).append("\n").append(new String(data,Math.max(0,data.length-18000),Math.min(data.length,18000),"UTF-8")).append("\n");}}catch(IOException e){text.append(e.getMessage());}
        TextView view=new TextView(this);view.setText(text.length()>0?text:"尚無記錄");view.setTextIsSelectable(true);view.setTextSize(11);ScrollView scroll=new ScrollView(this);scroll.addView(view);new AlertDialog.Builder(this).setTitle("執行記錄").setView(scroll).setPositiveButton("關閉",null).show();
    }
    void shortcuts(){new AlertDialog.Builder(this).setTitle("加入主畫面捷徑").setItems(new String[]{"llama.cpp 70K","Hermes 70K"},(d,index)->{
        ShortcutManager sm=getSystemService(ShortcutManager.class);if(!sm.isRequestPinShortcutSupported()){Toast.makeText(this,"此桌面不支援釘選捷徑",Toast.LENGTH_LONG).show();return;}
        String mode=index==0?"web":"hermes";Intent intent=new Intent(this,MainActivity.class).setAction(Intent.ACTION_VIEW).putExtra("mode",mode);
        sm.requestPinShortcut(new ShortcutInfo.Builder(this,mode).setShortLabel(index==0?"llama.cpp 70K":"Hermes 70K").setIcon(Icon.createWithResource(this,R.drawable.ic_local_ai)).setIntent(intent).build(),null);
    }).show();}
    protected void onNewIntent(Intent intent){super.onNewIntent(intent);String mode=intent.getStringExtra("mode");if(mode!=null)launch(mode);}
    protected void onDestroy(){if(service!=null&&service.activity==this)service.activity=null;if(bound)unbindService(connection);super.onDestroy();}
    public float onScale(float scale){return 1.0f;}public void onSingleTapUp(MotionEvent e){showKeyboard();}
    public boolean shouldBackButtonBeMappedToEscape(){return false;}public boolean shouldEnforceCharBasedInput(){return false;}public boolean shouldUseCtrlSpaceWorkaround(){return false;}public boolean isTerminalViewSelected(){return true;}public void copyModeChanged(boolean mode){}
    public boolean onKeyDown(int key,KeyEvent e,TerminalSession s){return false;}public boolean onKeyUp(int key,KeyEvent e){return false;}public boolean onLongPress(MotionEvent e){return false;}
    public boolean readControlKey(){boolean c=control;control=false;return c;}public boolean readAltKey(){return false;}public boolean readShiftKey(){return false;}public boolean readFnKey(){return false;}
    public boolean onCodePoint(int c,boolean ctrl,TerminalSession s){return false;}public void onEmulatorSet(){}
    public void logError(String t,String m){android.util.Log.e(t,m);}public void logWarn(String t,String m){android.util.Log.w(t,m);}public void logInfo(String t,String m){android.util.Log.i(t,m);}public void logDebug(String t,String m){android.util.Log.d(t,m);}public void logVerbose(String t,String m){android.util.Log.v(t,m);}public void logStackTraceWithMessage(String t,String m,Exception e){android.util.Log.e(t,m,e);}public void logStackTrace(String t,Exception e){android.util.Log.e(t,"terminal",e);}
}
