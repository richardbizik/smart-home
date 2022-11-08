#include <Arduino.h>

#define PIR 35
void setupPIR() { pinMode(PIR, INPUT); }

bool getPresence() {
  bool val = digitalRead(PIR);
  Serial.println("PIR value:");
  Serial.println(val);
  return val;
}
