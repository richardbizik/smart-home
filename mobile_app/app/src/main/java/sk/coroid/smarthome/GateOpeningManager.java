package sk.coroid.smarthome;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
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

public class GateOpeningManager extends Service {

    private static final String NOTIFICATION_CHANNEL_ID = "smart_home.permanence";

    private static final String TAG = GateOpeningManager.class.getSimpleName();
    public static final int NOTIFICATION_ID = 2;

    public static final String ACTION_GEOFENCE_TRANSITION = "sk.coroid.smarthome.GEOFENCE_TRANSITION";
    public static final String EXTRA_GEOFENCE_TRANSITION = "geofence_transition";
    private static final String HOME_GEOFENCE_ID = "home";
    /** Ignore an identical auto-open action fired again within this window (double-fire guard). */
    private static final long OPEN_GATES_DEBOUNCE = 5 * 1000;

    private static Location BASE_LOCATION = new Location("");

    /** Previous fix, owned exclusively by the perimeter state machine in onLocationChanged. */
    private Location previousLocation = null;
    /** Latest fix, refreshed by the fused callback; read by canSendCommand/getBestLastKnownLocation. */
    private Location cachedLocation = null;

    private boolean isServiceRunning;
    private final IBinder serviceBinder = new RunServiceBinder();

    private FusedLocationProviderClient fusedLocationClient;
    private GeofencingClient geofencingClient;
    private LocationCallback locationCallback;
    private PendingIntent geofencePendingIntent;

    private BluetoothDeviceAction lastOpenAction = null;
    private long lastOpenTime = 0;

