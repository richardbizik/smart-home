package sk.coroid.smarthome.settings.storage;

import android.content.Context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import sk.coroid.smarthome.settings.Device;

public class SettingsStorageManager {

    public static final String DEVICES_JSON_FILE = "devices.json";
    private ObjectMapper objectMapper;
    private Context context;

    public SettingsStorageManager(Context context) {
        this.objectMapper = new ObjectMapper();
        this.context = context;
    }

    public List<Device> readDevices() {
        try (FileInputStream fis = context.openFileInput(DEVICES_JSON_FILE)) {
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            return objectMapper.readValue(sb.toString(), new TypeReference<List<Device>>() {
            });
        } catch (IOException e) {
            if(e instanceof FileNotFoundException){
                File file = new File(context.getFilesDir(), DEVICES_JSON_FILE);
                try {
                    file.createNewFile();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
                readDevices();
            }
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void addDevice(Device device) {
        List<Device> devices = readDevices();
        devices.add(device);
        try (FileOutputStream fos = context.openFileOutput(DEVICES_JSON_FILE, Context.MODE_PRIVATE)) {
            fos.write(objectMapper.writeValueAsBytes(devices));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeDevice(Device device) {
        List<Device> devices = readDevices();
        devices.remove(device);
        try (FileOutputStream fos = context.openFileOutput(DEVICES_JSON_FILE, Context.MODE_PRIVATE)) {
            fos.write(objectMapper.writeValueAsBytes(devices));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Device getDevice(String bluetoothAddress){
        List<Device> devices = readDevices();
        return devices.stream().filter(device -> device.getDeviceBTAddress().equals(bluetoothAddress)).findAny().orElse(null);
    }

    public void toggleDeviceActive(Device device){
        List<Device> devices = readDevices();
        devices.get(devices.indexOf(device)).setEnabled(!device.isEnabled());
        try (FileOutputStream fos = context.openFileOutput(DEVICES_JSON_FILE, Context.MODE_PRIVATE)) {
            fos.write(objectMapper.writeValueAsBytes(devices));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
