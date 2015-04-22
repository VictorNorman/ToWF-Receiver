// Receive Audio over the Network (socket) and Playback through the speakers/headphones

package com.briggs_inc.towf_receiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import static com.briggs_inc.towf_receiver.MiscConstants.*;

interface NetworkPlaybackServiceListener {
	public void onReceivingAudioStatusChanged(boolean isReceivingAudio);
	public void onPlaybackSpeedStatusChanged(int playbackSpeed);
	public void onNpServiceFinished();
	public void onAudioFormatChanged(AudioFormatStruct af);
    public void onMissingPacketRequestCreated(List<PcmAudioDataPayload> missingPackets);
}

public class NetworkPlaybackService extends IntentService implements PlaybackManagerListener {
	private static final String TAG = "NetPlayService";
	
    List<NetworkPlaybackServiceListener> listeners = new ArrayList<NetworkPlaybackServiceListener>();
    
	protected Context context;
	
    DatagramSocket sock;
    
    boolean isListening;
	private boolean isAudioFormatValid = true;  //???delete???

    long currNumReceivedAudioDataPackets;
	long lastNumReceivedAudioDataPackets;

    long totalNumSamplesWritten;

	private boolean isReceivingAudio;

	int streamPort = 0;

    DatagramPacket datagram;

	NetworkManager netMan;
	PlaybackManager pbMan;
	
	NpServiceBinder npServiceBinder = new NpServiceBinder();

	Timer receivingAudioCheckTimer = new Timer();
	
	WifiLock wifiLock;
    WakeLock wakeLock;


    public class CheckIfReceivingAudioTask extends TimerTask {
        @Override
        public void run() {
            if (currNumReceivedAudioDataPackets == lastNumReceivedAudioDataPackets) {
                if (isReceivingAudio) {
                    isReceivingAudio = false;
                    Log.v(TAG, "Not receiving audio...");
                    NetworkPlaybackService.this.onCurrentlyNotReceivingAudio();
                }
            }
            lastNumReceivedAudioDataPackets = currNumReceivedAudioDataPackets;
        }
    }
	
	public class NpServiceBinder extends Binder {
		public NetworkPlaybackService getService() {
			// Return this instance of NetworkPlaybackService so clients can call public methods
			return NetworkPlaybackService.this;
		}
	}
	
