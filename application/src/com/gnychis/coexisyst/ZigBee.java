package com.gnychis.coexisyst;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Semaphore;

import org.jnetpcap.PcapHeader;
import org.jnetpcap.nio.JBuffer;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Message;
import android.util.Log;

import com.gnychis.coexisyst.CoexiSyst.ThreadMessages;
import com.stericson.RootTools.RootTools;

public class ZigBee {
	private static final String TAG = "ZigbeeDev";

	public static final int ZIGBEE_CONNECT = 200;
	public static final int ZIGBEE_DISCONNECT = 201;
	public static final String ZIGBEE_SCAN_RESULT = "com.gnychis.coexisyst.ZIGBEE_SCAN_RESULT";
	public static final int MS_SLEEP_UNTIL_PCAPD = 5000;
	
	CoexiSyst coexisyst;
	
	boolean _device_connected;
	ZigBeeMon _monitor_thread;
	protected ChannelScanner _cscan_thread;
	
	static int WTAP_ENCAP_802_15 = 230;
	
	ZigBeeState _state;
	private Semaphore _state_lock;
	public enum ZigBeeState {
		IDLE,
		SCANNING,
	}
	
	ArrayList<Packet> _scan_results;
	
	// http://en.wikipedia.org/wiki/List_of_WLAN_channels
	int[] channels = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
	
	// Set the state to scan and start to switch channels
	public boolean APScan() {
		
		// Only allow to enter scanning state IF idle
		if(!ZigBeeStateChange(ZigBeeState.SCANNING))
			return false;
		
		_scan_results.clear();
		
		_cscan_thread = new ChannelScanner(200);	// time to wait on each channel as parameter
		_cscan_thread.execute();
		
		return true;  // in scanning state, and channel hopping
	}
	
	public boolean APScanStop() {
		// Need to return the state back to IDLE from scanning
		if(!ZigBeeStateChange(ZigBeeState.IDLE)) {
			Log.d(TAG, "Failed to change from scanning to IDLE");
			return false;
		}
		
		// Now, send out a broadcast with the results
		Intent i = new Intent();
		i.setAction(ZIGBEE_SCAN_RESULT);
		i.putExtra("packets", _scan_results);
		coexisyst.sendBroadcast(i);
		
		return true;
	}
	
