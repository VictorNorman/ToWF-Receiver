package com.briggs_inc.towf_receiver;

import static com.briggs_inc.towf_receiver.PacketConstants.ADPL_AUDIO_DATA_AVAILABLE_SIZE;

public class MiscConstants {
	
	// Intent Key strings
    public static final String STREAM_PORT_KEY = "StreamPort";
    public static final String DESIRED_DELAY_KEY = "DesiredDelay";
    public static final String INFO_PORT_KEY = "InfoPort";
    public static final String SEND_MPRS_ENABLED_KEY = "SendMprsEnabled";
    public static final String AF_SAMPLE_RATE_KEY = "AfSampleRate";

    public static final int NETWORK_PLAYBACK_SERVICE_FG_NOTIFICATION_ID = 7;
    public static final int NETWORK_INFO_SERVICE_FG_NOTIFICATION_ID = 8;

    public static final int SERVER_STREAMING_CHECK_TIMER_INTERVAL_MS = 7000;  // Check if at least 1 audio data packet has been received in the last X seconds.

    // Audio Format According to the SPEC
    public static final int AF_SAMPLE_SIZE_IN_BITS = 16;
    public static final int AF_CHANNELS = 1;
    public static final boolean AF_SIGNED = true;
    public static final boolean AF_BIG_ENDIAN = false;
    // AF Derived:
    public static final int AF_SAMPLE_SIZE_IN_BYTES = 2;
    public static final int AF_FRAME_SIZE = AF_SAMPLE_SIZE_IN_BYTES * AF_CHANNELS;
    public static final int AUDIO_DATA_MAX_VALID_SIZE = (ADPL_AUDIO_DATA_AVAILABLE_SIZE - (ADPL_AUDIO_DATA_AVAILABLE_SIZE % AF_FRAME_SIZE));

}