	public NetworkPlaybackService() {
		super("NetworkPlaybackService");
        pbMan = new PlaybackManager();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// Create our instance of Playback Manager

		Bundle extras = intent.getExtras();
		if (extras != null && intent.getExtras().getInt(STREAM_PORT_KEY) != 0) {
			streamPort = intent.getExtras().getInt(STREAM_PORT_KEY, 7770);
			pbMan.addListener(this);
            pbMan.setDesiredDelay(intent.getExtras().getFloat(DESIRED_DELAY_KEY, 1.0f));
            //pbMan.createNewSpeakerLine();
            pbMan.setSendMissingPacketRequestsChecked(intent.getExtras().getBoolean(SEND_MPRS_ENABLED_KEY, false));
		} else {
			Log.v(TAG, "ERROR! NetworkPlaybackService onHandleEvent() cannot get extras from it's intent. Unable to start service.");
			return;
		}
		
		// Create our instance of NetworkManager
     	try {
			netMan = new NetworkManager(streamPort);
		} catch (SocketException ex) {
			String msg = "ExNote: Unable to set a new NetworkManager with port: " + streamPort + "\nExMessage: " + ex.getMessage(); 
            Log.v(TAG, msg);
            Log.v(TAG, "ERROR: NetworkPlaybackService NOT started");
            //cleanUp();
            // After this destroy() gets called, right? If so, just let destroy() call cleanUp().
            return;  // We can't do anything without a connected network manger
		}
		
		// Basic init's
        currNumReceivedAudioDataPackets = 0;
        lastNumReceivedAudioDataPackets = 0;
        totalNumSamplesWritten = 0;
		
		// Wi-Fi Lock
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    	wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TowfWifiLock");  // Need HIGH_PERF (at least for my Atrix 4G), even though warning says MIN of API Level 12 is needed, and my Atrix 4G is at API Level 10.
    	Log.v(TAG, "Acquiring Wi-Fi Lock");
	    wifiLock.acquire();
	    
	    // Wake Lock - NOTE: not sure if this is necessary or not. Not needed on my Atrix 4G, but maybe other devices will need this lock.
	    PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
	    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TowfWakeLock");  // PARTIAL_WAKE_LOCK => CPU will always run, no matter what screen status is.
	    Log.v(TAG, "Acquiring Wake Lock");
	    wakeLock.acquire();
	    
        // Socket-related
        //Log.v(TAG, "Creating New DatagramSocket (for Broadcast)...");
     	
        isListening = true;

        // Start the ReceivingAudio timer
        receivingAudioCheckTimer.schedule(new CheckIfReceivingAudioTask(), 200, 200);  //100ms => about 10 fps 'refresh rate' //200ms => about 5 fps 'refresh rate'
        
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		Notification notification = new Notification();
		notification.tickerText = "ToWF Receiving Audio";
		notification.icon = android.R.drawable.ic_media_play;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.setLatestEventInfo(this, "ToWF", "Receiving Audio", pi);
        startForeground(NETWORK_PLAYBACK_SERVICE_FG_NOTIFICATION_ID, notification);
        
		while (isListening) {
			try {
				datagram = netMan.receiveDatagram();  // Hangs (blocks) here until packet is received (or times out)... (but doesn't use CPU resources while blocking)
                if (datagram == null || !isListening) {
                    continue;  // to next iteration of while loop
                }
			} catch (SocketTimeoutException ex) {
				//Log.d(TAG, "ExNote: SocketTimeoutException in NpService.\nExMessage: " + ex.getMessage());
				continue;  // to next while loop iteration
			} catch (SocketException ex) {  // for .setSoTimeout() 
				// Probably something serious that will keep recurring, so exit this Service
				Log.e(TAG, "ExNote: SocketException while receiving packet.\nExMessage: " + ex.getMessage());
				Log.e(TAG, "ERROR: NetworkPlaybackService is FINISHED");
	            //cleanUp();
                // After this destroy() gets called, right? If so, just let destroy() call cleanUp().
	            return;
			} catch (IOException ex) {  // for .receive()
				// Probably something serious that will keep recurring, so exit this Service
				Log.e(TAG, "ExNote: IOException while receiving packet.\nExMessage: " + ex.getMessage());
				Log.e(TAG, "ERROR: NetworkPlaybackService is FINISHED");
	            //cleanUp();
                // After this destroy() gets called, right? If so, just let destroy() call cleanUp().
	            return;
			}

            currNumReceivedAudioDataPackets++;

			Payload payload = netMan.getPayload();
			
			if (payload != null) {
				if (payload instanceof PcmAudioDataPayload) {
                    //Log.v(TAG, "payload is instanceof PcmAudioDataPayload");
					// === Audio Data ===
					PcmAudioDataPayload pcmAudioDataPayload = (PcmAudioDataPayload) payload;
					
					
					if (!isReceivingAudio) {
	                	Log.v(TAG, "...Receiving audio again ");
	                	notifyListenersOnReceivingAudioStatusChanged(true);
	                }
	                isReceivingAudio = true;

                    //Log.v(TAG, "isAudioFormatValid: " + isAudioFormatValid);
	                if (isAudioFormatValid) {
                        if (pbMan != null) { pbMan.handleAudioDataPayload(pcmAudioDataPayload); }  // Adding pbMan null checks because pbMan could get destroyed while another thread is in these functions...
                        if (pbMan != null) {
                            int newPlaybackSpeed = pbMan.changePlaybackSpeedIfNeeded();
                            if (newPlaybackSpeed != PlaybackManager.PLAYBACK_SPEED_UNCHANGED) {
                                notifyListenersOnPlaybackSpeedStatusChanged(newPlaybackSpeed);
                            }
                        }
	                }
				} else {
					Log.w(TAG, "Hmm, received a packet/payload, but is an unexpected type...");
				}
			}
		}
		
		//cleanUp();
        // After this destroy() gets called, right? If so, just let destroy() call cleanUp().
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return npServiceBinder;
	}
	
    void setIsListening(boolean b) {
        isListening = b;
    }

    /*
    // KEEP for DEBUG
    public void logExtraInfo() {
    	// Extra info
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if(mWifi == null || !mWifi.isConnected())
		{
			Log.v(TAG, "Sorry! You need to be in a WiFi network in order to send UDP multicast packets. Aborting.");
			return;
		}
		
		// Check for IP address
		WifiManager wim = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		int ip = wim.getConnectionInfo().getIpAddress();
		Log.v(TAG, "IP address: " + Util.ip2String(ip));
		Log.v(TAG, "SSID: " + wim.getConnectionInfo().getSSID());
		Log.v(TAG, "sock.isBound(): " + sock.isBound());
        Log.v(TAG, "sock.getInetAddress(): " + sock.getInetAddress());
        Log.v(TAG, "sock.getLocalPort(): " + sock.getLocalPort());
        Log.v(TAG, "sock.getPort(): " + sock.getPort());
        Log.v(TAG, "sock.getLocalAddress(): " + sock.getLocalAddress());
        Log.v(TAG, "sock.isClosed(): " + sock.isClosed());
        Log.v(TAG, "sock.isConnected(): " + sock.isConnected());
    }
    */
    
