package it.innove;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import org.json.JSONException;

import java.util.*;

import static android.app.Activity.RESULT_OK;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;


public class BleManager extends ReactContextBaseJavaModule implements ActivityEventListener {

	public static final String LOG_TAG = "logs";
	private static final int ENABLE_REQUEST = 539;


	private BluetoothAdapter bluetoothAdapter;
	private Context context;
	private ReactApplicationContext reactContext;
	private Callback enableBluetoothCallback;
	private ScanManager scanManager;

	// key is the MAC Address
	public Map<String, Peripheral> peripherals = new LinkedHashMap<>();
	// scan session id


	public BleManager(ReactApplicationContext reactContext) {
		super(reactContext);
		context = reactContext;
		this.reactContext = reactContext;
		if(this.reactContext!=null) {
			reactContext.addActivityEventListener(this);
		}
		Log.d(LOG_TAG, "BleManager created");
	}
	public void setctx(Context con){
		context = con;
	}


	@Override
	public String getName() {
		return "BleManager";
	}

	private BluetoothAdapter getBluetoothAdapter() {
		if (bluetoothAdapter == null) {
			BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
			bluetoothAdapter = manager.getAdapter();
		}
		return bluetoothAdapter;
	}

	public void sendEvent(String eventName,
						   @Nullable WritableMap params) {
        if(reactContext!=null)
		getReactApplicationContext()
				.getJSModule(RCTNativeAppEventEmitter.class)
				.emit(eventName, params);
	}

	@ReactMethod
	public void start(ReadableMap options, Callback callback) {
		Log.d(LOG_TAG, "start");
		boolean restart = false;
		if (options!=null&&options.hasKey("restart")) {
			restart = options.getBoolean("restart");
			if(restart){
				Log.d(LOG_TAG, "restart");
				bluetoothAdapter=null;
				scanManager=null;
                try {
                    context.unregisterReceiver(mReceiver);
                }catch (Exception e){}
			}
		}
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
        Log.d(LOG_TAG, "options "+options.toString());
		boolean forceLegacy = true;
		if (options!=null&&options.hasKey("forceLegacy")) {
			forceLegacy = options.getBoolean("forceLegacy");
		}

		if (Build.VERSION.SDK_INT >= LOLLIPOP && !forceLegacy) {
            Log.d(LOG_TAG, "LollipopScanManager");
			scanManager = new LollipopScanManager(reactContext, context,this);
		} else {
			Log.d(LOG_TAG, "LegacyScanManager");
			scanManager = new LegacyScanManager(reactContext, context,this);
		}
		IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		context.registerReceiver(mReceiver, filter);
		callback.invoke();
		Log.d(LOG_TAG, "BleManager initialized");
	}

	@ReactMethod
	public void enableBluetooth(Callback callback) {
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		if (!getBluetoothAdapter().isEnabled()) {
			enableBluetoothCallback = callback;
			Intent intentEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			if (getCurrentActivity() == null)
				callback.invoke("Current activity not available");
			else
				getCurrentActivity().startActivityForResult(intentEnable, ENABLE_REQUEST);
		} else
			callback.invoke();
	}

	@ReactMethod
	public void scan(ReadableArray serviceUUIDs, final int scanSeconds, boolean allowDuplicates, Callback callback) {
		Log.d(LOG_TAG, "scan ");
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		if (!getBluetoothAdapter().isEnabled())
			return;
		synchronized(peripherals) {
            if(reactContext!=null) {
                for (Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<String, Peripheral> entry = iterator.next();
                    if (!entry.getValue().isConnected()) {
                        iterator.remove();
                    }
                }
            }
		}

		scanManager.scan(serviceUUIDs, scanSeconds, callback);
	}

	@ReactMethod
	public void stopScan(Callback callback) {
		Log.d(LOG_TAG, "Stop scan");
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		if (!getBluetoothAdapter().isEnabled()) {
			callback.invoke("Bluetooth not enabled");
			return;
		}
		scanManager.stopScan(callback);
	}

