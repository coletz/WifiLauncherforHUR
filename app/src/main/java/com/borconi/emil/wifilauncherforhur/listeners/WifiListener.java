package com.borconi.emil.wifilauncherforhur.listeners;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Build;

import android.os.SystemClock;
import android.util.Log;

import com.borconi.emil.wifilauncherforhur.R;
import com.borconi.emil.wifilauncherforhur.activities.MainActivity;
import com.borconi.emil.wifilauncherforhur.receivers.BTReceiver;
import com.borconi.emil.wifilauncherforhur.receivers.WifiReceiver;

import java.io.InputStream;
import java.net.Socket;
import java.util.List;

import uk.co.emil.borconi.wifip2pforhur.Wifip2pService;

import static android.app.Notification.EXTRA_NOTIFICATION_ID;

public class WifiListener extends Wifip2pService {


    private WifiReceiver mylistener = new WifiReceiver();

    private ConnectivityManager.NetworkCallback callback;
    private NetworkRequest networkRequest;
    static public boolean isConnected = false;
    static public boolean askingForWiFi = false;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        netId = -1;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.app.action.EXIT_CAR_MODE");
        registerReceiver(mylistener, intentFilter);

        Intent snoozeIntent = new Intent(this, WifiReceiver.class);
        snoozeIntent.setAction("com.borconi.emil.wifilauncherforhur.exit");
        snoozeIntent.putExtra(EXTRA_NOTIFICATION_ID, 0);
        PendingIntent snoozePendingIntent =
                PendingIntent.getBroadcast(this, 0, snoozeIntent, 0);

        mynotification.setContentIntent(snoozePendingIntent)
                .addAction(R.drawable.ic_exit_to_app_24px, "Exit", snoozePendingIntent);

        startForeground(1, mynotification.build());

        /* Only run this if we are on pie */
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        assert wifi != null;
        if (!wifi.isWifiEnabled()) {
            // Since Android 10 we can't turn on/off wifi programmatically
            // https://developer.android.com/reference/android/net/wifi/WifiManager#setWifiEnabled(boolean)
            // follow this post if Google enables it again: https://issuetracker.google.com/issues/128554616
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !askingForWiFi) {
                askingForWiFi = true;
                // Let's send a message to the user to turn it on.
                Intent mainActivityIntent = new Intent(getApplicationContext(), MainActivity.class);
                mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                mainActivityIntent.putExtra("showWifiAlertDialog", true);
                startActivity(mainActivityIntent);
            } else {
                wifi.setWifiEnabled(true);
            }

        }

        List<WifiConfiguration> x = wifi.getConfiguredNetworks();
        /*IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction("android.net.wifi.STATE_CHANGE");
        registerReceiver(mylistener, intentFilter);*/

        for (WifiConfiguration a : x) {
            if (a.SSID.startsWith("\"") && a.SSID.endsWith("\""))
                a.SSID = a.SSID.substring(1, a.SSID.length() - 1);
            if (a.SSID.equalsIgnoreCase("HUR")) {
                netId = a.networkId;
                break;
            }

        }
        if (netId <= 0) {
            Log.d("HU-Wifi", "HUR wifi not in the list, add it and try to connect");
            WifiConfiguration wifiConfig = new WifiConfiguration();
            wifiConfig.SSID = String.format("\"%s\"", "HUR");
            wifiConfig.preSharedKey = String.format("\"%s\"", "AndroidAutoConnect");
            netId = wifi.addNetwork(wifiConfig);
            //wifi.startScan();
        }
        BTReceiver.netid = netId;
        if (wifi.getConnectionInfo().getNetworkId() != netId) {
            Log.d("HU-Wifi", "Start up, not connected to HUR network, is it in range?");

            //Log.d("HU-Wifi","Start scan is: "+wifi.startScan());
            wifi.disconnect();
            wifi.enableNetwork(netId, true);
            wifi.reconnect();
        } else
            connectToHur(wifi.getDhcpInfo().gateway);
    }

    @Override
    public void connectToHur(final String address) {
        Log.d("WiFi Listenner", "Calling super connecto to HUR");
        super.connectToHur(address);

        isConnected = true;

        final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {


            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onLost(Network network) {
                    Log.d("Wifi Listener", "Lost connection to HUR wifi, exiting the app");
                    connectivityManager.unregisterNetworkCallback(this);
                    stopSelf();
                }
            };
            connectivityManager.registerNetworkCallback(
                    builder.build(), networkCallback
            );
        }

    }

    @Override
    public void startSlave(final WifiP2pInfo info, final WifiP2pGroup wifiP2pGroup) {
        if (hur_address != null) {
            return;
        }
        hur_address = info.groupOwnerAddress;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d("HU-Wifip2p", "Trying to connect to: " + info.groupOwnerAddress);
                    Socket sock = new Socket(info.groupOwnerAddress, 5299);
                    InputStream inpstr = sock.getInputStream();
                    while (inpstr.available() <= 0) {
                        Thread.sleep(10);
                    }
                    byte[] pass = new byte[inpstr.available()];
                    inpstr.read(pass);
                    sock.close();
                    String password = new String(pass);
                    Log.d("HU-Wifip2p", "Password from socket: " + password);
                    WifiConfiguration wifiConfig = new WifiConfiguration();
                    wifiConfig.SSID = String.format("\"%s\"", wifiP2pGroup.getNetworkName());
                    wifiConfig.preSharedKey = String.format("\"%s\"", password);
                    mManager.removeGroup(mChannel, null);
                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    assert wifiManager != null;
                    netId = wifiManager.addNetwork(wifiConfig);
                    mManager.removeGroup(mChannel, null);
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(netId, true);
                    wifiManager.reconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("WiFi-Launcher", "Start service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        isConnected = false;
        unregisterReceiver(mylistener);

        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }
}
