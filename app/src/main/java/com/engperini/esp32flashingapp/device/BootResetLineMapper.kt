package com.engperini.esp32flashingapp.device

data class ControlLines(val dtr: Boolean, val rts: Boolean)
object BootResetLineMapper {
 fun map(bootActive:Boolean, resetActive:Boolean, config:DeviceConfig):ControlLines {
  val dtr=if(config.swapDtrRts) resetActive else bootActive
  val rts=if(config.swapDtrRts) bootActive else resetActive
  return ControlLines(dtr xor config.invertDtr, rts xor config.invertRts)
 }
}