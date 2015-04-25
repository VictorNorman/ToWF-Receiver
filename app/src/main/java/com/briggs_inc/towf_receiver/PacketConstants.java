package com.briggs_inc.towf_receiver;

public class PacketConstants {
	// Broadcast
    public static final int DG_DATA_HEADER_LENGTH = 6;  // Bytes
    
    // UDP PACKET
    public static final int UDP_PACKET_SIZE = 512;
    public static final int UDP_HEADER_SIZE = 8;
    public static final int IPV4_HEADER_SIZE = 20;
    public static final int ETH_HEADER_SIZE = 14;
    public static final int UDP_DATA_SIZE = UDP_PACKET_SIZE - UDP_HEADER_SIZE - IPV4_HEADER_SIZE - ETH_HEADER_SIZE; //512-42=470
    public static final int UDP_DATA_PAYLOAD_SIZE = UDP_DATA_SIZE - DG_DATA_HEADER_LENGTH;  //470-6=464
    
    public static final int ToWF_AS_INT = 0x546F5746;
    
    // Datagram Constants
    public static final int DG_DATA_HEADER_ID_START = 0;  // "ToWF"
    public static final int DG_DATA_HEADER_ID_LENGTH = 4;
    public static final int DG_DATA_HEADER_CHANNEL_START = 4;  // Rsvd
    public static final int DG_DATA_HEADER_CHANNEL_LENGTH = 1; // Rsvd
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_START = 5;
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_LENGTH = 1;

    // Payload Types
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_PCM_AUDIO_FORMAT = 0;
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_PCM_AUDIO_DATA_REGULAR = 1;
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_LANG_PORT_PAIRS = 2;  // NOTE: Payload Types don't need to be unique across different PORTs, but I'm making them unique just to keep them a bit easier to keep track of.
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_CLIENT_LISTENING = 3;
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_MISSING_PACKETS_REQUEST = 4;
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_PCM_AUDIO_DATA_MISSING = 5;
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_ENABLE_MPRS = 6;
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_CHAT_MSG = 7;
    public static final int DG_DATA_HEADER_PAYLOAD_TYPE_RLS = 8;  // Request Listening State

    // Audio Data Payload Constants
    public static final int ADPL_AUDIO_DATA_AVAILABLE_SIZE = UDP_DATA_PAYLOAD_SIZE - PcmAudioDataPayload.ADPL_HEADER_LENGTH;
    
    // OS Constants
    public static final int OS_OTHER = 0;
    public static final int OS_IOS = 1;
    public static final int OS_ANDROID = 2;
}
