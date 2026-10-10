package com.engperini.esp32flashingapp.project

/**
 * Default project for new installations. Based on Espressif ESP-IDF v5.5:
 * examples/get-started/hello_world/main/hello_world_main.c (CC0-1.0).
 *
 * Existing projects are never overwritten by ProjectManager.ensureExampleProject().
 */
object ExampleFirmware {
 val componentManifest = """
dependencies:
  espressif/esp-lib-utils: "^0.3.0"
""".trimIndent() + "\n"

 val mainC = """
#include <stdio.h>
#include <inttypes.h>
#include "sdkconfig.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_chip_info.h"
#include "esp_flash.h"
#include "esp_system.h"

#define ESP_UTILS_LOG_TAG "IDF_EXAMPLE"
#include "esp_lib_utils.h"

void app_main(void)
{
    printf("Hello world from ESP32 Flashing App!\n");

    esp_chip_info_t chip_info;
    uint32_t flash_size = 0;
    esp_chip_info(&chip_info);

    printf("Target: %s | CPU cores: %d | Features: %s%s%s%s\n",
           CONFIG_IDF_TARGET,
           chip_info.cores,
           (chip_info.features & CHIP_FEATURE_WIFI_BGN) ? "WiFi/" : "",
           (chip_info.features & CHIP_FEATURE_BT) ? "BT/" : "",
           (chip_info.features & CHIP_FEATURE_BLE) ? "BLE/" : "",
           (chip_info.features & CHIP_FEATURE_IEEE802154) ? "802.15.4" : "");

    printf("Silicon revision: v%u.%u\n",
           (unsigned)(chip_info.revision / 100),
           (unsigned)(chip_info.revision % 100));

    if (esp_flash_get_size(NULL, &flash_size) == ESP_OK) {
        printf("Flash: %" PRIu32 " MB (%s)\n",
               flash_size / (uint32_t)(1024 * 1024),
               (chip_info.features & CHIP_FEATURE_EMB_FLASH) ? "embedded" : "external");
    } else {
        printf("Flash size unavailable\n");
    }

    printf("Minimum free heap: %" PRIu32 " bytes\n",
           esp_get_minimum_free_heap_size());

    /* Exercise an API from the official ESP-IDF Component Manager dependency. */
    ESP_UTILS_LOGI("External esp-lib-utils component is linked; target=%s", CONFIG_IDF_TARGET);

    while (1) {
        printf("Hello from %s - free heap: %" PRIu32 " bytes\n",
               CONFIG_IDF_TARGET, esp_get_free_heap_size());
        vTaskDelay(pdMS_TO_TICKS(5000));
    }
}
""".trimIndent()
}
