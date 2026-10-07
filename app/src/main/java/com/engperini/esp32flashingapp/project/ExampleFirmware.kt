package com.engperini.esp32flashingapp.project

object ExampleFirmware {
 val componentManifest = """
dependencies:
  espressif/esp-lib-utils: "^0.3.0"
""".trimIndent() + "\n"
 val mainC = """
#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/i2c.h"
#include "esp_err.h"
#include "esp_log.h"

#define I2C_PORT            I2C_NUM_0
#define I2C_SDA             GPIO_NUM_14
#define I2C_SCL             GPIO_NUM_15
#define I2C_FREQ_HZ         100000
#define PCA9685_ADDR        0x40
#define PCA9685_MODE1       0x00
#define PCA9685_PRESCALE    0xFE
#define PCA9685_LED0_ON_L   0x06
#define SERVO_FREQ_HZ       50
#define SERVO_MIN_US        500
#define SERVO_MAX_US        2500

static const char *TAG = "SERVO_TEST";

static esp_err_t pca9685_write(uint8_t reg, uint8_t value) {
    uint8_t data[2] = {reg, value};
    return i2c_master_write_to_device(I2C_PORT, PCA9685_ADDR, data, sizeof(data), pdMS_TO_TICKS(100));
}

static esp_err_t pca9685_init(void) {
    const uint8_t prescale = 121;
    esp_err_t err;
    if ((err = pca9685_write(PCA9685_MODE1, 0x10)) != ESP_OK) return err;
    if ((err = pca9685_write(PCA9685_PRESCALE, prescale)) != ESP_OK) return err;
    if ((err = pca9685_write(PCA9685_MODE1, 0x00)) != ESP_OK) return err;
    vTaskDelay(pdMS_TO_TICKS(10));
    if ((err = pca9685_write(PCA9685_MODE1, 0x20)) != ESP_OK) return err;
    ESP_LOGI(TAG, "PCA9685 initialized at %d Hz", SERVO_FREQ_HZ);
    return ESP_OK;
}

static esp_err_t pca9685_set_pwm(uint8_t channel, uint16_t on, uint16_t off) {
    if (channel > 15) return ESP_ERR_INVALID_ARG;
    uint8_t reg = PCA9685_LED0_ON_L + (4 * channel);
    uint8_t data[5] = {reg, on & 0xFF, (on >> 8) & 0x0F, off & 0xFF, (off >> 8) & 0x0F};
    return i2c_master_write_to_device(I2C_PORT, PCA9685_ADDR, data, sizeof(data), pdMS_TO_TICKS(100));
}

static esp_err_t servo_set_angle(uint8_t channel, float angle) {
    if (angle < 0) angle = 0;
    if (angle > 180) angle = 180;
    float pulse_us = SERVO_MIN_US + (angle / 180.0f) * (SERVO_MAX_US - SERVO_MIN_US);
    uint16_t ticks = (uint16_t)((pulse_us * 4096.0f) / 20000.0f);
    esp_err_t err = pca9685_set_pwm(channel, 0, ticks);
    if (err == ESP_OK) ESP_LOGI(TAG, "Servo %d -> %.0f deg (%d ticks)", channel, angle, ticks);
    return err;
}

static esp_err_t move_all(float angle) {
    for (int servo = 0; servo < 4; servo++) {
        esp_err_t err = servo_set_angle(servo, angle);
        if (err != ESP_OK) return err;
    }
    return ESP_OK;
}

void app_main(void) {
    ESP_LOGI(TAG, "ESP32 + PCA9685 + 4x SG90 test");
    i2c_config_t config = {
        .mode = I2C_MODE_MASTER,
        .sda_io_num = I2C_SDA,
        .scl_io_num = I2C_SCL,
        .sda_pullup_en = GPIO_PULLUP_ENABLE,
        .scl_pullup_en = GPIO_PULLUP_ENABLE,
        .master.clk_speed = I2C_FREQ_HZ,
        .clk_flags = 0
    };

    esp_err_t err = i2c_param_config(I2C_PORT, &config);
    if (err == ESP_OK) err = i2c_driver_install(I2C_PORT, I2C_MODE_MASTER, 0, 0, 0);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "I2C initialization failed: %s", esp_err_to_name(err));
        while (1) vTaskDelay(pdMS_TO_TICKS(1000));
    }

    err = pca9685_init();
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "PCA9685 not detected at 0x%02X: %s", PCA9685_ADDR, esp_err_to_name(err));
        ESP_LOGW(TAG, "Servo test disabled; firmware remains running.");
        while (1) vTaskDelay(pdMS_TO_TICKS(1000));
    }

    const float angles[] = {30, 90, 150, 90};
    while (1) {
        for (unsigned i = 0; i < sizeof(angles) / sizeof(angles[0]); i++) {
            ESP_LOGI(TAG, "%.0f degrees", angles[i]);
            err = move_all(angles[i]);
            if (err != ESP_OK) {
                ESP_LOGW(TAG, "PCA9685 communication lost: %s; servo test paused.", esp_err_to_name(err));
                while (pca9685_init() != ESP_OK) {
                    vTaskDelay(pdMS_TO_TICKS(1000));
                }
                ESP_LOGI(TAG, "PCA9685 communication restored.");
                break;
            }
            vTaskDelay(pdMS_TO_TICKS(1500));
        }
    }
}
""".trimIndent()
}
