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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.Build;
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
	//public void onAudioFormatChanged(AudioFormatStruct af);
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
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// Create our instance of Playback Manager
		Log.v(TAG, "onHandleIntent()");

		Bundle extras = intent.getExtras();
		if (extras != null && intent.getExtras().getInt(STREAM_PORT_KEY) != 0) {
            pbMan = new PlaybackManager(intent.getExtras().getInt(AF_SAMPLE_RATE_KEY, 0), intent.getExtras().getFloat(DESIRED_DELAY_KEY, 1.0f), intent.getExtras().getBoolean(SEND_MPRS_ENABLED_KEY, false));
			streamPort = intent.getExtras().getInt(STREAM_PORT_KEY, 7770);
			pbMan.addListener(this);
		} else {
			Log.v(TAG, "ERROR! NetworkPlaybackService onHandleEvent() cannot get extras from its intent. Unable to start service.");
			return;
		}

		Log.v(TAG, "onHandleIntent() starting network manager");
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

        // Socket-related
        Log.v(TAG, "Creating New DatagramSocket (for Broadcast)...");
     	
        isListening = true;

        // Start the ReceivingAudio timer
        receivingAudioCheckTimer.schedule(new CheckIfReceivingAudioTask(), 200, 200);  //100ms => about 10 fps 'refresh rate' //200ms => about 5 fps 'refresh rate'

		// Notification stuff -- required for services (https://developer.android.com/guide/components/services)
		Notification notification = createNotification();
		startForeground(NETWORK_PLAYBACK_SERVICE_FG_NOTIFICATION_ID, notification);



		while (isListening) {
			try {
				datagram = netMan.receiveDatagram();  // Hangs (blocks) here until packet is received (or times out)... (but doesn't use CPU resources while blocking)
                if (datagram == null || !isListening) {
                    continue;  // to next iteration of while loop
                }
                Log.v(TAG, "got datagram");
			} catch (SocketTimeoutException ex) {
				Log.d(TAG, "ExNote: SocketTimeoutException in NpService.\nExMessage: " + ex.getMessage());
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

    public void onAfSampleRateChanged (int sampleRate) {
        pbMan.onAfSampleRateChanged(sampleRate);
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

    public int getStreamPort() {
        return streamPort;
    }

    @Override
	public void onDestroy() {
		Log.v(TAG, "onDestroy()");
		
		// Clean up
		cleanUp();
		

		super.onDestroy();
	}

	private Notification createNotification() {
    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "com.mbriggs.towf_android";
			String channelName = "ToWF Network Playback Background Service";
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);

			Intent notificationIntent = new Intent(this, MainActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

			Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
					.setContentTitle("ToWF")
					.setContentText("Receiving Audio")
					.setSmallIcon(android.R.drawable.ic_media_play)
					.setContentIntent(pendingIntent)
					.setTicker("ToWF Receiving Audio")
					.build();

			return notification;
		} else {
			Intent notificationIntent = new Intent(this, MainActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

			Notification notification = new Notification.Builder(this)
					.setPriority(Notification.PRIORITY_LOW)
					.setContentTitle("ToWF")
					.setContentText("Receiving Audio")
					.setSmallIcon(android.R.drawable.ic_media_play)
					.setContentIntent(pendingIntent)
					.setTicker("ToWF Receiving Audio")
					.build();

			return notification;
		}
	}
}
