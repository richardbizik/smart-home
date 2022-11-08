# smart-home
collection of tools for home IoT

## esp32\_transmitter
Based on ESP32 DEVKIT V1   
Used for controlling devices with 433MHz radio as well as checking if my garage door is closed and if there is anyone present in the garage.
Contains http server that provides api over 433MHz transmitter, ultrasound distance meter and PIR sensor.

## kids\_room\_sensor
Based on TTGO T-Display ESP32 CP2104   
Serves as a clock, sleep training alarm, temperature and humidity sensor using built in oled display.
Uses RGB led for signaling resting time periods for kids.

## mobile\_app
I use it to control 433MHz devices in my home as well monitor status of sensors.   
App has a persistent service running in the background that enables to open gates and garage when I am coming home 
automatically.

## Connecting it together
All these projects are connected together using [node red](https://github.com/node-red/node-red)
