package com.briggs_inc.towf_receiver;

import java.util.ArrayList;
import java.util.List;
import static com.briggs_inc.towf_receiver.PacketConstants.*;

public class LangPortPairsPayload extends Payload {
	// Constants
	public static final int LPP_NUM_PAIRS_START = 0;
    public static final int LPP_NUM_PAIRS_LENGTH = 1;
    public static final int LPP_RSVD0_START = 1;
    public static final int LPP_RSVD0_LENGTH = 1;
    public static final int LPP_SERVER_VERSION_START = 2;
    public static final int LPP_SERVER_VERSION_LENGTH = 10;
    public static final int LPP_LANG0_START = 12;
    public static final int LPP_LANG_LENGTH = 16;
    public static final int LPP_PORT0_START = 28;
    public static final int LPP_PORT_LENGTH = 2;
    
	// "Struct" Variables
    String ServerVersion;
    List<LangPortPair> LppList;

    public LangPortPairsPayload() {
        this.ServerVersion = "0.0";
        this.LppList = new ArrayList<>();
    }

    public void initWithDgData(byte[] dgData) {
        // Server Version
        this.ServerVersion = Util.getNullTermStringFromByteArray(dgData, DG_DATA_HEADER_LENGTH + LPP_SERVER_VERSION_START, LPP_SERVER_VERSION_LENGTH);

        // Lang Port Pairs
        LppList.clear();
        int numLangPortPairs = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_LENGTH + LPP_NUM_PAIRS_START, LPP_NUM_PAIRS_LENGTH, false);
        for (int i = 0; i < numLangPortPairs; i++) {
            String language = Util.getNullTermStringFromByteArray(dgData, DG_DATA_HEADER_LENGTH + LPP_LANG0_START + (i*(LPP_LANG_LENGTH+LPP_PORT_LENGTH)), LPP_LANG_LENGTH);
            int port = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_LENGTH + LPP_PORT0_START + (i*(LPP_LANG_LENGTH+LPP_PORT_LENGTH)), LPP_PORT_LENGTH, false);
            LppList.add(new LangPortPair(language, port));
        }
    }
}
