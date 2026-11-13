package sk.coroid.smarthome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.GeofencingEvent;

/**
 * Receives home-geofence enter/exit transitions and wakes {@link GateOpeningManager} to handle
 * them. This is the backup path that fires even when the app is backgrounded or was killed;
 * starting the foreground service from here is legal on Android 14/15 because geofencing is a
 * background-start exemption.
 */
public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = GeofenceBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            Log.e(TAG, "Geofence event error: " + (event == null ? "null event" : event.getErrorCode()));
            return;
        }
        Intent svc = new Intent(context, GateOpeningManager.class);
        svc.setAction(GateOpeningManager.ACTION_GEOFENCE_TRANSITION);
        svc.putExtra(GateOpeningManager.EXTRA_GEOFENCE_TRANSITION, event.getGeofenceTransition());
        context.startForegroundService(svc);
    }
}
