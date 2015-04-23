package com.briggs_inc.towf_receiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import android.util.Log;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

public class NetworkManager {
	private static final String TAG = "NetworkManager";

	DatagramSocket socket;
    DatagramPacket dg;
	byte dgData[];

    PcmAudioFormatPayload pcmAudioFormatPayload = new PcmAudioFormatPayload();
    PcmAudioDataRegularPayload pcmAudioDataRegularPayload = new PcmAudioDataRegularPayload();
    LangPortPairsPayload langPortPairsPayload = new LangPortPairsPayload();
    PcmAudioDataMissingPayload pcmAudioDataMissingPayload = new PcmAudioDataMissingPayload();
    EnableMprsPayload enableMprsPayload = new EnableMprsPayload();
    ChatMsgPayload cmPayload = new ChatMsgPayload();

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
        dg = new DatagramPacket(dgData, dgData.length);
        socket.setSoTimeout(receiveTimeoutMs);
	}
	
	public DatagramPacket receiveDatagram() throws SocketException, SocketTimeoutException, IOException {
		if (socket != null) {
            socket.receive(dg);  // Hangs (blocks) here until packet is received (or times out)... (but doesn't use CPU resources while blocking)
            return dg;
        } else {
            return null;
        }
	}

	public void sendDatagram(DatagramPacket dg) throws IOException {
        // NOTE: Need to do Networking OFF of the Main thread. If try to do on Main thread, on newer Android devices, you get: NetworkOnMainThreadException
		new SendDatagramThread(dg).start();
	}

	public Payload getPayload() {
		// First, check for "ToWF" in Header
        int headerId = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_ID_START, DG_DATA_HEADER_ID_LENGTH, true); 
        if (headerId != ToWF_AS_INT) {
        	return null;  // This packet was not meant for ToWF
        }
		
		int payloadType = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_PAYLOAD_TYPE_START, DG_DATA_HEADER_PAYLOAD_TYPE_LENGTH, false);

		switch (payloadType) {
	    	case DG_DATA_HEADER_PAYLOAD_TYPE_PCM_AUDIO_FORMAT:
	            //Log.d(TAG, "*** Audio Format Datagram ***");
                pcmAudioFormatPayload.initWithDgData(dgData);
	            return pcmAudioFormatPayload;
	    	case DG_DATA_HEADER_PAYLOAD_TYPE_PCM_AUDIO_DATA_REGULAR:
                //Log.v(TAG, "REGULAR Payload");
                pcmAudioDataRegularPayload.initWithDgData(dgData);
	    		return pcmAudioDataRegularPayload;
	    	case DG_DATA_HEADER_PAYLOAD_TYPE_LANG_PORT_PAIRS:
                langPortPairsPayload.initWithDgData(dgData);
	    		return langPortPairsPayload;
            case DG_DATA_HEADER_PAYLOAD_TYPE_PCM_AUDIO_DATA_MISSING:
                //Log.v(TAG, "MISSING Payload");
                pcmAudioDataMissingPayload.initWithDgData(dgData);
                return pcmAudioDataMissingPayload;
            case DG_DATA_HEADER_PAYLOAD_TYPE_ENABLE_MPRS:
                enableMprsPayload.initWithDgData(dgData);
                return enableMprsPayload;
            case DG_DATA_HEADER_PAYLOAD_TYPE_CHAT_MSG:
                cmPayload.initWithDgData(dgData);
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
