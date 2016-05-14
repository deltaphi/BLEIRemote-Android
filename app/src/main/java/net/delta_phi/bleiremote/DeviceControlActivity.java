/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.delta_phi.bleiremote;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Vector;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static final UUID characteristicUUID = UUID.fromString("9184addc-850c-4057-b0fc-d49a10738a1b");

    //private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    //private ExpandableListView mGattServicesList;

    private Collection<Button> buttonCollection = new Vector<Button>(50);

    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
//    private final ExpandableListView.OnChildClickListener servicesListClickListner =
//            new ExpandableListView.OnChildClickListener() {
//                @Override
//                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
//                                            int childPosition, long id) {
//                    if (mGattCharacteristics != null) {
//                        final BluetoothGattCharacteristic characteristic =
//                                mGattCharacteristics.get(groupPosition).get(childPosition);
//                        final int charaProp = characteristic.getProperties();
//                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//                            // If there is an active notification on a characteristic, clear
//                            // it first so it doesn't update the data field on the user interface.
//                            if (mNotifyCharacteristic != null) {
//                                mBluetoothLeService.setCharacteristicNotification(
//                                        mNotifyCharacteristic, false);
//                                mNotifyCharacteristic = null;
//                            }
//                            mBluetoothLeService.readCharacteristic(characteristic);
//                        }
//                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
//                            mNotifyCharacteristic = characteristic;
//                            mBluetoothLeService.setCharacteristicNotification(
//                                    characteristic, true);
//                        }
//                        return true;
//                    }
//                    return false;
//                }
//    };

    private final View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Determine the button index
            Iterator<Button> buttonIterator = buttonCollection.iterator();
            int i = 0;
            boolean found = false;
            for (Button button : buttonCollection) {
                if (button == v) {
                    found = true;
                    break;
                }
                ++i;
            }
            if (found) {

                BluetoothGattCharacteristic characteristic = null;
                for (ArrayList<BluetoothGattCharacteristic> serviceList : mGattCharacteristics) {
                    for (BluetoothGattCharacteristic chara : serviceList) {
                        if (chara.getUuid().compareTo(characteristicUUID) == 0) {
                            characteristic = chara;
                            break;
                        }
                    }
                }

                Byte protocol = 0x2f;
                Byte[] address = {0x00, 0x00};
                byte[] command = DeviceControlActivity.getCommandForButton(v);
                Byte flags = 0x00;



                ByteArrayOutputStream baos = new ByteArrayOutputStream(10);
                baos.write(protocol);
                baos.write(address[0]);
                baos.write(address[1]);
                baos.write(command[1]);
                baos.write(command[0]);
                baos.write(flags);

                byte[] bytes = baos.toByteArray();

                characteristic.setValue(bytes);
                mBluetoothLeService.writeCharacteristic(characteristic);
                //mBluetoothLeService.readCharacteristic(characteristic);
            }
        }
    };

    private void clearUI() {
        //mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_control);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        //((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        //mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        //mGattServicesList.setOnChildClickListener(servicesListClickListner);
        //mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        buttonCollection.add((Button) findViewById(R.id.button_onoff));
        buttonCollection.add((Button) findViewById(R.id.button_muting));
        buttonCollection.add((Button) findViewById(R.id.button_vol_down));
        buttonCollection.add((Button) findViewById(R.id.button_vol_up));
        buttonCollection.add((Button) findViewById(R.id.button_fm_1));
        buttonCollection.add((Button) findViewById(R.id.button_fm_2));
        buttonCollection.add((Button) findViewById(R.id.button_fm_3));
        buttonCollection.add((Button) findViewById(R.id.button_fm_4));
        buttonCollection.add((Button) findViewById(R.id.button_fm_5));
        buttonCollection.add((Button) findViewById(R.id.button_fm_6));
        buttonCollection.add((Button) findViewById(R.id.button_fm_7));
        buttonCollection.add((Button) findViewById(R.id.button_fm_8));
        buttonCollection.add((Button) findViewById(R.id.button_deck_play));
        buttonCollection.add((Button) findViewById(R.id.button_deck_stop));
        buttonCollection.add((Button) findViewById(R.id.button_fm_9));
        buttonCollection.add((Button) findViewById(R.id.button_fm_0));
        buttonCollection.add((Button) findViewById(R.id.button_cd_play));
        buttonCollection.add((Button) findViewById(R.id.button_cd_stop));
        buttonCollection.add((Button) findViewById(R.id.button_cd_skip_rew));
        buttonCollection.add((Button) findViewById(R.id.button_cd_skip_ff));
        buttonCollection.add((Button) findViewById(R.id.button_cd_program));
        buttonCollection.add((Button) findViewById(R.id.button_cd_1));
        buttonCollection.add((Button) findViewById(R.id.button_cd_2));
        buttonCollection.add((Button) findViewById(R.id.button_cd_3));
        buttonCollection.add((Button) findViewById(R.id.button_cd_4));
        buttonCollection.add((Button) findViewById(R.id.button_cd_5));
        buttonCollection.add((Button) findViewById(R.id.button_cd_6));
        buttonCollection.add((Button) findViewById(R.id.button_cd_7));
        buttonCollection.add((Button) findViewById(R.id.button_cd_8));
        buttonCollection.add((Button) findViewById(R.id.button_cd_9));
        buttonCollection.add((Button) findViewById(R.id.button_cd_0));
        buttonCollection.add((Button) findViewById(R.id.button_cd_plus_ten));

        for (Button button : buttonCollection) {
            button.setOnClickListener(buttonClickListener);
        }


        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    static byte[] getCommandForButton(View v) {
        byte command[] = {0x00, 0x00};
        switch (v.getId()) {
            case R.id.button_onoff:
                command[0] = (byte) 0x04;
                command[1] = (byte) 0x09;
                break;
            case R.id.button_muting:
                command[0] = (byte) 0x04;
                command[1] = (byte) 0xE9;
                break;
            case R.id.button_vol_down:
                command[0] = (byte) 0x04;
                command[1] = (byte) 0xA9;
                break;
            case R.id.button_vol_up:
                command[0] = (byte) 0x04;
                command[1] = (byte) 0x89;
                break;
            case R.id.button_fm_1:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x09;
                break;
            case R.id.button_fm_2:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x29;
                break;
            case R.id.button_fm_3:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x49;
                break;
            case R.id.button_fm_4:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x69;
                break;
            case R.id.button_fm_5:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x89;
                break;
            case R.id.button_fm_6:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0xA9;
                break;
            case R.id.button_fm_7:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0xC9;
                break;
            case R.id.button_fm_8:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0xE9;
                break;
            case R.id.button_deck_play:
                command[0] = (byte) 0x01;
                command[1] = (byte) 0x49;
                break;
            case R.id.button_deck_stop:
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x09;
                break;
            case R.id.button_fm_9:
                command[0] = (byte) 0x03;
                command[1] = (byte) 0x09;
                break;
            case R.id.button_fm_0:
                command[0] = (byte) 0x03;
                command[1] = (byte) 0x29;
                break;
            case R.id.button_cd_play:
                command[0] = (byte) 0x01;
                command[1] = (byte) 0x4C;
                break;
            case R.id.button_cd_stop:
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x0C;
                break;
            case R.id.button_cd_skip_rew:
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x4C;
                break;
            case R.id.button_cd_skip_ff:
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x6C;
                break;
            case R.id.button_cd_program:
                command[0] = (byte) 0x03;
                command[1] = (byte) 0xAC;
                break;
            case R.id.button_cd_1:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x0C;
                break;
            case R.id.button_cd_2:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x2C;
                break;
            case R.id.button_cd_3:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x4C;
                break;
            case R.id.button_cd_4:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x6C;
                break;
            case R.id.button_cd_5:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0x8C;
                break;
            case R.id.button_cd_6:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0xAC;
                break;
            case R.id.button_cd_7:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0xCC;
                break;
            case R.id.button_cd_8:
                command[0] = (byte) 0x02;
                command[1] = (byte) 0xEC;
                break;
            case R.id.button_cd_9:
                command[0] = (byte) 0x03;
                command[1] = (byte) 0x0C;
                break;
            case R.id.button_cd_0:
                command[0] = (byte) 0x03;
                command[1] = (byte) 0x2C;
                break;
            case R.id.button_cd_plus_ten:
                command[0] = (byte) 0x03;
                command[1] = (byte) 0x4C;
                break;
            default:

        }
        return command;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

//        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
//                this,
//                gattServiceData,
//                android.R.layout.simple_expandable_list_item_2,
//                new String[] {LIST_NAME, LIST_UUID},
//                new int[] { android.R.id.text1, android.R.id.text2 },
//                gattCharacteristicData,
//                android.R.layout.simple_expandable_list_item_2,
//                new String[] {LIST_NAME, LIST_UUID},
//                new int[] { android.R.id.text1, android.R.id.text2 }
//        );
        //mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
