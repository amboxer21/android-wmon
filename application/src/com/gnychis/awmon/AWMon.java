package com.gnychis.awmon;

// do a random port number for pcapd

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.gnychis.awmon.BackgroundService.BackgroundService;
import com.gnychis.awmon.BackgroundService.BackgroundService.BackgroundServiceBinder;
import com.gnychis.awmon.Core.DBAdapter;
import com.gnychis.awmon.Core.UserSettings;
import com.gnychis.awmon.Interfaces.ManageNetworks;
import com.gnychis.awmon.Interfaces.Status;
import com.gnychis.awmon.Interfaces.Welcome;
import com.stericson.RootTools.RootTools;

public class AWMon extends Activity implements OnClickListener {
	
	private static final String TAG = "AWMon";
	public static String _app_name = "com.gnychis.awmon";
	public static final String THREAD_MESSAGE = "awmon.thread.message";
	
	// Internal Android mechanisms for settings/storage
	public DBAdapter _db;
	public UserSettings _settings;
	
	// Related to communication and tracking of the background service
	public BackgroundService _backgroundService;
	private boolean mBound=false;
	
	// Interface related
	private ProgressDialog _pd;
	public TextView textStatus;
		
	public enum ThreadMessages {
		WIFI_SCAN_START,
		WIFI_SCAN_COMPLETE,
		
		WIFIDEV_CONNECTED,
		WIFIDEV_INITIALIZED,
		WIFIDEV_FAILED,
		
		ZIGBEE_CONNECTED,
		ZIGBEE_INITIALIZED,
		ZIGBEE_FAILED,
		ZIGBEE_WAIT_RESET,
		ZIGBEE_SCAN_COMPLETE,
		
		BLUETOOTH_SCAN_COMPLETE,
		
		SHOW_TOAST,
		INCREMENT_SCAN_PROGRESS,
		NETWORK_SCANS_COMPLETE,
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
           	
        // Setup the database
    	_db = new DBAdapter(this);
    	_db.open();
    	
    	// Initialize the user settings
    	_settings = new UserSettings(this);
      
		// Setup UI
		textStatus = (TextView) findViewById(R.id.textStatus);
		textStatus.setText("");
		((Button) findViewById(R.id.buttonAddNetwork)).setOnClickListener(this);
		((Button) findViewById(R.id.buttonManageDevs)).setOnClickListener(this);
		((Button) findViewById(R.id.buttonSettings)).setOnClickListener(this);
		((Button) findViewById(R.id.buttonStatus)).setOnClickListener(this);
		
    	// Start the background service.  This MUST go after the linking of the libraries.
        BackgroundService.setMainActivity(this);
        startService(new Intent(this, BackgroundService.class));
    }
    
    // This is called when we are bound to the background service, which allows us to check
    // its state and know what to do with the main activity.
    public void serviceBound() {
    	if(!mBound)
    		return;
    	
    	// If the background service is initializing, pop up a spinner which we will cancel after initialized
    	if(_backgroundService.getSystemState()==BackgroundService.ServiceState.INITIALIZING)
    		_pd = ProgressDialog.show(AWMon.this, "", "Initializing application, please wait...", true, false); 
    	
    	// If the background service is already initialized, then we can go ahead call the post-init
    	if(_backgroundService.getSystemState()==BackgroundService.ServiceState.IDLE)
    		systemInitialized();
    }
    
    // This runs after the initialization of the libraries, etc.
    public void systemInitialized() {
    	
    	if(_pd!=null)
    		_pd.dismiss();

    	if(_settings.haveUserSettings())  // Do we have user settings?
    		return;
    	
    	// If we do not have the user settings, we open up an activity to query for them
		Intent i = new Intent(AWMon.this, Welcome.class);
        startActivity(i);
    }
    
