package com.briggs_inc.towf_receiver;

import java.nio.charset.Charset;

import android.util.Log;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

public class Util {
	private static final String TAG = "Util";
			
	public static int getIntFromByteArray(byte[] b, int offset, int length, boolean bigEndian) {
        int i = 0;
        
        if (length > 4) {
            Log.v(TAG, "*Warning! length > 4 bytes. Data might not fit in an int! Data loss possible! length: " + length + ", offset: " + offset);
        }
        
        for (int ctr = 0; ctr < length; ctr++) {
            int pos = bigEndian ? length-1 - ctr : ctr;
            i += ((b[offset+pos] & 0xFF) << (8*ctr));
        }
        
        return i;
    }
	
	public static void writeDgDataHeaderToByteArray(byte[] ba, int payloadType) {
		putIntInsideByteArray(ToWF_AS_INT, ba, DG_DATA_HEADER_ID_START, DG_DATA_HEADER_ID_LENGTH, true);  // "ToWF"
        putIntInsideByteArray(0x00, ba, DG_DATA_HEADER_CHANNEL_START, DG_DATA_HEADER_CHANNEL_LENGTH, false);  // Rsvd (not used)
        putIntInsideByteArray(payloadType, ba, DG_DATA_HEADER_PAYLOAD_TYPE_START, DG_DATA_HEADER_PAYLOAD_TYPE_LENGTH, false);
	}
	
	public static void putIntInsideByteArray(int i, byte[] b, int offset, int length, boolean bigEndian) {
        // Note: if "i" is bigger than what will fit in "length" bytes, data will be lost
        // e.g. i=0x89ABCDEF, offset=8, length=4, bigEndian=false

        // Check for possible data loss & warn user if so
        if (i >= Math.pow(2, length * 8)) {
            Log.w(TAG, "*WARNING! Full int will not fit inside byte array! Data lost! i: " + i + ", length: " + length + ", boundary: " + Math.pow(2, length * 8));
        }

        for (int ctr = 0; ctr < length; ctr++) {
            int pos = bigEndian ? length - 1 - ctr : ctr;  // e.g. bigEndian=>4, littleEndian=0
            b[offset + pos] = (byte) ((i & (0xFF << (8 * ctr))) >> (8 * ctr));  // e.g. 1st time thru loop => i & 0xFF, 2nd time => i & 0xFF00, etc., then shift back to right same amt.
        }
    }
	
	public static void putStringInsideByteArray(String s, byte[] b, int offset, int maxLength) {
		// Assumes US-ASCII string
		
		int length = Math.min(s.length(), maxLength);
		int i;
		for (i = 0; i < length; i++) {
			b[offset + i] = (byte)s.charAt(i);
		}
		// Null-terminate it, if there's room.
		if (i < maxLength) {
			b[offset + i] = 0x00;
		}
	}
	
	public static String getNullTermStringFromByteArray(byte[] b, int offset, int maxLength) {
        // Assumes US-ASCII
        byte byt;
        int itr = 0;
        byte allBytes[] = new byte[512];  // Plenty big enough

        while ((byt = b[offset + itr]) != 0 && itr < maxLength) {
            allBytes[itr++] = byt;
        }
        
        return new String(allBytes, 0, itr, Charset.forName("US-ASCII"));
    }
	
	public static String ip2String(int i) {
		// Convert IP Address (as int) to a String
		// int as in from: wifiInfo.getIpAddress(); i.e. 1.5.168.192
		
		return (i & 0xFF) + "." + ((i >> 8 ) & 0xFF) + "." + ((i >> 16 ) & 0xFF) + "." + ( (i >> 24) & 0xFF);
	}
	
	public static float convertAudioSamplesToSeconds(long numAudioSamples, AudioFormatStruct af) {
		return (float)numAudioSamples / (float)af.SampleRate;
	}
}
