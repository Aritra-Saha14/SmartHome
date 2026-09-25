#include <Wire.h>
#include <Adafruit_INA219.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <time.h>
#include "esp_system.h"
#include "esp_timer.h"
// ===============================
// WiFi Configuration
// ===============================
// IMPORTANT:
// A classic "ESP32 Dev Module" uses 2.4 GHz Wi-Fi.
// "416-5g" will NOT work when that SSID is a 5 GHz-only network.
// We therefore try the requested SSID first, then automatically
// fall back to the 2.4 GHz SSID using the same password.
const char* WIFI_SSID = "Galaxy A03s";
const char* WIFI_PASSWORD = "Shubhashis123";

// ===============================
// Supabase Configuration
// ===============================
const char* SUPABASE_URL = "https://rgpsckxlxklfujqfgdsj.supabase.co";
const char* SUPABASE_KEY = "sb_publishable_OI40Tem2RXS9JIE3wD4L9w_tC2jk-HH";
const char* SUPABASE_DEVICE_CODE = "SEMHAS-001";

// =====================================================
// 8) ESP32 CONNECTIONS WITH RELAY (DO NOT CHANGE)
//    - CH1 = GPIO14 (Relay only)
//    - CH2 = GPIO27 (Relay only)
//    - CH3 = GPIO26 (Relay only)
//    - CH4 = GPIO25 (Relay) / MOSFET GPIO13 (2-wire CPU fan)
//    - CH5 = GPIO33 (Relay) / MOSFET GPIO12 (PWM speed/intensity)
// =====================================================
#define RELAY_CH1 14
#define RELAY_CH2 27
#define RELAY_CH3 26
#define RELAY_CH4 25
#define RELAY_CH5 33

// =====================================================
// RELAY LOGIC (Active HIGH - DO NOT CHANGE)
// =====================================================
#define RELAY_ON  HIGH
#define RELAY_OFF LOW

// =====================================================
// PWM / MOSFET GATE PIN MAPPING (CONFIRMED HARDWARE)
// CH4 = MOSFET GPIO13 through 220 ohm (2-wire CPU fan)
// CH5 = MOSFET GPIO12 through 220 ohm (speed/intensity load)
// Gate pulldown = 10k to common GND
// =====================================================
#define MOSFET_CH4 13
#define MOSFET_CH5 12

// =====================================================
// PWM SETTINGS
// =====================================================
#define PWM_FREQUENCY 5000
#define PWM_RESOLUTION 8

// =====================================================
// 6) ESP32 CONNECTIONS WITH INA219 (DO NOT CHANGE)
//    - CH1–CH4: SCL = GPIO22, SDA = GPIO21 (I2C_BUS_1)
//    - CH5:     SCL = GPIO19, SDA = GPIO18 (I2C_BUS_2)
// =====================================================
// INA219 OBJECTS (DO NOT CHANGE ADDRESSES OR BUSES)
// =====================================================
Adafruit_INA219 ina219_CH1(0x40);
Adafruit_INA219 ina219_CH2(0x41);
Adafruit_INA219 ina219_CH3(0x44);
Adafruit_INA219 ina219_CH4(0x45);
Adafruit_INA219 ina219_CH5(0x40);

// Sensor Availability Flags
bool ina219Ch1Available = false;
bool ina219Ch2Available = false;
bool ina219Ch3Available = false;
bool ina219Ch4Available = false;
bool ina219Ch5Available = false;

// =====================================================
// TWO I2C BUSES (DO NOT CHANGE PINS)
// Bus 1 (CH1-CH4): SDA = GPIO21, SCL = GPIO22
// Bus 2 (CH5):     SDA = GPIO18, SCL = GPIO19
// =====================================================
TwoWire I2C_BUS_1 = TwoWire(0);
TwoWire I2C_BUS_2 = TwoWire(1);

// =====================================================
// CHANNEL STATUS
// =====================================================
bool ch1State = false;
bool ch2State = false;
bool ch3State = false;
bool ch4State = false;
bool ch5State = false;

// =====================================================
// PWM / INTENSITY VALUES (0-100 PERCENTAGE)
// =====================================================
int ch4Intensity = 100;  // CH4 PWM percentage (0-100)
int ch5Intensity = 100;  // CH5 PWM percentage (0-100)

// =====================================================
// SUPABASE / CLOUD SETTINGS & TIMINGS
// =====================================================
#define SUPABASE_HTTP_TIMEOUT 2500
#define COMMAND_HTTP_TIMEOUT 1000
#define COMMAND_POLL_INTERVAL_MS 250

TaskHandle_t commandTaskHandle = NULL;
TaskHandle_t telemetryTaskHandle = NULL;

const unsigned long SUPABASE_UPLOAD_INTERVAL = 5000;
const unsigned long SUPABASE_SYNC_INTERVAL = 250; // High-priority command polling target ~250ms
const unsigned long SUPABASE_INIT_RETRY_INTERVAL = 3000;
const unsigned long HEALTH_UPDATE_INTERVAL = 30000; // 30 seconds
const unsigned long HEALTH_SUMMARY_INTERVAL = 12000;
const float ELECTRICITY_RATE = 8.0; // Legacy rate (Rs/kWh)
const float ELECTRICITY_RATE_PER_WH = 0.008; // Migrated canonical rate (Rs/Wh)

String deviceId = "";
String applianceIds[5];

float energyWh[5] = {0, 0, 0, 0, 0};
unsigned long runtimeMs[5] = {0, 0, 0, 0, 0}; // Accurate millisecond runtime accounting
unsigned long lastEnergyMillis[5] = {0, 0, 0, 0, 0};

bool supabaseReady = false;
unsigned long lastSupabaseUpload = 0;
unsigned long lastSupabaseSync = 0;
unsigned long lastSupabaseInitRetry = 0;
unsigned long lastHealthUpdate = 0;
unsigned long lastHealthUpdateTimestamp = 0;
unsigned long lastHealthSummaryMillis = 0;
unsigned long lastCommandSyncTimestamp = 0;
unsigned long lastTelemetryUploadTimestamp = 0;

// =====================================================
// WIFI STATE MACHINE DEFINITIONS
// =====================================================
enum WiFiConnectionState {
  WIFI_STATE_DISCONNECTED,
  WIFI_STATE_CONNECTING,
  WIFI_STATE_CONNECTED
};

WiFiConnectionState wifiState = WIFI_STATE_DISCONNECTED;
unsigned long lastWiFiCheckMillis = 0;
unsigned long wifiConnectStartMillis = 0;

// Wi-Fi Connection & Timing Settings
const unsigned long WIFI_RETRY_INTERVAL = 2000;       // 2 seconds retry interval
const unsigned long WIFI_CONNECT_TIMEOUT = 10000;     // 10 seconds maximum per attempt
const unsigned long WIFI_STATUS_PRINT_INTERVAL = 5000;// 5 seconds heartbeat

uint32_t wifiRetryCount = 0;
unsigned long lastWiFiStatusPrint = 0;

// Forward declarations
void printBootDiagnostics();
void printSerialHeartbeat();
void handleSerialCommands();
void printINA219Readings();
void manageWiFi();
void manageSupabase();
bool syncRelaysFromSupabase();
bool loadSupabaseIds();
bool uploadDeviceHealth();
void uploadAllLiveReadings();
void printCommunicationHealthSummary();
void beginWiFiConnection();
void printWiFiFailureDiagnostic(wl_status_t status);
bool supabaseGET(const String& path, String& response, int& statusCode, unsigned long timeoutMs = 1000);
bool supabasePOST(const String& path, const String& body, String& response, int& statusCode, unsigned long timeoutMs = 1800);
bool supabasePATCH(const String& path, const String& body, String& response, int& statusCode, unsigned long timeoutMs = 1800);

// -----------------------------------------------------
// Map ESP32 reset reason enum to human-readable string
// -----------------------------------------------------
const char* getResetReasonString(esp_reset_reason_t reason) {
  switch (reason) {
    case ESP_RST_POWERON:   return "POWERON";
    case ESP_RST_EXT:       return "EXTERNAL";
    case ESP_RST_SW:        return "SOFTWARE";
    case ESP_RST_PANIC:     return "PANIC";
    case ESP_RST_INT_WDT:   return "INT_WDT";
    case ESP_RST_TASK_WDT:  return "TASK_WDT";
    case ESP_RST_WDT:       return "WDT";
    case ESP_RST_DEEPSLEEP: return "DEEPSLEEP";
    case ESP_RST_BROWNOUT:  return "BROWNOUT";
    case ESP_RST_SDIO:      return "SDIO";
    default:                return "UNKNOWN";
  }
}

