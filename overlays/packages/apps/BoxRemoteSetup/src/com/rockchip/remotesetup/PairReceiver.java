package com.rockchip.remotesetup;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.os.*;
import android.util.Log;
public class PairReceiver extends BroadcastReceiver {
    static boolean nameMatches(String n) { return n != null && "Bluetooth remote".equalsIgnoreCase(n.replace("\u0000", "").trim()); }
    static SharedPreferences prefs(Context c) { return c.createDeviceProtectedStorageContext().getSharedPreferences("remotes", Context.MODE_PRIVATE); }
    static void remember(Context c, BluetoothDevice d) {
        prefs(c).edit().putBoolean(d.getAddress(), true).apply();
    }
    static boolean matches(Context c, BluetoothDevice d) { return d != null && (nameMatches(d.getName()) || prefs(c).getBoolean(d.getAddress(),false)); }
    @Override public void onReceive(Context c, Intent i) {
        BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        int v=i.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,-1);
        if (d==null || (v!=BluetoothDevice.PAIRING_VARIANT_CONSENT && v!=BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION)) return;
        if (nameMatches(i.getStringExtra(BluetoothDevice.EXTRA_NAME))) remember(c,d);
        if (!matches(c,d)) return;
        remember(c,d);
        if (isOrderedBroadcast()) abortBroadcast();
        PendingResult pending=goAsync();
        confirm(c,d,pending,0);
    }
    static void confirm(Context c,BluetoothDevice d,PendingResult p,int n) {
        if(d.setPairingConfirmation(true)) {Log.i("BoxRemoteSetup","Confirmed remote without dialog");if(p!=null)p.finish();return;}
        if(n<8) {new Handler(Looper.getMainLooper()).postDelayed(()->confirm(c,d,p,n+1),250);return;}
        Log.w("BoxRemoteSetup","Remote consent failed; awaiting a new pairing attempt");if(p!=null)p.finish();
    }
}
