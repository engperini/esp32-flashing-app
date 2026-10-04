package com.engperini.esp32flashingapp.device
import org.junit.Assert.assertEquals
import org.junit.Test
class BootResetLineMapperTest {
 @Test fun defaultsMapResetToDtrAndBootToRts(){val c=DeviceConfig();assertEquals(ControlLines(false,false),BootResetLineMapper.map(false,false,c));assertEquals(ControlLines(true,false),BootResetLineMapper.map(false,true,c));assertEquals(ControlLines(false,true),BootResetLineMapper.map(true,false,c));assertEquals(ControlLines(true,true),BootResetLineMapper.map(true,true,c))}
 @Test fun inversionAppliedAfterMapping(){val c=DeviceConfig(invertDtr=true,invertRts=true);assertEquals(ControlLines(true,true),BootResetLineMapper.map(false,false,c));assertEquals(ControlLines(false,false),BootResetLineMapper.map(true,true,c))}
 @Test fun noSwapMapsBootToDtr(){val c=DeviceConfig(swapDtrRts=false);assertEquals(ControlLines(true,false),BootResetLineMapper.map(true,false,c));assertEquals(ControlLines(false,true),BootResetLineMapper.map(false,true,c))}
}