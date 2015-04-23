package com.briggs_inc.towf_receiver;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

public class PcmAudioFormatPayload extends Payload {
	private static final String TAG = "PcmAudioFormatPayload";
	
	// Constants
	public static final int AFDG_SAMPLE_RATE_START = 0;
    public static final int AFDG_SAMPLE_RATE_LENGTH = 4;

    // "Struct" variables
    public int AfSampleRate;

    public PcmAudioFormatPayload() {
        AfSampleRate = 0;
    }

    public void initWithDgData(byte[] dgData) {
        AfSampleRate = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_LENGTH + AFDG_SAMPLE_RATE_START, AFDG_SAMPLE_RATE_LENGTH, false);
    }
}
