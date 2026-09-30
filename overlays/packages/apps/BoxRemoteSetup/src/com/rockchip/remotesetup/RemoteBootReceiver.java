package com.rockchip.remotesetup;
import android.content.*;
public class RemoteBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) { c.startService(new Intent(c,RemoteService.class)); }
}
