package com.briggs_inc.towf_receiver;

/**
 * Created by briggsm on 4/19/15.
 */
public class PcmAudioDataRegularPayload extends PcmAudioDataPayload {

    public PcmAudioDataRegularPayload(byte[] dgDataPayload) {
        super(dgDataPayload);
    }

    public PcmAudioDataRegularPayload(SeqId seqId) {
        super(seqId);
    }
}
