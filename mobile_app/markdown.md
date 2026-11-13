# SmartHome App — Location Refactor Implementation Plan (Part B)

Goal: replace the legacy `LocationManager` + hand-written concentric-perimeter
polling with a **hybrid**:

- **FusedLocationProviderClient** for live, battery-friendly location while the
  user is actively driving / Bluetooth-connected (keeps the graduated perimeter
  state machine).
- **One home geofence** (radius = `BASE_OPEN_DISTANCE_SMALL`, 60 m) as a backup
  trigger that fires even when the app is backgrounded or was killed, waking the
  service to open/close the gate.

Part A (crash + logic bug fixes) is already done and compiles. This document
covers Part B only.

---

## 0. Prerequisites / decisions already made

- Approach chosen: **Fused + a single home geofence** (hybrid).
- Auto-open semantics preserved: gate opens on entering the small perimeter
  (`ENTERED_BASE`) and closes/opens on leaving it (`LEAVING_BASE`) **only while a
  configured Bluetooth device is connected** (i.e. user is in the car).
- Background-location runtime permission is now requested separately in
  `MainActivity` (done in Part A) — geofences require it on API 29+.

---

## 1. Dependencies (`app/build.gradle`)

- Bump `com.google.android.gms:play-services-location` from `17.0.0` →
  a modern version (`21.3.0`).
- No other new dependencies (geofencing + fused are both in play-services-location).

Verify with a clean `:app:compileDebugJavaWithJavac`.

---

## 2. `GateOpeningManager` — swap LocationManager → Fused

### 2.1 Class declaration & imports
- Remove `implements LocationListener`.
- Delete the empty `onStatusChanged` / `onProviderEnabled` / `onProviderDisabled`
  overrides and the `@Override` on `onLocationChanged` (it becomes an internal
  method).
- Remove imports: `android.location.LocationListener`, `android.location.LocationManager`,
  `android.os.Bundle` (only used by the deleted override).
- Add imports:
  - `android.app.PendingIntent`
  - `android.os.Looper`
  - `com.google.android.gms.location.FusedLocationProviderClient`
  - `com.google.android.gms.location.LocationServices`
  - `com.google.android.gms.location.LocationRequest`
  - `com.google.android.gms.location.LocationResult`
  - `com.google.android.gms.location.LocationCallback`
  - `com.google.android.gms.location.Priority`
  - `com.google.android.gms.location.Geofence`
  - `com.google.android.gms.location.GeofencingClient`
  - `com.google.android.gms.location.GeofencingRequest`
  - `com.google.android.gms.location.GeofencingEvent`

### 2.2 Fields
Replace `private LocationManager locationManager;` with:
```java
private FusedLocationProviderClient fusedLocationClient;
private GeofencingClient geofencingClient;
private LocationCallback locationCallback;
private PendingIntent geofencePendingIntent;

public static final String ACTION_GEOFENCE_TRANSITION = "sk.coroid.smarthome.GEOFENCE_TRANSITION";
public static final String EXTRA_GEOFENCE_TRANSITION = "geofence_transition";
private static final String HOME_GEOFENCE_ID = "home";
```
`lastKnownLocation` stays — it now acts as the **cache** continuously refreshed by
the fused callback, so synchronous callers (`canSendCommand`,
`getBestLastKnownLocation`) read from it instead of a blocking provider call.

### 2.3 `onCreate`
- Replace `locationManager = ...` with:
```java
fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
geofencingClient = LocationServices.getGeofencingClient(this);
locationCallback = new LocationCallback() {
    @Override public void onLocationResult(@NonNull LocationResult result) {
        Location loc = result.getLastLocation();
        if (loc != null) onLocationChanged(loc);
    }
};
seedLastLocation();      // async getLastLocation() to warm the cache
registerHomeGeofence();  // best-effort; logs on failure
```

### 2.4 `requestLocationUpdates(long interval)` (continuous)
```java
if (!hasLocationPermission()) return;
currentInterval = interval;
fusedLocationClient.removeLocationUpdates(locationCallback);
LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
        .setMinUpdateDistanceMeters(5)
        .build();
fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
```

### 2.5 `requestLocationUpdate()` (single)
```java
if (!hasLocationPermission()) return;
fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener(loc -> { if (loc != null) onLocationChanged(loc); });
```

### 2.6 `seedLastLocation()` (new)
```java
if (!hasLocationPermission()) return;
fusedLocationClient.getLastLocation()
        .addOnSuccessListener(loc -> { if (loc != null && lastKnownLocation == null) lastKnownLocation = loc; });
```

### 2.7 Helper: `hasLocationPermission()`
Extract the repeated fine/coarse permission check into one private boolean method
and use it in every location call site (removes ~5 copies of the same `if`).

### 2.8 `getBestLastKnownLocation()`
Simplify to return the cached value (fused keeps it fresh):
```java
return lastKnownLocation;
```
`isFresh()` (added in Part A) still guards the auto-open on BT connect/disconnect.

### 2.9 `canSendCommand()`
Replace the blocking `getLastKnownLocation(FUSED_PROVIDER)` with the cache:
```java
if (!hasLocationPermission()) return false;
return lastKnownLocation != null
        && BASE_LOCATION.distanceTo(lastKnownLocation) < Constants.MAX_ALLOWED_DISTANCE;
```
Note: `MainActivity` already calls `requestLocationUpdate()` on service-connect and
`onResume`, warming the cache before the user taps a gate button. If still null,
`canSendCommand` returns false → the existing confirm dialog appears (safe fallback).

