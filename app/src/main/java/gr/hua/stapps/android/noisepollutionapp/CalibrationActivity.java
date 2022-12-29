package gr.hua.stapps.android.noisepollutionapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import gr.hua.stapps.android.noisepollutionapp.databinding.ActivityCalibrationBinding;

public class CalibrationActivity extends AppCompatActivity {

    private ActivityCalibrationBinding binding;
    private CalibrationViewModel calibrationViewModel;
    private static final String LOG_INTRO = "CalibrationActivity -> ";

    private static final int REQUEST_ENABLE_BLUETOOTH = 100;
    private static final int RECORDING_PERMISSION = 0;

    private Boolean permissionToRecord = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Logger.getGlobal().log(Level.INFO, LOG_INTRO + "action is: " + action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(CalibrationActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress(); //MAC address
                    Logger.getGlobal().log(Level.INFO, LOG_INTRO + "FOUND: deviceName= " + deviceName + " and mac= " + deviceHardwareAddress + " and UUID= " + Arrays.toString(device.getUuids()));
                    if (Objects.equals(deviceName, "ESP32test")) {
                        Logger.getGlobal().log(Level.INFO, LOG_INTRO + "Found ESP32Test! Connecting to " + deviceHardwareAddress);
                        calibrationViewModel.initConnectionThread(deviceHardwareAddress);
                    }
                    return;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCalibrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //Provide ViewModel
        calibrationViewModel = new ViewModelProvider(this).get(CalibrationViewModel.class);

        // Register for broadcasts when a device is discovered.
        registerReceivers();

        // Get values from previous activity
        Intent intent = getIntent();
        String key = intent.getStringExtra("Key");
        System.out.println(LOG_INTRO + "value passed is " + key);

        setListeners();
        setObservers();
        requestPermission();
    }

    public void requestPermission() {
        //Request permission to record if it is not already granted
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(CalibrationActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("Permission to record granted");
                    permissionToRecord = true;
                } else {
                    System.out.println("Permission to record not granted");
                    permissionToRecord = false;
                    requestAudioPermission();
                }
            }
        }).start();
    }

    private void requestAudioPermission() {
        //Permission has not been granted and must be requested.
        if (ActivityCompat.shouldShowRequestPermissionRationale(CalibrationActivity.this, Manifest.permission.RECORD_AUDIO)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // Display a SnackBar with cda button to request the missing permission.
            Log.d("PERMISSIONS", "requestMicPermission()/if");
            Snackbar.make(binding.getRoot(), "Permission to record audio is required", Snackbar.LENGTH_INDEFINITE).setAction("Audio Permission", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //Request the permission
                    ActivityCompat.requestPermissions(CalibrationActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORDING_PERMISSION);
                }
            }).show();
        } else {
            Snackbar.make(binding.getRoot(), "Audio recording not available", Snackbar.LENGTH_SHORT).show();
            //Request the permission. The result will be received in onRequestPermissionResult().
            ActivityCompat.requestPermissions(CalibrationActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORDING_PERMISSION);
        }
    }

    @SuppressLint("MissingPermission")
    public void setObservers() {
        final Observer<Boolean> calibration_observer = isBluetoothEnabled -> {
            if (isBluetoothEnabled != null) {
                if (isBluetoothEnabled) {
                    Logger.getGlobal().log(Level.INFO, LOG_INTRO + "bluetooth is enabled");
                    calibrationViewModel.searchForDevices();
                } else {
                    Logger.getGlobal().log(Level.INFO, LOG_INTRO + "asking to enable bluetooth");
                    Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH);
                }
            } else Logger.getGlobal().log(Level.INFO, LOG_INTRO + "isBluetoothEnabled is false");
        };
        final Observer<Boolean> connection_observer = isConnectedToESP -> {
            if (isConnectedToESP != null) {
                if (isConnectedToESP) {
                    Logger.getGlobal().log(Level.INFO, LOG_INTRO + " connected to ESP");
                    binding.connectButton.setClickable(false);
                    areCommandButtonsClickable(true);
                    binding.buttonCalibrationGroupIV.setClickable(false);
                } else {
                    Toast.makeText(this, "Could not connect to calibration device, please retry in a few moments.", Toast.LENGTH_LONG).show();
                    binding.connectButton.setClickable(true);
                }
            }
        };
        final Observer<String> espMessage_observer = espMessage -> {
            if (espMessage != null) {
                Logger.getGlobal().log(Level.INFO, LOG_INTRO + "message received is: " + espMessage);
            }
        };
        calibrationViewModel.getIsBluetoothEnabled().observe(this, calibration_observer);
        calibrationViewModel.getIsConnectedToESP().observe(this, connection_observer);
        calibrationViewModel.getEspMessage().observe(this, espMessage_observer);
    }

    public void setListeners() {
        binding.connectButton.setOnClickListener(view -> {
            binding.connectButton.setClickable(false);
            // Use this check to determine whether Bluetooth classic is supported on the device.
            // Then you can selectively disable BLE-related features.
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                Toast.makeText(CalibrationActivity.this, "R.string.bluetooth_not_supported", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                if (ActivityCompat.checkSelfPermission(CalibrationActivity.this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                    calibrationViewModel.initNoiseCalibration(CalibrationActivity.this);
                } else {
                    Toast.makeText(CalibrationActivity.this, "permission for bluetooth not granted", Toast.LENGTH_SHORT).show();
                    binding.connectButton.setClickable(true);
                }
            }
        });
        binding.buttonCalibrationGroupI.setOnClickListener(view -> {
            calibrationViewModel.sendCommand("RECORD0");
            areCommandButtonsClickable(false);
            binding.buttonCalibrationGroupIV.setClickable(true);
        });
        binding.buttonCalibrationGroupII.setOnClickListener(view -> {
            calibrationViewModel.sendCommand("RECORD1");
            areCommandButtonsClickable(false);
            binding.buttonCalibrationGroupIV.setClickable(true);
        });
        binding.buttonCalibrationGroupIII.setOnClickListener(view -> {
            calibrationViewModel.sendCommand("RECORD2");
            areCommandButtonsClickable(false);
            binding.buttonCalibrationGroupIV.setClickable(true);
        });
        binding.buttonCalibrationGroupIV.setOnClickListener(view -> {
            calibrationViewModel.sendCommand("STOP");
            areCommandButtonsClickable(true);
            binding.buttonCalibrationGroupIV.setClickable(false);
        });
        areCommandButtonsClickable(false);
    }

    public void areCommandButtonsClickable(Boolean clickable) {
        binding.buttonCalibrationGroupI.setClickable(clickable);
        binding.buttonCalibrationGroupII.setClickable(clickable);
        binding.buttonCalibrationGroupIII.setClickable(clickable);
        binding.buttonCalibrationGroupIV.setClickable(clickable);
    }

    public void registerReceivers() {
        IntentFilter actionFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter actionDiscoveryStartedFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(receiver, actionFoundFilter);
        registerReceiver(receiver, actionDiscoveryStartedFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver);
    }
}