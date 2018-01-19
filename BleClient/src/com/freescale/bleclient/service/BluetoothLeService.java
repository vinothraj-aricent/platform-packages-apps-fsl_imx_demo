/*
 * Copyright (C) 2016 Freescale Semiconductor, Inc.
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
package com.freescale.bleclient.service;

import java.util.List;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.freescale.bleclient.global.GlobalContacts;
import com.freescale.bleclient.global.IMXUuid;
import com.freescale.bleclient.global.SampleGattAttributes;

public class BluetoothLeService extends Service {
	private final static String TAG = "BLE";

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;
	private int mConnectionState = STATE_DISCONNECTED;

	private static final int STATE_DISCONNECTED = 0;
	private static final int STATE_CONNECTING = 1;
	private static final int STATE_CONNECTED = 2;

	public final static String ACTION_GATT_CONNECTED           = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED        = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_AVAILABLE           = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
	public final static String EXTRA_DATA                      = "com.example.bluetooth.le.EXTRA_DATA";

	public final static UUID UUID_HEART_RATE_MEASUREMENT       = UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

	public static final String EXTRA_NAME = "extra_name";

	// Implements callback methods for GATT events that the app cares about. For example,
	// connection change and services discovered.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			String intentAction;
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				intentAction = ACTION_GATT_CONNECTED;
				mConnectionState = STATE_CONNECTED;
				broadcastUpdate(intentAction);
				Log.i(TAG, "Connected to GATT server.");
				// Attempts to discover services after successful connection.
				Log.i(TAG, "Attempting to start service discovery:" +
						mBluetoothGatt.discoverServices());

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				mConnectionState = STATE_DISCONNECTED;
				Log.i(TAG, "Disconnected from GATT server.");
				broadcastUpdate(intentAction);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic,
				int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			Log.e(TAG, "onCharacteristicChanged");
			broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
		}
	};

	private void broadcastUpdate(final String action) {
		final Intent intent = new Intent(action);
		sendBroadcast(intent);
	}

	private void broadcastUpdate(final String action,
			final BluetoothGattCharacteristic characteristic) {
		final Intent intent = new Intent(action);
		// This is special handling for the Heart Rate Measurement profile.  Data parsing is
		// carried out as per profile specifications:
		// http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
		// For all other profiles, writes the data formatted in HEX.
		final byte[] data = characteristic.getValue();
		String charUuid = characteristic.getUuid().toString();
		String charId = null;
		if(charUuid.equals(IMXUuid.CHAR_ALERT_LEVEL)){
			charId = GlobalContacts.ALERT_LEVEL;
		}else if(charUuid.equals(IMXUuid.CHAR_MANUFACTURER_NAME_STRING)){
			charId = GlobalContacts.MANFACTURER_NAME;
		}else if(charUuid.equals(IMXUuid.CHAR_MODEL_NUMBER_STRING)){
			charId = GlobalContacts.MODEL_NAME;
		}else if(charUuid.equals(IMXUuid.CHAR_SERIAL_NUMBER_STRING)){
			charId = GlobalContacts.SERIAL_NUMBER;
		}else if(charUuid.equals(IMXUuid.CHAR_BATTERY_LEVEL)){
			charId = GlobalContacts.BATTERY_LEVEL;
		}else if(charUuid.equals(IMXUuid.CHAR_BATTER)){
			charId = GlobalContacts.HEART_RATE;
		}else if(charUuid.equals(IMXUuid.CHAR_DATE)){
			charId = GlobalContacts.REMOTE_DATE;
		}else if(charUuid.equals(IMXUuid.CHAR_MESSAGE)){
			charId = GlobalContacts.CUSTOM_MESSAGE;
		}else if(charUuid.equals(IMXUuid.CHAR_CPU_TEMP)){
			charId = GlobalContacts.CPU_TEMPERATURE;
		}
		intent.putExtra(EXTRA_NAME, charId);
		intent.putExtra(EXTRA_DATA, new String(data));
		sendBroadcast(intent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return new BluetoothLeController();
	}

	class BluetoothLeController extends	Binder implements BluetoothLeInterface{

		@Override
		public boolean connect(String deviceAddress) {
			return BluetoothLeService.this.connect(deviceAddress);
		}

		@Override
		public boolean initialize() {
			return BluetoothLeService.this.initialize();
		}

		@Override
		public List<BluetoothGattService> getSupportedGattServices() {
			return BluetoothLeService.this.getSupportedGattServices();
		}

		@Override
		public void disconnect(String mDeviceAddress) {
			BluetoothLeService.this.disconnect();
		}

		@Override
		public BluetoothGatt getGatt() {
			return BluetoothLeService.this.getGatt();
		}
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// After using a given device, you should make sure that BluetoothGatt.close() is called
		// such that resources are cleaned up properly.  In this particular example, close() is
		// invoked when the UI is disconnected from the Service.
		close();
		return super.onUnbind(intent);
	}


	public boolean initialize() {
		// For API level 18 and above, get a reference to BluetoothAdapter through
		// BluetoothManager.
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return false;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return false;
		}

		return true;
	}

	public boolean connect(final String address) {
		Log.e(TAG, "connect");
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		// We want to directly connect to the device, so we are setting the autoConnect
		// parameter to false.
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;
		mConnectionState = STATE_CONNECTING;
		return true;
	}

	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
	}

	public void close() {
		if (mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.readCharacteristic(characteristic);
	}

	public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
			boolean enabled) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

		// This is specific to Heart Rate Measurement.
		if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
					UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
			descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}
	}

	public List<BluetoothGattService> getSupportedGattServices() {
		if (mBluetoothGatt == null) 
			return null;
		return mBluetoothGatt.getServices();
	}

	public BluetoothGatt getGatt(){
		return  mBluetoothGatt;
	}
}