### 2.10 Bluetooth disconnect
In `handleBluetoothActions`, replace `locationManager.removeUpdates(this)` with
`fusedLocationClient.removeLocationUpdates(locationCallback)`.

### 2.11 `onDestroy`
Add cleanup:
```java
if (fusedLocationClient != null) fusedLocationClient.removeLocationUpdates(locationCallback);
if (geofencingClient != null && geofencePendingIntent != null)
    geofencingClient.removeGeofences(geofencePendingIntent);
```

---

## 3. Home geofence

### 3.1 `registerHomeGeofence()` (new, in service)
```java
if (!hasLocationPermission()) return;
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
geofencingClient.addGeofences(req, getGeofencePendingIntent())
        .addOnFailureListener(e -> Log.e(TAG, "Failed to add geofence", e));
```

### 3.2 `getGeofencePendingIntent()` (new)
Targets a broadcast receiver; **must** use `FLAG_MUTABLE` (system injects the
transition extras) on API 31+:
```java
if (geofencePendingIntent != null) return geofencePendingIntent;
Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
geofencePendingIntent = PendingIntent.getBroadcast(this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
return geofencePendingIntent;
```

### 3.3 New class `GeofenceBroadcastReceiver`
```java
public class GeofenceBroadcastReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) return;
        Intent svc = new Intent(context, GateOpeningManager.class);
        svc.setAction(GateOpeningManager.ACTION_GEOFENCE_TRANSITION);
        svc.putExtra(GateOpeningManager.EXTRA_GEOFENCE_TRANSITION, event.getGeofenceTransition());
        context.startForegroundService(svc);
    }
}
```
Register in `AndroidManifest.xml`:
```xml
<receiver android:name=".GeofenceBroadcastReceiver"
          android:enabled="true" android:exported="false" />
```

### 3.4 Handle the transition in `onStartCommand`
```java
if (intent != null && ACTION_GEOFENCE_TRANSITION.equals(intent.getAction())) {
    handleGeofenceTransition(intent.getIntExtra(EXTRA_GEOFENCE_TRANSITION, -1));
}
return Service.START_STICKY;
```
`handleGeofenceTransition(int transition)`:
- `GEOFENCE_TRANSITION_ENTER` → `currentRadius = "Inside base"`; if BT connected → `openGates(ENTERED_BASE)`.
- `GEOFENCE_TRANSITION_EXIT`  → `currentRadius = "Left small perimeter"`; if BT connected → `openGates(LEAVING_BASE)`.
- then `changeNotification(NOTIFICATION_RUNNING_MESSAGE)`.

This reuses the exact same `openGates` path as the fused perimeter machine, so the
geofence is a pure redundant/backup trigger — no duplicated command logic.

---

## 4. Edge cases & notes

- **Double-fire**: when the app is alive, both the fused perimeter machine and the
  geofence may fire on arrival. The gate server should be idempotent for a rapid
  repeat; if not, add a short debounce (ignore identical `openGates(action)` within
  N seconds) in `openGates`. **Decide with the user before adding.**
- **Background FGS start**: `startForegroundService` from a geofence broadcast is
  generally allowed (location is an exempted reason), but the FGS type is currently
  `specialUse`. Verify on-device on the target Android version; if the system
  rejects it, consider declaring `location`/`connectedDevice` FGS type instead.
- **Geofence latency**: enter/exit events are typically delivered within ~1–2 min
  and depend on OEM/Doze. The geofence is a backup, not the primary path — the
  fused machine still gives fast reaction while the app is foregrounded/connected.
- Geofences are **not** persisted across reboot by the OS — re-register on
  `BOOT_COMPLETED` (see §6) if reliability after reboot matters.

---

## 5. Verification checklist

1. `./gradlew :app:compileDebugJavaWithJavac` — clean compile.
2. Install on device; grant location (incl. **Allow all the time**) + Bluetooth.
3. Notification body shows real text immediately (no `null`) — regression check of Part A.
4. Drive out of the 60 m radius with car BT connected → gate command fires
   (`LEAVING_BASE`); drive back in → `ENTERED_BASE`.
5. Kill the app, then cross the geofence → service restarts and fires (backup path).
6. Confirm battery: no continuous high-accuracy GPS when far from home and not
   connected (updates should only run on the appropriate interval / when connected).

---

## 6. Out-of-scope follow-ups (not in this refactor, noted for later)

- Volley `RequestQueue` singleton (currently created per request — thread leak).
- `MediaPlayer.create(...)` never released in `notifySuccess`/`notifyFail` — release on completion.
- Move hardcoded credentials out of `Secret.java` (compiled into APK) → revocable per-device token.
- `SettingsStorageManager.readDevices()` recursion ignores its return value after creating the file.
- Re-register geofence + restart service on `BOOT_COMPLETED`.
- Replace deprecated `new Handler()` with `new Handler(Looper.getMainLooper())`.
- Remove dead code (`isExternalStorageWritable`, empty `handleDeviceSettingsChanged`, unused imports).
- Bump other stale deps (appcompat 1.1.0, material alpha).