    private void cleanUp() {
        Log.v(TAG, "NpService::cleanUp");

        isListening = false;

        if (receivingAudioCheckTimer != null) {
            receivingAudioCheckTimer.cancel();
            receivingAudioCheckTimer.purge();
            receivingAudioCheckTimer = null;
        }

        if (pbMan != null) {
        	pbMan.cleanUp();
            pbMan = null;
        }
        
        if (netMan != null) {
        	netMan.cleanUp();
            netMan = null;
        }
        
        // Notify listeners
        notifyListenersOnReceivingAudioStatusChanged(false);
        notifyListenersOnPlaybackSpeedStatusChanged(PlaybackManager.PLAYBACK_SPEED_NORMAL);
        notifyListenersOnNpServiceFinished();
    }
    
    private void notifyListenersOnReceivingAudioStatusChanged(boolean isReceivingAudio) {
    	for (NetworkPlaybackServiceListener listener : listeners) {
    		listener.onReceivingAudioStatusChanged(isReceivingAudio);
    	}
    }
    
    private void notifyListenersOnPlaybackSpeedStatusChanged(int playbackSpeed) {
    	for (NetworkPlaybackServiceListener listener : listeners) {
    		listener.onPlaybackSpeedStatusChanged(playbackSpeed);
    	}
    }
    
    private void notifyListenersOnNpServiceFinished() {
    	for (NetworkPlaybackServiceListener listener : listeners) {
    		listener.onNpServiceFinished();
    	}
	}

    private void notifyListenersOnMissingPacketRequestCreated(List<PcmAudioDataPayload> missingPackets) {
        //Log.v(TAG, "notifyListenersOnMissingPacketRequestCreated");
        for (NetworkPlaybackServiceListener listener : listeners) {
            listener.onMissingPacketRequestCreated(missingPackets);
        }
    }

    public void onAudioFormatChanged(AudioFormatStruct af) {
        Log.v(TAG, "onAudioFormatChanged() to: " + af.SampleRate + ". Creating new speaker line");
        pbMan.createNewSpeakerLine(af);
        isAudioFormatValid = true;  //??? Maybe later, check to make sure the format actually IS valid data.  //??? Do we need this anymore???
    }

    public void addListener(NetworkPlaybackServiceListener listener) {
		listeners.add(listener);
	}
    
    public void removeListener(NetworkPlaybackServiceListener listener) {
		listeners.remove(listener);
	}
    
    public void onCurrentlyNotReceivingAudio() {
    	//Log.v(TAG, "onCurrentlyNotReceivingAudio()");
    	isReceivingAudio = false;
    	notifyListenersOnReceivingAudioStatusChanged(false);
    }

	public void onDesiredDelayChanged(float desiredDelay) {
		if (pbMan != null) {
			pbMan.setDesiredDelay(desiredDelay);
		}
	}

	public boolean getIsReceivingAudio() {
		return isReceivingAudio;
	}

	public int getPlaybackSpeed() {
		if (pbMan != null) {
			return pbMan.getPlaybackSpeed();
		}
		
		return PlaybackManager.PLAYBACK_SPEED_NORMAL;
	}

    public void setSendMissingPacketRequestsChecked(boolean enabled) {
        pbMan.setSendMissingPacketRequestsChecked(enabled);
    }

    @Override
    public void onMissingPacketRequestCreated(List<PcmAudioDataPayload> missingPackets) {
        notifyListenersOnMissingPacketRequestCreated(missingPackets);
    }

	@Override
	public void onDestroy() {
		Log.v(TAG, "onDestroy()");
		
		// Clean up
		cleanUp();
		
		// Release the wifiLock
    	if (wifiLock != null) {
    		Log.v(TAG, "Releasing Wi-Fi Lock");
    		wifiLock.release();
    	}
    	
    	// Release the Wake Lock
    	if (wakeLock != null) {
    		Log.v(TAG, "Releasing Wake Lock");
    		wakeLock.release();
    	}
    	
		super.onDestroy();
	}
}
