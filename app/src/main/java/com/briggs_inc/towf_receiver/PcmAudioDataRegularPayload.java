package com.briggs_inc.towf_receiver;

/**
 * Created by briggsm on 4/19/15.
 */
public class PcmAudioDataRegularPayload extends PcmAudioDataPayload {

    public PcmAudioDataRegularPayload() {
        super();
    }

    // Copy constructor
    PcmAudioDataRegularPayload(PcmAudioDataPayload adp) {
        super(adp);
    }

    public PcmAudioDataRegularPayload(SeqId seqId) {
        super(seqId);
    }
}
