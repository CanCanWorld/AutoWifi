package com.zrq.wifi.com.zrq.wifi.utils

/**
 * @Description: 一些自定义标准函数
 * @author zhangruiqian
 * @date 2023/4/25 10:33
 */
fun <T, R> T?.elif(block: (T) -> R, block2: () -> R): R {
    return if (this != null) {
        block(this)
    } else {
        block2()
    }
}