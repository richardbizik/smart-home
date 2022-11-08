package sk.coroid.smarthome;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.ArraySet;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sk.coroid.smarthome.settings.Device;
import sk.coroid.smarthome.settings.SettingsActivity;
import sk.coroid.smarthome.settings.storage.SettingsStorageManager;

public class GateOpeningManager extends Service implements LocationListener {

    private static final String NOTIFICATION_CHANNEL_ID = "smart_home.permanence";

    private static final String TAG = GateOpeningManager.class.getSimpleName();
    public static final int NOTIFICATION_ID = 2;

    private static Location BASE_LOCATION = new Location("");

    private Location lastKnownLocation = null;

    private boolean isServiceRunning;
    private final IBinder serviceBinder = new RunServiceBinder();

    private LocationManager locationManager;
    private String connectedBluetoothDevice = null;
    private long currentInterval = Constants.BASE_OPEN_DISTANCE_INTERVAL_SMALL;
    private String currentRadius = null;
    NotificationManager notificationManager;
    private SmartHomeClient smartHomeClient;
    private String garageStatus = "";
    private int previousStatus = 0; // 1 - open, 2 - closed

    SettingsStorageManager settingsStorageManager;

    private List<LocationChangeListener> locationChangeListeners = new ArrayList<>();

    private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice bluetoothDevice = (BluetoothDevice) intent.getExtras().get(BluetoothDevice.EXTRA_DEVICE);
            handleBluetoothActions(intent.getAction(), bluetoothDevice.getAddress());
        }
    };

    private BroadcastReceiver deviceSettingsChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SettingsActivity.DEVICE_LIST_CHANGED)) {
                handleDeviceSettingsChanged();
            }
        }
    };

    public GateOpeningManager() {
        BASE_LOCATION.setLatitude(Secret.BASE_LAT);
        BASE_LOCATION.setLongitude(Secret.BASE_LON);
        settingsStorageManager = new SettingsStorageManager(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        smartHomeClient = new SmartHomeClient(this);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Creating service");
        }

        startMyOwnForeground();

        isServiceRunning = false;
        //bluetooth
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        startService();
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String channelName = "Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(chan);

        Notification notification = getNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
        startForeground(NOTIFICATION_ID, notification);
    }

    private Notification getNotification(String title) {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        // vibration and sound only if previous status was closed
        if (garageStatus.contains("open") && previousStatus==2) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(VibrationEffect.createWaveform(new long[]{1000,350}, -1));
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            notificationBuilder.setSound(alarmSound);
        }
        return notificationBuilder.setOngoing(true)
                .setContentTitle(title)
                .setContentText(garageStatus + "\n" + currentRadius)
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.ic_house)
                .build();
    }

    private void handleDeviceSettingsChanged() {

    }

    public void registerLocationChangeListener(LocationChangeListener locationChangeListener) {
        this.locationChangeListeners.add(locationChangeListener);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location lastKnownLocationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastKnownLocationGSM = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location lastKnownLocation = null;
        if (lastKnownLocationGPS != null && lastKnownLocationGSM != null) {
            lastKnownLocation = lastKnownLocationGPS.getTime() + 30 * 1000 < lastKnownLocationGSM.getTime() ? lastKnownLocationGSM : lastKnownLocationGPS;
        } else if (lastKnownLocationGPS != null) {
            lastKnownLocation = lastKnownLocationGPS;
        } else if (lastKnownLocationGSM != null) {
            lastKnownLocation = lastKnownLocationGSM;
        }
        if (lastKnownLocation != null) {
            onLocationChanged(lastKnownLocation);
        }
    }

    public void removeLocationChangeListener(LocationChangeListener locationChangeListener) {
        this.locationChangeListeners.remove(locationChangeListener);
    }

    private void openGates(BluetoothDeviceAction action) {
        if (connectedBluetoothDevice != null) {
            try {
                Log.println(Log.DEBUG, TAG, "----------------OPENING-----------------");
                String command = getCommandForDevice(connectedBluetoothDevice, action);
                if (command == null) {
                    Toast.makeText(this, "Device needs to be set", Toast.LENGTH_LONG).show();
                    return;
                }
                changeNotification("Opening Gates for action: " + action.name());
                sendCommand(command);
            } catch (Exception ex) {
                changeNotification("Could not open gates");
                Toast.makeText(this, "Could not send command", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getCommandForDevice(String connectedBluetoothDeviceAddress, BluetoothDeviceAction enteredBase) {
        Device device = settingsStorageManager.getDevice(connectedBluetoothDeviceAddress);
        if (device != null && device.isEnabled()) {
            return connectedBluetoothDeviceAddress + "|" + enteredBase.name();
        } else {
            return null;
        }
    }

    private void handleBluetoothActions(String action, String address) {
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            Device device = settingsStorageManager.getDevice(address);
            if (device.isEnabled()) {
                Log.d(TAG, "BT device connected: " + address);
                connectedBluetoothDevice = address;
                handleDeviceConnected();
                requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_SMALL);
            }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            Log.println(Log.DEBUG, TAG, "BT device disconnected");
            if (address.equals(connectedBluetoothDevice)) {
                handleDeviceDisconnected();
                connectedBluetoothDevice = null;
            }
            locationManager.removeUpdates(this);
        }
    }

    private void handleDeviceDisconnected() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location lastKnownLocationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastKnownLocationGSM = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location lastKnownLocation = lastKnownLocationGPS.getTime() + 30 * 1000 < lastKnownLocationGSM.getTime() ? lastKnownLocationGSM : lastKnownLocationGPS;
        if (lastKnownLocation != null
                && lastKnownLocation.getTime() + 10 * 1000 < System.currentTimeMillis() // check if lastKnownLocation is fresh
                && lastKnownLocation.distanceTo(BASE_LOCATION) < Constants.BASE_OPEN_DISTANCE_SMALL) {
            openGates(BluetoothDeviceAction.LEFT_VEHICLE);
        }
    }

    private void handleDeviceConnected() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location lastKnownLocationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastKnownLocationGSM = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location lastKnownLocation = lastKnownLocationGPS.getTime() + 30 * 1000 < lastKnownLocationGSM.getTime() ? lastKnownLocationGSM : lastKnownLocationGPS;
        if (lastKnownLocation != null
                && lastKnownLocation.getTime() + 10 * 1000 < System.currentTimeMillis() // check if lastKnownLocation is fresh
                && lastKnownLocation.distanceTo(BASE_LOCATION) < Constants.BASE_OPEN_DISTANCE_SMALL) {
            openGates(BluetoothDeviceAction.ENTERED_VEHICLE);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.println(Log.DEBUG, TAG, "Starting service");
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Binding service");
        }
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Destroying service");
        }
    }

    public void startService() {
        if (!isServiceRunning) {
            isServiceRunning = true;
        } else {
            Log.e(TAG, "startService request for an already running Service");
        }
    }

    public void stopService() {
        if (isServiceRunning) {
            isServiceRunning = false;
        } else {
            Log.e(TAG, "stopTimer request for a timer that isn't running");
        }
    }

    public boolean isServiceRunning() {
        return isServiceRunning;
    }

    private int commandRetry = 0;
    private String commandMessage = null;

    public void sendCommand(String message) {
        if (!canSendCommand()) {
            commandResult(Boolean.FALSE);
            return;
        }
        commandRetry = 0;
        commandMessage = message;
        sendHttpCommand(message);
    }

    public void sendHttpCommand(String message) {
        Log.d(TAG, "sending Http command: " + message + " iteration: " + commandRetry);
        commandRetry++;
        smartHomeClient.sendCommand(message, aBoolean -> {
            commandResult(aBoolean);
            return null;
        });
    }

    private void commandResult(Boolean result) {
        if (Boolean.FALSE.equals(result) && commandRetry < Constants.MAX_COMMAND_RETRY) {
            final Handler h = new Handler();
            h.postDelayed(() -> {
                sendHttpCommand(commandMessage);
            }, Constants.COMMAND_WAIT);
        } else if (Boolean.FALSE.equals(result) && commandRetry == Constants.MAX_COMMAND_RETRY) {
            notifyFail();
        } else if (Boolean.TRUE.equals(result)) {
            notifySuccess();
        }
    }

    private void notifySuccess() {
        changeNotification("Command was sent successfully");
        MediaPlayer mp = MediaPlayer.create(this, R.raw.success);
        mp.start();
    }

    private void notifyFail() {
        changeNotification("Could not send command");
        MediaPlayer mp = MediaPlayer.create(this, R.raw.error);
        mp.start();
    }

    private void changeNotification(String text) {
        notificationManager.notify(NOTIFICATION_ID, getNotification(text));
        final Handler h = new Handler();
        h.postDelayed(() -> {
            notificationManager.notify(NOTIFICATION_ID, getNotification(Constants.NOTIFICATION_RUNNING_MESSAGE));
        }, Constants.NOTIFICATION_STATE_TRANSFER_DELAY);
    }

    public void requestLocationUpdate() {
        Log.println(Log.DEBUG, TAG, String.format("Requesting new location update"));
        if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, this.getMainLooper());
    }

    private void requestLocationUpdates(long interval) {
        Log.println(Log.DEBUG, TAG, String.format("Requesting new location updates with interval: %s", interval));
        if (ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        currentInterval = interval;
        locationManager.removeUpdates(this);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 5, this);
    }

    public void setGarageStatus(String s, boolean updateNotification) {
        if (this.garageStatus.contains("open")){
            this.previousStatus = 1;
        } else {
            this.previousStatus = 2;
        }
        this.garageStatus = s;
        if (updateNotification){
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location newLocation) {
        Log.println(Log.DEBUG, TAG, "Received location: " + newLocation.getLatitude() + ", " + newLocation.getLongitude());
        if (lastKnownLocation == null) {
            lastKnownLocation = newLocation;
        }
        if (lastKnownLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_SMALL
                && newLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_SMALL) {
            //if we entered small perimeter
            currentRadius = "Inside base";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            if (connectedBluetoothDevice != null) {
                openGates(BluetoothDeviceAction.ENTERED_BASE);
            }
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_SMALL);
        } else if (lastKnownLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_LARGE
                && newLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_LARGE) {
            //if we left large perimeter
            currentRadius = "Outside of large perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_LARGE);
        } else if (lastKnownLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_MEDIUM
                && newLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_MEDIUM) {
            //if we left medium perimeter
            currentRadius = "Left medium perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_MEDIUM);
        } else if (lastKnownLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_SMALL
                && newLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_SMALL) {
            //if we left small perimeter
            currentRadius = "Left small perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            if (connectedBluetoothDevice != null) {
                openGates(BluetoothDeviceAction.LEAVING_BASE);
            }
        } else if (lastKnownLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_MEDIUM
                && newLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_MEDIUM) {
            //if we entered medium perimeter
            currentRadius = "Entered medium perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_SMALL);
        } else if (lastKnownLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_LARGE
                && newLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_LARGE) {
            //if we entered large perimeter
            currentRadius = "Entered large perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_MEDIUM);
        } else if (currentInterval < Constants.BASE_OPEN_DISTANCE_INTERVAL_LARGE
                && newLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_LARGE) {
            //if we entered vehicle outside of large perimeter
            currentRadius = "Outside of large perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_LARGE);
        } else if (currentInterval < Constants.BASE_OPEN_DISTANCE_INTERVAL_MEDIUM
                && newLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_MEDIUM) {
            //if we entered vehicle but outside of medium
            currentRadius = "Outside of medium perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_MEDIUM);
        } else if (newLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_SMALL) {
            //we are inside base
            currentRadius = "Inside base";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
        } else if (newLocation.distanceTo(BASE_LOCATION) > Constants.BASE_OPEN_DISTANCE_SMALL) {
            //we are inside base
            currentRadius = "Outside base";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
        }
        lastKnownLocation = newLocation;
        for (LocationChangeListener locationChangeListener : locationChangeListeners) {
            locationChangeListener.locationChanged(currentRadius);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }


    public boolean canSendCommand() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        return lastKnownLocation != null && BASE_LOCATION.distanceTo(lastKnownLocation) < Constants.MAX_ALLOWED_DISTANCE;
    }


    public class RunServiceBinder extends Binder {
        GateOpeningManager getService() {
            return GateOpeningManager.this;
        }
    }

}
