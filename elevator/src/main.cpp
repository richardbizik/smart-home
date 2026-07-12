#include <AccelStepper.h>
#include <Arduino.h>

// Defines pins numbers
const int stepPin = 3;
const int dirPin = 2;
const int level3Pin = 12;
const int level2Pin = 11;
const int level1Pin = 10;
#define motorInterfaceType 1

int currLevel = 1;
int nextLevel = 0;

const int level3 = 1900;
const int level2 = 1000;
const int level1 = 0;
const int top = 2300;
const int baseFromTop = top-1900;

// Creates an instance
AccelStepper myStepper(motorInterfaceType, stepPin, dirPin);

void setup() {
  Serial.begin(115200);
  pinMode(level3Pin, INPUT_PULLUP);
  pinMode(level2Pin, INPUT_PULLUP);
  pinMode(level1Pin, INPUT_PULLUP);
  // set the maximum speed, acceleration factor,
  // initial speed and the target position
  myStepper.setCurrentPosition(0);
  myStepper.setMaxSpeed(400);
  myStepper.setAcceleration(600);
  myStepper.setSpeed(400);
  myStepper.moveTo(top);
  int initStep = 0;
  while (true) {
    if (initStep == 0 && myStepper.currentPosition() == top) {
      initStep = 1;
      myStepper.moveTo(baseFromTop);
    }
    if (initStep == 1 && myStepper.currentPosition() == baseFromTop) {
      initStep = 2;
    }
    if (initStep == 2) {
			break;
    }
		Serial.println(myStepper.currentPosition());
    myStepper.run();
  }
  myStepper.setCurrentPosition(0);
  myStepper.setMaxSpeed(300);
  myStepper.setAcceleration(200);
  myStepper.setSpeed(300);
}

void loop() {
  // Move the motor one step
  // Change direction once the motor reaches target position
  int button3 = digitalRead(level3Pin);
  int button2 = digitalRead(level2Pin);
  int button1 = digitalRead(level1Pin);
  if (button3 == 0 && nextLevel != 3) {
    nextLevel = 3;
    myStepper.moveTo(level3);
  }
  if (button2 == 0 && nextLevel != 2) {
    nextLevel = 2;
    myStepper.moveTo(level2);
  }
  if (button1 == 0 && nextLevel != 1) {
    nextLevel = 1;
    myStepper.moveTo(level1);
  }
  myStepper.run();
}
