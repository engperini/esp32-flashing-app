package com.engperini.esp32flashingapp.project

/** Native ESP-IDF 5.5 camera web server for Seeed XIAO ESP32-S3 Sense OV2640. */
object CameraWebFirmware {
    val mainC = """
#include <stdio.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_err.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_http_server.h"
#include "nvs_flash.h"
#include "esp_camera.h"

#define WIFI_SSID "CHANGE_ME"
#define WIFI_PASSWORD "CHANGE_ME"
static const char *TAG = "XIAO_CAMERA";

static esp_err_t index_handler(httpd_req_t *req) {
    const char *html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                       "<body style='text-align:center;font-family:sans-serif'><h2>XIAO ESP32-S3 Sense</h2>"
                       "<img src='/stream' style='width:100%;max-width:640px'><p><a href='/capture'>Capture JPEG</a></p></body></html>";
    httpd_resp_set_type(req, "text/html");
    return httpd_resp_send(req, html, HTTPD_RESP_USE_STRLEN);
}
static esp_err_t capture_handler(httpd_req_t *req) {
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Camera capture failed");
    httpd_resp_set_type(req, "image/jpeg");
    esp_err_t ret = httpd_resp_send(req, (const char *)fb->buf, fb->len);
    esp_camera_fb_return(fb);
    return ret;
}
static esp_err_t stream_handler(httpd_req_t *req) {
    esp_err_t ret = httpd_resp_set_type(req, "multipart/x-mixed-replace;boundary=frame");
    if (ret != ESP_OK) return ret;
    while (1) {
        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) { ESP_LOGW(TAG, "Camera frame unavailable"); break; }
        char header[128];
        int size = snprintf(header, sizeof(header), "--frame\\r\\nContent-Type: image/jpeg\\r\\nContent-Length: %u\\r\\n\\r\\n", (unsigned)fb->len);
        ret = httpd_resp_send_chunk(req, header, size);
        if (ret == ESP_OK) ret = httpd_resp_send_chunk(req, (const char *)fb->buf, fb->len);
        if (ret == ESP_OK) ret = httpd_resp_send_chunk(req, "\\r\\n", 2);
        esp_camera_fb_return(fb);
        if (ret != ESP_OK) break;
        vTaskDelay(pdMS_TO_TICKS(30));
    }
    return ret;
}
static void start_server(void) {
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.stack_size = 8192;
    httpd_handle_t server = NULL;
    ESP_ERROR_CHECK(httpd_start(&server, &config));
    httpd_uri_t index = {.uri="/", .method=HTTP_GET, .handler=index_handler};
    httpd_uri_t capture = {.uri="/capture", .method=HTTP_GET, .handler=capture_handler};
    httpd_uri_t stream = {.uri="/stream", .method=HTTP_GET, .handler=stream_handler};
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &index));
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &capture));
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &stream));
}
static void camera_start(void) {
    camera_config_t c = {
        .pin_pwdn=-1, .pin_reset=-1, .pin_xclk=10,
        .pin_sccb_sda=40, .pin_sccb_scl=39,
        .pin_d0=15, .pin_d1=17, .pin_d2=18, .pin_d3=16,
        .pin_d4=14, .pin_d5=12, .pin_d6=11, .pin_d7=48,
        .pin_vsync=38, .pin_href=47, .pin_pclk=13,
        .xclk_freq_hz=20000000,
        .ledc_timer=LEDC_TIMER_0, .ledc_channel=LEDC_CHANNEL_0,
        .pixel_format=PIXFORMAT_JPEG, .frame_size=FRAMESIZE_VGA,
        .jpeg_quality=12, .fb_count=2,
        .fb_location=CAMERA_FB_IN_PSRAM, .grab_mode=CAMERA_GRAB_LATEST
    };
    ESP_ERROR_CHECK(esp_camera_init(&c));
    ESP_LOGI(TAG, "Camera ready");
}
static void wifi_event(void *arg, esp_event_base_t base, int32_t id, void *data) {
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_START) esp_wifi_connect();
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        ESP_LOGW(TAG, "WiFi disconnected; reconnecting");
        esp_wifi_connect();
    }
    if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)data;
        ESP_LOGI(TAG, "Open http://" IPSTR "/", IP2STR(&event->ip_info.ip));
    }
}
static void wifi_start(void) {
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init));
    ESP_ERROR_CHECK(esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event, NULL));
    wifi_config_t cfg = {0};
    snprintf((char *)cfg.sta.ssid, sizeof(cfg.sta.ssid), "%s", WIFI_SSID);
    snprintf((char *)cfg.sta.password, sizeof(cfg.sta.password), "%s", WIFI_PASSWORD);
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &cfg));
    ESP_ERROR_CHECK(esp_wifi_start());
}
void app_main(void) {
    ESP_LOGI(TAG, "XIAO ESP32-S3 Sense Camera WebServer starting");
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);
    camera_start();
    wifi_start();
    start_server();
    while (1) {
        ESP_LOGI(TAG, "Camera server running");
        vTaskDelay(pdMS_TO_TICKS(5000));
    }
}
""".trimIndent()
}
