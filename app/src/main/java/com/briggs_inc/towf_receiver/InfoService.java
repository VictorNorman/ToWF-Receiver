package com.briggs_inc.towf_receiver;

import static com.briggs_inc.towf_receiver.MiscConstants.*;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

import java.io.IOException;
import java.net.DatagramPacket;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;



import android.app.IntentService;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;

import android.os.IBinder;
import android.util.Log;

interface InfoServiceListener {
    public void onAudioFormatChanged(AudioFormatStruct af);
	public void onLppListChanged(List<LangPortPair> lppList);
    public void onChatMsgReceived(String msg);
	public void onServerStoppedStreaming();
	public void onServerStartedStreaming();
}

public class InfoService extends IntentService {
	private static final String TAG = "InfoService";
	
	private static final int INFO_PORT_NUMBER = 7769;
	private static final int INFO_PORT_RECEIVE_TIMEOUT_MS = 1000;
	
	InfoServiceBinder infoServiceBinder = new InfoServiceBinder();
	List<InfoServiceListener> listeners = new ArrayList<InfoServiceListener>();

	DatagramPacket datagram;
	
	int listeningPort;
	
	Boolean isRunning = true;

	private InetAddress serverInetAddress;
	private int serverPort;

    Timer serverStreamingCheckTimer = new Timer();
	//Timer serverStreamWatchdogTimer = new Timer();
    boolean receivedAnLppPacket = false;

	Boolean isServerStreaming = false;
	
	NetworkManager netMan;

    AudioFormatStruct currAudioFormat;
	List<LangPortPair> lppList = new ArrayList<LangPortPair>();

    /*
	public class OnServerStoppedStreamingTask extends TimerTask {
		
		@Override
		public void run() {
			isServerStreaming = false;
			InfoService.this.notifyListenersOnServerStoppedStreaming();
			lppList.clear();
		}
	}
    */

    public class CheckServerStoppedStreamingTask extends TimerTask {
        @Override
        public void run() {
            if (!receivedAnLppPacket) {
                if (isServerStreaming) {
                    isServerStreaming = false;
                    notifyListenersOnServerStoppedStreaming();
                }
            }
            receivedAnLppPacket = false;
        }
    }
	
	public class InfoServiceBinder extends Binder {
		public InfoService getService() {
			// Return this instance of NetworkPlaybackService so clients can call public methods
			return InfoService.this;
		}
	}
	
	public InfoService() {
		super("InfoService");
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.v(TAG, "onBind()");

		return infoServiceBinder;
	}
	
	public void setIsRunning(Boolean b) {
		isRunning = b;
	}
	
