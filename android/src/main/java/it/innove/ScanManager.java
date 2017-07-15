package it.innove;


import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class ScanManager {

	protected BluetoothAdapter bluetoothAdapter;
	protected Context context;
	protected ReactContext reactContext;
	protected BleManager bleManager;
	protected AtomicInteger scanSessionId = new AtomicInteger();

	public ScanManager(ReactApplicationContext reactContext, Context con,BleManager bleManager) {
		context = con;
		this.reactContext = reactContext;
		this.bleManager = bleManager;
	}

	protected BluetoothAdapter getBluetoothAdapter() {
		if (bluetoothAdapter == null) {
			android.bluetooth.BluetoothManager manager = (android.bluetooth.BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
			bluetoothAdapter = manager.getAdapter();
		}
		return bluetoothAdapter;
	}

	public abstract void stopScan(Callback callback);

	public abstract void scan(ReadableArray serviceUUIDs, final int scanSeconds, Callback callback);
}
