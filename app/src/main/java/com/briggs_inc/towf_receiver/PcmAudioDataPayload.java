package com.briggs_inc.towf_receiver;

import java.util.Arrays;

public class PcmAudioDataPayload extends Payload {
	// Constants
	public static final int ADPL_HEADER_SEQ_ID_START = 0;
    public static final int ADPL_HEADER_SEQ_ID_LENGTH = 2;
    public static final int ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_START = 2;
    public static final int ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_LENGTH = 2;

    public static final int ADPL_HEADER_LENGTH = ADPL_HEADER_SEQ_ID_LENGTH + ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_LENGTH;
	
	// "Struct" Variables
    public int SeqId;
    public int AudioDataAllocatedBytes;
    public byte[] AudioData;
	
	public PcmAudioDataPayload(byte[] dgDataPayload) {
		SeqId = Util.getIntFromByteArray(dgDataPayload, ADPL_HEADER_SEQ_ID_START, ADPL_HEADER_SEQ_ID_LENGTH, false);
		AudioDataAllocatedBytes = Util.getIntFromByteArray(dgDataPayload, ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_START, ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_LENGTH, false);
		AudioData = Arrays.copyOfRange(dgDataPayload, ADPL_HEADER_LENGTH, ADPL_HEADER_LENGTH + AudioDataAllocatedBytes);
	}
}