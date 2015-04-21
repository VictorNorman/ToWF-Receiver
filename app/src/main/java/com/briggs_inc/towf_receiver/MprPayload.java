package com.briggs_inc.towf_receiver;

import java.util.ArrayList;
import java.util.List;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

/**
 * Created by briggsm on 4/19/15.
 */
public class MprPayload extends Payload {
    private static final String TAG = "MprPayload";

    // Constants
    public static final int MPRPL_NUM_MISSING_PACKETS_START = 0;
    public static final int MPRPL_NUM_MISSING_PACKETS_LENGTH = 1;
    public static final int MPRPL_RSVD0_START = 1;
    public static final int MPRPL_RSVD0_LENGTH = 1;
    public static final int MPRPL_PORT_START = 2;
    public static final int MPRPL_PORT_LENGTH = 2;
    public static final int MPRPL_PACKET0_SEQID_START = 4;
    public static final int MPRPL_PACKET_SEQID_LENGTH = 2;
    public static final int MPRPL_PACKETS_AVAILABLE_SIZE = UDP_DATA_SIZE - DG_DATA_HEADER_LENGTH - MPRPL_NUM_MISSING_PACKETS_LENGTH - MPRPL_RSVD0_LENGTH - MPRPL_PORT_LENGTH;

    // "Struct" Variables
    public int Port;
    //public List<Integer> SeqIdList = new ArrayList<>();
    public List<PcmAudioDataPayload> MissingPackets = new ArrayList<>();

    byte dgDataPayload[] = new byte[UDP_DATA_PAYLOAD_SIZE];

    //public MprPayload(int port, List<Integer> seqIdList) {
    public MprPayload(int port, List<PcmAudioDataPayload> missingPackets) {
        this.Port = port;
        //this.SeqIdList = seqIdList;
        this.MissingPackets = missingPackets;

        // Num Missing Packets
        Util.putIntInsideByteArray(missingPackets.size(), dgDataPayload, MPRPL_NUM_MISSING_PACKETS_START, MPRPL_NUM_MISSING_PACKETS_LENGTH, false);

        // Rsvd
        Util.putIntInsideByteArray(0x00, dgDataPayload, MPRPL_RSVD0_START, MPRPL_RSVD0_LENGTH, false);

        // Port
        Util.putIntInsideByteArray(port, dgDataPayload, MPRPL_PORT_START, MPRPL_PORT_LENGTH, false);

        // Missing Packet's SeqId's
        //for (Integer mprSeqId : seqIdList) {
        for (int i = 0; i < missingPackets.size(); i++) {
            Util.putIntInsideByteArray(missingPackets.get(i).SeqId.intValue, dgDataPayload, MPRPL_PACKET0_SEQID_START + (i * MPRPL_PACKET_SEQID_LENGTH), MPRPL_PACKET_SEQID_LENGTH, false);
        }
    }

    public byte[] getDgDataPayloadBytes() {
        return dgDataPayload;
    }
}