// -----------------------------------------------------
// Helper to format ISO-8601 UTC timestamp
// -----------------------------------------------------
bool getUtcTimestamp(time_t epoch, char* buffer, size_t bufSize) {
  if (epoch < 1700000000) return false; // Not synced (> late 2023)
  struct tm timeinfo;
  gmtime_r(&epoch, &timeinfo);
  strftime(buffer, bufSize, "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
  return true;
}

// -----------------------------------------------------
// Perform a Supabase GET request (Non-blocking timeout)
// -----------------------------------------------------
bool supabaseGET(const String& path, String& response, int& statusCode, unsigned long timeoutMs) {
  response = "";
  statusCode = 0;

  if (WiFi.status() != WL_CONNECTED) {
    statusCode = -1;
    response = "WIFI_NOT_CONNECTED";
    return false;
  }

  Serial.println("HTTP_START:GET");
  unsigned long httpOpStart = millis();

  WiFiClientSecure client;
  client.setInsecure(); // Prototype only
  client.setTimeout(timeoutMs);
  client.setHandshakeTimeout(timeoutMs);

  HTTPClient http;
  String url = String(SUPABASE_URL) + path;

  http.setConnectTimeout(timeoutMs);
  http.setTimeout(timeoutMs);
  http.setReuse(false);

  if (!http.begin(client, url)) {
    statusCode = -2;
    response = "HTTP_BEGIN_FAILED";
    http.end();
    Serial.print("HTTP_END:GET:duration_ms=");
    Serial.println(millis() - httpOpStart);
    return false;
  }

  http.addHeader("apikey", SUPABASE_KEY);
  http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);
  http.addHeader("Accept", "application/json");
  http.addHeader("Cache-Control", "no-cache");
  http.addHeader("Pragma", "no-cache");
  http.addHeader("Connection", "close");

  statusCode = http.GET();
  if (statusCode > 0) {
    response = http.getString();
  } else {
    response = http.errorToString(statusCode);
  }

  http.end();
  Serial.print("HTTP_END:GET:duration_ms=");
  Serial.println(millis() - httpOpStart);
  return (statusCode >= 200 && statusCode < 300);
}

// -----------------------------------------------------
// Perform a Supabase POST request (Non-blocking timeout)
// -----------------------------------------------------
bool supabasePOST(const String& path, const String& body, String& response, int& statusCode, unsigned long timeoutMs) {
  response = "";
  statusCode = 0;

  if (WiFi.status() != WL_CONNECTED) {
    statusCode = -1;
    response = "WIFI_NOT_CONNECTED";
    return false;
  }

  Serial.println("HTTP_START:POST");
  unsigned long httpOpStart = millis();

  WiFiClientSecure client;
  client.setInsecure(); // Prototype only
  client.setTimeout(timeoutMs);
  client.setHandshakeTimeout(timeoutMs);

  HTTPClient http;
  String url = String(SUPABASE_URL) + path;

  http.setConnectTimeout(timeoutMs);
  http.setTimeout(timeoutMs);
  http.setReuse(false);

  if (!http.begin(client, url)) {
    Serial.println("Supabase HTTP begin failed.");
    statusCode = -2;
    response = "HTTP_BEGIN_FAILED";
    http.end();
    Serial.print("HTTP_END:POST:duration_ms=");
    Serial.println(millis() - httpOpStart);
    return false;
  }

  http.addHeader("apikey", SUPABASE_KEY);
  http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Prefer", "return=minimal");
  http.addHeader("Connection", "close");

  statusCode = http.POST(body);
  if (statusCode > 0) {
    if (statusCode != 204 && http.getSize() != 0) {
      response = http.getString();
    }
  } else {
    response = http.errorToString(statusCode);
  }

  http.end();
  Serial.print("HTTP_END:POST:duration_ms=");
  Serial.println(millis() - httpOpStart);
  return (statusCode >= 200 && statusCode < 300);
}

// -----------------------------------------------------
// Perform a Supabase PATCH request (Non-blocking timeout)
// -----------------------------------------------------
bool supabasePATCH(const String& path, const String& body, String& response, int& statusCode, unsigned long timeoutMs) {
  response = "";
  statusCode = 0;

  if (WiFi.status() != WL_CONNECTED) {
    statusCode = -1;
    response = "WIFI_NOT_CONNECTED";
    return false;
  }

  Serial.println("HTTP_START:PATCH");
  unsigned long httpOpStart = millis();

  WiFiClientSecure client;
  client.setInsecure(); // Prototype only
  client.setTimeout(timeoutMs);
  client.setHandshakeTimeout(timeoutMs);

  HTTPClient http;
  String url = String(SUPABASE_URL) + path;

  http.setConnectTimeout(timeoutMs);
  http.setTimeout(timeoutMs);
  http.setReuse(false);

  if (!http.begin(client, url)) {
    Serial.println("Supabase HTTP begin failed.");
    statusCode = -2;
    response = "HTTP_BEGIN_FAILED";
    http.end();
    Serial.print("HTTP_END:PATCH:duration_ms=");
    Serial.println(millis() - httpOpStart);
    return false;
  }

  http.addHeader("apikey", SUPABASE_KEY);
  http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Prefer", "return=minimal");
  http.addHeader("Connection", "close");

  statusCode = http.sendRequest("PATCH", body);
  if (statusCode > 0) {
    if (statusCode != 204 && http.getSize() != 0) {
      response = http.getString();
    }
  } else {
    response = http.errorToString(statusCode);
  }

  http.end();
  Serial.print("HTTP_END:PATCH:duration_ms=");
  Serial.println(millis() - httpOpStart);
  return (statusCode >= 200 && statusCode < 300);
}

// =====================================================
// SET RELAY
// =====================================================
void setRelay(int pin, bool state) {
  digitalWrite(pin, state ? RELAY_ON : RELAY_OFF);
}

// =====================================================
// CHANNEL PIN AND STATE HELPERS
// =====================================================
int getRelayPin(int channel) {
  switch (channel) {
    case 1: return RELAY_CH1;
    case 2: return RELAY_CH2;
    case 3: return RELAY_CH3;
    case 4: return RELAY_CH4;
    case 5: return RELAY_CH5;
    default: return -1;
  }
}

bool* getChannelStatePtr(int channel) {
  switch (channel) {
    case 1: return &ch1State;
    case 2: return &ch2State;
    case 3: return &ch3State;
    case 4: return &ch4State;
    case 5: return &ch5State;
    default: return nullptr;
  }
}

// =====================================================
// APPLY RELAY STATE WITH MANDATORY HARDWARE RECONCILIATION (Section 6 & 7)
// =====================================================
void applyRelayState(int ch, bool newState, const char* source = "UNKNOWN") {
  bool* statePtr = getChannelStatePtr(ch);
  int pin = getRelayPin(ch);
  if (!statePtr || pin < 0) return;

  *statePtr = newState;

  // Unconditional physical write
  setRelay(pin, newState);

  if (ch == 4) {
    int duty = (int)round((float)ch4Intensity * 255.0f / 100.0f);
    ledcWrite(MOSFET_CH4, newState ? duty : 0);
  } else if (ch == 5) {
    int duty = (int)round((float)ch5Intensity * 255.0f / 100.0f);
    ledcWrite(MOSFET_CH5, newState ? duty : 0);
  }

  // Verify physical readback
  int readback = digitalRead(pin);
  if ((readback == HIGH) != newState) {
    Serial.print("RELAY_GPIO_MISMATCH: CH");
    Serial.println(ch);
    Serial.flush();
  }

  // Reset interval accounting timestamp on relay state transition
  if (ch >= 1 && ch <= 5) {
    lastEnergyMillis[ch - 1] = millis();
  }
}

// =====================================================
// APPLY INTENSITY DOWNLINK (CH4 & CH5 ONLY - SECTION 17)
// =====================================================
void applyIntensityDownlink(int ch, int intensity) {

  // CH4 and CH5 are the only physical PWM channels.
  if (ch != 4 && ch != 5) return;

  if (intensity < 0) intensity = 0;
  if (intensity > 100) intensity = 100;

  int* storedIntensityPtr = (ch == 4) ? &ch4Intensity : &ch5Intensity;
  *storedIntensityPtr = intensity;

  int duty = (int)round((float)intensity * 255.0f / 100.0f);
  if (duty < 0) duty = 0;
  if (duty > 255) duty = 255;

  if (ch == 4) {
    ledcWrite(MOSFET_CH4, ch4State ? duty : 0);
  } else {
    ledcWrite(MOSFET_CH5, ch5State ? duty : 0);
  }

  Serial.println();
  Serial.print("INTENSITY_SYNC: channel=");
  Serial.print(ch);
  Serial.print(" intensity=");
  Serial.print(intensity);
  Serial.print("% pwm=");
  Serial.println(duty);
  Serial.flush();
}

