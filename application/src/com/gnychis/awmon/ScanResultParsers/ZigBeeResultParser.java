package com.gnychis.awmon.ScanResultParsers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;

import com.gnychis.awmon.Core.Packet;
import com.gnychis.awmon.DeviceAbstraction.WirelessRadio;
import com.gnychis.awmon.HardwareHandlers.ZigBee;

public class ZigBeeResultParser extends ScanResultParser {
	
	public <T extends Object> ArrayList<WirelessRadio> returnDevices(ArrayList<T> scanResult) {
				
	    // For keeping track of the APs that we have already parsed, by MAC
	    Hashtable<String,WirelessRadio> devs_in_list = new Hashtable<String,WirelessRadio>();	    
	    ArrayList<WirelessRadio> devices = new ArrayList<WirelessRadio>();
	    
	    // Go through each scan result, and get the access point information
	    Iterator<T> results = scanResult.iterator();
	    while(results.hasNext()) {
	    	Packet pkt = (Packet) results.next();
	    	WirelessRadio dev = new WirelessRadio(WirelessRadio.Type.ZigBee);
	    	
	    	// If it's a bad packet, ignore
	    	if(pkt.getField("wpan.fcs_ok").equals("0"))
	    		continue;    	
	    	
	    	dev._MAC = pkt.getField("wpan.src16");
	    	dev._RSSI.add(pkt._lqi);
	    	dev._frequency = ZigBee.chanToFreq(pkt._band);
	    	dev._SSID = pkt.getField("wpan.src_pan");	
	    	
	    	// Keep the device if we don't already have a record for it
	    	if(!devs_in_list.containsKey(dev._MAC)) {
	    		devs_in_list.put(dev._MAC, dev);  // mark that we've seen it
	    		devices.add(dev);
	    	} else {  // we already have it, but we can add multiple RSSI readings
	    		WirelessRadio tdev = devs_in_list.get(dev._MAC);
	    		tdev._RSSI.add(pkt._lqi);
	    	}
	    }

	    // Save this scan as our most current scan
	    Collections.sort(devices, WirelessRadio.compareRSSI);
		
		return devices;
	}
}
