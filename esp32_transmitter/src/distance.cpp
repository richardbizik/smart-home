#include <Arduino.h>

#define SOUND_SPEED 0.034
#define CM_TO_INCH 0.393701
#define TRIG 33
#define ECHO 25

const int DURATIONS_COUNT = 10;
long duration;
float distanceCm;
long latestDurations[DURATIONS_COUNT];

void performMeasurement() {
  // Clears the trigPin
  digitalWrite(TRIG, LOW);
  delayMicroseconds(2);
  // Sets the trigPin on HIGH state for 10 micro seconds
  digitalWrite(TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG, LOW);
  // get time from trigger to receiving echo
  duration = pulseIn(ECHO, HIGH);
  distanceCm = duration * SOUND_SPEED / 2;
  Serial.print("Distance cm: ");
  Serial.println(distanceCm);

  for (int i = 0; i < DURATIONS_COUNT; i++) {
    if (i == DURATIONS_COUNT - 1) {
      latestDurations[i] = distanceCm;
    } else {
      latestDurations[i] = latestDurations[i + 1];
    }
  }
}

int getDistance() {
  int sum = 0;
  for (int i = 0; i < DURATIONS_COUNT; i++) {
    sum += latestDurations[i];
  }
  return sum / DURATIONS_COUNT;
}

void setupDistanceMeter() {
  pinMode(TRIG, OUTPUT);
  pinMode(ECHO, INPUT);
	// initialize array of distances to first measurement
	int cm = getDistance();
  for (int i = 0; i < DURATIONS_COUNT; i++) {
    latestDurations[i] = cm;
  }
}