// -----------------------------------------------------
// Find device UUID and all 5 appliance UUIDs with retries
// -----------------------------------------------------
bool loadSupabaseIds() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println();
    Serial.println("SUPABASE_INIT_FAILED: Wi-Fi not connected");
    Serial.println("SUPABASE_INIT_RETRY_SCHEDULED");
    Serial.flush();
    return false;
  }

  Serial.println();
  Serial.println("SUPABASE_INIT_ATTEMPT");
  Serial.println("DEVICE_LOOKUP");
  Serial.flush();

  String devicePath =
    "/rest/v1/devices?select=id&device_code=eq." +
    String(SUPABASE_DEVICE_CODE) +
    "&limit=1";

  String response;
  int statusCode = 0;
  if (!supabaseGET(devicePath, response, statusCode, 2000)) {
    Serial.println("SUPABASE_INIT_FAILED: Device lookup failed");
    Serial.println("ERROR_STAGE: DEVICE_LOOKUP");
    Serial.print("ERROR_CODE: HTTP_");
    Serial.println(statusCode);
    if (response.length() > 0) {
      Serial.print("ERROR_MESSAGE: ");
      Serial.println(response);
    }
    Serial.println("SUPABASE_INIT_RETRY_SCHEDULED");
    Serial.flush();
    return false;
  }

  JsonDocument devDoc;
  DeserializationError devErr = deserializeJson(devDoc, response);
  if (devErr || !devDoc.is<JsonArray>() || devDoc.as<JsonArray>().size() == 0) {
    Serial.println("SUPABASE_INIT_FAILED: Device SEMHAS-001 not found");
    Serial.println("ERROR_STAGE: DEVICE_LOOKUP");
    Serial.println("ERROR_CODE: DEVICE_NOT_FOUND");
    Serial.println("SUPABASE_INIT_RETRY_SCHEDULED");
    Serial.flush();
    return false;
  }

  deviceId = devDoc[0]["id"].as<String>();
  if (deviceId.length() == 0) {
    Serial.println("SUPABASE_INIT_FAILED: Device UUID is empty");
    Serial.println("ERROR_STAGE: DEVICE_LOOKUP");
    Serial.println("ERROR_CODE: EMPTY_UUID");
    Serial.println("SUPABASE_INIT_RETRY_SCHEDULED");
    Serial.flush();
    return false;
  }

  Serial.print("DEVICE_UUID: ");
  Serial.println(deviceId);
  Serial.println("APPLIANCE_LOOKUP");
  Serial.flush();

  // Query all 5 appliances for this device
  String appliancePath =
    "/rest/v1/appliances?select=id,channel_number&device_id=eq." +
    deviceId +
    "&order=channel_number.asc";

  if (!supabaseGET(appliancePath, response, statusCode, 2000)) {
    Serial.println("SUPABASE_INIT_FAILED: Could not load appliances");
    Serial.println("ERROR_STAGE: APPLIANCE_LOOKUP");
    Serial.print("ERROR_CODE: HTTP_");
    Serial.println(statusCode);
    if (response.length() > 0) {
      Serial.print("ERROR_MESSAGE: ");
      Serial.println(response);
    }
    Serial.println("SUPABASE_INIT_RETRY_SCHEDULED");
    Serial.flush();
    return false;
  }

  JsonDocument appDoc;
  DeserializationError appErr = deserializeJson(appDoc, response);
  if (appErr || !appDoc.is<JsonArray>()) {
    Serial.println("SUPABASE_INIT_FAILED: Failed to parse appliances JSON");
    Serial.println("ERROR_STAGE: APPLIANCE_LOOKUP");
    Serial.println("ERROR_CODE: JSON_PARSE_ERROR");
    Serial.println("SUPABASE_INIT_RETRY_SCHEDULED");
    Serial.flush();
    return false;
  }

  JsonArray appRows = appDoc.as<JsonArray>();
  for (JsonObject row : appRows) {
    int ch = row["channel_number"];
    if (ch >= 1 && ch <= 5) {
      applianceIds[ch - 1] = row["id"].as<String>();
    }
  }

  int loadedCount = 0;
  for (int i = 0; i < 5; i++) {
    int ch = i + 1;
    if (applianceIds[i].length() > 0) {
      loadedCount++;
      Serial.print("CH");
      Serial.print(ch);
      Serial.print("_UUID: ");
      Serial.println(applianceIds[i]);
    } else {
      Serial.print("SUPABASE_INIT_FAILED: Missing CH");
      Serial.print(ch);
      Serial.println(" appliance UUID");
    }
  }

  Serial.print("SUPABASE_APPLIANCE_COUNT: ");
  Serial.println(loadedCount);
  Serial.flush();

  // Strict: Do NOT mark READY unless all 5 appliance IDs exist
  if (loadedCount < 5) {
    Serial.println("SUPABASE_INIT_FAILED: Incomplete appliances (< 5)");
    Serial.println("ERROR_STAGE: APPLIANCE_LOOKUP");
    Serial.println("ERROR_CODE: INCOMPLETE_APPLIANCES");
    Serial.println("SUPABASE_INIT_RETRY_SCHEDULED");
    Serial.flush();
    return false;
  }

  supabaseReady = true;
  Serial.println("SUPABASE_READY");
  Serial.flush();
  return true;
}

// Dedicated Command Connection variables (Persistent Keep-Alive)
WiFiClientSecure cmdClient;
HTTPClient cmdHttp;
bool cmdHttpInitialized = false;

