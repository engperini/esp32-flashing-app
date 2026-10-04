#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_chip_info.h"
#include "esp_flash.h"
#include "esp_system.h"
#include "esp_idf_version.h"
#include "driver/temperature_sensor.h"

void app_main(void) {
    esp_chip_info_t chip;
    uint32_t flash_size = 0;
    esp_chip_info(&chip);
    esp_flash_get_size(NULL, &flash_size);
    printf("\n=== ESP32 FLASHING APP TEST ===\n");
    printf("ESP-IDF: %s\n", esp_get_idf_version());
    printf("Cores: %d | Revision: %d | Flash: %lu MB\n", chip.cores, chip.revision, (unsigned long)(flash_size / (1024 * 1024)));
    temperature_sensor_handle_t sensor = NULL;
    temperature_sensor_config_t cfg = TEMPERATURE_SENSOR_CONFIG_DEFAULT(10, 50);
    if (temperature_sensor_install(&cfg, &sensor) == ESP_OK) temperature_sensor_enable(sensor);
    unsigned long counter = 0;
    while (1) {
        float temp = 0.0f;
        if (sensor && temperature_sensor_get_celsius(sensor, &temp) == ESP_OK)
            printf("[%06lu] ESP32-S3 alive | Temp: %.1f C | Free heap: %lu bytes\n", counter++, temp, (unsigned long)esp_get_free_heap_size());
        else
            printf("[%06lu] ESP32-S3 alive | Free heap: %lu bytes\n", counter++, (unsigned long)esp_get_free_heap_size());
        vTaskDelay(pdMS_TO_TICKS(1000));
    }
}
