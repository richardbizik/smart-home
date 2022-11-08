#include <Arduino.h>
#include <DHT.h> //https://github.com/adafruit/DHT-sensor-library
#include <esp_adc_cal.h>
#include <HTTPClient.h>
#include <NTPClient.h> //https://github.com/taranais/NTPClient
#include <SPI.h>
#include <TFT_eSPI.h> // https://github.com/Bodmer/TFT_eSPI
#include <TimeLib.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <analogWrite.h>
#include <driver/adc.h>
#include <secret.h>
#include <timezones.h>

#define TFT_GREY 0x5AEB
#define lightblue 0x01E9
#define red 0xF800
#define blue 0x001F
#define LEFT_BUTTON 0
#define RIGHT_BUTTON 35
#define DHT_PIN 15
#define DHT_TYPE DHT22
#define uS_TO_S_FACTOR 1000000
#define TIME_TO_SLEEP 1800 // 30m (in seconds)
#define Threshold 40       // treshold for wakup pin

// RGB LED
#define PIN_RED 27
#define PIN_GREEN 26
#define PIN_BLUE 25

TFT_eSPI tft = TFT_eSPI(); // Invoke custom library
DHT dht(DHT_PIN, DHT_TYPE);
// Define NTP Client to get time
WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "europe.pool.ntp.org", 0, 60000);

const int pwmFreq = 5000;
const int pwmResolution = 8;
const int pwmLedChannelTFT = 0;

String payload = ""; // whole json

// Variables to save date and time
String formattedDate;
String dayStamp;
String timeStamp;

int backlight[5] = {0, 10, 60, 120, 220};
byte b = 1;
unsigned long lastWakeUp = 0;

bool manualWakeUp = 0;

void callback() { Serial.println("callback called"); }

void setup(void) {
  // LED
  pinMode(PIN_RED, OUTPUT);
  pinMode(PIN_GREEN, OUTPUT);
  pinMode(PIN_BLUE, OUTPUT);

  pinMode(0, INPUT_PULLUP);
  pinMode(35, INPUT);
  tft.init();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setTextSize(1);
  dht.begin();
  ledcSetup(pwmLedChannelTFT, pwmFreq, pwmResolution);
  ledcAttachPin(TFT_BL, pwmLedChannelTFT);
  ledcWrite(pwmLedChannelTFT, backlight[b]);

  // setup battery level read
  esp_adc_cal_characteristics_t adc_chars;
  esp_adc_cal_value_t val_type = esp_adc_cal_characterize(
      (adc_unit_t)ADC_UNIT_1, (adc_atten_t)ADC_ATTEN_DB_2_5,
      (adc_bits_width_t)ADC_WIDTH_BIT_12, 1100, &adc_chars);
  pinMode(14, OUTPUT);

  Serial.begin(115200);
  tft.print("Connecting to ");
  tft.println(ssid);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    tft.print(".");
  }

  tft.println("");
  tft.println("WiFi connected.");
  tft.println("IP address: ");
  tft.println(WiFi.localIP());
  delay(3000);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setTextSize(1);
  tft.fillScreen(TFT_BLACK);
  tft.setSwapBytes(true);

  tft.setCursor(2, 232, 1);
  tft.println(WiFi.localIP());
  tft.setCursor(80, 204, 1);
  tft.println("BRIGHT:");

  tft.setRotation(2);
  tft.setFreeFont(&Orbitron_Medium_20);
  tft.setCursor(10, 30);
  tft.println(place);

  tft.setRotation(1);

  tft.setFreeFont(&Orbitron_Light_32);
  tft.setTextColor(red);
  tft.setCursor(10, 120);
  tft.println("C");

  tft.setTextColor(blue);
  tft.setCursor(120, 120);
  tft.println("%");
  tft.setFreeFont(&Orbitron_Medium_20);
  tft.setTextColor(TFT_WHITE);

  tft.fillRect(68, 152, 1, 74, TFT_GREY);

  for (int i = 0; i < b + 1; i++)
    tft.fillRect(78 + (i * 7), 216, 3, 10, blue);

  // Initialize a NTPClient to get time
  timeClient.begin();
  timeClient.setTimeOffset(0);
  while (!timeClient.update()) {
    timeClient.forceUpdate();
  }
  lastWakeUp = timeClient.getEpochTime();
  // getData();
  delay(500);
}
int i = 0;
String tt = "";
int count = 0;
bool inv = 1;