	//@Override
	protected void onHandleIntent(Intent intent) {
		Log.v(TAG, "onHandleIntent()");

        serverStreamingCheckTimer.schedule(new CheckServerStoppedStreamingTask(), 1000, SERVER_STREAMING_CHECK_TIMER_INTERVAL_MS);

		// Create our instance of NetworkManager
     	try {
     		netMan = new NetworkManager(INFO_PORT_NUMBER, INFO_PORT_RECEIVE_TIMEOUT_MS);
		} catch (SocketException ex) {
			Log.v(TAG, "ExNote: Unable to set a new NetworkManager with port: " + INFO_PORT_NUMBER + "\nExMessage: " + ex.getMessage());
            Log.v(TAG, "ERROR: InfoService NOT started");
            cleanUp();
            return;  // We can't do anything without a connected network manger
		}

	    isRunning = true;
	    while (isRunning) {
	    	try {
				datagram = netMan.receiveDatagram();
                if (datagram == null) {
                    continue;  // to next iteration of while loop
                }
			} catch (SocketTimeoutException ex) {
				continue;  // to next while loop iteration
			} catch (SocketException ex) {  // for .setSoTimeout() 
				// Probably something serious that will keep recurring, so exit this Service
				Log.v(TAG, "ExNote: SocketException while receiving packet.\nExMessage: " + ex.getMessage());
				Log.e(TAG, "ERROR: InfoService NOT started");
	            cleanUp();
	            return;
			} catch (IOException ex) {  // for .receive()
				// Probably something serious that will keep recurring, so exit this Service
				Log.v(TAG, "ExNote: IOException while receiving packet.\nExMessage: " + ex.getMessage());
				Log.e(TAG, "ERROR: InfoService NOT started");
	            cleanUp();
	            return;
			}
			
			Payload payload = netMan.getPayload();  // Note: returns null if datagram is not for "ToWF"
			
			if (payload != null) {
				/*
                if (!isServerStreaming) {
					notifyListenersServerStartedStreaming();
				}
				isServerStreaming = true;
				*/
                /*
                receivedAnLppPacket = true;
                if (!isServerStreaming) {
                    isServerStreaming = true;
                    notifyListenersOnServerStartedStreaming();
                }
                */

                if (payload instanceof PcmAudioFormatPayload) {
                    // === Audio Format ===
                    PcmAudioFormatPayload pcmAudioFormatPayload = (PcmAudioFormatPayload) payload;
                    AudioFormatStruct plAudioFormat = pcmAudioFormatPayload.AudioFormat;
                    if (!plAudioFormat.equals(currAudioFormat)) {

                        // Create new currAudioFormat
                        currAudioFormat = new AudioFormatStruct(plAudioFormat.SampleRate, plAudioFormat.SampleSizeInBits, plAudioFormat.Channels, plAudioFormat.IsSigned, plAudioFormat.IsBigEndian);

                        // Print
                        Log.i(TAG, "New Audio Format:");
                        Log.i(TAG, " sampleRate: " + currAudioFormat.SampleRate);
                        Log.i(TAG, " sampleSizeInBits: " + currAudioFormat.SampleSizeInBits);
                        Log.i(TAG, " channels: " + currAudioFormat.Channels);
                        Log.i(TAG, " isSigned: " + currAudioFormat.IsSigned);
                        Log.i(TAG, " isBigEndian: " + currAudioFormat.IsBigEndian);

                        notifyListenersOnAudioFormatChanged(currAudioFormat);
                    }
                } else if (payload instanceof LangPortPairsPayload) {

                    LangPortPairsPayload lppPayload = (LangPortPairsPayload) payload;

                    //resetServerStreamWatchdogTimer();

                    // Save Server's ip (inet) address & port
                    serverInetAddress = datagram.getAddress();
                    serverPort = datagram.getPort();

                    if (!isLppListsSame(lppList, lppPayload.LppList)) {
                        // Lists are not the same, build from scratch
                        lppList.clear();

                        for (int i = 0; i < lppPayload.LppList.size(); i++) {
                            String plLanguage = lppPayload.LppList.get(i).Language;
                            int plPort = lppPayload.LppList.get(i).Port;
                            if (plPort >= 0 && plPort <= 65535) {
                                lppList.add(new LangPortPair(plLanguage, plPort));
                            }
                        }

                        notifyListenersOnLppListChanged(lppList);
                    }

                    receivedAnLppPacket = true;
                    if (!isServerStreaming) {
                        isServerStreaming = true;
                        notifyListenersOnLppListChanged(lppList);
                        notifyListenersOnServerStartedStreaming();
                    }
                } else if (payload instanceof ChatMsgPayload) {
                    ChatMsgPayload cmPayload = (ChatMsgPayload) payload;
                    notifyListenersOnChatMsgReceived(cmPayload.Msg);
				} else {
                    Log.w(TAG, "Hmm, received a packet/payload, but is an unexpected or unknown type...");
                }
			}
	    }
	    // Think I don't need this here, but shouldn't hurt...
	    cleanUp();
	}

