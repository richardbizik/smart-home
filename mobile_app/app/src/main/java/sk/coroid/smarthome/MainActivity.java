package sk.coroid.smarthome;

import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import sk.coroid.smarthome.sensor.SensorCardAdapter;
import sk.coroid.smarthome.settings.SettingsActivity;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSION_REQUEST_CODE = 7192;
    private static final int PLAY_SERVICE_REQUEST = 300193;
    private GateOpeningManager gateOpeningManager;
    private SmartHomeClient smartHomeClient;

    ProgressBar progressBar;

    private boolean isVisible = false;

    RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        smartHomeClient = new SmartHomeClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_NETWORK_STATE",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.BLUETOOTH"
            }, MY_PERMISSION_REQUEST_CODE);
        } else {
            if (checkPlayService()) {
                Intent intent = new Intent(this, GateOpeningManager.class);
                startService(intent);
            }
            initActivity();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, GateOpeningManager.class);
        bindService(intent, connection, BIND_IMPORTANT);
        isVisible = true;
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
//                updateSensorData();
                updateGarageStatus(false);
                if (isVisible) {
                    handler.postDelayed(this, 1000 * 5);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, Restarter.class);
        this.sendBroadcast(broadcastIntent);
        isVisible = false;
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        isVisible = false;
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(MY_PERMISSION_REQUEST_CODE, permissions, grantResults);
        if (requestCode == 1) {
            initActivity();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.preferences:
                goToPreferences();
                return true;
            default:
                return false;
        }
    }

    private void goToPreferences() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private LocationChangeListener locationChangeListener = new LocationChangeListener() {
        @Override
        public void locationChanged(String locationText) {
            updateLocationText("Location: " + locationText);
        }
    };

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GateOpeningManager.RunServiceBinder service1 = (GateOpeningManager.RunServiceBinder) service;
            gateOpeningManager = service1.getService();
//            Toast.makeText(MainActivity.this, "MQTTManager is connected", Toast.LENGTH_SHORT).show();
            gateOpeningManager.requestLocationUpdate();
            gateOpeningManager.registerLocationChangeListener(locationChangeListener);Handler handler = new Handler();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateGarageStatus(true);
                    handler.postDelayed(this, 1000 * 60 * 5);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
//            Toast.makeText(MainActivity.this, "MQTTManager is disconnected", Toast.LENGTH_SHORT).show();
            gateOpeningManager.removeLocationChangeListener(locationChangeListener);
            gateOpeningManager = null;
        }
    };

    private void updateLocationText(String text) {
        TextView locationTextView = this.findViewById(R.id.locationTextView);
        locationTextView.setText(text);
    }

    private void initActivity() {
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                super.onTouchEvent(rv, e);
            }
        });
        registerOnClickEvents();
    }

    private void updateSensorData() {
        smartHomeClient.getDHTData(dhtDataDTOS -> {
            SensorCardAdapter adapter = new SensorCardAdapter(this, dhtDataDTOS);
            recyclerView.setAdapter(adapter);
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
            return null;
        }, Secret.SENSOR_NAMES);
    }

    private void updateGarageStatus(boolean updateNotification) {
        smartHomeClient.getGarageInfo(garageInfo -> {
            if (garageInfo == null){
                return null;
            }
            TextView garageInfoTW = findViewById(R.id.garageInfo);
            String status = "";
            if (garageInfo.getDistance() < 50) {
                status += " open";
            } else {
                status += " closed";
            }
            if (garageInfo.getPresence()) {
                status += ", detected movement";
            }
            garageInfoTW.setText("Garage:" + status);
            if (gateOpeningManager != null){
                gateOpeningManager.setGarageStatus("Garage:" + status, updateNotification);
            }
            return null;
        });
    }

    private void registerOnClickEvents() {
        Button gateYard = findViewById(R.id.gateYard);
        Button gateHouse = findViewById(R.id.gateHouse);
        Button gateGarage = findViewById(R.id.gateGarage);

        gateHouse.setOnClickListener(new MessageOnClickListener("brana_dom"));
        gateYard.setOnClickListener(new MessageOnClickListener("brana_zahrada"));
        gateGarage.setOnClickListener(new MessageOnClickListener("brana_garaz"));
    }

    private class MessageOnClickListener implements View.OnClickListener {
        String message;

        public MessageOnClickListener(String message) {
            this.message = message;
        }

        public void onClick(View view) {
            if (!gateOpeningManager.canSendCommand()) {
                new AlertDialog.Builder(view.getContext())
                        .setTitle("Vykonať akciu?")
                        .setMessage("Vzdialenosť prekračuje povolenú hranicu. Naozaj vyslať signál?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                sendMobileCommand(message);
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        }).show();
            } else {
                sendMobileCommand(message);
            }
        }
    }

    private boolean checkPlayService() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            if (googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result, PLAY_SERVICE_REQUEST).show();
            } else {
                Toast.makeText(this, "This Device is not supported.", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    private void sendMobileCommand(String message) {
        try {
            gateOpeningManager.sendHttpCommand(message);
        } catch (Exception e) {
            Log.e("MainActivity", e.getMessage(), e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gateOpeningManager != null) {
            gateOpeningManager.requestLocationUpdate();
        }
    }


}