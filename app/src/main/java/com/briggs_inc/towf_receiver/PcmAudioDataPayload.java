package com.briggs_inc.towf_receiver;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

public abstract class PcmAudioDataPayload extends Payload implements Comparable<PcmAudioDataPayload> {
	// Constants
	public static final int ADPL_HEADER_SEQ_ID_START = 0;
    public static final int ADPL_HEADER_SEQ_ID_LENGTH = 2;
    public static final int ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_START = 2;
    public static final int ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_LENGTH = 2;

    public static final int ADPL_HEADER_LENGTH = ADPL_HEADER_SEQ_ID_LENGTH + ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_LENGTH;


	// "Struct" Variables
    public SeqId SeqId;
    public int AudioDataAllocatedBytes;
    //public byte[] AudioData = null;
    public byte DgData[];  // Saving pointer to dgData (instead of creating AudioData), so we don't have to waste CPU cycles & Memory Allocation for Arrays.copyOfRange(). Just need to remember to specify offset into DgData when we want the actual AudioData values.

    public PcmAudioDataPayload() {
        this.SeqId = null;
        this.AudioDataAllocatedBytes = 0;
        this.DgData = null;
    }

    // Copy Constructor
    PcmAudioDataPayload(PcmAudioDataPayload adp) {
        this.SeqId = adp.SeqId;  // ??? I think it's ok to just point to the SeqId object
        this.AudioDataAllocatedBytes = adp.AudioDataAllocatedBytes;

        // DgData
        if (adp.DgData != null) {
            this.DgData = new byte[adp.DgData.length];
            System.arraycopy(adp.DgData, 0, this.DgData, 0, adp.DgData.length);
        } else {
            this.DgData = null;
        }
    }

    public void initWithDgData(byte[] dgData) {
        this.SeqId = new SeqId(Util.getIntFromByteArray(dgData, DG_DATA_HEADER_LENGTH + ADPL_HEADER_SEQ_ID_START, ADPL_HEADER_SEQ_ID_LENGTH, false));
        AudioDataAllocatedBytes = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_LENGTH + ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_START, ADPL_HEADER_AUDIO_DATA_ALLOCATED_BYTES_LENGTH, false);
        this.DgData = dgData;
    }

    public PcmAudioDataPayload(SeqId seqId) {
        // Note: creating this "constructor" so we can "compare" PcmAudioDataPayload's, and to compare them, we just check the SeqId of each. And to add a "missing" PcmAudioDataPayload.
        this.SeqId = seqId;
        this.AudioDataAllocatedBytes = 0;
        this.DgData = null;
    }

    @Override
    public int compareTo(PcmAudioDataPayload otherPayload) {
        if (this.SeqId.isLessThanSeqId(otherPayload.SeqId)) {
            return 0 - Math.abs(this.SeqId.numSeqIdsExclusivelyBetweenMeAndSeqId(otherPayload.SeqId));
        } else if (this.SeqId.isEqualToSeqId(otherPayload.SeqId)) {
            return 0;
        } else {
            return Math.abs(this.SeqId.numSeqIdsExclusivelyBetweenMeAndSeqId(otherPayload.SeqId));
        }
    }
}