    /*
	private void resetServerStreamWatchdogTimer() {
		// Set "server streaming" watchdog timer - if it fires, then hide streamView & show "waiting for server" message.
        serverStreamWatchdogTimer.cancel();
        serverStreamWatchdogTimer.purge();
        serverStreamWatchdogTimer = new Timer();
        TimerTask onServerStoppedStreamingTask = new OnServerStoppedStreamingTask();
        serverStreamWatchdogTimer.schedule(onServerStoppedStreamingTask, SERVER_STREAMING_WATCHDOG_TIMER_TIMEOUT_MS);
	}
    */

	private Boolean isLppListsSame(List<LangPortPair> lppList1, List<LangPortPair> lppList2) {
		// Check size of the lists first
		if (lppList1.size() != lppList2.size()) {
			return false;
		}
		
		// Check each element 1-by-1
		for (int i = 0; i < lppList1.size(); i++) {
			if (!lppList1.get(i).Language.equals(lppList2.get(i).Language)) { return false; }
			if (lppList1.get(i).Port != lppList2.get(i).Port) { return false; }
		}
		
		// If we get here, the lists must be exactly the same.
		return true;
	}

	@Override
	public void onDestroy() {
		Log.v(TAG, "onDestroy()");
		// On Destroy should only get called when system destroy the entire app, I think, because I don't have a "stop" coded anywhere.
		
		// Notify Server that we're not listening anymore. //////Oops - can't do this 'cuz we unbind and stop the InfoService even on device orientation change.
		sendClientListening(false, listeningPort);
		
		super.onDestroy();
	}

    private void notifyListenersOnAudioFormatChanged(AudioFormatStruct af) {
        for (InfoServiceListener listener : listeners) {
            listener.onAudioFormatChanged(af);
        }
    }

	private void notifyListenersOnLppListChanged(List<LangPortPair> lppList) {
    	for (InfoServiceListener listener : listeners) {
    		listener.onLppListChanged(lppList);
    	}
	}

    private void notifyListenersOnChatMsgReceived(String msg) {
        for (InfoServiceListener listener : listeners) {
            listener.onChatMsgReceived(msg);
        }
    }
	
	public void sendClientListening(boolean isListening, int port) {
		listeningPort = port;
		
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		String macAddress = wifiInfo.getMacAddress();
		int ipAddress = wifiInfo.getIpAddress();
		
		if (ipAddress == 0) {
			return;  // Don't send anything cuz ipAddress is invalid.
		}
		
		// === Build "client listening" packet ===
		byte dgData[] = new byte[UDP_DATA_SIZE];
		
		// "ToWF" Header
		Util.writeDgDataHeaderToByteArray(dgData, DG_DATA_HEADER_PAYLOAD_TYPE_CLIENT_LISTENING);
		
		// Create a ClientListeningPayload object 
		ClientListeningPayload clPayload = new ClientListeningPayload(isListening, OS_ANDROID, listeningPort, macAddress, ipAddress, String.format("%d", android.os.Build.VERSION.SDK_INT), Build.MANUFACTURER, Build.MODEL, "");
		
		// Get out payload bytes
		byte dgDataPayload[] = clPayload.getDgDataPayloadBytes();
		
		// Append the payload to the DgData array (after the "ToWF" header)
		System.arraycopy(dgDataPayload, 0, dgData, DG_DATA_HEADER_LENGTH, dgDataPayload.length);
		
		// Build our datagram packet & send it.
		DatagramPacket clDatagramPacket = new DatagramPacket(dgData, UDP_DATA_SIZE, serverInetAddress, serverPort);
		
		try {
			//netMan.sendDatagram(clDatagramPacket);
            netMan.sendDatagramSync(clDatagramPacket);
		} catch (IOException e) {
			Log.v(TAG, "ExNote: Sending clDatagramPacket over socket FAILED!\nExMessage: " + e.getMessage());
		}
	}

