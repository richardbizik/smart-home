#include "stdlib.h"
#include <Arduino.h>
#include <stdlib.h>
#include <string.h>

#define TRANSMIT_PIN 32

String getValue(String data, char separator, int index) {
  int found = 0;
  int strIndex[] = {0, -1};
  int maxIndex = data.length() - 1;

  for (int i = 0; i <= maxIndex && found <= index; i++) {
    if (data.charAt(i) == separator || i == maxIndex) {
      found++;
      strIndex[0] = strIndex[1] + 1;
      strIndex[1] = (i == maxIndex) ? i + 1 : i;
    }
  }

  return found > index ? data.substring(strIndex[0], strIndex[1]) : "";
}

void transmitBit(byte b, int length) {
  digitalWrite(TRANSMIT_PIN, b);
  delayMicroseconds(length);
}

int transmit(String command) {
  pinMode(TRANSMIT_PIN, OUTPUT);
  int bitlength = getValue(command, '|', 0).toInt();
  // delay between repeats in ms
  int repeatDelay = getValue(command, '|', 1).toInt();
  String code = getValue(command, '|', 2);

  Serial.println("Transmitting code:");
  Serial.println(code);
  Serial.println(bitlength);

  for (int k = 0; k < 10; k++) {
    for (int i = 0; i < code.length(); i++) {
      if (code.charAt(i) == '1') {
        transmitBit(HIGH, bitlength);
      } else {
        transmitBit(LOW, bitlength);
      }
    }
    Serial.println();
    digitalWrite(TRANSMIT_PIN, LOW);
    delay(repeatDelay);
  }
  digitalWrite(TRANSMIT_PIN, LOW);
  return 0;
}