// =====================================================
// SYNCHRONIZE RELAYS & INTENSITY FROM SUPABASE (DOWNLINK)
// =====================================================
bool syncRelaysFromSupabase() {
  if (!supabaseReady || deviceId.length() == 0) return false;
  if (WiFi.status() != WL_CONNECTED) {
    if (cmdHttpInitialized) {
      cmdHttp.end();
      cmdClient.stop();
      cmdHttpInitialized = false;
    }
    return false;
  }

  Serial.println("COMMAND_POLL_START");
  Serial.println("COMMAND_HTTP_START");
  unsigned long httpStart = millis();

  String path = "/rest/v1/appliances?select=channel_number,relay_state,intensity&device_id=eq." +
                deviceId +
                "&order=channel_number.asc";
  String url = String(SUPABASE_URL) + path;

  // Initialize or re-establish connection if needed
  if (!cmdHttpInitialized || !cmdClient.connected()) {
    cmdHttp.end();
    cmdClient.stop();
    cmdClient.setInsecure();
    cmdClient.setTimeout(COMMAND_HTTP_TIMEOUT);
    cmdClient.setHandshakeTimeout(1200);

    cmdHttp.setConnectTimeout(COMMAND_HTTP_TIMEOUT);
    cmdHttp.setTimeout(COMMAND_HTTP_TIMEOUT);
    cmdHttp.setReuse(true);

    if (!cmdHttp.begin(cmdClient, url)) {
      cmdHttpInitialized = false;
      Serial.println("COMMAND_HTTP_END:duration_ms=0");
      Serial.println("HTTP_FAILURE: stage=COMMAND_BEGIN");
      return false;
    }
    cmdHttpInitialized = true;
  }

  // Set request headers for keep-alive command polling
  cmdHttp.addHeader("apikey", SUPABASE_KEY);
  cmdHttp.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);
  cmdHttp.addHeader("Accept", "application/json");
  cmdHttp.addHeader("Cache-Control", "no-cache");
  cmdHttp.addHeader("Pragma", "no-cache");
  cmdHttp.addHeader("Connection", "keep-alive");

  int statusCode = cmdHttp.GET();
  unsigned long httpDuration = millis() - httpStart;

  Serial.print("COMMAND_HTTP_END:duration_ms=");
  Serial.println(httpDuration);

  if (statusCode != 200) {
    Serial.print("HTTP_FAILURE: stage=COMMAND_SYNC status=");
    Serial.println(statusCode);
    // On negative error or status, reset connection so next attempt reconnects cleanly
    cmdHttp.end();
    cmdClient.stop();
    cmdHttpInitialized = false;
    return false;
  }

  String response = cmdHttp.getString();
  if (response.length() == 0) {
    return false;
  }

  // Section 5: Robust JSON validation with ArduinoJson
  JsonDocument doc;
  DeserializationError error = deserializeJson(doc, response);
  if (error || !doc.is<JsonArray>()) {
    Serial.println("HTTP_FAILURE: stage=COMMAND_SYNC reason=JSON_PARSE_ERROR");
    return false;
  }

  Serial.println("COMMAND_PARSE_END");

  JsonArray rows = doc.as<JsonArray>();
  int count = rows.size();
  if (count < 5) {
    Serial.println("HTTP_FAILURE: stage=COMMAND_SYNC reason=INCOMPLETE_CHANNEL_SET");
    return false;
  }

  bool channelPresent[5] = {false, false, false, false, false};
  bool channelRelayState[5];
  int channelIntensity[5];
  bool hasIntensity[5] = {false, false, false, false, false};

  for (JsonObject row : rows) {
    if (!row["channel_number"].is<int>()) continue;
    int ch = row["channel_number"].as<int>();
    if (ch >= 1 && ch <= 5) {
      if (!row["relay_state"].is<bool>()) continue;
      channelPresent[ch - 1] = true;
      channelRelayState[ch - 1] = row["relay_state"].as<bool>();
      if (!row["intensity"].isNull() && row["intensity"].is<int>()) {
        hasIntensity[ch - 1] = true;
        channelIntensity[ch - 1] = row["intensity"].as<int>();
      }
    }
  }

  for (int i = 0; i < 5; i++) {
    if (!channelPresent[i]) return false;
  }

  lastCommandSyncTimestamp = millis();

  unsigned long nowMs = millis();
  unsigned long applyStartMs = millis();

  // Section 4, 5, 6: Unconditionally apply and reconcile all five channels
  for (int i = 0; i < 5; i++) {
    int chNum = i + 1;
    bool dbState = channelRelayState[i];
    int pin = getRelayPin(chNum);
    bool* currentStatePtr = getChannelStatePtr(chNum);
    bool stateChanged = (currentStatePtr && *currentStatePtr != dbState);

    // Apply relay state immediately
    applyRelayState(chNum, dbState, "CLOUD_SYNC");

    // Apply PWM intensity immediately (CH4 / CH5)
    if (hasIntensity[i]) {
      applyIntensityDownlink(chNum, channelIntensity[i]);
    }

    int readback = digitalRead(pin);

    if (stateChanged) {
      unsigned long appliedAtMs = millis();
      Serial.print("COMMAND_RECEIVED_AT_MS=");
      Serial.println(nowMs);
      Serial.print("COMMAND_GPIO_APPLY_START_MS=");
      Serial.println(applyStartMs);
      Serial.print("COMMAND_GPIO_APPLIED_AT_MS=");
      Serial.println(appliedAtMs);
      Serial.print("COMMAND_GPIO_LATENCY_MS=");
      Serial.println(appliedAtMs - applyStartMs);

      Serial.print("CH"); Serial.print(chNum);
      Serial.print("_DB_STATE: "); Serial.println(dbState ? "ON" : "OFF");
      Serial.print("CH"); Serial.print(chNum);
      Serial.print("_GPIO: GPIO"); Serial.println(pin);
      Serial.print("CH"); Serial.print(chNum);
      Serial.print("_GPIO_READBACK: "); Serial.println(readback == HIGH ? "HIGH" : "LOW");
      Serial.flush();
    }
  }

  Serial.println("COMMAND_GPIO_APPLY_END");
  Serial.flush();
  return true;
}

// -----------------------------------------------------
// Upload Real ESP32 Hardware Health Telemetry (Section 13)
// -----------------------------------------------------
bool uploadDeviceHealth() {
  if (!supabaseReady || deviceId.length() == 0) return false;

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("ESP32_HEALTH_WAITING_FOR_WIFI");
    return false;
  }

  Serial.println();
  Serial.println("ESP32_HEALTH_UPDATE_STARTED");

  int wifiRssi = WiFi.RSSI();
  String ipAddress = WiFi.localIP().toString();
  uint64_t uptimeSeconds = esp_timer_get_time() / 1000000ULL;
  uint32_t freeHeapBytes = esp_get_free_heap_size();
  uint32_t minFreeHeapBytes = esp_get_minimum_free_heap_size();
  const char* resetReason = getResetReasonString(esp_reset_reason());

  time_t now = time(nullptr);
  char nowStr[32] = {0};
  char bootStr[32] = {0};
  bool ntpSynced = getUtcTimestamp(now, nowStr, sizeof(nowStr));

  if (ntpSynced) {
    time_t bootEpoch = now - (time_t)uptimeSeconds;
    getUtcTimestamp(bootEpoch, bootStr, sizeof(bootStr));
  }

  Serial.print("WIFI_RSSI: ");
  Serial.print(wifiRssi);
  Serial.println(" dBm");

  Serial.print("IP_ADDRESS: ");
  Serial.println(ipAddress);

  Serial.print("UPTIME_SECONDS: ");
  Serial.println((unsigned long)uptimeSeconds);

  Serial.print("FREE_HEAP_BYTES: ");
  Serial.println(freeHeapBytes);

  Serial.print("MIN_FREE_HEAP_BYTES: ");
  Serial.println(minFreeHeapBytes);

  Serial.print("RESET_REASON: ");
  Serial.println(resetReason);

  Serial.print("BOOT_TIME: ");
  if (ntpSynced) {
    Serial.println(bootStr);
  } else {
    Serial.println("NOT_SYNCED");
  }

  Serial.println("ESP32_HEALTH_PATCH_START");
  Serial.flush();

  // Build JSON Document
  JsonDocument doc;
  doc["is_online"] = true;
  doc["wifi_rssi"] = wifiRssi;
  doc["ip_address"] = ipAddress;
  doc["uptime_seconds"] = uptimeSeconds;
  doc["free_heap_bytes"] = freeHeapBytes;
  doc["min_free_heap_bytes"] = minFreeHeapBytes;
  doc["reset_reason"] = resetReason;

  if (ntpSynced) {
    doc["last_seen"] = nowStr;
    doc["health_updated_at"] = nowStr;
    doc["boot_time"] = bootStr;
  } else {
    doc["boot_time"] = nullptr;
  }

  String body;
  serializeJson(doc, body);

  String path = "/rest/v1/devices?id=eq." + deviceId;
  String response;
  int statusCode = 0;

  Serial.println("HEALTH_HTTP_START");
  unsigned long healthStartMs = millis();

  bool ok = supabasePATCH(path, body, response, statusCode, 1800);

  unsigned long healthDurationMs = millis() - healthStartMs;
  Serial.print("HEALTH_HTTP_END duration_ms=");
  Serial.println(healthDurationMs);

  Serial.print("ESP32_HEALTH_HTTP: status=");
  Serial.println(statusCode);

  if (ok) {
    Serial.println("ESP32_HEALTH_UPDATE_SUCCESS");
    Serial.println("HEARTBEAT_CONFIRMED");
    lastHealthUpdateTimestamp = millis();
    Serial.flush();
    return true;
  } else {
    Serial.println("ESP32_HEALTH_HTTP_FAILED");
    Serial.println("ESP32_HEALTH_UPDATE_FAILED");
    Serial.println("HEARTBEAT_FAILED");
    Serial.println("ERROR_STAGE: HEALTH_TELEMETRY");
    Serial.print("ERROR_CODE: HTTP_");
    Serial.println(statusCode);
    if (response.length() > 0) {
      Serial.print("ERROR_MESSAGE: ");
      Serial.println(response);
    }
    Serial.flush();
    return false;
  }
}

