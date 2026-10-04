package com.engperini.esp32flashingapp.device
data class DeviceConfig(val baudRate:Int=115200,val invertDtr:Boolean=false,val invertRts:Boolean=false,val swapDtrRts:Boolean=true,val resetPulseMs:Long=120,val bootloaderHoldMs:Long=250)
