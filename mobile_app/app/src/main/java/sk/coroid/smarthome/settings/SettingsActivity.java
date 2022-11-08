package sk.coroid.smarthome.settings;

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.util.Function;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import sk.coroid.smarthome.R;
import sk.coroid.smarthome.settings.storage.SettingsStorageManager;

public class SettingsActivity extends AppCompatActivity {

    public static final String DEVICE_LIST_CHANGED = "DEVICE_LIST_CHANGED";
    public static final String ACTIVE_DEVICES = "ACTIVE_DEVICES";
    public static final String SMART_HOME_APP_SETTINGS = "smart_home_app_settings";

    DeviceListAdapter deviceListAdapter;
    List<Device> devices;
    SettingsStorageManager settingsStorageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        setTitle(R.string.settings_name);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        settingsStorageManager = new SettingsStorageManager(this);
        devices = getDevicesFromSettings();
        deviceListAdapter = new DeviceListAdapter(this, devices, new BiFunction<Boolean, Device, Void>() {
            //on list item checked
            @Override
            public Void apply(Boolean isChecked, Device device) {
                settingsStorageManager.toggleDeviceActive(device);
                notifyDevicesChange();
                return null;
            }
        }, new Function<Device, Void>() {
            // on list item deleted
            @Override
            public Void apply(Device input) {
                settingsStorageManager.removeDevice(input);
                notifyDevicesChange();
                return null;
            }
        });
        ListView devicesList = findViewById(R.id.deviceList);

        devicesList.setAdapter(deviceListAdapter);

        Button addDeviceButton = findViewById(R.id.add_device_button);
        addDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewDevice();
            }
        });
    }

    private void addNewDevice() {
        final Dialog dialog = new Dialog(this);
        LinearLayout layout = new LinearLayout(this);
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager.getAdapter() != null) {
            final Set<BluetoothDevice> bluetoothDevices = bluetoothManager.getAdapter().getBondedDevices();
            List<Device> currentDevices = getDevicesFromSettings();
            ListView listView = new ListView(this);
            List<Device> pairedDevicesList = new ArrayList<>();
            for (final BluetoothDevice bluetoothDevice : bluetoothDevices) {
                if (currentDevices.stream().noneMatch(btDevice -> btDevice.getDeviceBTAddress().equals(bluetoothDevice.getAddress()))) {
                    pairedDevicesList.add(new Device(bluetoothDevice.getName(), bluetoothDevice.getAddress(), false));
                }
            }
            listView.setAdapter(new ReadOnlyDeviceListAdapter(this, pairedDevicesList));
            listView.setOnItemClickListener((parent, view, position, id) -> {
                Device device = pairedDevicesList.get(position);
                addDevice(device.getDeviceName(), device.getDeviceBTAddress(), dialog);
            });
            layout.addView(listView);
        } else {
            layout.setOrientation(LinearLayout.VERTICAL);
            final EditText titleBox = new EditText(this);
            titleBox.setHint("Názov");
            layout.addView(titleBox);
            final EditText addressBox = new EditText(this);
            addressBox.setHint("Bluetooth address");
            layout.addView(addressBox);
            final Button ok = new Button(this);
            ok.setText(android.R.string.ok);
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addDevice(titleBox.getText().toString(), addressBox.getText().toString(), dialog);
                }
            });
            layout.addView(ok);
        }
        final Button cancel = new Button(this);
        cancel.setText(android.R.string.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        layout.addView(cancel);

        dialog.setContentView(layout);
        dialog.show();
    }

    private void addDevice(String name, String bluetoothAddress, Dialog dialog) {
        settingsStorageManager.addDevice(new Device(name, bluetoothAddress, false));
        dialog.dismiss();
        notifyDevicesChange();
    }

    private void notifyDevicesChange() {
        devices.clear();
        devices.addAll(getDevicesFromSettings());
        deviceListAdapter.notifyDataSetChanged();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(DEVICE_LIST_CHANGED);
        localBroadcastManager.sendBroadcast(intent);
    }

    private List<Device> getDevicesFromSettings() {
        return settingsStorageManager.readDevices();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}