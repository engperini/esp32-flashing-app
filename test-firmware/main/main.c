#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_chip_info.h"
#include "esp_idf_version.h"

void app_main(void)
{
    esp_chip_info_t chip;
    esp_chip_info(&chip);
    printf("ESP32_FLASHING_APP_TEST READY idf=%s cores=%d\n",
           esp_get_idf_version(), chip.cores);
    unsigned counter = 0;
    while (1) {
        printf("heartbeat=%u\n", counter++);
        vTaskDelay(pdMS_TO_TICKS(1000));
    }
}
