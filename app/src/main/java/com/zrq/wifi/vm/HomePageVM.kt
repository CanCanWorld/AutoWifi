package com.zrq.wifi.vm

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.*
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class HomePageVM @Inject constructor(
    private val application: Application,
) : ViewModel() {
    //data


    //methods
    init {
    }

    fun openWifi() {
    }

    fun scanWifi() {

    }

    private companion object {
        const val TAG = "HomePageVM"
    }
}