// -----------------------------------------------------
// Upload one live INA219 reading (Section 11 & 12)
// -----------------------------------------------------
bool uploadLiveReading(
  int index,
  Adafruit_INA219& sensor,
  bool channelState
) {
  int chNum = index + 1;

  if (!supabaseReady || applianceIds[index].length() == 0) {
    Serial.print("TELEMETRY_SKIP: CH");
    Serial.print(chNum);
    Serial.println(" reason=APPLIANCE_ID_MISSING");
    Serial.flush();
    return false;
  }

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("TELEMETRY_WAITING_FOR_WIFI");
    Serial.flush();
    return false;
  }

  // Check if sensor is hardware available
  bool sensorAvail = false;
  switch (index) {
    case 0: sensorAvail = ina219Ch1Available; break;
    case 1: sensorAvail = ina219Ch2Available; break;
    case 2: sensorAvail = ina219Ch3Available; break;
    case 3: sensorAvail = ina219Ch4Available; break;
    case 4: sensorAvail = ina219Ch5Available; break;
  }

  if (!sensorAvail) {
    Serial.print("TELEMETRY_SKIP: CH");
    Serial.print(chNum);
    Serial.println(" reason=SENSOR_UNAVAILABLE");
    Serial.flush();
    return false;
  }

  Serial.print("TELEMETRY_UPLOAD_STARTED: CH");
  Serial.print(chNum);
  Serial.print(" state=");
  Serial.println(channelState ? "ON" : "OFF");
  Serial.flush();

  unsigned long telemetryStart = millis();

  float loadVoltage = 0.0;
  float currentA = 0.0;
  float powerW = 0.0;
  float energyWhDelta = 0.0;
  float energyDeltaKWh = 0.0;
  float runtimeDeltaSeconds = 0.0;
  float estimatedCost = 0.0;

  unsigned long now = millis();
  if (lastEnergyMillis[index] == 0) {
    lastEnergyMillis[index] = now;
  }

  unsigned long elapsedMs = now - lastEnergyMillis[index];
  lastEnergyMillis[index] = now;

  // Cap interval to 10 seconds to avoid giant delta spikes after reconnects/delays
  if (elapsedMs > 10000) {
    elapsedMs = 10000;
  }

  if (channelState) {
    // Channel is ON: Measure physical electrical draw and accumulate interval delta
    float shuntVoltage = sensor.getShuntVoltage_mV();
    float busVoltage = sensor.getBusVoltage_V();
    float current_mA = sensor.getCurrent_mA();
    float power_mW = sensor.getPower_mW();

    loadVoltage = busVoltage + (shuntVoltage / 1000.0);
    currentA = current_mA / 1000.0;
    powerW = power_mW / 1000.0;

    // Canonical accumulated energy delta in Wh
    energyWhDelta = powerW * ((float)elapsedMs / 3600000.0);
    energyDeltaKWh = energyWhDelta / 1000.0; // Retain DB column compatibility
    runtimeDeltaSeconds = (float)elapsedMs / 1000.0;
    estimatedCost = energyWhDelta * ELECTRICITY_RATE_PER_WH;

    // Maintain boot-local accumulators for serial debug
    energyWh[index] += energyWhDelta;
    runtimeMs[index] += elapsedMs;
  } else {
    // Channel is OFF: Power and current are zero.
    // No new energy accumulates. Previous historical database energy remains preserved.
    loadVoltage = 0.0;
    currentA = 0.0;
    powerW = 0.0;
    energyWhDelta = 0.0;
    energyDeltaKWh = 0.0;
    runtimeDeltaSeconds = 0.0;
    estimatedCost = 0.0;
  }

  // Build JSON for persistent energy_readings table
  String body = "{";
  body += "\"appliance_id\":\"" + applianceIds[index] + "\",";
  body += "\"voltage\":" + String(loadVoltage, 3) + ",";
  body += "\"current\":" + String(currentA, 3) + ",";
  body += "\"power\":" + String(powerW, 3) + ",";
  body += "\"energy_delta_kwh\":" + String(energyDeltaKWh, 8) + ",";
  body += "\"runtime_delta_seconds\":" + String(runtimeDeltaSeconds, 3) + ",";
  body += "\"estimated_cost\":" + String(estimatedCost, 4);
  body += "}";

  String response;
  int statusCode = 0;

  Serial.print("TELEMETRY_HTTP_START:CH");
  Serial.println(chNum);
  unsigned long telemetryHttpStart = millis();

  bool ok = supabasePOST("/rest/v1/energy_readings", body, response, statusCode, 1800);

  // Fallback to live_readings if energy_readings table is not yet created
  if (!ok && statusCode == 404) {
    unsigned long totalRuntimeSeconds = runtimeMs[index] / 1000UL;
    float energyKWh = energyWh[index] / 1000.0;
    float legacyCost = energyKWh * ELECTRICITY_RATE;

    String legacyBody = "{";
    legacyBody += "\"appliance_id\":\"" + applianceIds[index] + "\",";
    legacyBody += "\"voltage\":" + String(loadVoltage, 3) + ",";
    legacyBody += "\"current\":" + String(currentA, 3) + ",";
    legacyBody += "\"power\":" + String(powerW, 3) + ",";
    legacyBody += "\"energy\":" + String(energyKWh, 6) + ",";
    legacyBody += "\"runtime_seconds\":" + String(totalRuntimeSeconds) + ",";
    legacyBody += "\"estimated_cost\":" + String(legacyCost, 4);
    legacyBody += "}";

    ok = supabasePOST("/rest/v1/live_readings", legacyBody, response, statusCode, 1800);
  }

  unsigned long telemetryHttpDuration = millis() - telemetryHttpStart;
  Serial.print("TELEMETRY_HTTP_END:CH");
  Serial.print(chNum);
  Serial.print(" duration_ms=");
  Serial.println(telemetryHttpDuration);

  unsigned long telemetryDuration = millis() - telemetryStart;
  Serial.print("TELEMETRY_DURATION_MS: ");
  Serial.println(telemetryDuration);

  Serial.print("TELEMETRY_HTTP: CH");
  Serial.print(chNum);
  Serial.print(" status=");
  Serial.println(statusCode);

  if (ok) {
    Serial.print("TELEMETRY_UPLOAD_SUCCESS: CH");
    Serial.print(chNum);
    Serial.print(" state=");
    Serial.print(channelState ? "ON" : "OFF");
    Serial.print(" power=");
    Serial.print(powerW, 1);
    Serial.print("W delta_wh=");
    Serial.print(energyWhDelta, 6);
    Serial.print(" delta_kwh=");
    Serial.println(energyDeltaKWh, 6);
    lastTelemetryUploadTimestamp = millis();
    Serial.flush();
    return true;
  } else {
    Serial.print("TELEMETRY_UPLOAD_FAILED: CH");
    Serial.println(chNum);
    Serial.print("HTTP_FAILURE: stage=TELEMETRY_UPLOAD ch=");
    Serial.print(chNum);
    Serial.print(" status=");
    Serial.print(statusCode);
    Serial.print(" reason=");
    Serial.println(response);
    Serial.flush();
    return false;
  }
}

// -----------------------------------------------------
// Upload all currently ON channels (Section 11)
// -----------------------------------------------------
void uploadAllLiveReadings() {
  if (!supabaseReady) return;

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("TELEMETRY_WAITING_FOR_WIFI");
    Serial.flush();
    return;
  }

  for (int i = 0; i < 5; i++) {
    // Check serial commands before every upload so terminal stays immediately responsive
    handleSerialCommands();

    Adafruit_INA219* sensorPtr = nullptr;
    switch (i) {
      case 0: sensorPtr = &ina219_CH1; break;
      case 1: sensorPtr = &ina219_CH2; break;
      case 2: sensorPtr = &ina219_CH3; break;
      case 3: sensorPtr = &ina219_CH4; break;
      case 4: sensorPtr = &ina219_CH5; break;
    }
    bool chState = false;
    switch (i) {
      case 0: chState = ch1State; break;
      case 1: chState = ch2State; break;
      case 2: chState = ch3State; break;
      case 3: chState = ch4State; break;
      case 4: chState = ch5State; break;
    }

    if (sensorPtr) {
      uploadLiveReading(i, *sensorPtr, chState);
    }
  }
}

// =====================================================
// FREERTOS TASKS: HIGH-PRIORITY COMMANDS & LOW-PRIORITY TELEMETRY
// =====================================================

// High-Priority Cloud Command Polling Task (Never blocked by telemetry or health)
void commandTaskLoop(void* pvParameters) {
  for (;;) {
    if (supabaseReady && wifiState == WIFI_STATE_CONNECTED) {
      syncRelaysFromSupabase();
    }
    vTaskDelay(pdMS_TO_TICKS(COMMAND_POLL_INTERVAL_MS)); // 250ms target
  }
}

