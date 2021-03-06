package com.briggs_inc.towf_receiver;

import java.util.Arrays;

import static com.briggs_inc.towf_receiver.PacketConstants.DG_DATA_HEADER_LENGTH;
import static com.briggs_inc.towf_receiver.PacketConstants.UDP_DATA_PAYLOAD_SIZE;

/**
 * Created by briggsm on 4/18/15.
 */
public class ChatMsgPayload extends Payload {
    private static final String TAG = "ChatMsgPayload";

    // Constants
    public static final int CHATMSG_MSG_START = 0;

    // "Struct" Variables
    String Msg;

    byte dgDataPayload[] = new byte[UDP_DATA_PAYLOAD_SIZE];

    // Constructor - for creating a Chat Msg Packet
    public ChatMsgPayload(String msg) {
        this.Msg = msg;

        // Message
        Util.putNullTermStringInsideByteArray(msg, dgDataPayload, CHATMSG_MSG_START, UDP_DATA_PAYLOAD_SIZE, false);
    }

    // Constructor & initWithDgData - for receiving a Chat Msg Packet
    public ChatMsgPayload() {
        this.Msg = "";
    }

    public void initWithDgData(byte[] dgData) {
        this.Msg = Util.getNullTermStringFromByteArray(dgData, DG_DATA_HEADER_LENGTH + CHATMSG_MSG_START, dgData.length - DG_DATA_HEADER_LENGTH);
    }

    public byte[] getDgDataPayloadBytes() {
        int payloadLength = this.Msg.length() + 1;  // +1 for the null-terminator
        return Arrays.copyOfRange(dgDataPayload, 0, payloadLength);
    }
}
