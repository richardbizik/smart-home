package sk.coroid.smarthome.dto;


import java.time.OffsetDateTime;


public class DHTDataDTO {
    private String sensorName;
    private Double temp;
    private Double hum;
    private Double batt;
    private OffsetDateTime date;

    public String getSensorName() {
        return sensorName;
    }

    public void setSensorName(String sensorName) {
        this.sensorName = sensorName;
    }

    public Double getTemp() {
        return temp;
    }

    public void setTemp(Double temp) {
        this.temp = temp;
    }

    public Double getHum() {
        return hum;
    }

    public void setHum(Double hum) {
        this.hum = hum;
    }

    public Double getBatt() {
        return batt;
    }

    public void setBatt(Double batt) {
        this.batt = batt;
    }

    public OffsetDateTime getDate() {
        return date;
    }

    public void setDate(OffsetDateTime date) {
        this.date = date;
    }
}
