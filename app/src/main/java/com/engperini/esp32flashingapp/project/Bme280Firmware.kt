package com.engperini.esp32flashingapp.project

/** Standalone BME280 I2C sensor example: temperature, pressure and humidity. */
object Bme280Firmware {
    val mainC = """
#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/i2c.h"
#include "esp_log.h"
#include "esp_err.h"
#include "esp_check.h"

#define SDA_PIN GPIO_NUM_5
#define SCL_PIN GPIO_NUM_6
#define I2C_PORT I2C_NUM_0
static const char *TAG = "BME280";
static uint8_t addr = 0x76;
static uint16_t t1, p1;
static int16_t t2,t3,p2,p3,p4,p5,p6,p7,p8,p9,h2,h4,h5;
static uint8_t h1,h3;
static int8_t h6;
static int32_t fine;

static esp_err_t read_regs(uint8_t reg, uint8_t *data, size_t n) {
    return i2c_master_write_read_device(I2C_PORT, addr, &reg, 1, data, n, pdMS_TO_TICKS(250));
}
static esp_err_t write_reg(uint8_t reg, uint8_t value) {
    uint8_t data[2] = {reg, value};
    return i2c_master_write_to_device(I2C_PORT, addr, data, 2, pdMS_TO_TICKS(250));
}
static uint16_t u16(const uint8_t *p) { return (uint16_t)p[0] | ((uint16_t)p[1]<<8); }
static int16_t s16(const uint8_t *p) { return (int16_t)u16(p); }
static esp_err_t sensor_init(void) {
    uint8_t id=0;
    addr=0x76;
    if (read_regs(0xD0,&id,1)!=ESP_OK || id!=0x60) {
        addr=0x77;
        if (read_regs(0xD0,&id,1)!=ESP_OK || id!=0x60) return ESP_ERR_NOT_FOUND;
    }
    uint8_t a[26], b[7];
    ESP_RETURN_ON_ERROR(read_regs(0x88,a,sizeof(a)),TAG,"calibration 1");
    ESP_RETURN_ON_ERROR(read_regs(0xE1,b,sizeof(b)),TAG,"calibration 2");
    t1=u16(a); t2=s16(a+2); t3=s16(a+4);
    p1=u16(a+6); p2=s16(a+8); p3=s16(a+10);
    p4=s16(a+12); p5=s16(a+14); p6=s16(a+16);
    p7=s16(a+18); p8=s16(a+20); p9=s16(a+22);
    h1=a[25]; h2=s16(b); h3=b[2];
    h4=(int16_t)((((int16_t)(int8_t)b[3])<<4) | (b[4]&0x0F));
    h5=(int16_t)((((int16_t)(int8_t)b[5])<<4) | (b[4]>>4));
    h6=(int8_t)b[6];
    ESP_RETURN_ON_ERROR(write_reg(0xF2,0x01),TAG,"humidity oversampling");
    ESP_RETURN_ON_ERROR(write_reg(0xF4,0x27),TAG,"temperature/pressure oversampling");
    ESP_RETURN_ON_ERROR(write_reg(0xF5,0xA0),TAG,"standby");
    ESP_LOGI(TAG,"BME280 found at 0x%02X",addr);
    return ESP_OK;
}
static void sample(void) {
    uint8_t a[8];
    if (read_regs(0xF7,a,8)!=ESP_OK) { ESP_LOGW(TAG,"Read failed"); return; }
    int32_t rp=((int32_t)a[0]<<12)|((int32_t)a[1]<<4)|(a[2]>>4);
    int32_t rt=((int32_t)a[3]<<12)|((int32_t)a[4]<<4)|(a[5]>>4);
    int32_t rh=((int32_t)a[6]<<8)|a[7];
    double v1=((double)rt/16384.0-(double)t1/1024.0)*(double)t2;
    double v2=(((double)rt/131072.0-(double)t1/8192.0)*((double)rt/131072.0-(double)t1/8192.0))*(double)t3;
    fine=(int32_t)(v1+v2);
    double temp=(v1+v2)/5120.0;
    v1=(double)fine/2.0-64000.0;
    v2=v1*v1*(double)p6/32768.0;
    v2=v2+v1*(double)p5*2.0;
    v2=v2/4.0+(double)p4*65536.0;
    v1=((double)p3*v1*v1/524288.0+(double)p2*v1)/524288.0;
    v1=(1.0+v1/32768.0)*(double)p1;
    double pressure=0.0;
    if (v1!=0.0) {
        pressure=1048576.0-(double)rp;
        pressure=(pressure-v2/4096.0)*6250.0/v1;
        v1=(double)p9*pressure*pressure/2147483648.0;
        v2=pressure*(double)p8/32768.0;
        pressure=pressure+(v1+v2+(double)p7)/16.0;
    }
    double hum=(double)fine-76800.0;
    hum=(rh-((double)h4*64.0+(double)h5/16384.0*hum))*
        ((double)h2/65536.0*(1.0+(double)h6/67108864.0*hum*
        (1.0+(double)h3/67108864.0*hum)));
    hum=hum*(1.0-(double)h1*hum/524288.0);
    if(hum<0.0)hum=0.0; if(hum>100.0)hum=100.0;
    ESP_LOGI(TAG,"Temperature %.2f C | Pressure %.2f hPa | Humidity %.1f %%",
             temp,pressure/100.0,hum);
}
void app_main(void) {
    ESP_LOGI(TAG,"BME280 example starting; SDA=GPIO5 SCL=GPIO6");
    i2c_config_t cfg = {.mode=I2C_MODE_MASTER,.sda_io_num=SDA_PIN,.scl_io_num=SCL_PIN,
        .sda_pullup_en=GPIO_PULLUP_ENABLE,.scl_pullup_en=GPIO_PULLUP_ENABLE,
        .master.clk_speed=100000};
    ESP_ERROR_CHECK(i2c_param_config(I2C_PORT,&cfg));
    ESP_ERROR_CHECK(i2c_driver_install(I2C_PORT,I2C_MODE_MASTER,0,0,0));
    while (1) {
        if(sensor_init()==ESP_OK) {
            for(int i=0;i<10;i++){sample();vTaskDelay(pdMS_TO_TICKS(2000));}
        } else {
            ESP_LOGW(TAG,"BME280 not found at 0x76 or 0x77; check wiring and 3.3V");
            vTaskDelay(pdMS_TO_TICKS(3000));
        }
    }
}
""".trimIndent()
}
