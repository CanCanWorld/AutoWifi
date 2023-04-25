package com.zrq.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.zrq.wifi.com.zrq.wifi.utils.EasyWifi
import com.zrq.wifi.ui.theme.WifiTheme
import dagger.hilt.android.AndroidEntryPoint

@SuppressLint("MutableCollectionMutableState")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var openWifi: ActivityResultLauncher<Intent>
    private lateinit var wifiManager: WifiManager
    private var list by mutableStateOf(mutableStateListOf<ScanResult>())
    private lateinit var easyWifi: EasyWifi
    private var isAutoing by mutableStateOf(false)
    private var isMoreThenOne by mutableStateOf(false)
    private var msg by mutableStateOf("")


    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        easyWifi = EasyWifi(this)
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        registerIntent()

        val filter = IntentFilter()
        filter.apply {
            addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        val wifiStateReceiver = WifiStateReceiver()
        registerReceiver(wifiStateReceiver, filter)

        setContent {
            WifiTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Button(onClick = {
                        autoConnect()
                    }) {
                        Text(text = "自动连接")
                    }
                    Row {
                        Button(onClick = { manualConnectHigh() }) {
                            Text(text = "手动连接")
                        }
                    }
                    if (isAutoing) {
                        CircularProgressIndicator()
                    }
                    Text(text = msg)

                    if (isMoreThenOne && isAutoing) {
                        LazyColumn {
                            items(list.size) {
                                Text(
                                    text = list[it].SSID,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .clickable {
                                            connectClick(list[it])
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun connectClick(scanResult: ScanResult) {
        Log.d(TAG, "选中${scanResult.SSID}，直接连接")
        easyWifi.connectWifi(scanResult, "12345678") {
            Log.d(TAG, "自动连接结果: $it")
            msg = if (it) {
                "自动连接成功"
            } else {
                "自动连接失败，请再次尝试自动连接或手动连接"
            }
            isAutoing = false
        }
    }


    private fun registerIntent() {
        openWifi = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        }
    }

    private fun requestWifiPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ACCESS_FINE_LOCATION_CODE) {
            if (isAutoing) {
                autoConnect()
            }
        }
    }

    private fun openWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openWifi.launch(Intent(Settings.Panel.ACTION_WIFI))
        } else {
            wifiManager.isWifiEnabled = true
        }
    }

    private fun autoConnect() {
        isAutoing = true
        msg = "正在自动连接"
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "没有wifi权限")
            msg = "没有wifi权限，请同意权限"
            requestWifiPermission()
            return
        }
        val isWifiEnable = wifiManager.isWifiEnabled
        Log.d(TAG, "wifi是否开启: $isWifiEnable")
        if (!isWifiEnable) {
            Toast.makeText(this, "请打开wifi开关", Toast.LENGTH_SHORT).show()
            msg = "请打开wifi开关"
            openWifi()
            return
        }
        val topsList = mutableListOf<ScanResult>()
        val scanResults = easyWifi.getWifiListByLevel()
        scanResults.forEach {
            Log.d(TAG, "new: ${it.SSID}==>${it.level}")
            if (it.SSID.startsWith("Tops_")) {
                topsList.add(it)
            }
        }
        if (topsList.size == 1) {
            isMoreThenOne = false
            Log.d(TAG, "只有一个Tops_开头的wifi，直接连接")
            easyWifi.connectWifi(topsList[0], "12345678") {
                Log.d(TAG, "自动连接结果: $it")
                msg = if (it) {
                    "自动连接成功"
                } else {
                    "自动连接失败，请再次尝试自动连接或手动连接"
                }
                isAutoing = false
            }
        } else {
            isMoreThenOne = true
            Log.d(TAG, "有多个Tops_开头的wifi信号")
            //有多个"Tops_"的wifi信号
            msg = "有多个Tops_开头的wifi信号，请手动选择"
            list.clear()
            list.addAll(topsList)
        }
    }

    private fun manualConnectHigh() {
        //Android10及以上版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openWifi.launch(Intent(Settings.Panel.ACTION_WIFI))
        } else {
            val intent = Intent()
            intent.action = "android.net.wifi.PICK_WIFI_NETWORK"
            startActivityForResult(intent, 100)
        }
    }

    /**
     * @Description: 监听wifi状态改变的广播接收者
     * @author zhangruiqian
     * @date 2023/4/25 14:35
     */
    inner class WifiStateReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val msg = when (wifiManager.wifiState) {
                WifiManager.WIFI_STATE_DISABLING -> {
                    "WIFI正在关闭"
                }
                WifiManager.WIFI_STATE_DISABLED -> {
                    "WIFI已经关闭"
                }
                WifiManager.WIFI_STATE_ENABLING -> {
                    "WIFI正在开启"
                }
                WifiManager.WIFI_STATE_ENABLED -> {
                    if (isAutoing) {
                        autoConnect()
                    }
                    "WIFI已经开启"
                }
                else -> "未知"
            }
            Log.d(TAG, "checkWifiState: $msg")
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val ACCESS_FINE_LOCATION_CODE = 1
    }
}