	@ReactMethod
	public void connect(String peripheralUUID, Callback callback) {
		Log.d(LOG_TAG, "Connect to: " + peripheralUUID );

		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(peripheralUUID);
			Log.e(LOG_TAG, "peripheral " + peripheral);
			if (peripheral == null) {
				if (peripheralUUID != null) {
					peripheralUUID = peripheralUUID.toUpperCase();
				}
				if (BluetoothAdapter.checkBluetoothAddress(peripheralUUID)) {
					BluetoothDevice device = bluetoothAdapter.getRemoteDevice(peripheralUUID);
					peripheral = new Peripheral(device, reactContext);
					peripherals.put(peripheralUUID, peripheral);
				} else {
					callback.invoke("Invalid peripheral uuid");
					return;
				}
			}
			peripheral.connect(callback, reactContext!=null?getCurrentActivity():context);
		}
	}

	@ReactMethod
	public void disconnect(String peripheralUUID, Callback callback) {
		Log.d(LOG_TAG, "Disconnect from: " + peripheralUUID);

		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(peripheralUUID);
			if (peripheral != null) {
				peripheral.disconnect();
				callback.invoke();
				Log.d(LOG_TAG, "disconnected " + peripheralUUID);
			} else {
				callback.invoke(peripheralUUID + " Peripheral not found");
				Log.e(LOG_TAG, peripheralUUID + " Peripheral not found");
			}
		}
	}

	@ReactMethod
	public void startNotification(String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
		Log.d(LOG_TAG, "startNotification "+deviceUUID);

		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(deviceUUID);
			if (peripheral != null) {
				peripheral.registerNotify(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
			} else
				callback.invoke("Peripheral not found");
		}
	}

	@ReactMethod
	public void stopNotification(String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
		Log.d(LOG_TAG, "stopNotification");

		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(deviceUUID);
			if (peripheral != null) {
				peripheral.removeNotify(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
			} else
				callback.invoke("Peripheral not found");
		}
	}



	@ReactMethod
	public void write(String deviceUUID, String serviceUUID, String characteristicUUID, String message, Integer maxByteSize, Callback callback) {
		Log.d(LOG_TAG, "Write to: " + deviceUUID);

		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(deviceUUID);
			if (peripheral != null) {
				byte[] decoded = Base64.decode(message.getBytes(), Base64.DEFAULT);
				Log.d(LOG_TAG, "Message(" + decoded.length + "): " + bytesToHex(decoded));
				peripheral.write(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), decoded, maxByteSize, null, callback, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
			} else
				callback.invoke("Peripheral not found");
		}
	}

	@ReactMethod
	public void writeWithoutResponse(String deviceUUID, String serviceUUID, String characteristicUUID, String message, Integer maxByteSize, Integer queueSleepTime, Callback callback) {
		Log.d(LOG_TAG, "Write without response to: " + deviceUUID);

		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(deviceUUID);
			if (peripheral != null) {
				byte[] decoded = Base64.decode(message.getBytes(), Base64.DEFAULT);
				Log.d(LOG_TAG, "Message(" + decoded.length + "): " + bytesToHex(decoded));
				peripheral.write(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), decoded, maxByteSize, queueSleepTime, callback, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
			} else
				callback.invoke("Peripheral not found");
		}
	}

	@ReactMethod
	public void read(String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
		Log.d(LOG_TAG, "Read from: " + deviceUUID);
		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(deviceUUID);
			if (peripheral != null) {
				peripheral.read(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
			} else
				callback.invoke("Peripheral not found", null);
		}
	}

	@ReactMethod
	public void readRSSI(String deviceUUID,  Callback callback) {
		Log.d(LOG_TAG, "Read RSSI from: " + deviceUUID);
		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(deviceUUID);
			if (peripheral != null) {
				peripheral.readRSSI(callback);
			} else
				callback.invoke("Peripheral not found", null);
		}
	}
