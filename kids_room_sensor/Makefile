all:
	pio project init --ide vim --board ttgo-lora32-v1
	pio -f -c vim run

upload:
	pio -f -c vim run --target upload --upload-port /dev/ttyUSB1

clean:
	pio -f -c vim run --target clean

program:
	pio -f -c vim run --target program

uploadfs:
	pio -f -c vim run --target uploadfs

update:
	pio -f -c vim update

monitor:
	@stty -F /dev/ttyUSB1 raw 115200
	@cat /dev/ttyUSB1

