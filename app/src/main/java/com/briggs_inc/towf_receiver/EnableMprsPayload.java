package com.briggs_inc.towf_receiver;

import java.util.ArrayList;
import java.util.List;

import static com.briggs_inc.towf_receiver.PacketConstants.DG_DATA_HEADER_LENGTH;
import static com.briggs_inc.towf_receiver.PacketConstants.UDP_DATA_PAYLOAD_SIZE;
import static com.briggs_inc.towf_receiver.PacketConstants.UDP_DATA_SIZE;

/**
 * Created by briggsm on 4/19/15.
 */
public class EnableMprsPayload extends Payload {
    private static final String TAG = "MprPayload";

    // Constants
    public static final int ENMPRS_ENABLED_START = 0;
    public static final int ENMPRS_ENABLED_LENGTH = 1;

    // "Struct" Variables
    public boolean Enabled;

    public EnableMprsPayload () {
        Enabled = false;
    }

    public void initWithDgData(byte[] dgData) {
        this.Enabled = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_LENGTH + ENMPRS_ENABLED_START, ENMPRS_ENABLED_LENGTH, false) == 1;
    }
}