    public void sendMissingPacketRequest(int port, List<PcmAudioDataPayload> missingPackets) {
        Log.v(TAG, String.format("sendMissingPacketRequest(%d, %d)", port, missingPackets.size()));
        if (missingPackets.size() == 0) {
            return;  // Don't send anything if we don't have missing packets
        }

        int remainingMissingPacketsToSend = missingPackets.size();
        int numMissingPacketsSent = 0;
        while (remainingMissingPacketsToSend > 0) {
            int currNumMissingPacketsToSend = Math.min(remainingMissingPacketsToSend, MprPayload.MPRPL_PACKETS_AVAILABLE_SIZE / 2);

            // === Build "MPR" packet ===
            byte dgData[] = new byte[UDP_DATA_SIZE];

            // "ToWF" Header
            Util.writeDgDataHeaderToByteArray(dgData, DG_DATA_HEADER_PAYLOAD_TYPE_MISSING_PACKETS_REQUEST);

            // Create an MprPayload object
            MprPayload mprPayload = new MprPayload(port, missingPackets.subList(numMissingPacketsSent, numMissingPacketsSent + currNumMissingPacketsToSend));

            // Get out payload bytes
            byte dgDataPayload[] = mprPayload.getDgDataPayloadBytes();

            // Append the payload to the DgData array (after the "ToWF" header)
            System.arraycopy(dgDataPayload, 0, dgData, DG_DATA_HEADER_LENGTH, dgDataPayload.length);

            // Build our datagram packet & send it.
            DatagramPacket mprDatagramPacket = new DatagramPacket(dgData, UDP_DATA_SIZE, serverInetAddress, serverPort);

            try {
                //netMan.sendDatagram(mprDatagramPacket);
                netMan.sendDatagramSync(mprDatagramPacket);
            } catch (IOException e) {
                Log.v(TAG, "ExNote: Sending mprDatagramPacket over socket FAILED!\nExMessage: " + e.getMessage());
            }

            numMissingPacketsSent += currNumMissingPacketsToSend;
            remainingMissingPacketsToSend -= currNumMissingPacketsToSend;
        }
    }

    public void sendChatMsg(String msg) {
        byte dgData[] = new byte[UDP_DATA_SIZE];

        // "ToWF" Header
        Util.writeDgDataHeaderToByteArray(dgData, DG_DATA_HEADER_PAYLOAD_TYPE_CHAT_MSG);

        // Create a ChatMsgPayload object
        ChatMsgPayload cmPayload = new ChatMsgPayload(msg) ;

        // Get out payload bytes
        byte dgDataPayload[] = cmPayload.getDgDataPayloadBytes();

        // Append the payload to the DgData array (after the "ToWF" header)
        System.arraycopy(dgDataPayload, 0, dgData, DG_DATA_HEADER_LENGTH, dgDataPayload.length);

        // Build our datagram packet & send it.
        int dataLength = DG_DATA_HEADER_LENGTH + msg.length() + 1;  // +1 for the null-terminator
        DatagramPacket cmDatagramPacket = new DatagramPacket(dgData, dataLength, serverInetAddress, serverPort);

        try {
            //netMan.sendDatagram(cmDatagramPacket);
            netMan.sendDatagramSync(cmDatagramPacket);
        } catch (IOException e) {
            Log.v(TAG, "ExNote: Sending cmDatagramPacket over socket FAILED!\nExMessage: " + e.getMessage());
        }
    }

	private void cleanUp() {
		Log.v(TAG, "InfoService::cleanUp()");
		netMan.cleanUp();
	}
	
	public void addListener(InfoServiceListener listener) {
		listeners.add(listener);
	}
	
	public void removeListener(InfoServiceListener listener) {
		listeners.remove(listener);
	}

	private void notifyListenersOnServerStartedStreaming() {
		for (InfoServiceListener listener : listeners) {
			listener.onServerStartedStreaming();
		}
	}

	private void notifyListenersOnServerStoppedStreaming() {
    	for (InfoServiceListener listener : listeners) {
    		listener.onServerStoppedStreaming();
    	}
	}

	public Boolean getIsServerStreaming() {
		return isServerStreaming;
	}
}
