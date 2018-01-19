package com.fsl.ethernet;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.content.Context;
import android.net.EthernetDataTracker;
import android.provider.Settings;
import android.os.ServiceManager;
import android.os.IBinder;
import android.content.ContentResolver;
import android.os.INetworkManagementService;
import android.net.DhcpInfo;
import android.net.NetworkUtils;
import android.net.LinkAddress;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.LinkProperties;
import android.net.InterfaceConfiguration;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.content.SharedPreferences;
/**
 * Created by B38613 on 9/27/13.
 */
public class EthernetManager {
    public static final String TAG = "EthernetManager";

    public static final int ETHERNET_DEVICE_SCAN_RESULT_READY = 0;
    public static final String ETHERNET_STATE_CHANGED_ACTION =
            "android.net.ethernet.ETHERNET_STATE_CHANGED";
    public static final String NETWORK_STATE_CHANGED_ACTION =
            "android.net.ethernet.STATE_CHANGE";

    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    public static final String EXTRA_ETHERNET_STATE = "ETHERNET_state";
    public static final String EXTRA_PREVIOUS_ETHERNET_STATE = "previous_ETHERNET_state";
    /**
     * The lookup key for a {@link android.net.LinkProperties} object associated with the
     * Ethernet network. Retrieve with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     * @hide
     */
    public static final String EXTRA_LINK_PROPERTIES = "linkProperties";

    /**
     * The lookup key for a {@link android.net.LinkCapabilities} object associated with the
     * Ethernet network. Retrieve with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     * @hide
     */
    public static final String EXTRA_LINK_CAPABILITIES = "linkCapabilities";

    public static final int ETHERNET_STATE_UNKNOWN = 0;
    public static final int ETHERNET_STATE_DISABLED = 1;
    public static final int ETHERNET_STATE_ENABLED = 2;
    private static final int ETHERNET_HAS_CONFIG = 1;


    /** @hide */
    public static final int DATA_ACTIVITY_NONE         = 0x00;
    /** @hide */
    public static final int DATA_ACTIVITY_IN           = 0x01;
    /** @hide */
    public static final int DATA_ACTIVITY_OUT          = 0x02;
    /** @hide */
    public static final int DATA_ACTIVITY_INOUT        = 0x03;

    private Context mContext;
    private String[] DevName;
    private int mEthState= ETHERNET_STATE_UNKNOWN;
    private EthernetDataTracker mTracker;
    private INetworkManagementService mNMService;
    private DhcpInfo mDhcpInfo;
    private Handler mTrackerTarget;

    public EthernetManager(Context context) {
        mContext = context;
        mTracker = EthernetDataTracker.getInstance();

        DevName = new String[1];

        DevName[0] = "eth0";//mTracker.getLinkProperties().getInterfaceName();


        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNMService = INetworkManagementService.Stub.asInterface(b);
        HandlerThread dhcpThread = new HandlerThread("DHCP Handler Thread");
        dhcpThread.start();
        mDhcpInfo = new DhcpInfo();
    }

    /**
     * check if the ethernet service has been configured.
     * @return {@code true} if configured {@code false} otherwise
     */
    public boolean isConfigured() {
        return "1".equals(SystemProperties.get("net."+ DevName[0] + ".config", "0"));
    }

    /**
     * Return the saved ethernet configuration
     * @return ethernet interface configuration on success, {@code null} on failure
     */
    public synchronized EthernetDevInfo getSavedConfig() {
        if (!isConfigured())
            return null;

        EthernetDevInfo info = new EthernetDevInfo();
        info.setConnectMode(SystemProperties.get("net."+ DevName[0] + ".mode", ""));
        info.setIpAddress(SystemProperties.get("net."+ DevName[0] + ".ip", ""));
        info.setDnsAddr(SystemProperties.get("net."+ DevName[0] + ".dns1", ""));
        //info.setNetMask(Settings.Secure.getString(cr, Settings.Secure.ETHERNET_MASK));
        //info.setRouteAddr(Settings.Secure.getString(cr, Settings.Secure.ETHERNET_ROUTE));
        info.setIfName(DevName[0]);
        return info;
    }

