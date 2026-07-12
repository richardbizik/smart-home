#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <distance.h>
#include <esp_http_server.h>
#include <presence.h>
#include <secret.h>
#include <server.h>
#include <string>
#include <sys/param.h>
#include <transmit.h>

#define LED 2
WiFiServer server(80);

void setup() {
  Serial.begin(115200);
  pinMode(LED, OUTPUT);

  WiFi.begin(network_ssid, network_password);

  while (WiFi.status() != WL_CONNECTED) {
    Serial.println("Waiting for connection...");
    delay(300);
  }

  Serial.println("");
  Serial.println("WiFi connected.");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());

  setupDistanceMeter();
  setupPIR();
  start_webserver();
}

bool network_connected = false;

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    if (!network_connected) {
      digitalWrite(LED, HIGH);
    }
    network_connected = true;
  } else if (network_connected) {
    digitalWrite(LED, LOW);
    network_connected = false;
    WiFi.begin(network_ssid, network_password);
    while (WiFi.status() != WL_CONNECTED) {
      Serial.println("Waiting for connection...");
      delay(300);
    }
  }
  performMeasurement();
  delay(1000);
}