// Lower-Priority Telemetry & Health Upload Task (Staggered 1 channel/sec)
void telemetryTaskLoop(void* pvParameters) {
  int currentChannelIndex = 0;
  unsigned long lastHealthUploadTime = 0;

  for (;;) {
    if (!supabaseReady || wifiState != WIFI_STATE_CONNECTED) {
      vTaskDelay(pdMS_TO_TICKS(500));
      continue;
    }

    // 1. Staggered Live Telemetry: Upload ONE channel per cycle (every 1 second)
    Adafruit_INA219* sensorPtr = nullptr;
    switch (currentChannelIndex) {
      case 0: sensorPtr = &ina219_CH1; break;
      case 1: sensorPtr = &ina219_CH2; break;
      case 2: sensorPtr = &ina219_CH3; break;
      case 3: sensorPtr = &ina219_CH4; break;
      case 4: sensorPtr = &ina219_CH5; break;
    }
    bool chState = false;
    switch (currentChannelIndex) {
      case 0: chState = ch1State; break;
      case 1: chState = ch2State; break;
      case 2: chState = ch3State; break;
      case 3: chState = ch4State; break;
      case 4: chState = ch5State; break;
    }

    if (sensorPtr) {
      uploadLiveReading(currentChannelIndex, *sensorPtr, chState);
    }

    currentChannelIndex = (currentChannelIndex + 1) % 5;

    // 2. Periodic Device Health Telemetry (every 30 seconds)
    if (millis() - lastHealthUploadTime >= HEALTH_UPDATE_INTERVAL) {
      lastHealthUploadTime = millis();
      uploadDeviceHealth();
    }

    // Stagger delay: 1000ms between channel uploads
    vTaskDelay(pdMS_TO_TICKS(1000));
  }
}

// =====================================================
// ALL CHANNELS ON
// =====================================================
void allChannelsON() {
  ch1State = true;
  ch2State = true;
  ch3State = true;
  ch4State = true;
  ch5State = true;

  setRelay(RELAY_CH1, true);
  setRelay(RELAY_CH2, true);
  setRelay(RELAY_CH3, true);
  setRelay(RELAY_CH4, true);
  setRelay(RELAY_CH5, true);

  // Apply current PWM settings
  ledcWrite(MOSFET_CH4, (int)round((float)ch4Intensity * 255.0f / 100.0f));
  ledcWrite(MOSFET_CH5, (int)round((float)ch5Intensity * 255.0f / 100.0f));

  Serial.println();
  Serial.println("========================================");
  Serial.println("ALL CHANNELS -> ON");
  Serial.println("========================================");
  Serial.flush();
}

// =====================================================
// ALL CHANNELS OFF
// =====================================================
void allChannelsOFF() {
  ch1State = false;
  ch2State = false;
  ch3State = false;
  ch4State = false;
  ch5State = false;

  setRelay(RELAY_CH1, false);
  setRelay(RELAY_CH2, false);
  setRelay(RELAY_CH3, false);
  setRelay(RELAY_CH4, false);
  setRelay(RELAY_CH5, false);

  // Turn MOSFET outputs OFF
  ledcWrite(MOSFET_CH4, 0);
  ledcWrite(MOSFET_CH5, 0);

  Serial.println();
  Serial.println("========================================");
  Serial.println("ALL CHANNELS -> OFF");
  Serial.println("========================================");
  Serial.flush();
}

// =====================================================
// PRINT INA219 READING
// =====================================================
void printINA219(
  Adafruit_INA219 &sensor,
  const char* channelName,
  bool channelState
) {
  if (!channelState) {
    return;
  }

  float shuntVoltage = sensor.getShuntVoltage_mV();
  float busVoltage = sensor.getBusVoltage_V();
  float current_mA = sensor.getCurrent_mA();
  float power_mW = sensor.getPower_mW();

  float loadVoltage = busVoltage + (shuntVoltage / 1000.0);

  Serial.print(channelName);
  Serial.println(" --------------------------------");

  Serial.print("Bus Voltage   : ");
  Serial.print(busVoltage, 3);
  Serial.println(" V");

  Serial.print("Shunt Voltage : ");
  Serial.print(shuntVoltage, 3);
  Serial.println(" mV");

  Serial.print("Load Voltage  : ");
  Serial.print(loadVoltage, 3);
  Serial.println(" V");

  Serial.print("Current       : ");
  Serial.print(current_mA, 2);
  Serial.print(" mA (");
  Serial.print(current_mA / 1000.0, 3);
  Serial.println(" A)");

  Serial.print("Power         : ");
  Serial.print(power_mW, 2);
  Serial.print(" mW (");
  Serial.print(power_mW / 1000.0, 3);
  Serial.println(" W");

  Serial.println();
  Serial.flush();
}

// =====================================================
// CH4 FAN SPEED (MOSFET GPIO13)
// =====================================================
void setCH4Speed(int duty, const char* level) {
  if (duty < 0) duty = 0;
  if (duty > 255) duty = 255;

  ch4Intensity = (int)round((float)duty * 100.0f / 255.0f);

  if (ch4State) {
    ledcWrite(MOSFET_CH4, duty);
  }

  Serial.print("CH4 FAN SPEED -> ");
  Serial.print(level);
  Serial.print(" | PWM = ");
  Serial.print(duty);
  Serial.print(" | ");
  Serial.print(ch4Intensity);
  Serial.println("%");
  Serial.flush();
}

// =====================================================
// CH5 SPEED / INTENSITY (MOSFET GPIO12)
// =====================================================
void setCH5Intensity(int duty, const char* level) {
  if (duty < 0) duty = 0;
  if (duty > 255) duty = 255;

  ch5Intensity = (int)round((float)duty * 100.0f / 255.0f);

  if (ch5State) {
    ledcWrite(MOSFET_CH5, duty);
  }

  Serial.print("CH5 SPEED / INTENSITY -> ");
  Serial.print(level);
  Serial.print(" | PWM = ");
  Serial.print(duty);
  Serial.print(" | ");
  Serial.print(ch5Intensity);
  Serial.println("%");
  Serial.flush();
}

// =====================================================
// COMMUNICATION HEALTH SUMMARY (Section 18)
// =====================================================
void printCommunicationHealthSummary() {
  int loadedAppliances = 0;
  for (int i = 0; i < 5; i++) {
    if (applianceIds[i].length() > 0) loadedAppliances++;
  }

  Serial.println();
  Serial.println("===== SEMHAS COMMUNICATION HEALTH =====");
  Serial.print("WIFI: ");
  Serial.println((WiFi.status() == WL_CONNECTED) ? "CONNECTED" : "DISCONNECTED");

  Serial.print("RSSI: ");
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("N/A");
    Serial.println("IP: N/A");
  }

  Serial.print("SUPABASE: ");
  Serial.println(supabaseReady ? "READY" : "NOT_READY");

  Serial.print("DEVICE_ID: ");
  Serial.println((deviceId.length() > 0) ? "LOADED" : "NOT_LOADED");

  Serial.print("APPLIANCES: ");
  Serial.print(loadedAppliances);
  Serial.println("/5");

  Serial.print("LAST_COMMAND_SYNC: ");
  if (lastCommandSyncTimestamp > 0) {
    Serial.print((millis() - lastCommandSyncTimestamp) / 1000);
    Serial.println("s ago");
  } else {
    Serial.println("NEVER");
  }

  Serial.print("LAST_TELEMETRY_UPLOAD: ");
  if (lastTelemetryUploadTimestamp > 0) {
    Serial.print((millis() - lastTelemetryUploadTimestamp) / 1000);
    Serial.println("s ago");
  } else {
    Serial.println("NEVER");
  }

  Serial.print("LAST_HEALTH_UPDATE: ");
  if (lastHealthUpdateTimestamp > 0) {
    Serial.print((millis() - lastHealthUpdateTimestamp) / 1000);
    Serial.println("s ago");
  } else {
    Serial.println("NEVER");
  }

  Serial.println("========================================");
  Serial.println();
  Serial.flush();
}

// =====================================================
// PRINT BOOT DIAGNOSTICS (Section 8)
// =====================================================
void printBootDiagnostics() {
  esp_reset_reason_t rstReason = esp_reset_reason();
  const char* rstReasonStr = getResetReasonString(rstReason);

  Serial.println("FIRMWARE_VERSION: 1.0.0");
  Serial.print("RESET_REASON: ");
  Serial.println(rstReasonStr);
  Serial.print("CHIP_MODEL: ");
  Serial.println(ESP.getChipModel());
  Serial.print("CHIP_CORES: ");
  Serial.println(ESP.getChipCores());
  Serial.print("CHIP_FREQUENCY_MHZ: ");
  Serial.println(ESP.getCpuFreqMHz());
  Serial.println("========================================");
  Serial.flush();
}

