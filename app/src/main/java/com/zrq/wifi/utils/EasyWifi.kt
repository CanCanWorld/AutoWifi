package com.zrq.wifi.com.zrq.wifi.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat

/**
 * @Description:连接wifi的工具类,请在onCreate及之后创建
 * @author zhangruiqian
 * @date 2023/4/25 10:26
 */
@Suppress("DEPRECATION")
class EasyWifi(
    private val context: Context
) {

    //连接管理者
    private var connectivityManager: ConnectivityManager =
        context.getSystemService(ComponentActivity.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var wifiManager: WifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /*public*/

    /**
     * 自动连接wifi
     */
    fun connectWifi(scanResult: ScanResult, password: String, callback: (Boolean)->Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectByNew(scanResult.SSID, password,callback)
        } else {
            connectByOld(scanResult, password,callback)
        }
    }


    /**
     * 根据最大的level值筛选出wifi信号返回ScanResult集合
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getWifiListByLevel(): MutableList<ScanResult> {
        val list = mutableListOf<ScanResult>()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "没有wifi权限")
            return list
        }
        for (scanResult in wifiManager.scanResults) {
            val indexOfElement = list.indexOfElement(scanResult)
            if (indexOfElement != -1) {
                val old = list[indexOfElement]
                val new = maxOf(old, scanResult) { o1, o2 -> o1.level - o2.level }
                list.removeAt(indexOfElement)
                list.add(indexOfElement, new)
            } else {
                list.add(scanResult)
            }
        }
        list.sortWith { o1, o2 -> o1.level - o2.level }
        return list
    }


    /*private*/

    private fun MutableList<ScanResult>.indexOfElement(scanResult: ScanResult): Int {
        this.forEachIndexed { index, element ->
            if (scanResult.SSID == element.SSID) {
                return index
            }
        }
        return -1
    }

    /**
     * 安卓10及以上连接方式
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectByNew(ssid: String, password: String, callback: (Boolean)->Unit) {

        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()
        //网络请求
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .setNetworkSpecifier(wifiNetworkSpecifier)
            .build()
        //网络回调处理
        val networkCallback: ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                callback(true)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                callback(false)
            }
        }
        //请求连接网络
        connectivityManager.requestNetwork(request, networkCallback)
    }


    /**
     * 安卓10以下连接方式
     */
    private fun connectByOld(scanResult: ScanResult, password: String, callback: (Boolean)->Unit) {
        var isSuccess = false
        val ssid = scanResult.SSID
        isExist(ssid).elif({
            isSuccess = wifiManager.enableNetwork(it.networkId, true)
        }, {
            val wifiConfiguration = createWifiConfig(ssid, password, getCipherType(scanResult.capabilities))
            val netId = wifiManager.addNetwork(wifiConfiguration)
            isSuccess = wifiManager.enableNetwork(netId, true)
        })
        callback(isSuccess)
    }

    private fun createWifiConfig(ssid: String, password: String, type: WifiCapability): WifiConfiguration {
        val config = WifiConfiguration()
        config.apply {
            allowedAuthAlgorithms.clear()
            allowedGroupCiphers.clear()
            allowedKeyManagement.clear()
            allowedPairwiseCiphers.clear()
            allowedProtocols.clear()
            SSID = "\"$ssid\""
        }
        isExist(ssid).elif({
            wifiManager.removeNetwork(it.networkId)
            wifiManager.saveConfiguration()
        }, {})


        config.apply {
            //不需要密码的场景
            when (type) {
                WifiCapability.WIFI_CIPHER_NO_PASS -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    //以WEP加密的场景
                }
                WifiCapability.WIFI_CIPHER_WEP -> {
                    hiddenSSID = true
                    wepKeys[0] = "\"" + password + "\""
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    wepTxKeyIndex = 0
                    //以WPA加密的场景，自己测试时，发现热点以WPA2建立时，同样可以用这种配置连接
                }
                WifiCapability.WIFI_CIPHER_WPA -> {
                    preSharedKey = "\"" + password + "\""
                    hiddenSSID = true
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                    allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                    status = WifiConfiguration.Status.ENABLED
                }
            }
        }
        return config
    }

    private fun isExist(ssid: String): WifiConfiguration? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val existingConfigs = wifiManager.configuredNetworks
        existingConfigs.forEach {
            if (it.SSID == "\"$ssid\"") {
                return it
            }
        }
        return null
    }

    private fun getCipherType(capabilities: String): WifiCapability {
        return if (capabilities.contains("WEB")) {
            WifiCapability.WIFI_CIPHER_WEP
        } else if (capabilities.contains("PSK")) {
            WifiCapability.WIFI_CIPHER_WPA
        } else if (capabilities.contains("WPS")) {
            WifiCapability.WIFI_CIPHER_NO_PASS
        } else {
            WifiCapability.WIFI_CIPHER_NO_PASS
        }
    }

    private companion object {
        const val TAG = "EasyWifi"
    }
}