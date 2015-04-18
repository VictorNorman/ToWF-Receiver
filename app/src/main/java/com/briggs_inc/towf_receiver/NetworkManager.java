package com.briggs_inc.towf_receiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

public class NetworkManager {
	private static final String TAG = "NetworkManager";

	DatagramSocket socket;
	byte dgData[];
	int receiveTimeoutMs;
	
	class SendDatagramThread extends Thread {
		DatagramPacket datagram;
		
		public SendDatagramThread(DatagramPacket dg) {
			this.datagram = dg;
		}
		
		public void run() {
			try {
				NetworkManager.this.socket.send(datagram);
			} catch (IOException ex) {
				Log.e(TAG, "SendDatagramThread had IOException.\nExMessage: " + ex.getMessage());
				
			}
		}
	}
	
	public NetworkManager(int port) throws SocketException {
		this(port, 1000);
	}
	
	public NetworkManager(int port, int receiveTimeoutMs) throws SocketException {
		Log.d(TAG, "NetworkManager Constructor - port: " + port);
      	socket = new DatagramSocket(port);
        dgData = new byte[UDP_DATA_SIZE];
        this.receiveTimeoutMs = receiveTimeoutMs;
	}
	
	public DatagramPacket receiveDatagram() throws SocketException, SocketTimeoutException, IOException {
		DatagramPacket dg = new DatagramPacket(dgData, dgData.length);
		if (socket != null) {
            socket.setSoTimeout(receiveTimeoutMs);
            socket.receive(dg);  // Hangs (blocks) here until packet is received (or times out)... (but doesn't use CPU resources while blocking)
            return dg;
        } else {
            return null;
        }
	}
	
	public void sendDatagram(DatagramPacket dg) throws IOException {
		new SendDatagramThread(dg).start();
	}

	public Payload getPayload() {
		// First, check for "ToWF" in Header
        int headerId = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_ID_START, DG_DATA_HEADER_ID_LENGTH, true); 
        if (headerId != ToWF_AS_INT) {
        	return null;  // This packet was not meant for ToWF
        }
		
		int payloadType = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_PAYLOAD_TYPE_START, DG_DATA_HEADER_PAYLOAD_TYPE_LENGTH, false);
		byte dgDataPayload[] = Arrays.copyOfRange(dgData, DG_DATA_HEADER_LENGTH, dgData.length);
		
		switch (payloadType) {
	    	case DG_DATA_HEADER_PAYLOAD_TYPE_PCM_AUDIO_FORMAT:
	            //Log.d(TAG, "*** Audio Format Datagram ***");
	            PcmAudioFormatPayload pcmAudioFormatPayload = new PcmAudioFormatPayload(dgDataPayload);
	            return pcmAudioFormatPayload;
	    	case DG_DATA_HEADER_PAYLOAD_TYPE_PCM_AUDIO_DATA:
	    		PcmAudioDataPayload pcmAudioDataPayload = new PcmAudioDataPayload(dgDataPayload);
	    		return pcmAudioDataPayload;
	    	case DG_DATA_HEADER_PAYLOAD_TYPE_LANG_PORT_PAIRS:
	    		LangPortPairsPayload langPortPairsPayload = new LangPortPairsPayload(dgDataPayload);
	    		return langPortPairsPayload;
            case DG_DATA_HEADER_PAYLOAD_TYPE_CHAT_MSG:
                ChatMsgPayload cmPayload = new ChatMsgPayload(dgDataPayload);
                return cmPayload;
	    	default:
	    		return null;
		}
	}
	
	public void cleanUp() {
		Log.v(TAG, "cleanUp()");
		if (socket != null) {
            socket.close();
            socket = null;
        }
	}
}
