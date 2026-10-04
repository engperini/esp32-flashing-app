package com.engperini.esp32flashingapp.flash
data class FlashImage(val address:Long,val path:String)
data class FlashPlan(val chip:String="esp32s3",val baudRate:Int=460800,val images:List<FlashImage>)
object FlashArgsParser { fun parse(tokens:List<String>):List<FlashImage>{val out=mutableListOf<FlashImage>();var i=0;while(i+1<tokens.size){val a=tokens[i].removePrefix("0x").toLongOrNull(16);if(a!=null&&!tokens[i+1].startsWith("-")){out+=FlashImage(a,tokens[i+1]);i+=2}else i++};return out} }