// =====================================================
// PRINT SERIAL HEARTBEAT (Section 9 - Zero network)
// =====================================================
void printSerialHeartbeat() {
  Serial.println("SERIAL_HEARTBEAT");
  Serial.print("UPTIME_MS: ");
  Serial.println(millis());
  Serial.print("FREE_HEAP: ");
  Serial.println(esp_get_free_heap_size());
  Serial.flush();
}

// =====================================================
// LOCAL SERIAL COMMAND HANDLER (Section 10 - Offline)
// =====================================================
void handleSerialCommands() {
  if (Serial.available()) {
    char command = Serial.read();

    if (command != '\n' && command != '\r') {
      Serial.print("SERIAL_COMMAND_RECEIVED: ");
      Serial.println(command);
      Serial.flush();

      if (command == 'I') {
        allChannelsON();
      } else if (command == 'O') {
        allChannelsOFF();
      } else if (command == 'A') {
        ch1State = true;
        setRelay(RELAY_CH1, true);
        applyRelayState(1, true, "LOCAL_SERIAL command=A");
        Serial.println("CH1 -> ON");
        Serial.flush();
      } else if (command == 'B') {
        ch2State = true;
        setRelay(RELAY_CH2, true);
        applyRelayState(2, true, "LOCAL_SERIAL command=B");
        Serial.println("CH2 -> ON");
        Serial.flush();
      } else if (command == 'C') {
        ch3State = true;
        setRelay(RELAY_CH3, true);
        applyRelayState(3, true, "LOCAL_SERIAL command=C");
        Serial.println("CH3 -> ON");
        Serial.flush();
      } else if (command == 'D') {
        ch4State = true;
        setRelay(RELAY_CH4, true);
        applyRelayState(4, true, "LOCAL_SERIAL command=D");
        Serial.println("CH4 -> ON");
        Serial.flush();
      } else if (command == 'E') {
        ch5State = true;
        setRelay(RELAY_CH5, true);
        applyRelayState(5, true, "LOCAL_SERIAL command=E");
        Serial.println("CH5 -> ON");
        Serial.flush();
      } else if (command == 'a') {
        ch1State = false;
        setRelay(RELAY_CH1, false);
        applyRelayState(1, false, "LOCAL_SERIAL command=a");
        Serial.println("CH1 -> OFF");
        Serial.flush();
      } else if (command == 'b') {
        ch2State = false;
        setRelay(RELAY_CH2, false);
        applyRelayState(2, false, "LOCAL_SERIAL command=b");
        Serial.println("CH2 -> OFF");
        Serial.flush();
      } else if (command == 'c') {
        ch3State = false;
        setRelay(RELAY_CH3, false);
        applyRelayState(3, false, "LOCAL_SERIAL command=c");
        Serial.println("CH3 -> OFF");
        Serial.flush();
      } else if (command == 'd') {
        ch4State = false;
        setRelay(RELAY_CH4, false);
        applyRelayState(4, false, "LOCAL_SERIAL command=d");
        Serial.println("CH4 -> OFF");
        Serial.flush();
      } else if (command == 'e') {
        ch5State = false;
        setRelay(RELAY_CH5, false);
        applyRelayState(5, false, "LOCAL_SERIAL command=e");
        Serial.println("CH5 -> OFF");
        Serial.flush();
      } else if (command == '1') {
        setCH4Speed(64, "25%");
      } else if (command == '2') {
        setCH4Speed(128, "50%");
      } else if (command == '3') {
        setCH4Speed(191, "75%");
      } else if (command == '4') {
        setCH4Speed(255, "100%");
      } else if (command == '5') {
        setCH5Intensity(64, "25%");
      } else if (command == '6') {
        setCH5Intensity(128, "50%");
      } else if (command == '7') {
        setCH5Intensity(191, "75%");
      } else if (command == '8') {
        setCH5Intensity(255, "100%");
      } else {
        Serial.println("Unknown command!");
        Serial.flush();
      }
    }
  }
}

// =====================================================
// PRINT INA219 READINGS (Only if channel ON)
// =====================================================
void printINA219Readings() {
  if (ch1State || ch2State || ch3State || ch4State || ch5State) {
    Serial.println();
    Serial.println("==========================================");
    Serial.println("          INA219 LIVE READINGS");
    Serial.println("==========================================");

    if (ina219Ch1Available) printINA219(ina219_CH1, "CH1", ch1State);
    if (ina219Ch2Available) printINA219(ina219_CH2, "CH2", ch2State);
    if (ina219Ch3Available) printINA219(ina219_CH3, "CH3", ch3State);
    if (ina219Ch4Available) printINA219(ina219_CH4, "CH4", ch4State);
    if (ina219Ch5Available) printINA219(ina219_CH5, "CH5", ch5State);

    Serial.println("==========================================");
    Serial.flush();
  }
}

// =====================================================
// NON-BLOCKING WI-FI SUBSYSTEM (DIRECT 2.4 GHz CONNECTION)
// =====================================================
void printWiFiFailureDiagnostic(wl_status_t status) {
  Serial.println("WIFI_CONNECT_FAILED");
  Serial.print("WIFI_STATUS_CODE: ");
  Serial.println((int)status);

  Serial.print("WIFI_STATUS_TEXT: ");
  switch (status) {
    case WL_NO_SSID_AVAIL:
      Serial.println("NO_SSID_AVAILABLE");
      break;
    case WL_CONNECT_FAILED:
      Serial.println("CONNECT_FAILED");
      break;
    case WL_CONNECTION_LOST:
      Serial.println("CONNECTION_LOST");
      break;
    case WL_DISCONNECTED:
      Serial.println("DISCONNECTED");
      break;
    case WL_IDLE_STATUS:
      Serial.println("IDLE");
      break;
    case WL_SCAN_COMPLETED:
      Serial.println("SCAN_COMPLETED");
      break;
    default:
      Serial.println("OTHER");
      break;
  }
  Serial.println("WIFI_RETRYING");
  Serial.flush();
}

void beginWiFiConnection() {
  Serial.println();
  Serial.println("WIFI_CONNECT_ATTEMPT");
  Serial.print("WIFI_TARGET_SSID: ");
  Serial.println(WIFI_SSID);
  Serial.flush();

  // Initialize Wi-Fi in STA mode without powering down radio
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  WiFi.setSleep(false);

  // Disconnect cleanly without turning off radio
  WiFi.disconnect(false, false);
  delay(100);

  // Directly initiate connection to target 2.4 GHz network
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  wifiState = WIFI_STATE_CONNECTING;
  wifiConnectStartMillis = millis();
  lastWiFiStatusPrint = 0;
}