    // A broadcast receiver to get messages from background service and threads
    private BroadcastReceiver _initializedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
        	systemInitialized(); 	
        }
    }; 
    
    // A broadcast receiver to get messages from background service and threads
    private BroadcastReceiver _messageReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
        	ThreadMessages tm = (ThreadMessages) intent.getExtras().get("type");
        	
        	switch(tm) {
        		case SHOW_TOAST:
        			String msg = (String) intent.getExtras().get("msg");
        			Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        		break;
        	}     	
        }
    }; 
    
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BackgroundServiceBinder binder = (BackgroundServiceBinder) service;
            _backgroundService = binder.getService();
            mBound = true;
            serviceBound();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
    
    // Everything related to clicking buttons in the main interface
	public void onClick(View view) {
		Intent i;
		
		switch(view.getId()) {
			case R.id.buttonAddNetwork:
				clickAddNetwork();
				break;
			
			case R.id.buttonManageDevs:
				i = new Intent(AWMon.this, ManageNetworks.class);
		        startActivity(i);
				break;
				
			case R.id.buttonSettings:
				i = new Intent(AWMon.this, Welcome.class);
				startActivity(i);
				break;
				
			case R.id.buttonStatus:
				i = new Intent(AWMon.this, Status.class);
				startActivity(i);
				break;
		}
	}
	
	public void scanResultsAvailable() {
		
	}
	
	public void showProgressUpdate(String s) {
		_pd = ProgressDialog.show(this, "", s, true, false);  
	}
	

	static public ArrayList<String> runCommand(String c) {
		ArrayList<String> res = new ArrayList<String>();
		try {
			// First, run the command push the result to an ArrayList
			List<String> res_list = RootTools.sendShell(c,0);
			Iterator<String> it=res_list.iterator();
			while(it.hasNext()) 
				res.add((String)it.next());
			
			res.remove(res.size()-1);
			
			// Trim the ArrayList of an extra blank lines at the end
			while(true) {
				int index = res.size()-1;
				if(index>=0 && res.get(index).length()==0)
					res.remove(index);
				else
					break;
			}
			return res;
			
		} catch(Exception e) {
			Log.e("AWMon", "error writing to RootTools the command: " + c, e);
			return null;
		}
	}
    
    public String getAppUser() {
    	try {
    		List<String> res = RootTools.sendShell("ls -l /data/data | grep " + _app_name,0);
    		return res.get(0).split(" ")[1];
    	} catch(Exception e) {
    		return "FAIL";
    	}
    }
    
    
    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Intent intent = new Intent(this, BackgroundService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
    
	@Override
	public void onResume() { 
		super.onResume(); 
		registerReceiver(_messageReceiver, new IntentFilter(AWMon.THREAD_MESSAGE));
		registerReceiver(_initializedReceiver, new IntentFilter(BackgroundService.SYSTEM_INITIALIZED));
	}
	public void onPause() { 
		super.onPause(); 
		Log.d(TAG, "onPause()"); 
		unregisterReceiver(_messageReceiver);
		unregisterReceiver(_initializedReceiver);
	}
	public void onDestroy() { super.onDestroy(); Log.d(TAG, "onDestroy()"); }

	// This triggers a scan through the networks to return a list of
	// networks and devices for a user to add for management.
	// FIXME: this should pop up the progress dialogue and just wait for the broadcast results
	public void clickAddNetwork() {
		
		int max_progress;
		
		// Do not start another scan, if we already are
		if(_backgroundService._deviceHandler._networks_scan.isScanning())
			return;
		
		// Create a progress dialog to show progress of the scan
		// to the user.
		_pd = new ProgressDialog(this);
		_pd.setCancelable(false);
		_pd.setMessage("Scanning for networks...");
		
		// Call the networks scan class to initiate a new scan
		// which, based on the devices connected for scanning,
		// will return a maximum value for the progress bar
		max_progress = _backgroundService._deviceHandler._networks_scan.initiateScan();
		if(max_progress==-1) {
			Toast.makeText(getApplicationContext(), "No networks available to scan!", Toast.LENGTH_LONG).show();
			return;
		}
		if(max_progress > 0) {
			_pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			_pd.setProgress(0);
			_pd.setMax(max_progress);
		}
		_pd.show();
		
	}
	
	// Used to send messages to the main Activity (UI) thread
	public static void sendMainMessage(Handler handler, AWMon.ThreadMessages t) {
		Message msg = new Message();
		msg.what = t.ordinal();
		handler.sendMessage(msg);
	}
	
	public native String[] getDeviceNames();
	public native String[] getWiSpyList();
	public native int USBcheckForDevice(int vid, int pid);
	public native void libusbTest();
	public native int pcapGetInterfaces();
	public native int dissectPacket(byte[] header, byte[] data, int encap);
	public native void dissectCleanup(int dissect_ptr);
	public native String wiresharkGet(int dissect_ptr, String param);
	public native void wiresharkTest(String filename);
	public native void wiresharkTestGetAll(String filename);
	public native String[] wiresharkGetAll(int dissect_ptr);
	public native void wiresharkGetAllTest(int dissect_ptr);	
		
}