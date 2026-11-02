package com.jackyang.localai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.util.*;

public class SettingsActivity extends Activity {
    EditText ctx,threads;
    TextView modelLabel;
    static final String DEFAULT_MODEL="LFM3-1.7B-Uncensored-AD-Q6_K.gguf";
    int maxThreads;
    public void onCreate(Bundle state){
        super.onCreate(state);setTitle("模型設定");
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,30,24,20);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
        SharedPreferences p=getSharedPreferences("model",MODE_PRIVATE);
        maxThreads=Runtime.getRuntime().availableProcessors();
        label(body,"模型設定",25);
        modelLabel=new TextView(this);modelLabel.setTextSize(16);body.addView(modelLabel);showModel();
        Button choose=new Button(this);choose.setText("選擇模型");body.addView(choose);choose.setOnClickListener(v->chooseModel());
        label(body,"支援 GGUF。匯入時會複製到 App 儲存空間；選擇後，重新啟動模型即可套用。其他模型可搭配不同 CTX 與執行緒設定。",14);
        label(body,"CTX（context tokens）",17);ctx=number(body,p.getInt("ctx",71680));
        label(body,"可輸入 1024～131072。70K = 71680。較大的 CTX 會使用更多記憶體。",14);
        LinearLayout presets=new LinearLayout(this);body.addView(presets);
        for(int n:new int[]{8192,32768,71680}){Button b=new Button(this);b.setText((n/1024)+"K");b.setOnClickListener(v->ctx.setText(String.valueOf(n)));presets.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        label(body,"CPU 執行緒數",17);threads=number(body,p.getInt("threads",Math.min(4,maxThreads)));
        label(body,"此手機可用 "+maxThreads+" 個 CPU 執行緒。此值同時套用於生成與提示處理；執行緒較多不一定較快。",14);
        label(body,"儲存後於下次啟動模型套用。若選擇重新啟動，會停止目前的模型及終端機工作。",14);
        Button save=new Button(this);save.setText("儲存");body.addView(save);save.setOnClickListener(v->save(false));
        Button restart=new Button(this);restart.setText("儲存並重新啟動模型");body.addView(restart);restart.setOnClickListener(v->save(true));
        Button defaults=new Button(this);defaults.setText("恢復預設：70K / 4 執行緒");body.addView(defaults);defaults.setOnClickListener(v->{ctx.setText("71680");threads.setText(String.valueOf(Math.min(4,maxThreads)));});
        Button licenses=new Button(this);licenses.setText("開源與模型授權");body.addView(licenses);licenses.setOnClickListener(v->{try{TextView text=new TextView(this);text.setText(new RuntimeFiles(this).readAsset("licenses/NOTICE.txt")+"\n\n"+new RuntimeFiles(this).readAsset("licenses/LFM-LICENSE.txt"));text.setTextIsSelectable(true);text.setPadding(20,10,20,10);ScrollView view=new ScrollView(this);view.addView(text);new AlertDialog.Builder(this).setTitle("授權與來源").setView(view).setPositiveButton("關閉",null).show();}catch(IOException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});
    }
    void showModel(){modelLabel.setText("目前選擇：\n"+getSharedPreferences("model",MODE_PRIVATE).getString("modelFile",DEFAULT_MODEL));}
    void chooseModel(){
        File dir=new RuntimeFiles(this).guest(RuntimeFiles.HOME+"/models");
        ArrayList<String> names=new ArrayList<>();names.add(DEFAULT_MODEL);
        File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile()&&f.getName().toLowerCase(Locale.ROOT).endsWith(".gguf")&&!f.getName().equals(DEFAULT_MODEL))names.add(f.getName());
        ArrayList<String> labels=new ArrayList<>(names);labels.set(0,"內建 LFM3 1.7B");labels.add("從手機匯入其他 GGUF…");
        new AlertDialog.Builder(this).setTitle("選擇 GGUF 模型").setItems(labels.toArray(new String[0]),(d,index)->{
            if(index==names.size()){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");startActivityForResult(pick,71);}
            else {getSharedPreferences("model",MODE_PRIVATE).edit().putString("modelFile",names.get(index)).apply();showModel();}
        }).setNegativeButton("取消",null).show();
    }
    protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=71||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();String name="model.gguf";long size=-1;
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){name=c.getString(0);if(!c.isNull(1))size=c.getLong(1);}
        }
        if(name==null||!name.toLowerCase(Locale.ROOT).endsWith(".gguf")){Toast.makeText(this,"請選擇 .gguf 模型檔案",Toast.LENGTH_LONG).show();return;}
        String sanitized=name.replaceAll("[^\\p{L}\\p{N}._ -]","_");
        final String safeName=sanitized.length()>100?sanitized.substring(0,90)+".gguf":sanitized;final long expected=size;
        ProgressDialog progress=new ProgressDialog(this);progress.setTitle("匯入模型");progress.setMessage("正在複製 GGUF…");progress.setCancelable(false);progress.show();
        new Thread(()->{
            File partial=null;
            try{
                File dir=new RuntimeFiles(this).guest(RuntimeFiles.HOME+"/models");dir.mkdirs();
                if(expected>0&&dir.getUsableSpace()<expected+128L*1024*1024)throw new IOException("內部儲存空間不足");
                String filename=UUID.randomUUID().toString().substring(0,8)+"-"+safeName;
                File destination=new File(dir,filename);partial=new File(dir,filename+".part");long total=0;
                try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(partial)){
                    if(in==null)throw new IOException("無法讀取此檔案");
                    byte[] header=new byte[8];int got=0,n;
                    while(got<8&&(n=in.read(header,got,8-got))>0)got+=n;
                    if(got!=8||header[0]!='G'||header[1]!='G'||header[2]!='U'||header[3]!='F'||(header[4]!=2&&header[4]!=3))throw new IOException("此檔案不是支援的 GGUF 模型");
                    out.write(header);total=8;byte[] buf=new byte[4*1024*1024];int last=-1;
                    while((n=in.read(buf))!=-1){out.write(buf,0,n);total+=n;int value=(int)(expected>0?100*total/expected:total/1024/1024);if(value!=last){last=value;String message=expected>0?"正在複製："+value+"%":"已複製 "+value+" MB";runOnUiThread(()->progress.setMessage(message));}}
                    out.getFD().sync();
                }
                if(expected>=0&&total!=expected)throw new IOException("檔案未完整複製，請重新匯入");
                if(!partial.renameTo(destination))throw new IOException("無法儲存模型");
                getSharedPreferences("model",MODE_PRIVATE).edit().putString("modelFile",filename).apply();
                runOnUiThread(()->{progress.dismiss();showModel();Toast.makeText(this,"匯入完成，重新啟動後套用",Toast.LENGTH_LONG).show();});
            }catch(Exception e){if(partial!=null)partial.delete();runOnUiThread(()->{progress.dismiss();new AlertDialog.Builder(this).setTitle("匯入失敗").setMessage(e.getMessage()).setPositiveButton("關閉",null).show();});}
        },"model-import").start();
    }
    void label(LinearLayout body,String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setPadding(0,14,0,8);body.addView(t);}
    EditText number(LinearLayout body,int value){EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSingleLine(true);e.setText(String.valueOf(value));body.addView(e);return e;}
    void save(boolean restart){
        int c,t;
        try{c=Integer.parseInt(ctx.getText().toString().trim());}catch(NumberFormatException e){ctx.setError("請輸入整數");return;}
        if(c<1024||c>131072){ctx.setError("請輸入 1024～131072");return;}
        try{t=Integer.parseInt(threads.getText().toString().trim());}catch(NumberFormatException e){threads.setError("請輸入整數");return;}
        if(t<1||t>maxThreads){threads.setError("請輸入 1～"+maxThreads);return;}
        getSharedPreferences("model",MODE_PRIVATE).edit().putInt("ctx",c).putInt("threads",t).apply();
        Toast.makeText(this,"已儲存：CTX "+c+" / "+t+" 執行緒",Toast.LENGTH_LONG).show();
        if(restart)startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("mode","restart"));
        finish();
    }
}