    /**
     * update a ethernet interface information
     * @param info  the interface infomation
     */
    private int scanDevice() {
        return 1;
    }

    /**
     * get all the ethernet device names
     * @return interface name list on success, {@code null} on failure
     */
    public String[] getDeviceNameList() {
        return (scanDevice() > 0) ? DevName : null;
    }

    /**
     * Set the ethernet interface configuration mode
     * @param mode {@code ETHERNET_CONN_MODE_DHCP} for dhcp {@code ETHERNET_CONN_MODE_MANUAL} for manual configure
     */
    public synchronized void setMode(String mode) {
        final ContentResolver cr = mContext.getContentResolver();
        if (DevName != null) {
            SystemProperties.set("net." + DevName[0] + ".mode", mode);
        }
    }

    private void setInterfaceUp(String InterfaceName)
    {
        try {
            mNMService.setInterfaceUp(InterfaceName);
        } catch (RemoteException re){
            Log.e(TAG,"Set interface up failed: " + re);
        } catch (IllegalStateException e) {
            Log.e(TAG,"Set interface up fialed: " + e);
        }

    }

    void configureInterface(EthernetDevInfo info) {
        if (info.getConnectMode().equals(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP)) {

            try {
                mNMService.setInterfaceDown(info.getIfName());
                mNMService.setInterfaceUp(info.getIfName());
            } catch (RemoteException re){
                Log.e(TAG,"DHCP configuration failed: " + re);
            } catch (IllegalStateException e) {
                Log.e(TAG,"DHCP configuration fialed: " + e);
            }
        } else {
            InterfaceConfiguration ifcg = null;
            Log.d(TAG, "Static IP =" + info.getIpAddress());
            try {
                ifcg = mNMService.getInterfaceConfig(info.getIfName());
                ifcg.setLinkAddress(new LinkAddress(NetworkUtils.numericToInetAddress(
                        info.getIpAddress()), 24));
                ifcg.setInterfaceUp();
                mNMService.setInterfaceConfig(info.getIfName(), ifcg);

                Log.d(TAG,"Static IP configuration succeeded");
            } catch (RemoteException re){
                Log.e(TAG,"Static IP configuration failed: " + re);
            } catch (IllegalStateException e) {
                Log.e(TAG,"Static IP configuration fialed: " + e);
            }
            Log.d(TAG, "set ip manually " + info.toString());
            SystemProperties.set("net.dns1", info.getDnsAddr());
            SystemProperties.set("net." + info.getIfName() + ".dns1",info.getDnsAddr());
            SystemProperties.set("net." + info.getIfName() + ".dns2", "0.0.0.0");
        }
    }
    /**
     * reset ethernet interface
     * @return true
     * @throws UnknownHostException
     */
    public void resetInterface() {
        /*
         * This will guide us to enabled the enabled device
         */
        String mInterfaceName ;
        EthernetDevInfo info = getSavedConfig();
        if (info != null && isConfigured()) {
            synchronized (this) {
                mInterfaceName = info.getIfName();
                Log.d(TAG, "reset device " + mInterfaceName);
                NetworkUtils.resetConnections(mInterfaceName, NetworkUtils.RESET_ALL_ADDRESSES);
            }
            if (!NetworkUtils.stopDhcp(mInterfaceName)) {
                Log.d(TAG, "Could not stop DHCP");
            }
            configureInterface(info);
        }
    }

    /**
     * update a ethernet interface information
     * @param info  the interface infomation
     */
    public synchronized void updateDevInfo(EthernetDevInfo info) {
        SystemProperties.set("net.dns1", info.getDnsAddr());
        SystemProperties.set("net." + info.getIfName() + ".dns1",info.getDnsAddr());
        SystemProperties.set("net." + info.getIfName() + ".dns2", "0.0.0.0");
        SystemProperties.set("net." + info.getIfName() + ".config", "1");
        SystemProperties.set("net." + info.getIfName() + ".mode", info.getConnectMode());
        SystemProperties.set("net." + info.getIfName() + ".ip", info.getIpAddress());
    }

}