/*
	private BluetoothAdapter.LeScanCallback mLeScanCallback =
			new BluetoothAdapter.LeScanCallback() {


				@Override
				public void onLeScan(final BluetoothDevice device, final int rssi,
									 final byte[] scanRecord) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Log.i(LOG_TAG, "DiscoverPeripheral: " + device.getName());
							String address = device.getAddress();

							if (!peripherals.containsKey(address)) {

								Peripheral peripheral = new Peripheral(device, rssi, scanRecord, reactContext);
								peripherals.put(device.getAddress(), peripheral);
								Log.e(LOG_TAG, "peripherals: put ");

								try {
									Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
									WritableMap map = Arguments.fromBundle(bundle);
									sendEvent("BleManagerDiscoverPeripheral", map);
								} catch (JSONException ignored) {

								}

							} else {
								// this isn't necessary
								Peripheral peripheral = peripherals.get(address);
								peripheral.updateRssi(rssi);
								Log.e(LOG_TAG, "peripherals: update ");
							}
						}
					});
				}


			};
*/
	@ReactMethod
	public void checkState(){
		Log.d(LOG_TAG, "checkState");

		BluetoothAdapter adapter = getBluetoothAdapter();
		String state = "off";
		if (adapter != null) {
			switch (adapter.getState()) {
				case BluetoothAdapter.STATE_ON:
					state = "on";
					break;
				case BluetoothAdapter.STATE_OFF:
					state = "off";
			}
		}

		WritableMap map = Arguments.createMap();
		map.putString("state", state);
		Log.d(LOG_TAG, "state:" + state);
		sendEvent("BleManagerDidUpdateState", map);
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(LOG_TAG, "onReceive");
			final String action = intent.getAction();

			String stringState = "";
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
						BluetoothAdapter.ERROR);
				switch (state) {
					case BluetoothAdapter.STATE_OFF:
						stringState = "off";
						break;
					case BluetoothAdapter.STATE_TURNING_OFF:
						stringState = "turning_off";
						break;
					case BluetoothAdapter.STATE_ON:
						stringState = "on";
						break;
					case BluetoothAdapter.STATE_TURNING_ON:
						stringState = "turning_on";
						break;
				}
			}

			WritableMap map = Arguments.createMap();
			map.putString("state", stringState);
			Log.d(LOG_TAG, "state: " + stringState);
			sendEvent("BleManagerDidUpdateState", map);
		}
	};

	@ReactMethod
	public void getDiscoveredPeripherals(Callback callback) {
		Log.d(LOG_TAG, "Get discovered peripherals");
		synchronized(peripherals) {
			WritableArray map = Arguments.createArray();
			for (Map.Entry<String, Peripheral> entry : peripherals.entrySet()) {
				Peripheral peripheral = entry.getValue();
				try {
					Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
					WritableMap jsonBundle = Arguments.fromBundle(bundle);
					map.pushMap(jsonBundle);
				} catch (JSONException ignored) {
					callback.invoke("Peripheral json conversion error", null);
				}
			}
			Log.d(LOG_TAG, "peripherals " + map.toString());
			callback.invoke(null, map);
		}
	}

	@ReactMethod
	public void getConnectedPeripherals(ReadableArray serviceUUIDs, Callback callback) {
		Log.d(LOG_TAG, "Get connected peripherals");
		synchronized(peripherals) {
			WritableArray map = Arguments.createArray();
			for (Map.Entry<String, Peripheral> entry : peripherals.entrySet()) {
				Peripheral peripheral = entry.getValue();
				Boolean accept = false;

				if (serviceUUIDs != null && serviceUUIDs.size() > 0) {
					for (int i = 0; i < serviceUUIDs.size(); i++) {
						accept = peripheral.hasService(UUIDHelper.uuidFromString(serviceUUIDs.getString(i)));
					}
				} else {
					accept = true;
				}

				if (peripheral.isConnected() && accept) {
					try {
						Bundle bundle = BundleJSONConverter.convertToBundle(peripheral.asJSONObject());
						WritableMap jsonBundle = Arguments.fromBundle(bundle);
						map.pushMap(jsonBundle);
					} catch (JSONException ignored) {
						callback.invoke("Peripheral json conversion error", null);
					}
				}
			}
			Log.d(LOG_TAG, "Get connected peripherals --》" + map.toString());
			callback.invoke(null, map);
		}
	}

	@ReactMethod
	public void removePeripheral(String deviceUUID, Callback callback) {
		Log.e(LOG_TAG, "Removing from list: " + deviceUUID);
		synchronized(peripherals) {
			Peripheral peripheral = peripherals.get(deviceUUID);
			if (peripheral != null) {
				if (peripheral.isConnected()) {
					callback.invoke("Peripheral can not be removed while connected");
				} else {
					peripherals.remove(deviceUUID);
				}
			} else
				callback.invoke("Peripheral not found");
		}
  }

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	@Override
	public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
		Log.d(LOG_TAG, "onActivityResult");
		if (requestCode == ENABLE_REQUEST && enableBluetoothCallback != null) {
			if (resultCode == RESULT_OK) {
				enableBluetoothCallback.invoke();
			} else {
				enableBluetoothCallback.invoke("User refused to enable");
			}
			enableBluetoothCallback = null;
		}
	}

	@Override
	public void onNewIntent(Intent intent) {

	}

}
