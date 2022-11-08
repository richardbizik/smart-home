package sk.coroid.smarthome.settings;

import java.util.Objects;

public class Device {
    private String deviceName;
    private String deviceBTAddress;
    private boolean enabled;

    public Device(){

    }

    public Device(String deviceName, String deviceBTAddress, boolean enabled) {
        this.deviceName = deviceName;
        this.deviceBTAddress = deviceBTAddress;
        this.enabled = enabled;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceBTAddress() {
        return deviceBTAddress;
    }

    public void setDeviceBTAddress(String deviceBTAddress) {
        this.deviceBTAddress = deviceBTAddress;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return deviceBTAddress.equals(device.deviceBTAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceBTAddress);
    }
}
