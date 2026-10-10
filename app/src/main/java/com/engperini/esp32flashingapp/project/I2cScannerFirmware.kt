package com.engperini.esp32flashingapp.project

/** Standalone I2C bus scanner for XIAO ESP32-S3 Sense, ESP-IDF 5.5. */
object I2cScannerFirmware {
    val mainC = """
#include <stdio.h>
#include <stdint.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/i2c.h"
#include "esp_log.h"
#include "esp_err.h"

#define I2C_PORT I2C_NUM_0
#define SDA_PIN GPIO_NUM_5
#define SCL_PIN GPIO_NUM_6
#define I2C_FREQ_HZ 100000

static const char *TAG = "I2C_SCAN";

static esp_err_t init_i2c(void)
{
    i2c_config_t cfg = {
        .mode = I2C_MODE_MASTER,
        .sda_io_num = SDA_PIN,
        .scl_io_num = SCL_PIN,
        .sda_pullup_en = GPIO_PULLUP_ENABLE,
        .scl_pullup_en = GPIO_PULLUP_ENABLE,
        .master.clk_speed = I2C_FREQ_HZ,
    };
    esp_err_t err = i2c_param_config(I2C_PORT, &cfg);
    if (err != ESP_OK) return err;
    return i2c_driver_install(I2C_PORT, cfg.mode, 0, 0, 0);
}

static esp_err_t probe_address(uint8_t address)
{
    i2c_cmd_handle_t cmd = i2c_cmd_link_create();
    if (cmd == NULL) return ESP_ERR_NO_MEM;
    i2c_master_start(cmd);
    i2c_master_write_byte(cmd, (address << 1) | I2C_MASTER_WRITE, true);
    i2c_master_stop(cmd);
    esp_err_t err = i2c_master_cmd_begin(I2C_PORT, cmd, pdMS_TO_TICKS(40));
    i2c_cmd_link_delete(cmd);
    return err;
}

void app_main(void)
{
    ESP_LOGI(TAG, "XIAO ESP32-S3 I2C scanner | ESP-IDF 5.5");
    ESP_LOGI(TAG, "SDA=GPIO5 (D4), SCL=GPIO6 (D5), 100 kHz");
    ESP_LOGI(TAG, "PCA9685 default address: 0x40");
    ESP_LOGI(TAG, "BME280 typical addresses: 0x76 / 0x77");
    ESP_LOGI(TAG, "Connect VCC to 3V3 and share GND");
    ESP_LOGW(TAG, "External pull-ups to 3V3 (e.g. 4.7k) may be needed");

    esp_err_t err = init_i2c();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "I2C initialization failed: %s", esp_err_to_name(err));
        return;
    }

    unsigned cycle = 0;
    while (true) {
        unsigned found = 0;
        unsigned timeouts = 0;
        ESP_LOGI(TAG, "----- Scan %u: 0x08 through 0x77 -----", ++cycle);
        for (uint8_t address = 0x08; address <= 0x77; address++) {
            err = probe_address(address);
            if (err == ESP_OK) {
                ESP_LOGI(TAG, "FOUND I2C device at 0x%02X%s",
                         address, address == 0x40 ? " (PCA9685 default)" : "");
                found++;
            } else if (err == ESP_ERR_TIMEOUT) {
                timeouts++;
            }
        }
        if (found == 0) {
            ESP_LOGW(TAG, "No I2C addresses responded. Check SDA/SCL, 3V3, GND, pull-ups.");
        }
        ESP_LOGI(TAG, "Scan finished: %u device(s), %u timeout(s)", found, timeouts);
        vTaskDelay(pdMS_TO_TICKS(5000));
    }
}
""".trimIndent()
}