bool pressRight = 0;
bool pressLeft = 0;

void setSleep() {
  analogWrite(PIN_RED, 255);
  analogWrite(PIN_GREEN, 0);
  analogWrite(PIN_BLUE, 0);
}

void setWakeup() {
  analogWrite(PIN_RED, 0);
  analogWrite(PIN_GREEN, 255);
  analogWrite(PIN_BLUE, 0);
}

void setOff() {
  analogWrite(PIN_RED, 0);
  analogWrite(PIN_GREEN, 0);
  analogWrite(PIN_BLUE, 0);
}

void getSensorData() {
  digitalWrite(14, HIGH);
  delay(10);
  float measurement = (float)analogRead(34);
  float battery_voltage = (measurement / 4095.0) * 7.05;
  digitalWrite(14, LOW);
  tft.fillRect(6, 60, 90, 20, TFT_BLACK);
  tft.setTextFont(2);
  tft.setCursor(6, 60);
  tft.printf("Battery: %.2fV", battery_voltage);
  tft.setTextColor(TFT_WHITE);
  Serial.println(battery_voltage);

  float t = dht.readTemperature();
  float h = dht.readHumidity();
  Serial.println(t);
  Serial.println(h);

  if (isnan(h) || isnan(t)) {
    Serial.println(F("Failed to read from DHT sensor!"));
    return;
  }

  tft.fillRect(40, 95, 60, 30, TFT_BLACK);
  tft.setFreeFont(&Orbitron_Medium_20);
  tft.setCursor(40, 120);
  tft.printf("%.1f", t);

  tft.fillRect(160, 95, 60, 30, TFT_BLACK);
  tft.setCursor(160, 120);
  tft.printf("%.1f", h);
  tft.setTextColor(TFT_WHITE);
}

String pad(int val) {
  char buf[3];
  if (val < 10) {
    sprintf(buf, "0%d", val);
    return buf;
  }
  return String(val);
}

void loop() {
  if (digitalRead(RIGHT_BUTTON) == 0) {
    if (pressRight == 0) {
      pressRight = 1;
      // do the logic
    }
  } else {
    pressRight = 0;
  }

  if (digitalRead(LEFT_BUTTON) == 0) {
    if (pressLeft == 0) {
      pressLeft = 1;
      // do the logic
    }
  } else {
    pressLeft = 0;
  }

  if (count == 0) {
    getSensorData();
  }

  count++;
  if (count > 600) {
    count = 0;
  }

  unsigned long timeNow = timeClient.getEpochTime();
  time_t zdt = getDateTime(timeNow);
  dayStamp = "";
  dayStamp.concat(day(zdt));
  dayStamp.concat(".");
  dayStamp.concat(month(zdt));
  dayStamp.concat(".");
  dayStamp.concat(year(zdt));

  timeStamp = "";
  timeStamp.concat(pad(hour(zdt)));
  timeStamp.concat(":");
  timeStamp.concat(pad(minute(zdt)));
  timeStamp.concat(":");
  timeStamp.concat(pad(second(zdt)));

  tft.setFreeFont(&Orbitron_Light_32);
  if (timeStamp != tt) {
    tft.fillRect(3, 8, 190, 30, TFT_BLACK);
    tft.setCursor(5, 34);
    tft.println(timeStamp);
    tt = timeStamp;

    tft.setTextColor(TFT_ORANGE, TFT_BLACK);
    tft.setTextFont(2);
    tft.setCursor(6, 44);
    tft.println(dayStamp);
    tft.setTextColor(TFT_WHITE, TFT_BLACK);
  }

  Serial.printf("Time now %ld\n", zdt);
  Serial.printf("Hour now %d\n", hour(zdt));

  if (hour(zdt) == 19 && minute(zdt) > 30) {
    setSleep();
  } else if (hour(zdt) > 19 || hour(zdt) < 7) {
    setSleep();
  } else if (hour(zdt) == 7) {
    setWakeup();
  } else if (hour(zdt) >= 12 && hour(zdt) < 13) {
    setSleep();
  } else if (hour(zdt) == 13) {
    setWakeup();
  } else {
    setOff();
  }
  delay(1000);
}
