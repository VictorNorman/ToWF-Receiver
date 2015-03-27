package com.briggs_inc.towf_receiver;

public class PcmAudioFormatPayload extends Payload {
	private static final String TAG = "PcmAudioFormatPayload";
	
	// Constants
	public static final int AFDG_SAMPLE_RATE_START = 0;
    public static final int AFDG_SAMPLE_RATE_LENGTH = 4;
    public static final int AFDG_SAMPLE_SIZE_IN_BITS_START = 4;
    public static final int AFDG_SAMPLE_SIZE_IN_BITS_LENGTH = 1;
    public static final int AFDG_CHANNELS_START = 5;
    public static final int AFDG_CHANNELS_LENGTH = 1;
    public static final int AFDG_SIGNED_START = 6;
    public static final int AFDG_SIGNED_LENGTH = 1;
    public static final int AFDG_BIG_ENDIAN_START = 7;
    public static final int AFDG_BIG_ENDIAN_LENGTH = 1;
    
    // "Struct" variables
    public AudioFormatStruct AudioFormat;
	
	public PcmAudioFormatPayload(byte[] dgDataPayload) {
		AudioFormat = new AudioFormatStruct(
			Util.getIntFromByteArray(dgDataPayload, AFDG_SAMPLE_RATE_START, AFDG_SAMPLE_RATE_LENGTH, false),
			Util.getIntFromByteArray(dgDataPayload, AFDG_SAMPLE_SIZE_IN_BITS_START, AFDG_SAMPLE_SIZE_IN_BITS_LENGTH, false),
			Util.getIntFromByteArray(dgDataPayload, AFDG_CHANNELS_START, AFDG_CHANNELS_LENGTH, false),
			Util.getIntFromByteArray(dgDataPayload, AFDG_SIGNED_START, AFDG_SIGNED_LENGTH, false) == 1,
			Util.getIntFromByteArray(dgDataPayload, AFDG_BIG_ENDIAN_START, AFDG_BIG_ENDIAN_LENGTH, false) == 1
		); 
	}
}