void manageWiFi() {
  wl_status_t status = WiFi.status();

  // ==========================================
  // STATE: CONNECTED
  // ==========================================
  if (status == WL_CONNECTED) {
    if (wifiState != WIFI_STATE_CONNECTED) {
      wifiState = WIFI_STATE_CONNECTED;
      supabaseReady = false;
      lastSupabaseInitRetry = 0;

      Serial.println();
      Serial.println("WIFI_CONNECTED");
      Serial.print("WIFI_ACTIVE_SSID: ");
      Serial.println(WiFi.SSID());
      Serial.print("WIFI_IP: ");
      Serial.println(WiFi.localIP());
      Serial.print("WIFI_RSSI: ");
      Serial.println(WiFi.RSSI());
      Serial.print("WIFI_CHANNEL: ");
      Serial.println(WiFi.channel());
      Serial.flush();

      configTime(0, 0, "pool.ntp.org", "time.nist.gov", "time.google.com");

      // Immediately reload device + all 5 appliance UUIDs after Wi-Fi connection
      Serial.println("SUPABASE_REINIT_STARTED");
      Serial.flush();

      bool idsLoaded = loadSupabaseIds();

      if (idsLoaded) {
        supabaseReady = true;
        Serial.println("SUPABASE_READY");
        Serial.println("COMMAND_SYNC_RESTORED");
        Serial.println("TELEMETRY_UPLOAD_RESTORED");
        Serial.println("HEALTH_UPLOAD_RESTORED");
      } else {
        supabaseReady = false;
        Serial.println("SUPABASE_REINIT_PENDING_RETRY");
      }
      Serial.flush();
    }

    // Periodic Wi-Fi alive diagnostic while connected (Requirement 13)
    if (millis() - lastWiFiStatusPrint >= WIFI_STATUS_PRINT_INTERVAL) {
      lastWiFiStatusPrint = millis();
      Serial.print("WIFI_ALIVE: RSSI=");
      Serial.print(WiFi.RSSI());
      Serial.print(" IP=");
      Serial.println(WiFi.localIP());
      Serial.flush();
    }

    return;
  }

  // ==========================================
  // STATE: DISCONNECTED (was previously connected)
  // ==========================================
  if (wifiState == WIFI_STATE_CONNECTED) {
    wifiState = WIFI_STATE_DISCONNECTED;
    supabaseReady = false;

    Serial.println();
    Serial.println("WIFI_DISCONNECTED");
    printWiFiFailureDiagnostic(status);

    lastWiFiCheckMillis = millis();
    return;
  }

  // ==========================================
  // STATE: CONNECTING (10s maximum per attempt)
  // ==========================================
  if (wifiState == WIFI_STATE_CONNECTING) {
    if (millis() - wifiConnectStartMillis >= WIFI_CONNECT_TIMEOUT) {
      Serial.println();
      Serial.println("WIFI_CONNECT_TIMEOUT");
      Serial.print("WIFI_FAILED_SSID: ");
      Serial.println(WIFI_SSID);

      wifiState = WIFI_STATE_DISCONNECTED;
      wifiRetryCount++;
      lastWiFiCheckMillis = millis();

      printWiFiFailureDiagnostic(status);
    }
    return;
  }

  // ==========================================
  // STATE: DISCONNECTED (retry every 2 seconds)
  // ==========================================
  if (wifiState == WIFI_STATE_DISCONNECTED) {
    if (millis() - lastWiFiCheckMillis >= WIFI_RETRY_INTERVAL) {
      lastWiFiCheckMillis = millis();
      beginWiFiConnection();
    }
  }
}

// =====================================================
// MANAGE SUPABASE INITIALIZATION (Section 7)
// =====================================================
void manageSupabase() {
  if (!supabaseReady && WiFi.status() == WL_CONNECTED) {
    if (millis() - lastSupabaseInitRetry >= SUPABASE_INIT_RETRY_INTERVAL) {
      lastSupabaseInitRetry = millis();
      loadSupabaseIds();
    }
  }
}

// =====================================================
// SETUP (Section 8 - Zero blocking on network)
// =====================================================
void setup() {
  // 1. SERIAL INITIALIZATION (FIRST EXECUTABLE LINE)
  Serial.begin(115200);
  delay(300);
  Serial.println();
  Serial.println("========================================");
  Serial.println("SEMHAS ESP32 SERIAL BOOT");
  Serial.println("========================================");
  Serial.println("SERIAL_READY");
  Serial.flush();

  printBootDiagnostics();

  Serial.println("INIT_START");
  Serial.flush();

  // 2. RELAY GPIO INITIALIZATION
  pinMode(RELAY_CH1, OUTPUT);
  pinMode(RELAY_CH2, OUTPUT);
  pinMode(RELAY_CH3, OUTPUT);
  pinMode(RELAY_CH4, OUTPUT);
  pinMode(RELAY_CH5, OUTPUT);

  setRelay(RELAY_CH1, false);
  setRelay(RELAY_CH2, false);
  setRelay(RELAY_CH3, false);
  setRelay(RELAY_CH4, false);
  setRelay(RELAY_CH5, false);

  Serial.println("INIT_GPIO_OK");
  Serial.flush();

  // 3. PWM SETUP (ESP32 LEDC API)
  ledcAttach(MOSFET_CH4, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttach(MOSFET_CH5, PWM_FREQUENCY, PWM_RESOLUTION);

  ledcWrite(MOSFET_CH4, 0);
  ledcWrite(MOSFET_CH5, 0);

  Serial.println("INIT_PWM_OK");
  Serial.flush();

  // 4. I2C BUS 1 (SDA = GPIO21, SCL = GPIO22) with 50ms Timeout Protection
  I2C_BUS_1.begin(21, 22, 100000);
  I2C_BUS_1.setTimeOut(50); // Prevent bus lockup
  Serial.println("INIT_I2C_BUS1_OK");
  Serial.flush();

  // 5. I2C BUS 2 (SDA = GPIO18, SCL = GPIO19) with 50ms Timeout Protection
  I2C_BUS_2.begin(18, 19, 100000);
  I2C_BUS_2.setTimeOut(50); // Prevent bus lockup
  Serial.println("INIT_I2C_BUS2_OK");
  Serial.flush();

  // 6. INA219 SENSORS (Safe init with individual availability flags)
  if (ina219_CH1.begin(&I2C_BUS_1)) {
    ina219_CH1.setCalibration_32V_2A();
    ina219Ch1Available = true;
  } else {
    Serial.println("INA219_CH1_INIT_FAILED");
    Serial.flush();
  }

  if (ina219_CH2.begin(&I2C_BUS_1)) {
    ina219_CH2.setCalibration_32V_2A();
    ina219Ch2Available = true;
  } else {
    Serial.println("INA219_CH2_INIT_FAILED");
    Serial.flush();
  }

  if (ina219_CH3.begin(&I2C_BUS_1)) {
    ina219_CH3.setCalibration_32V_2A();
    ina219Ch3Available = true;
  } else {
    Serial.println("INA219_CH3_INIT_FAILED");
    Serial.flush();
  }

  if (ina219_CH4.begin(&I2C_BUS_1)) {
    ina219_CH4.setCalibration_32V_2A();
    ina219Ch4Available = true;
  } else {
    Serial.println("INA219_CH4_INIT_FAILED");
    Serial.flush();
  }

  if (ina219_CH5.begin(&I2C_BUS_2)) {
    ina219_CH5.setCalibration_32V_2A();
    ina219Ch5Available = true;
  } else {
    Serial.println("INA219_CH5_INIT_FAILED");
    Serial.flush();
  }

  Serial.println("INIT_INA219_OK");
  Serial.flush();

  // 7. SETUP FINISHED - Zero network blocking in setup()
  Serial.println("INIT_COMPLETE");
  Serial.flush();

  // 1. On boot, initialize Wi-Fi in STA mode
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  WiFi.setSleep(false);

  // 2. Directly initiate connection to configured 2.4 GHz SSID (zero blocking scan)
  wifiRetryCount = 0;
  lastWiFiCheckMillis = millis();
  beginWiFiConnection();

  // 8. Start Dedicated FreeRTOS Tasks
  // High-Priority Cloud Command Polling Task (Priority 3, Core 1)
  xTaskCreatePinnedToCore(
    commandTaskLoop,
    "CommandTask",
    10240,
    NULL,
    3,
    &commandTaskHandle,
    1
  );

  // Lower-Priority Telemetry & Health Upload Task (Priority 1, Core 1)
  xTaskCreatePinnedToCore(
    telemetryTaskLoop,
    "TelemetryTask",
    10240,
    NULL,
    1,
    &telemetryTaskHandle,
    1
  );
}

// =====================================================
// LOOP (Fully decoupled, independent schedulers)
// =====================================================
void loop() {
  // Hard proof that loop() has started
  static bool firstLoop = true;
  if (firstLoop) {
    firstLoop = false;
    Serial.println("LOOP_STARTED");
    Serial.flush();
  }

  // 1. SERIAL HEARTBEAT & LOOP_ALIVE (2s interval, NO network dependency)
  static unsigned long lastSerialHeartbeat = 0;
  if (millis() - lastSerialHeartbeat >= 2000) {
    lastSerialHeartbeat = millis();
    Serial.println("LOOP_ALIVE");
    printSerialHeartbeat();
  }

  // 2. LOCAL SERIAL COMMAND PROCESSOR (Section 8 - Always runs first)
  handleSerialCommands();

  // 3. LOCAL INA219 READINGS (1s interval on Serial, only if channels active)
  static unsigned long lastReading = 0;
  if (millis() - lastReading >= 1000) {
    lastReading = millis();
    printINA219Readings();
  }

  // 4. NON-BLOCKING WI-FI STATE MACHINE (Section 11)
  manageWiFi();

  // 5. MANAGE SUPABASE INITIALIZATION
  manageSupabase();

  // 6. PERIODIC COMMUNICATION HEALTH SUMMARY (12s interval)
  if (millis() - lastHealthSummaryMillis >= HEALTH_SUMMARY_INTERVAL) {
    lastHealthSummaryMillis = millis();
    printCommunicationHealthSummary();
  }
}