    private String connectedBluetoothDevice = null;
    private long currentInterval = Constants.BASE_OPEN_DISTANCE_INTERVAL_SMALL;
    private String currentRadius = "";
    NotificationManager notificationManager;
    private SmartHomeClient smartHomeClient;
    private String garageStatus = "";

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) {
                    onLocationChanged(loc);
                }
            }
        };
        seedLastLocation();          // async getLastLocation() to warm cachedLocation
        restoreConnectedBluetooth(); // restore BT state so the geofence backup fires after a kill
        registerHomeGeofence();      // backup trigger that survives backgrounding/kill
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
        // No vibration here: this is rebuilt on every location fix / geofence event, so any
        // side effect here fires constantly. The garage-open buzz lives in setGarageStatus().
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        return notificationBuilder.setOngoing(true)
                .setContentTitle(title)
                .setContentText(buildNotificationBody())
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.ic_house)
                .build();
    }

    /**
     * Joins the garage status and current perimeter into the notification body,
     * skipping any part that is empty so we never render a stray blank line or "null".
     */
    private String buildNotificationBody() {
        StringBuilder sb = new StringBuilder();
        if (garageStatus != null && !garageStatus.isEmpty()) {
            sb.append(garageStatus);
        }
        if (currentRadius != null && !currentRadius.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(currentRadius);
        }
        return sb.toString();
    }

    private void handleDeviceSettingsChanged() {

    }

    public void registerLocationChangeListener(LocationChangeListener locationChangeListener) {
        if (this.locationChangeListeners.size() == 1){
            this.locationChangeListeners = new ArrayList<>();
        }
        this.locationChangeListeners.add(locationChangeListener);

        Location lastKnownLocation = getBestLastKnownLocation();
        if (lastKnownLocation != null) {
            onLocationChanged(lastKnownLocation);
        }
    }

    public void removeLocationChangeListener(LocationChangeListener locationChangeListener) {
        this.locationChangeListeners.remove(locationChangeListener);
    }

    private void openGates(BluetoothDeviceAction action) {
        if (connectedBluetoothDevice != null) {
            // Debounce: the fused perimeter machine and the geofence can both fire the same
            // action on arrival/departure while the app is alive. Ignore an identical repeat.
            long now = System.currentTimeMillis();
            if (action == lastOpenAction && now - lastOpenTime < OPEN_GATES_DEBOUNCE) {
                Log.d(TAG, "Debounced duplicate openGates action: " + action.name());
                return;
            }
            lastOpenAction = action;
            lastOpenTime = now;
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
            if (device != null && device.isEnabled()) {
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
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    /**
     * On (re)create the ACL_CONNECTED broadcast has already fired for any device that was
     * connected before the service started, so {@link #connectedBluetoothDevice} is null.
     * Query the currently-connected classic audio profiles (car head units) and restore it,
     * so a geofence-triggered service after an app kill still knows it is "in the car".
     */
    private void restoreConnectedBluetooth() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return;
        }
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return;
        }
        BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                try {
                    for (BluetoothDevice device : proxy.getConnectedDevices()) {
                        Device configured = settingsStorageManager.getDevice(device.getAddress());
                        if (configured != null && configured.isEnabled()) {
                            connectedBluetoothDevice = device.getAddress();
                            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_SMALL);
                            break;
                        }
                    }
                } catch (SecurityException ignored) {
                    // permission revoked between the check and the callback
                } finally {
                    adapter.closeProfileProxy(profile, proxy);
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
            }
        };
        adapter.getProfileProxy(this, listener, BluetoothProfile.A2DP);
        adapter.getProfileProxy(this, listener, BluetoothProfile.HEADSET);
    }

    /** True when fine or coarse location permission is granted. */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns the latest cached fix (kept fresh by the fused location callback),
     * or {@code null} if no permission or no fix has arrived yet.
     */
    private Location getBestLastKnownLocation() {
        if (!hasLocationPermission()) {
            return null;
        }
        return cachedLocation;
    }

    /** Warms {@link #cachedLocation} with the last known fix on service start. */
    private void seedLastLocation() {
        if (!hasLocationPermission()) {
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null && cachedLocation == null) {
                        cachedLocation = loc;
                    }
                });
    }

    /** True when the fix is recent enough to act on (within the freshness window). */
    private boolean isFresh(Location location) {
        return location != null
                && System.currentTimeMillis() - location.getTime() < Constants.LOCATION_FRESHNESS_WINDOW;
    }

    private void handleDeviceDisconnected() {
        Location lastKnownLocation = getBestLastKnownLocation();
        if (isFresh(lastKnownLocation)
                && lastKnownLocation.distanceTo(BASE_LOCATION) < Constants.BASE_OPEN_DISTANCE_SMALL) {
            openGates(BluetoothDeviceAction.LEFT_VEHICLE);
        }
    }

    private void handleDeviceConnected() {
        Location lastKnownLocation = getBestLastKnownLocation();
        if (isFresh(lastKnownLocation)
                && lastKnownLocation.distanceTo(BASE_LOCATION) < Constants.BASE_OPEN_DISTANCE_SMALL) {
            openGates(BluetoothDeviceAction.ENTERED_VEHICLE);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.println(Log.DEBUG, TAG, "Starting service");
        }
        if (intent != null && ACTION_GEOFENCE_TRANSITION.equals(intent.getAction())) {
            handleGeofenceTransition(intent.getIntExtra(EXTRA_GEOFENCE_TRANSITION, -1));
        }
        return Service.START_STICKY;
    }

    private void handleGeofenceTransition(int transition) {
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            currentRadius = "Inside base";
            if (connectedBluetoothDevice != null) {
                openGates(BluetoothDeviceAction.ENTERED_BASE);
            }
        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            currentRadius = "Left small perimeter";
            if (connectedBluetoothDevice != null) {
                openGates(BluetoothDeviceAction.LEAVING_BASE);
            }
        } else {
            return;
        }
        changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
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
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (geofencingClient != null && geofencePendingIntent != null) {
            geofencingClient.removeGeofences(geofencePendingIntent);
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

    public void sendCommand(String message) {
        if (!canSendCommand()) {
            commandResult(Boolean.FALSE, message, 0);
            return;
        }
        sendHttpCommand(message,Constants.MAX_COMMAND_RETRY);
    }

    public void sendHttpCommand(String message, int retry) {
        Log.d(TAG, "sending Http command: " + message + " retries: " + retry);
        smartHomeClient.sendCommand(message, aBoolean -> {
            commandResult(aBoolean, message, retry);
            return null;
        });
    }

    private void commandResult(Boolean result, String message, int retry) {
        if (Boolean.FALSE.equals(result) && retry > 0) {
            final Handler h = new Handler();
            h.postDelayed(() -> {
                sendHttpCommand(message, retry-1);
            }, Constants.COMMAND_WAIT);
        } else if (Boolean.FALSE.equals(result) && retry <= 0) {
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
        if (!hasLocationPermission()) {
            return;
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        onLocationChanged(loc);
                    }
                });
    }

    private void requestLocationUpdates(long interval) {
        Log.println(Log.DEBUG, TAG, String.format("Requesting new location updates with interval: %s", interval));
        if (!hasLocationPermission()) {
            return;
        }
        currentInterval = interval;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
                .setMinUpdateDistanceMeters(5)
                .build();
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    public void setGarageStatus(String s, boolean updateNotification) {
        // Empty until the first reading after the service (re)starts; that first reading is
        // not a transition, so an already-open garage must not buzz.
        boolean statusKnown = this.garageStatus != null && !this.garageStatus.isEmpty();
        boolean wasOpen = statusKnown && this.garageStatus.contains("open");
        boolean isOpen = s != null && s.contains("open");
        this.garageStatus = s;
        if (statusKnown && isOpen == wasOpen) {
            // no open<->closed transition, nothing to announce
            return;
        }
        if (statusKnown && isOpen) {
            // genuine closed -> open change: buzz exactly once, right now
            vibrateGarageOpened();
        }
        if (updateNotification) {
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
        }
    }

    private void vibrateGarageOpened() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(VibrationEffect.createWaveform(new long[]{1000, 350}, -1));
        }
    }

    private void onLocationChanged(@NonNull Location newLocation) {
        Log.println(Log.DEBUG, TAG, "Received location: " + newLocation.getLatitude() + ", " + newLocation.getLongitude());
        cachedLocation = newLocation;
        if (previousLocation == null) {
            previousLocation = newLocation;
        }
        if (previousLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_SMALL
                && newLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_SMALL) {
            //if we entered small perimeter
            currentRadius = "Inside base";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            if (connectedBluetoothDevice != null) {
                openGates(BluetoothDeviceAction.ENTERED_BASE);
            }
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_SMALL);
        } else if (previousLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_LARGE
                && newLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_LARGE) {
            //if we left large perimeter
            currentRadius = "Outside of large perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_LARGE);
        } else if (previousLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_MEDIUM
                && newLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_MEDIUM) {
            //if we left medium perimeter
            currentRadius = "Left medium perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_MEDIUM);
        } else if (previousLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_SMALL
                && newLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_SMALL) {
            //if we left small perimeter
            currentRadius = "Left small perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            if (connectedBluetoothDevice != null) {
                openGates(BluetoothDeviceAction.LEAVING_BASE);
            }
        } else if (previousLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_MEDIUM
                && newLocation.distanceTo(BASE_LOCATION) <= Constants.BASE_OPEN_DISTANCE_MEDIUM) {
            //if we entered medium perimeter
            currentRadius = "Entered medium perimeter";
            changeNotification(Constants.NOTIFICATION_RUNNING_MESSAGE);
            requestLocationUpdates(Constants.BASE_OPEN_DISTANCE_INTERVAL_SMALL);
        } else if (previousLocation.distanceTo(BASE_LOCATION) >= Constants.BASE_OPEN_DISTANCE_LARGE
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
        previousLocation = newLocation;
        for (LocationChangeListener locationChangeListener : locationChangeListeners) {
            locationChangeListener.locationChanged(currentRadius);
        }
    }

    public boolean canSendCommand() {
        if (!hasLocationPermission()) {
            return false;
        }
        return cachedLocation != null && BASE_LOCATION.distanceTo(cachedLocation) < Constants.MAX_ALLOWED_DISTANCE;
    }


    /**
     * Registers a single home geofence (radius = small perimeter) as a backup trigger that
     * fires even when the app is backgrounded or was killed. Best-effort — logs on failure.
     */
    private void registerHomeGeofence() {
        if (!hasLocationPermission()) {
            return;
        }
        Geofence geofence = new Geofence.Builder()
                .setRequestId(HOME_GEOFENCE_ID)
                .setCircularRegion(Secret.BASE_LAT, Secret.BASE_LON, Constants.BASE_OPEN_DISTANCE_SMALL)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
        GeofencingRequest req = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
        try {
            geofencingClient.addGeofences(req, getGeofencePendingIntent())
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to add geofence", e));
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission for geofence", e);
        }
    }

    private PendingIntent getGeofencePendingIntent() {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
        // FLAG_MUTABLE is required on API 31+ so the system can inject the transition extras.
        geofencePendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        return geofencePendingIntent;
    }

    public class RunServiceBinder extends Binder {
        GateOpeningManager getService() {
            return GateOpeningManager.this;
        }
    }

}
