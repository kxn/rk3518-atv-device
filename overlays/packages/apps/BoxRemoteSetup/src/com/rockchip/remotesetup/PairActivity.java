package com.rockchip.remotesetup;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
/** First-boot UI observes the service; it never starts a second scan or bond operation. */
public class PairActivity extends Activity {
    final Handler handler=new Handler(Looper.getMainLooper());
    TextView status;boolean required,closed,registered;
    final BroadcastReceiver events=new BroadcastReceiver(){public void onReceive(Context c,Intent i){update();}};
    final Runnable tick=new Runnable(){public void run(){if(!closed){update();handler.postDelayed(this,500);}}};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        required="com.android.provision".equals(getCallingPackage())&&Settings.Secure.getInt(getContentResolver(),Settings.Secure.USER_SETUP_COMPLETE,0)==0;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(70,50,70,50);root.setBackgroundColor(Color.rgb(18,24,33));
        TextView title=new TextView(this);title.setText("连接遥控器");title.setTextColor(Color.WHITE);title.setTextSize(34);title.setGravity(Gravity.CENTER);root.addView(title);
        TextView instruction=new TextView(this);instruction.setText("已配对的遥控器：短按方向键唤醒。\n首次连接：按住配对组合键，直到指示灯持续闪烁。\n盒子会自动配对，无需点击确认。");instruction.setTextColor(0xffb9c7d8);instruction.setTextSize(21);instruction.setGravity(Gravity.CENTER);instruction.setPadding(0,30,0,34);root.addView(instruction);
        status=new TextView(this);status.setTextColor(0xff88d7ff);status.setTextSize(24);status.setGravity(Gravity.CENTER);root.addView(status);setContentView(root);
        registerReceiver(events,new IntentFilter(RemoteService.ACTION_STATUS),Context.RECEIVER_NOT_EXPORTED);registered=true;
        startService(new Intent(this,RemoteService.class).putExtra("ui_visible",true));handler.post(tick);
    }
    void update(){if(closed)return;status.setText(RemoteService.status);
        if(RemoteService.ready&&RemoteService.hasDpad()){closed=true;status.setText("遥控器已连接，即将继续");handler.postDelayed(()->{setResult(RESULT_OK);finish();},500);}
    }
    @Override public void onBackPressed(){if(!required)super.onBackPressed();}
    @Override protected void onDestroy(){closed=true;handler.removeCallbacksAndMessages(null);if(registered)unregisterReceiver(events);startService(new Intent(this,RemoteService.class).putExtra("ui_visible",false));super.onDestroy();}
}