	// Attempts to change the current state, will return
	// the state after the change if successful/failure
	public boolean ZigBeeStateChange(ZigBeeState s) {
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
					if(s==ZigBeeState.IDLE) {  // cannot go directly to IDLE from SCANNING
						Log.d(TAG, "Switching state from " + _state.toString() + " to " + s.toString());
						_state = s;
						res = true;
					} else if(s==ZigBeeState.SCANNING) {  // ignore an attempt to switch in to same state
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
	
	public ZigBee(CoexiSyst c) {
		_state_lock = new Semaphore(1,true);
		_scan_results = new ArrayList<Packet>();
		coexisyst = c;
		_state = ZigBeeState.IDLE;
		Log.d(TAG, "Initializing ZigBee class...");

	}
	
	public void connected() {
		_device_connected=true;
		coexisyst.zigbee._monitor_thread = new ZigBeeMon();
		coexisyst.zigbee._monitor_thread.execute(coexisyst);
	}
	
	public void disconnected() {
		_device_connected=false;
		coexisyst.zigbee._monitor_thread.cancel(true);
	}
	
	public static byte[] parseMacAddress(String macAddress)
    {
        String[] bytes = macAddress.split(":");
        byte[] parsed = new byte[bytes.length];

        for (int x = 0; x < bytes.length; x++)
        {
            BigInteger temp = new BigInteger(bytes[x], 16);
            byte[] raw = temp.toByteArray();
            parsed[x] = raw[raw.length - 1];
        }
        return parsed;
    }
	
	public static BigInteger parseMacStringToBigInteger(String macAddress)
	{
		String newMac = macAddress.replaceAll(":", "");
		BigInteger ret = new BigInteger(newMac, 16);  // 16 specifies hex
		return ret;
	}

	
	protected class ZigBeeMon extends AsyncTask<Context, Integer, String>
	{
		Context parent;
		CoexiSyst coexisyst;
		Socket skt;
		InputStream skt_in;
		OutputStream skt_out;
		private static final String ZIGMON_TAG = "ZigBeeMonitor";
		private int PCAP_HDR_SIZE = 16;
		Zigcapd zigcapd_thread;
		int _channel;
		
		// Incoming commands
		byte CHANGE_CHAN=0x0000;
		byte TRANSMIT_PACKET=0x0001;
		byte RECEIVED_PACKET=0x0002;
		byte INITIALIZED=0x0003;
		
		// On pre-execute, we make sure that we initialize the card properly and set the state to IDLE
		@Override 
		protected void onPreExecute( )
		{
			_state = ZigBeeState.IDLE;			
		}
		
		// Used to send messages to the main Activity (UI) thread
		protected void sendMainMessage(CoexiSyst.ThreadMessages t) {
			Message msg = new Message();
			msg.obj = t;
			coexisyst._handler.sendMessage(msg);
		}
		
		@Override
		protected void onCancelled()
		{
			Log.d(TAG, "ZigBee monitor thread is canceled...");
			try {
				RootTools.sendShell("busybox killall zigcapd");
			} catch(Exception e) {
				
			}
		}
		
		// The entire meat of the thread, pulls packets off the interface and dissects them
		@Override
		protected String doInBackground( Context ... params )
		{
			parent = params[0];
			coexisyst = (CoexiSyst) params[0];

			// Connect to the pcap daemon to pull packets from the hardware
			if(connectToZigcapd() == false) {
				Log.d(TAG, "failed to connect to the pcapd daemon, doh");
				sendMainMessage(ThreadMessages.ZIGBEE_FAILED);
				return "FAIL";
			}
			sendMainMessage(ThreadMessages.ZIGBEE_WAIT_RESET);
			
			// Wait for the initialized byte
			if(getSocketData(1)[0]!=INITIALIZED) {
				sendMainMessage(ThreadMessages.ZIGBEE_FAILED);
				return "FAIL";
			}
			
			sendMainMessage(ThreadMessages.ZIGBEE_INITIALIZED);
			
			// Send a command to set the channel to 1
			setChannel(1);
						
			// Loop and read headers and packets
			while(true) {
				byte cmd = getSocketData(1)[0];
				
				// Wait for a byte which signals a command
				if(cmd==RECEIVED_PACKET) {
				
					Packet rpkt = new Packet(WTAP_ENCAP_802_15);
	
					if((rpkt._rawHeader = getPcapHeader())==null) {
						zigcapd_thread.cancel(true);
						return "error reading pcap header";
					}
					rpkt._headerLen = rpkt._rawHeader.length;
									
					// Get the raw data now from the wirelen in the pcap header
					if((rpkt._rawData = getPcapPacket(rpkt._rawHeader))==null) {
						zigcapd_thread.cancel(true);
						return "error reading data";
					}
					rpkt._dataLen = rpkt._rawData.length;
					
					// Get the rx time
					getSocketData(4);
					
					// Get the LQI
					rpkt._lqi = (int)getSocketData(1)[0];
					
					Log.d(TAG, "Got ZigBee packet on channel " + Integer.toString(_channel) + " with LQI: " + Integer.toString(rpkt._lqi));
					
					// Based on the state of our wifi thread, we determine what to do with the packet
					switch(_state) {
					
					case IDLE:
						break;
					
					// In the scanning state, we save all beacon frames as we hop channels (handled by a
					// separate thread).
					case SCANNING:
						// To identify beacon: wlan_mgt.fixed.beacon is set.  If it is a beacon, add it
						// to our scan result.  This does not guarantee one beacon frame per network, but
						// pruning can be done at the next level.
						//if(rpkt.getField("wlan_mgt.fixed.beacon")!=null)
						//	_scan_results.add(rpkt);
						
						break;
					}
				}
			}
		}
		
		public boolean setChannel(int channel) {
			try {
				skt_out.write(CHANGE_CHAN);		// first send the command
				skt_out.write(channel);	// then send the channel
			} catch(Exception e) { 
				return false;
			}
			_channel = channel;
			return true;
		}
		
		public boolean connectToZigcapd() {
			
			// Generate a random port for Pcapd
			Random generator = new Random();
			int pcapd_port = 2000 + generator.nextInt(500);
			
			Log.d(ZIGMON_TAG, "a new Wifi monitor thread was started");
			
			// Attempt to create capture process spawned in the background
			// which we will connect to for pcap information.
			zigcapd_thread = new Zigcapd(pcapd_port);
			zigcapd_thread.execute(coexisyst);
			
			// Send a message to block the main dialog after the card is done initializing
			try { Thread.sleep(MS_SLEEP_UNTIL_PCAPD); } catch (Exception e) {} // give some time for the process
			
			// Attempt to connect to the socket via TCP for the PCAP info
			try {
				skt = new Socket("localhost", pcapd_port);
			} catch(Exception e) {
				Log.e(ZIGMON_TAG, "exception trying to connect to wifi socket for pcap on " + Integer.toString(pcapd_port), e);
				return false;
			}
			
			try {
				skt_in = skt.getInputStream();
				skt_out = skt.getOutputStream();
			} catch(Exception e) {
				Log.e(ZIGMON_TAG, "exception trying to get inputbuffer from socket stream");
				return false;
			}
			Log.d(ZIGMON_TAG, "successfully connected to pcapd on port " + Integer.toString(pcapd_port));
			return true;
		}
		
		public byte[] getPcapHeader() {
			byte[] rawdata = getSocketData(PCAP_HDR_SIZE);
			return rawdata;
		}
		
		public byte[] getPcapPacket(byte[] rawHeader) {
			byte[] rawdata;
			PcapHeader header = null;

			try {
				header = new PcapHeader();
				JBuffer headerBuffer = new JBuffer(rawHeader);  
				header.peer(headerBuffer, 0);				
			} catch(Exception e) {
				Log.e("WifiMon", "exception trying to read pcap header",e);
			}
			
			rawdata = getSocketData(header.wirelen());
			return rawdata;
		}
		
		public byte[] getSocketData(int length) {
			byte[] data = new byte[length];
			int v=0;
			try {
				int total=0;
				while(total < length) {
					v = skt_in.read(data, total, length-total);
					//Log.d("WifiMon", "Read in " + Integer.toString(v) + " - " + Integer.toString(total+v) + " / " + Integer.toString(length));
					if(v==-1)
						cancel(true);  // cancel the thread if we have errors reading socket
					total+=v;
				}
			} catch(Exception e) { 
				Log.e("WifiMon", "unable to read from pcapd buffer",e);
				return null;
			}
			
			return data;
		}
	}
	
	////////////////////////////////////////////////////////////////////////////////
	// ChannelScanner: a class which instantiates a new thread to issues commands
	//     which changes the channel of the Atheros card.  This allows packet
	//     capture to continue smoothly, as the channel hops in the background.
	protected class ChannelScanner extends AsyncTask<Integer, Integer, String>
	{
		private static final String TAG = "WiFiChannelManager";
		
		private int _scan_interval;  // in milliseconds, time-per-channel
		
		public ChannelScanner(int scan_interval) {
			_scan_interval = scan_interval;
		}
		
		public ChannelScanner() {
			_scan_interval = 110;  // default value
		}

		
		@Override
		protected String doInBackground( Integer ... params )
		{
			Log.d(TAG, "a new Wifi channel manager thread was started");
			
			try {

			} catch(Exception e) {
				Log.e(TAG, "error trying to scan channels", e);
			}
			
			// Alerts the main thread that the scanning has stopped, by changing the state and
			// saving the relevant data
			if(APScanStop())
				return "OK";
			else
				return "FAIL";
		}
	}
	
}
