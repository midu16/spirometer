package com.example.mihaiidu.myapplication;


import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TargetApi(21)
public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;

    private boolean isAlreadyStarted = false;

    private static final String UUID_Battery = "00002a19-0000-1000-8000-00805f9b34fb";
    private static final String UUID_DeviceStatus = "2d417c80-b667-11e3-a5e2-0800200c9a66";
    private static final String UUID_FwVersion = "00002a26-0000-1000-8000-00805f9b34fb";
    private static final String UUID_In = "7d32c0f0-bef5-11e3-b1b6-0800200c9a66";
    private static final String UUID_Out = "92b403f0-b665-11e3-a5e2-0800200c9a66";
    private static final String UUID_Service = "7f04f3f0-b665-11e3-a5e2-0800200c9a66";
    private static final String UUID_ServiceBattery = "0000180f-0000-1000-8000-00805f9b34fb";
    private static final String UUID_ServiceInfo = "0000180a-0000-1000-8000-00805f9b34fb";
    private static final String UUID_SwVersion = "00002a28-0000-1000-8000-00805f9b34fb";
    private static final String UUID_VolumeStep = "f9f84152-b667-11e3-a5e2-0800200c9a66";
    private static BluetoothGattCharacteristic inData;
    private static BluetoothGattCharacteristic outData;
    private static BluetoothGattService mainService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .build();
                filters = new ArrayList<ScanFilter>();
            }
            //Initializes list view adapter
            scanLeDevice(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[(len / 2)];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }


    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);

                    }
                }
            }, SCAN_PERIOD);
            if (!isAlreadyStarted) {
                isAlreadyStarted = true;
                mLEScanner.startScan(filters, settings, mScanCallback);
                new Handler()
                        .postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mLEScanner.stopScan(mScanCallback);
                                isAlreadyStarted = false;
                            }
                        }, 12000);
            }
        }
    }
//00:26:33:90:18:41



    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            Log.i("onLeScan", device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("onServicesDiscovered", services.toString());
            gatt.readCharacteristic(services.get(1).getCharacteristics().get
                    (0));

            mainService = gatt.getService(UUID.fromString(UUID_Service));

            for (BluetoothGattCharacteristic characteristic :
                    mainService.getCharacteristics()) {
                if (characteristic.getUuid().toString().equals(UUID_In)) {
                    inData = characteristic;


                    mGatt.setCharacteristicNotification(inData, true);


                } else if (characteristic.getUuid().toString().equals(UUID_Out)) {
                    outData = characteristic;
                    sendCommand(Command.cod_START_TEST);
                }
            }


            Log.d("TAG", "FINISHED");

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
//            new Handler()
//                    .postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            sendCommand(Command.cod_START_TEST);
//                        }
//                    },1000);
//            gatt.disconnect();
        }
    };

    public boolean sendCommand(Command cod_command) {

        switch (cod_command) {
            case cod_START_TEST:
                outData.setValue(hexStringToByteArray("0009FFFF0101000000000000000000000009"));
                return mGatt.writeCharacteristic(outData);
            case cod_STOP_TEST:
                outData.setValue(hexStringToByteArray("001BFFFF010000000000000000000000001A"));
                return mGatt.writeCharacteristic(outData);
            default:
                return false;
        }
    }


public enum Command {
    cod_START_TEST,
    cod_STOP_TEST
}



}