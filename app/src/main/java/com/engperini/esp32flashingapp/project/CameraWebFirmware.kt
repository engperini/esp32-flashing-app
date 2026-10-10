package com.engperini.esp32flashingapp.project

/** Native ESP-IDF 5.5 XIAO ESP32-S3 Sense camera server with Wi-Fi setup AP. */
object CameraWebFirmware {
    val previousMainC = """
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
    val mainC = """
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>
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

#define AP_SSID "XIAO-Camera-Setup"
#define NVS_NAMESPACE "camera_wifi"
static const char *TAG = "XIAO_CAMERA";
static bool camera_ready = false;
static bool sta_has_credentials = false;
static bool sta_connected = false;
static httpd_handle_t server = NULL;
static esp_netif_t *sta_netif = NULL;

static void load_credentials(char *ssid, size_t ssid_len, char *pass, size_t pass_len) {
    nvs_handle_t nvs;
    ssid[0] = 0; pass[0] = 0;
    if (nvs_open(NVS_NAMESPACE, NVS_READONLY, &nvs) != ESP_OK) return;
    size_t n = ssid_len;
    if (nvs_get_str(nvs, "ssid", ssid, &n) != ESP_OK) ssid[0] = 0;
    n = pass_len;
    if (nvs_get_str(nvs, "pass", pass, &n) != ESP_OK) pass[0] = 0;
    nvs_close(nvs);
}
static esp_err_t save_credentials(const char *ssid, const char *pass) {
    nvs_handle_t nvs;
    esp_err_t err = nvs_open(NVS_NAMESPACE, NVS_READWRITE, &nvs);
    if (err != ESP_OK) return err;
    err = nvs_set_str(nvs, "ssid", ssid);
    if (err == ESP_OK) err = nvs_set_str(nvs, "pass", pass);
    if (err == ESP_OK) err = nvs_commit(nvs);
    nvs_close(nvs);
    return err;
}
static void decode_form(char *dst, size_t cap, const char *src) {
    size_t j = 0;
    for (size_t i = 0; src[i] && j + 1 < cap; i++) {
        if (src[i] == '+') dst[j++] = ' ';
        else if (src[i] == '%' && src[i+1] && src[i+2]) {
            char hex[3] = {src[i+1], src[i+2], 0};
            char *end = NULL;
            long value = strtol(hex, &end, 16);
            if (end == hex + 2) { dst[j++] = (char)value; i += 2; }
            else dst[j++] = src[i];
        } else dst[j++] = src[i];
    }
    dst[j] = 0;
}
static void form_value(const char *body, const char *key, char *out, size_t cap) {
    out[0] = 0;
    size_t key_len = strlen(key);
    const char *p = body;
    while (*p) {
        if (strncmp(p, key, key_len) == 0 && p[key_len] == '=') {
            const char *start = p + key_len + 1;
            const char *end = strchr(start, '&');
            size_t len = end ? (size_t)(end - start) : strlen(start);
            char raw[256];
            if (len >= sizeof(raw)) return;
            memcpy(raw, start, len); raw[len] = 0;
            decode_form(out, cap, raw);
            return;
        }
        p = strchr(p, '&');
        if (!p) return;
        p++;
    }
}
static esp_err_t index_handler(httpd_req_t *req) {
    const char *top = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
        "<body style='font:16px sans-serif;max-width:650px;margin:24px auto;padding:12px'>"
        "<h2>XIAO ESP32-S3 Sense Camera</h2><p>Setup AP: <b>XIAO-Camera-Setup</b> (open) &mdash; http://192.168.4.1/</p>";
    httpd_resp_set_type(req, "text/html; charset=utf-8");
    httpd_resp_send_chunk(req, top, HTTPD_RESP_USE_STRLEN);
    if (camera_ready) {
        httpd_resp_send_chunk(req, "<p>Camera: <b>ready</b></p><p><img src='/stream' style='width:100%;max-width:640px'></p><a href='/capture'>Capture JPEG</a>", HTTPD_RESP_USE_STRLEN);
    } else {
        httpd_resp_send_chunk(req, "<p>Camera: <b>not detected</b>. Connect the Sense camera module and restart. Wi-Fi configuration is still available.</p>", HTTPD_RESP_USE_STRLEN);
    }
    httpd_resp_send_chunk(req, sta_connected ? "<p>Home Wi-Fi: <b>connected</b></p>" : "<p>Home Wi-Fi: <b>not connected</b></p>", HTTPD_RESP_USE_STRLEN);
    const char *form = "<h3>Configure home Wi-Fi</h3><form method='POST' action='/wifi'>"
        "<label>SSID <input name='ssid' maxlength='32' required></label><p>"
        "<label>Password <input type='password' name='pass' maxlength='63'></label></p>"
        "<button type='submit'>Save and connect</button></form>"
        "<p>Settings are saved on the device. The setup AP stays available.</p></body></html>";
    httpd_resp_send_chunk(req, form, HTTPD_RESP_USE_STRLEN);
    return httpd_resp_send_chunk(req, NULL, 0);
}
static esp_err_t wifi_save_handler(httpd_req_t *req) {
    if (req->content_len <= 0 || req->content_len > 512) {
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Invalid form");
    }
    char body[513] = {0};
    int received = 0;
    while (received < req->content_len) {
        int n = httpd_req_recv(req, body + received, req->content_len - received);
        if (n <= 0) return ESP_FAIL;
        received += n;
    }
    char ssid[33], pass[64];
    form_value(body, "ssid", ssid, sizeof(ssid));
    form_value(body, "pass", pass, sizeof(pass));
    if (!ssid[0]) return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "SSID required");
    esp_err_t err = save_credentials(ssid, pass);
    if (err != ESP_OK) return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "NVS save failed");
    wifi_config_t cfg = {0};
    memcpy(cfg.sta.ssid, ssid, strlen(ssid));
    memcpy(cfg.sta.password, pass, strlen(pass));
    sta_has_credentials = true;
    sta_connected = false;
    esp_wifi_disconnect();
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &cfg));
    esp_wifi_connect();
    ESP_LOGI(TAG, "Connecting to configured Wi-Fi SSID: %s", ssid);
    httpd_resp_set_type(req, "text/html");
    return httpd_resp_sendstr(req, "<html><body><h3>Wi-Fi saved</h3><p>Connecting. Check the USB serial log for the home network IP.</p><a href='/'>Back</a></body></html>");
}
static esp_err_t capture_handler(httpd_req_t *req) {
    if (!camera_ready) return httpd_resp_send_err(req, HTTPD_503_SERVICE_UNAVAILABLE, "Camera not connected");
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Camera capture failed");
    httpd_resp_set_type(req, "image/jpeg");
    esp_err_t ret = httpd_resp_send(req, (const char *)fb->buf, fb->len);
    esp_camera_fb_return(fb);
    return ret;
}
static esp_err_t stream_handler(httpd_req_t *req) {
    if (!camera_ready) return httpd_resp_send_err(req, HTTPD_503_SERVICE_UNAVAILABLE, "Camera not connected");
    esp_err_t ret = httpd_resp_set_type(req, "multipart/x-mixed-replace;boundary=frame");
    if (ret != ESP_OK) return ret;
    while (1) {
        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) { ESP_LOGW(TAG, "Camera frame unavailable"); break; }
        char header[128];
        int size = snprintf(header, sizeof(header), "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n", (unsigned)fb->len);
        ret = httpd_resp_send_chunk(req, header, size);
        if (ret == ESP_OK) ret = httpd_resp_send_chunk(req, (const char *)fb->buf, fb->len);
        if (ret == ESP_OK) ret = httpd_resp_send_chunk(req, "\r\n", 2);
        esp_camera_fb_return(fb);
        if (ret != ESP_OK) break;
        vTaskDelay(pdMS_TO_TICKS(30));
    }
    return ret;
}
static void start_server(void) {
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.stack_size = 8192;
    ESP_ERROR_CHECK(httpd_start(&server, &config));
    httpd_uri_t index = {.uri="/", .method=HTTP_GET, .handler=index_handler};
    httpd_uri_t save = {.uri="/wifi", .method=HTTP_POST, .handler=wifi_save_handler};
    httpd_uri_t capture = {.uri="/capture", .method=HTTP_GET, .handler=capture_handler};
    httpd_uri_t stream = {.uri="/stream", .method=HTTP_GET, .handler=stream_handler};
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &index));
    ESP_ERROR_CHECK(httpd_register_uri_handler(server, &save));
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
    esp_err_t err = esp_camera_init(&c);
    if (err == ESP_OK) {
        camera_ready = true;
        ESP_LOGI(TAG, "Camera ready");
    } else {
        camera_ready = false;
        ESP_LOGW(TAG, "Camera absent or initialization failed: %s; continuing without camera", esp_err_to_name(err));
    }
}
static void wifi_event(void *arg, esp_event_base_t base, int32_t id, void *data) {
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_START && sta_has_credentials) esp_wifi_connect();
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        sta_connected = false;
        if (sta_has_credentials) {
            ESP_LOGW(TAG, "Home Wi-Fi disconnected; retrying");
            esp_wifi_connect();
        }
    }
    if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        sta_connected = true;
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)data;
        ESP_LOGI(TAG, "Home Wi-Fi camera URL: http://" IPSTR "/", IP2STR(&event->ip_info.ip));
    }
}
static void wifi_start(void) {
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_ap();
    sta_netif = esp_netif_create_default_wifi_sta();
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init));
    ESP_ERROR_CHECK(esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event, NULL));
    wifi_config_t ap = {0};
    memcpy(ap.ap.ssid, AP_SSID, sizeof(AP_SSID));
    ap.ap.ssid_len = strlen(AP_SSID);
    ap.ap.channel = 1;
    ap.ap.max_connection = 4;
    ap.ap.authmode = WIFI_AUTH_OPEN;
    char ssid[33], pass[64];
    load_credentials(ssid, sizeof(ssid), pass, sizeof(pass));
    sta_has_credentials = ssid[0] != 0;
    wifi_config_t sta = {0};
    if (sta_has_credentials) {
        memcpy(sta.sta.ssid, ssid, strlen(ssid));
        memcpy(sta.sta.password, pass, strlen(pass));
    }
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_APSTA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &sta));
    ESP_ERROR_CHECK(esp_wifi_start());
    ESP_LOGI(TAG, "Setup AP ready: SSID=%s (no password), URL=http://192.168.4.1/", AP_SSID);
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
        ESP_LOGI(TAG, "Webserver alive | camera=%s | home Wi-Fi=%s | setup=http://192.168.4.1/",
                 camera_ready ? "ready" : "absent", sta_connected ? "connected" : "offline");
        vTaskDelay(pdMS_TO_TICKS(5000));
    }
}

""".trimIndent()
}
