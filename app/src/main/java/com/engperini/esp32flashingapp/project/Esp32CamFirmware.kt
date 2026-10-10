package com.engperini.esp32flashingapp.project

/** ESP32-CAM AI-Thinker OV2640, ESP-IDF 5.5. VGA baseline validated on hardware. */
object Esp32CamFirmware {
    val mainC = """
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_err.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_http_server.h"
#include "esp_heap_caps.h"
#include "nvs_flash.h"
#include "nvs.h"
#include "esp_camera.h"

#define AP_SSID "ESP32CAM-Setup"
#define NVS_NAMESPACE "camwifi"
static const char *TAG = "ESP32CAM";
static httpd_handle_t server = NULL;
static volatile bool camera_ready = false;
static volatile bool wifi_connected = false;
static bool has_credentials = false;

#define CAM_PIN_PWDN 32
#define CAM_PIN_RESET -1
#define CAM_PIN_XCLK 0
#define CAM_PIN_SIOD 26
#define CAM_PIN_SIOC 27
#define CAM_PIN_D0 5
#define CAM_PIN_D1 18
#define CAM_PIN_D2 19
#define CAM_PIN_D3 21
#define CAM_PIN_D4 36
#define CAM_PIN_D5 39
#define CAM_PIN_D6 34
#define CAM_PIN_D7 35
#define CAM_PIN_VSYNC 25
#define CAM_PIN_HREF 23
#define CAM_PIN_PCLK 22

static void load_wifi_credentials(char *ssid, size_t ssid_size, char *password, size_t password_size) {
    ssid[0] = 0; password[0] = 0;
    nvs_handle_t h;
    if (nvs_open(NVS_NAMESPACE, NVS_READONLY, &h) != ESP_OK) return;
    size_t n = ssid_size;
    if (nvs_get_str(h, "ssid", ssid, &n) != ESP_OK) ssid[0] = 0;
    n = password_size;
    if (nvs_get_str(h, "pass", password, &n) != ESP_OK) password[0] = 0;
    nvs_close(h);
}
static esp_err_t save_wifi_credentials(const char *ssid, const char *password) {
    nvs_handle_t h;
    esp_err_t err = nvs_open(NVS_NAMESPACE, NVS_READWRITE, &h);
    if (err != ESP_OK) return err;
    err = nvs_set_str(h, "ssid", ssid);
    if (err == ESP_OK) err = nvs_set_str(h, "pass", password);
    if (err == ESP_OK) err = nvs_commit(h);
    nvs_close(h);
    return err;
}
static void url_decode(char *dst, size_t cap, const char *src) {
    size_t j = 0;
    for (size_t i = 0; src[i] && j + 1 < cap; i++) {
        if (src[i] == '+') dst[j++] = ' ';
        else if (src[i] == '%' && src[i+1] && src[i+2]) {
            char hex[3] = {src[i+1], src[i+2], 0};
            char *end = NULL;
            long v = strtol(hex, &end, 16);
            if (end == hex + 2) { dst[j++] = (char)v; i += 2; }
            else dst[j++] = src[i];
        } else dst[j++] = src[i];
    }
    dst[j] = 0;
}
static void get_form_field(const char *body, const char *key, char *out, size_t cap) {
    out[0] = 0;
    size_t klen = strlen(key);
    const char *p = body;
    while (*p) {
        if (strncmp(p, key, klen) == 0 && p[klen] == '=') {
            const char *start = p + klen + 1;
            const char *end = strchr(start, '&');
            size_t len = end ? (size_t)(end-start) : strlen(start);
            char raw[256];
            if (len >= sizeof(raw)) return;
            memcpy(raw, start, len); raw[len] = 0;
            url_decode(out, cap, raw); return;
        }
        p = strchr(p, '&');
        if (!p) return;
        p++;
    }
}
static esp_err_t page_handler(httpd_req_t *req) {
    httpd_resp_set_type(req, "text/html; charset=utf-8");
    esp_err_t err = httpd_resp_send_chunk(req,
        "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
        "<title>ESP32-CAM</title><style>body{font-family:Arial;max-width:700px;margin:20px auto;padding:15px}"
        "img{width:100%;max-width:640px}input{padding:10px;margin:6px 0}button{padding:12px}</style>"
        "</head><body><h2>ESP32-CAM WebServer</h2><p>Setup AP: <b>ESP32CAM-Setup</b></p>"
        "<p>http://192.168.4.1/</p>", HTTPD_RESP_USE_STRLEN);
    if (err != ESP_OK) return err;
    err = httpd_resp_send_chunk(req, camera_ready
        ? "<p>Camera: <b>READY</b></p><img src='/stream'><p><a href='/capture'>Capture JPEG</a></p>"
        : "<p>Camera: <b>NOT DETECTED</b></p><p>Check camera connection and PSRAM.</p>",
        HTTPD_RESP_USE_STRLEN);
    if (err != ESP_OK) return err;
    err = httpd_resp_send_chunk(req, wifi_connected
        ? "<p>Home Wi-Fi: <b>CONNECTED</b></p>"
        : "<p>Home Wi-Fi: <b>OFFLINE</b></p>", HTTPD_RESP_USE_STRLEN);
    if (err != ESP_OK) return err;
    err = httpd_resp_send_chunk(req,
        "<h3>Configure home Wi-Fi</h3><form method='POST' action='/wifi'>"
        "<p>SSID<br><input name='ssid' maxlength='32' required></p>"
        "<p>Password<br><input type='password' name='pass' maxlength='63'></p>"
        "<button type='submit'>Save and connect</button></form>"
        "<p>Settings are stored in NVS.</p></body></html>", HTTPD_RESP_USE_STRLEN);
    if (err != ESP_OK) return err;
    return httpd_resp_send_chunk(req, NULL, 0);
}
static esp_err_t wifi_handler(httpd_req_t *req) {
    if (req->content_len <= 0 || req->content_len > 512)
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Invalid form");
    char body[513] = {0};
    int received = 0;
    while (received < req->content_len) {
        int n = httpd_req_recv(req, body + received, req->content_len - received);
        if (n == HTTPD_SOCK_ERR_TIMEOUT) continue;
        if (n <= 0) return ESP_FAIL;
        received += n;
    }
    char ssid[33] = {0}, pass[64] = {0};
    get_form_field(body, "ssid", ssid, sizeof(ssid));
    get_form_field(body, "pass", pass, sizeof(pass));
    if (!ssid[0]) return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "SSID required");
    esp_err_t err = save_wifi_credentials(ssid, pass);
    if (err != ESP_OK) return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Unable to save Wi-Fi");
    wifi_config_t cfg = {0};
    memcpy(cfg.sta.ssid, ssid, strlen(ssid));
    memcpy(cfg.sta.password, pass, strlen(pass));
    has_credentials = true;
    wifi_connected = false;
    esp_wifi_disconnect();
    err = esp_wifi_set_config(WIFI_IF_STA, &cfg);
    if (err != ESP_OK) return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Wi-Fi configuration failed");
    esp_wifi_connect();
    ESP_LOGI(TAG, "Connecting to SSID: %s", ssid);
    httpd_resp_set_type(req, "text/html");
    return httpd_resp_sendstr(req,
        "<html><body><h3>Wi-Fi saved!</h3><p>Connecting to your router.</p>"
        "<p>Check Serial Monitor for the IP address.</p><a href='/'>Back</a></body></html>");
}
static esp_err_t capture_handler(httpd_req_t *req) {
    if (!camera_ready) return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Camera unavailable");
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Capture failed");
    httpd_resp_set_type(req, "image/jpeg");
    esp_err_t ret = httpd_resp_send(req, (const char *)fb->buf, fb->len);
    esp_camera_fb_return(fb);
    return ret;
}
static esp_err_t stream_handler(httpd_req_t *req) {
    if (!camera_ready) return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Camera unavailable");
    esp_err_t ret = httpd_resp_set_type(req, "multipart/x-mixed-replace;boundary=frame");
    if (ret != ESP_OK) return ret;
    while (true) {
        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) break;
        char header[128];
        int n = snprintf(header, sizeof(header),
            "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n",
            (unsigned)fb->len);
        ret = httpd_resp_send_chunk(req, header, n);
        if (ret == ESP_OK) ret = httpd_resp_send_chunk(req, (const char *)fb->buf, fb->len);
        if (ret == ESP_OK) ret = httpd_resp_send_chunk(req, "\r\n", 2);
        esp_camera_fb_return(fb);
        if (ret != ESP_OK) break;
        vTaskDelay(pdMS_TO_TICKS(60));
    }
    return ret;
}
static void start_http_server(void) {
    httpd_config_t cfg = HTTPD_DEFAULT_CONFIG();
    cfg.stack_size = 8192;
    cfg.max_open_sockets = 5;
    cfg.lru_purge_enable = true;
    esp_err_t err = httpd_start(&server, &cfg);
    if (err != ESP_OK) { ESP_LOGE(TAG, "HTTP server failed: %s", esp_err_to_name(err)); return; }
    httpd_uri_t root = {.uri="/", .method=HTTP_GET, .handler=page_handler};
    httpd_uri_t wifi = {.uri="/wifi", .method=HTTP_POST, .handler=wifi_handler};
    httpd_uri_t capture = {.uri="/capture", .method=HTTP_GET, .handler=capture_handler};
    httpd_uri_t stream = {.uri="/stream", .method=HTTP_GET, .handler=stream_handler};
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &root));
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &wifi));
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &capture));
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &stream));
    ESP_LOGI(TAG, "HTTP server running");
}
static void wifi_event_handler(void *arg, esp_event_base_t base, int32_t id, void *data) {
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_START && has_credentials) esp_wifi_connect();
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        wifi_connected = false;
        ESP_LOGW(TAG, "Home Wi-Fi disconnected");
        if (has_credentials) esp_wifi_connect();
    }
    if (base == WIFI_EVENT && id == WIFI_EVENT_AP_START) ESP_LOGI(TAG, "Setup AP started");
    if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        wifi_connected = true;
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)data;
        ESP_LOGI(TAG, "HOME WI-FI CONNECTED");
        ESP_LOGI(TAG, "CAMERA URL: http://" IPSTR "/", IP2STR(&event->ip_info.ip));
    }
}
static void start_wifi(void) {
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_ap();
    esp_netif_create_default_wifi_sta();
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init));
    ESP_ERROR_CHECK(esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event_handler, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event_handler, NULL));
    wifi_config_t ap = {0};
    memcpy(ap.ap.ssid, AP_SSID, strlen(AP_SSID));
    ap.ap.ssid_len = strlen(AP_SSID);
    ap.ap.channel = 1;
    ap.ap.max_connection = 4;
    ap.ap.authmode = WIFI_AUTH_OPEN;
    char ssid[33] = {0}, pass[64] = {0};
    load_wifi_credentials(ssid, sizeof(ssid), pass, sizeof(pass));
    has_credentials = ssid[0] != 0;
    wifi_config_t sta = {0};
    if (has_credentials) {
        memcpy(sta.sta.ssid, ssid, strlen(ssid));
        memcpy(sta.sta.password, pass, strlen(pass));
    }
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_APSTA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &sta));
    ESP_ERROR_CHECK(esp_wifi_start());
    ESP_LOGI(TAG, "SETUP AP: %s", AP_SSID);
    ESP_LOGI(TAG, "SETUP URL: http://192.168.4.1/");
}
static void start_camera(void) {
    size_t psram_size = heap_caps_get_total_size(MALLOC_CAP_SPIRAM);
    bool psram_available = psram_size > 0;
    ESP_LOGI(TAG, "PSRAM available: %u bytes", (unsigned)psram_size);
    camera_config_t cfg = {
        .pin_pwdn=CAM_PIN_PWDN, .pin_reset=CAM_PIN_RESET, .pin_xclk=CAM_PIN_XCLK,
        .pin_sccb_sda=CAM_PIN_SIOD, .pin_sccb_scl=CAM_PIN_SIOC,
        .pin_d0=CAM_PIN_D0, .pin_d1=CAM_PIN_D1, .pin_d2=CAM_PIN_D2, .pin_d3=CAM_PIN_D3,
        .pin_d4=CAM_PIN_D4, .pin_d5=CAM_PIN_D5, .pin_d6=CAM_PIN_D6, .pin_d7=CAM_PIN_D7,
        .pin_vsync=CAM_PIN_VSYNC, .pin_href=CAM_PIN_HREF, .pin_pclk=CAM_PIN_PCLK,
        .xclk_freq_hz=20000000, .ledc_timer=LEDC_TIMER_0, .ledc_channel=LEDC_CHANNEL_0,
        .pixel_format=PIXFORMAT_JPEG,
        .frame_size=psram_available ? FRAMESIZE_VGA : FRAMESIZE_QQVGA,
        .jpeg_quality=psram_available ? 12 : 15,
        .fb_count=psram_available ? 2 : 1,
        .fb_location=psram_available ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM,
        .grab_mode=psram_available ? CAMERA_GRAB_LATEST : CAMERA_GRAB_WHEN_EMPTY
    };
    esp_err_t err = esp_camera_init(&cfg);
    if (err == ESP_OK) {
        camera_ready = true;
        ESP_LOGI(TAG, "OV2640 CAMERA READY");
    } else {
        camera_ready = false;
        ESP_LOGE(TAG, "Camera initialization failed: %s", esp_err_to_name(err));
    }
}
void app_main(void) {
    ESP_LOGI(TAG, "ESP32-CAM AI-Thinker | ESP-IDF Camera WebServer");
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);
    ESP_LOGI(TAG, "Free heap: %u bytes", (unsigned)esp_get_free_heap_size());
    start_wifi();
    start_http_server();
    start_camera();
    while (true) {
        wifi_mode_t mode = WIFI_MODE_NULL;
        wifi_config_t ap = {0};
        esp_err_t mode_err = esp_wifi_get_mode(&mode);
        esp_err_t ap_err = esp_wifi_get_config(WIFI_IF_AP, &ap);
        ESP_LOGI(TAG, "WIFI_DIAG: mode=%s (%d) ap=%s ssid=%s",
            esp_err_to_name(mode_err), (int)mode, esp_err_to_name(ap_err),
            ap_err == ESP_OK ? (char *)ap.ap.ssid : "unavailable");
        ESP_LOGI(TAG, "Webserver alive | camera=%s | home Wi-Fi=%s | heap=%u | setup=http://192.168.4.1/",
            camera_ready ? "ready" : "absent",
            wifi_connected ? "connected" : "offline",
            (unsigned)esp_get_free_heap_size());
        vTaskDelay(pdMS_TO_TICKS(5000));
    }
}
""".trimIndent()
}
