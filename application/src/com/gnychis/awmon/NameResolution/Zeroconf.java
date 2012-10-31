package com.gnychis.awmon.NameResolution;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import android.util.Log;

import com.gnychis.awmon.Core.Device;

// Bonjour 
public class Zeroconf extends NameResolver {
	
	static final String TAG = "Zeroconf";
	static final boolean VERBOSE = true;
	
	android.net.wifi.WifiManager.MulticastLock lock;
    android.os.Handler handler = new android.os.Handler();
    private boolean _waitingOnResults;
    
    private String _listenType = "_workstation._tcp.local.";
    private JmDNS _jmdns = null;
    private ServiceListener _jmdnsListener = null;

	public Zeroconf(NameResolutionManager nrm) {
		super(nrm, Arrays.asList(Device.Type.Wifi));
	}

	// The application needs to request the multicast lock.  Without it the application will not
	// receive packets that are not addressed to it.  This should be disabled when the scan is complete.
	// Otherwise, you will get battery drain.
    private void setUp() {
        android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) _nr_manager._parent.getSystemService(android.content.Context.WIFI_SERVICE);
        lock = wifi.createMulticastLock("mylockthereturn");
        lock.setReferenceCounted(true);
        lock.acquire();
        try {
            _jmdns = JmDNS.create();
            _jmdns.addServiceListener(_listenType, _jmdnsListener = new ServiceListener() {

                @Override
                public void serviceResolved(ServiceEvent ev) {
                    debugOut("Service resolved: " + ev.getInfo().getQualifiedName() + " port:" + ev.getInfo().getPort());
                }

                @Override
                public void serviceRemoved(ServiceEvent ev) {
                    debugOut("Service removed: " + ev.getName());
                }

                @Override
                public void serviceAdded(ServiceEvent event) {
                    // Required to force serviceResolved to be called again (after the first search)
                    _jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        
        // Could create a 2 second timer or something to timeout on service discovery.
    }
    
    // Give up the multicast lock and teardown, this saves us battery usage.
    private void tearDown() {
    	if (_jmdns != null) {
            if (_jmdnsListener != null) {
                _jmdns.removeServiceListener(_listenType, _jmdnsListener);
                _jmdnsListener = null;
            }
            try {
                _jmdns.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            _jmdns = null;
    	}
    	//repo.stop();
        //s.stop();
        lock.release();
    }
	
	public ArrayList<Device> resolveSupportedDevices(ArrayList<Device> supportedDevices) {
		setUp();
		
		// Setup a handler to change the value of _waitingOnResults which blocks progress
		// until we have waited from results of a scan to trickle in.
		_waitingOnResults=true;
		handler.postDelayed(new Runnable() {
            public void run() {
                _waitingOnResults=false;
            }
            }, 3000);
		
		// We need to wait a bit for some results
		while(_waitingOnResults) { 
			try{ Thread.sleep(100); } catch(Exception e) {} 
		}
		
		tearDown();	// tear down the search for services
		
		return supportedDevices;
	}
	
	private void debugOut(String msg) {
		if(VERBOSE)
			Log.d(TAG, msg);
	}
}
