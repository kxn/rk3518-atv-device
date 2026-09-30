package com.rockchip.remotesetup;

import android.app.Service;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import android.view.InputDevice;
import android.widget.Toast;
import java.util.Locale;

/** Single owner of scan, bond and HID operations, derived from the stock BTHelp flow. */
public class RemoteService extends Service {
    static final String TAG="BoxRemoteSetup", ACTION_STATUS="com.rockchip.remotesetup.STATUS";
    static String status="正在开启蓝牙…";
    static boolean ready;
    final Handler handler=new Handler(Looper.getMainLooper());
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    BluetoothHidHost hid;
    BluetoothDevice target;
    boolean scanning,registered,aclUp,uiVisible,destroyed,profileRequested;
    int profileGeneration;
    long toastAt;
    long scanAt,advertisedAt,connectAt,bondAt,cooldownUntil,aclAt;
    String advertisedAddress;
    final Runnable tick=new Runnable(){public void run(){if(!destroyed){check();handler.postDelayed(this,1000);}}};
    final ScanCallback scan=new ScanCallback(){
        public void onScanResult(int type,ScanResult result){
            if(destroyed||!scanning||adapter==null||!adapter.isEnabled())return;
            BluetoothDevice device=result.getDevice();
            String name=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();
            if(!PairReceiver.matches(RemoteService.this,device)&&!PairReceiver.nameMatches(name))return;
            if(target!=null&&!target.equals(device)){
                // A manually forgotten remote must not pin the service forever.
                // Never replace a bonded/connecting remote because another advert appears.
                if(aclUp||target.getBondState()!=BluetoothDevice.BOND_NONE||
                        (hid!=null&&hid.getConnectionState(target)!=BluetoothProfile.STATE_DISCONNECTED))return;
                resetConnectionState();
            }
            PairReceiver.remember(RemoteService.this,device);target=device;
            advertisedAt=now();advertisedAddress=device.getAddress();
            check();
        }
        public void onScanFailed(int code){scanning=false;cooldownUntil=now()+2000;Log.w(TAG,"Scan failed "+code);}
    };
    final BroadcastReceiver events=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
        String action=intent.getAction();
        if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
            if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1)==BluetoothAdapter.STATE_ON)obtainHid();
            else {stopScan();resetConnectionState();closeHid();}
            check();return;
        }
        BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if(!PairReceiver.matches(context,device))return;
        if(target!=null&&!target.equals(device))return;
        target=device;PairReceiver.remember(context,device);
        if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
            aclUp=true;aclAt=now();if(connectAt==0)connectAt=now();
            publish("遥控器正在重新连接…",true);
        } else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
            aclUp=false;ready=false;cooldownUntil=now()+1000;
        } else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
            int bond=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,-1);
            Log.i(TAG,"Remote bond state="+bond);
            if(bond==BluetoothDevice.BOND_NONE){
                bondAt=0;connectAt=0;ready=false;
                // Allow the native failed transaction and ACL teardown to finish first.
                cooldownUntil=now()+2000;
            } else if(bond==BluetoothDevice.BOND_BONDING){bondAt=now();stopScan();publish("正在自动配对，请稍候…",true);}
            else if(bond==BluetoothDevice.BOND_BONDED){connectAt=0;bondAt=0;cooldownUntil=now()+500;}
        } else if(BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED.equals(action)){
            int state=intent.getIntExtra(BluetoothProfile.EXTRA_STATE,-1);
            Log.i(TAG,"Remote HID state="+state);
            if(state==BluetoothProfile.STATE_CONNECTED){connectAt=0;}
            if(state==BluetoothProfile.STATE_DISCONNECTED){ready=false;if(connectAt==0)cooldownUntil=now()+1000;}
        }
        check();
    }};
    static long now(){return SystemClock.elapsedRealtime();}
    @Override public void onCreate(){super.onCreate();adapter=BluetoothAdapter.getDefaultAdapter();
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);filter.addAction(BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(events,filter,android.Manifest.permission.BLUETOOTH_CONNECT,handler,Context.RECEIVER_EXPORTED);registered=true;
        obtainHid();handler.post(tick);Log.i(TAG,"Stock-derived remote state machine ready");
    }
    void resetConnectionState(){
        aclUp=false;ready=false;connectAt=0;bondAt=0;aclAt=0;
        advertisedAt=0;advertisedAddress=null;cooldownUntil=0;
    }
    void closeHid(){
        ++profileGeneration;profileRequested=false;
        if(adapter!=null&&hid!=null)adapter.closeProfileProxy(BluetoothProfile.HID_HOST,hid);
        hid=null;
    }
    void obtainHid(){
        if(destroyed||adapter==null||!adapter.isEnabled()||hid!=null||profileRequested)return;
        profileRequested=true;
        final int generation=++profileGeneration;
        boolean accepted=adapter.getProfileProxy(this,new BluetoothProfile.ServiceListener(){
            public void onServiceConnected(int profile,BluetoothProfile proxy){
                if(destroyed||generation!=profileGeneration||!adapter.isEnabled()){
                    adapter.closeProfileProxy(profile,proxy);return;
                }
                profileRequested=false;hid=(BluetoothHidHost)proxy;check();
            }
            public void onServiceDisconnected(int profile){
                // Keep the proxy: it rebinds itself when the service returns.
                if(generation==profileGeneration)ready=false;
            }
        },BluetoothProfile.HID_HOST);
        if(!accepted)profileRequested=false;
    }
    void selectTarget(){
        if(target!=null)return;
        for(BluetoothDevice device:adapter.getBondedDevices())if(PairReceiver.matches(this,device)){target=device;PairReceiver.remember(this,device);return;}
    }
    void check(){
        if(destroyed)return;
        if(adapter==null){publish("未找到蓝牙设备，请检查固件。",false);return;}
        if(!adapter.isEnabled()){ready=false;publish("正在开启蓝牙…",false);if(adapter.getState()==BluetoothAdapter.STATE_OFF)adapter.enable();return;}
        obtainHid();selectTarget();
        if(target!=null&&hid!=null&&hid.getConnectionState(target)==BluetoothProfile.STATE_CONNECTED){
            stopScan();connectAt=0;
            if(hasDpad()){if(!ready)publish("遥控器已连接",true);ready=true;}
            else {ready=false;publish("HID 已连接，正在等待方向键设备…",false);}
            return;
        }
        ready=false;
        if(now()<cooldownUntil)return;
        if(target!=null){
            int bond=target.getBondState();
            if(bond==BluetoothDevice.BOND_BONDING){
                stopScan();publish("正在自动配对，请稍候…",false);
                if(bondAt==0)bondAt=now();
                if(now()-bondAt>40000){target.cancelBondProcess();cooldownUntil=now()+3000;Log.w(TAG,"Cancelling timed out bond");}
                return;
            }
            if(bond==BluetoothDevice.BOND_BONDED){
                publish("遥控器正在重新连接…",false);
                if(connectAt!=0&&now()-connectAt>20000){
                    // A sleeping/out-of-range remote is not evidence of a bad key.
                    if(aclAt<connectAt&&advertisedAt<connectAt){connectAt=0;startScan();cooldownUntil=now()+10000;return;}
                    // A timeout alone is not proof of a stale key. Native SMP
                    // repairs a rejected key; preserve the bond during radio/HID delays.
                    stopScan();
                    if(hid!=null)hidOperation("disconnect",target);
                    connectAt=0;cooldownUntil=now()+3000;
                    publish("连接暂未完成，正在重试…",true);
                    Log.w(TAG,"Remote HID timed out; retry without deleting bond");return;
                }
                if(connectAt==0&&hid!=null){
                    stopScan();connectAt=now();
                    // ALLOWED already initiates connect in HidHostService; never enqueue a second one.
                    if(hid.getConnectionPolicy(target)!=BluetoothProfile.CONNECTION_POLICY_ALLOWED){boolean ok=hid.setConnectionPolicy(target,BluetoothProfile.CONNECTION_POLICY_ALLOWED);Log.i(TAG,"Enabled remote HID policy accepted="+ok);}
                    else if(hid.getConnectionState(target)==BluetoothProfile.STATE_DISCONNECTED){boolean ok=hidOperation("connect",target);Log.i(TAG,"Connecting remote HID accepted="+ok);}
                }
                return;
            }
            // Pair only after a fresh advertisement, with no ACL/SMP attempt still active.
            if(aclUp)return;
            if(advertisedAddress!=null&&advertisedAddress.equals(target.getAddress())&&now()-advertisedAt<5000&&now()-bondAt>2000){
                stopScan();adapter.cancelDiscovery();bondAt=now();
                boolean ok=target.createBond(BluetoothDevice.TRANSPORT_LE);
                Log.i(TAG,"Advertisement -> createBond accepted="+ok);
                publish("发现遥控器，正在自动配对…",true);cooldownUntil=now()+2000;return;
            }
        }
        publish("正在寻找 Bluetooth remote…",false);startScan();
    }
    boolean hidOperation(String operation,BluetoothDevice device){
        try{return (Boolean)hid.getClass().getMethod(operation,BluetoothDevice.class).invoke(hid,device);}
        catch(ReflectiveOperationException e){Log.e(TAG,"HID "+operation+" failed",e);return false;}
    }
    void startScan(){
        if(scanning&&now()-scanAt<10000)return;
        if(scanning){stopScan();cooldownUntil=now()+1000;return;}
        scanner=adapter.getBluetoothLeScanner();if(scanner==null)return;
        try{
            scanning=true;scanAt=now();
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan);
            Log.i(TAG,"Remote BLE scan started");
        }catch(IllegalStateException e){scanning=false;cooldownUntil=now()+2000;Log.w(TAG,"Bluetooth changed during scan",e);}
    }
    void stopScan(){
        boolean wasScanning=scanning;scanning=false;
        if(wasScanning&&scanner!=null)try{scanner.stopScan(scan);}
        catch(IllegalStateException e){Log.w(TAG,"Bluetooth changed while stopping scan",e);}
        scanner=null;
    }
    static boolean hasDpad(){for(int id:InputDevice.getDeviceIds()){
        InputDevice device=InputDevice.getDevice(id);
        if(device!=null&&!device.isVirtual()&&device.isEnabled()&&device.getName()!=null&&device.getName().toLowerCase(Locale.ROOT).startsWith("bluetooth remote")&&device.supportsSource(InputDevice.SOURCE_DPAD))return true;
    }return false;}
    void publish(String text,boolean toast){
        if(toast&&!uiVisible&&now()-toastAt>3000){toastAt=now();Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
        if(text.equals(status))return;status=text;Log.i(TAG,text);
        sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName()));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&intent.hasExtra("ui_visible"))uiVisible=intent.getBooleanExtra("ui_visible",false);
        check();return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);stopScan();if(registered)unregisterReceiver(events);closeHid();resetConnectionState();super.onDestroy();}
}
