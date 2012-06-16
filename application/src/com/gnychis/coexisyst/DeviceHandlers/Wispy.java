package com.gnychis.coexisyst.DeviceHandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Message;
import android.util.Log;

import com.gnychis.coexisyst.CoexiSyst;
import com.gnychis.coexisyst.CoexiSyst.ThreadMessages;
import com.gnychis.coexisyst.Core.Packet;
import com.gnychis.coexisyst.Core.USBSerial;
import com.gnychis.coexisyst.DeviceHandlers.ZigBee.ZigBeeInit;
import com.gnychis.coexisyst.DeviceHandlers.ZigBee.ZigBeeScan;
import com.gnychis.coexisyst.DeviceHandlers.ZigBee.ZigBeeState;
import com.stericson.RootTools.RootTools;

public class WiSpy {

	private static final String TAG = "WiSpyDev";
	private static final boolean VERBOSE = true;

	public static final int WISPY_CONNECT = 200;
	public static final int WISPY_DISCONNECT = 201;
	public static final String WISPY_SCAN_RESULT = "com.gnychis.coexisyst.WISPY_SCAN_RESULT";
	
	CoexiSyst coexisyst;
	
	public boolean _device_connected;
	WiSpyScan _monitor_thread;

	WiSpyState _state;
	private Semaphore _state_lock;
	public enum WiSpyState {
		IDLE,
		SCANNING,
	}
	
	ArrayList<Packet> _scan_results;
	
	public WiSpy(CoexiSyst c) {
		_state_lock = new Semaphore(1,true);
		_scan_results = new ArrayList<Packet>();
		coexisyst = c;
		_state = WiSpyState.IDLE;
		Log.d(TAG, "Initializing ZigBee class...");
	}
	
	public boolean isConnected() {
		return _device_connected;
	}
	
	public void connected() {
		_device_connected=true;
		WiSpyInit wsi = new WiSpyInit();
		wsi.execute(coexisyst);
	}
	
	public void disconnected() {
		_device_connected=false;
	}
	
	protected class WiSpyInit extends AsyncTask<Context, Integer, String>
	{
		Context parent;
		CoexiSyst coexisyst;
		USBSerial _dev;
		
		// The initialized sequence (hardware sends it when it is initialized)
		byte initialized_sequence[] = {0x67, 0x65, 0x6f, 0x72, 0x67, 0x65, 0x6e, 0x79, 0x63, 0x68, 0x69, 0x73};
		
		private void debugOut(String msg) {
			if(VERBOSE)
				Log.d("WiSpyInit", msg);
		}
		
		// Used to send messages to the main Activity (UI) thread
		protected void sendMainMessage(CoexiSyst.ThreadMessages t) {
			Message msg = new Message();
			msg.obj = t;
			coexisyst._handler.sendMessage(msg);
		}
		
		public boolean checkInitSeq(byte buf[]) {
			
			for(int i=0; i<initialized_sequence.length; i++)
				if(initialized_sequence[i]!=buf[i])
					return false;
						
			return true;
		}
		
		@Override
		protected String doInBackground( Context ... params )
		{
			parent = params[0];
			coexisyst = (CoexiSyst) params[0];
			
			if(initWiSpyDevices()!=1) {
				sendMainMessage(ThreadMessages.WISPY_FAILED);
				debugOut("Failed to initialize WiSpy device");
			}
				
			sendMainMessage(ThreadMessages.WISPY_INITIALIZED);
			debugOut("Successfully initialized WiSpy device");

			return "OK";
		}
		
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Below here is all related to the scanning functionality of the WiSpy device
	
	// Set the state to scan and start to switch channels
	public boolean scanStart() {
		
		// Only allow to enter scanning state IF idle
		if(!WiSpyStateChange(WiSpyState.SCANNING))
			return false;
		
		_scan_results.clear();
		
		_monitor_thread = new WiSpyScan();
		_monitor_thread.execute(coexisyst);
		
		return true;  // in scanning state, and channel hopping
	}
	
	// Attempts to change the current state, will return
	// the state after the change if successful/failure.
	// This is the state of the use of the hardware for scanning, whether
	// it is idle or it is currently scanning.
	public boolean WiSpyStateChange(WiSpyState s) {
		boolean res = false;
		if(_state_lock.tryAcquire()) {
			try {
				
				// Can add logic here to only allow certain state changes
				// Given a _state... then...
				switch(_state) {
				
				// From the IDLE state, we can go anywhere...
				case IDLE:
					Log.d(TAG, "Switching state from " + _state.toString() + " to " + s.toString());
					_state = s;
					res = true;
				break;
				
				// We can go to idle, or ignore if we are in a
				// scan already.
				case SCANNING:
					if(s==WiSpyState.IDLE) {  // cannot go directly to IDLE from SCANNING
						Log.d(TAG, "Switching state from " + _state.toString() + " to " + s.toString());
						_state = s;
						res = true;
					} else if(s==WiSpyState.SCANNING) {  // ignore an attempt to switch in to same state
						res = false;
					} 
				break;
				
				default:
					res = false;
				}
				
			} finally {
				_state_lock.release();
			}
		} 		
		
		return res;
	}
	
	protected class WiSpyScan extends AsyncTask<Context, Integer, String>
	{
		Context parent;
		CoexiSyst coexisyst;
		private Semaphore _comm_lock;
		
		// Used to send messages to the main Activity (UI) thread
		protected void sendMainMessage(CoexiSyst.ThreadMessages t) {
			Message msg = new Message();
			msg.obj = t;
			coexisyst._handler.sendMessage(msg);
		}
		
		private void debugOut(String msg) {
			if(VERBOSE)
				Log.d("WiSpyScan", msg);
		}
		
		// The entire meat of the thread, pulls packets off the interface and dissects them
		@Override
		protected String doInBackground( Context ... params )
		{
			parent = params[0];
			coexisyst = (CoexiSyst) params[0];
			_comm_lock = new Semaphore(1,true);		// FIXME: Not really sure if we need a comm lock here
			
			try {
				_comm_lock.acquire();
			} catch(Exception e) {
				_comm_lock.release();
				return "FAIL";
			}			
			
			// Initialize the array to do a "max" on the spectrum
			int max_results[];
			max_results = new int[256];
	        for(int i=0; i<256; i++)
	        	max_results[i]=-200;			
			
			for(int poll_count=0; poll_count<5; poll_count++) {
				int[] scan_res = pollWiSpy();
				
				if(scan_res==null) {
					sendMainMessage(ThreadMessages.WISPY_SCAN_FAILED);
					break;
				}				

				if(scan_res.length==256) {
					for(int i=0; i<scan_res.length; i++) {
						if(scan_res[i] > max_results[i]) 
							max_results[i] = scan_res[i];
					}
				} else {
					sendMainMessage(ThreadMessages.WISPY_SCAN_FAILED);
					debugOut("Failed WiSpy poll, the length was not 256");
					break;
				}

			}
			
			_comm_lock.release();
			
			return "PASS";
		}
		
	}
	
	public native int getWiSpy();
	public native String[] getWiSpyList();
	public native int initWiSpyDevices();
	public native int[] pollWiSpy();
}