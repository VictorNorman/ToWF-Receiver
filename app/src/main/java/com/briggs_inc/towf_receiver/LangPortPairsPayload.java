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
    public static final int LPP_LANG0_START = 2;
    public static final int LPP_LANG_LENGTH = 16;
    public static final int LPP_PORT0_START = 18;
    public static final int LPP_PORT_LENGTH = 2;
    
	// "Struct" Variables
    List<LangPortPair> LppList = new ArrayList<LangPortPair>();
	
	//public LangPortPairsPayload(byte[] dgDataPayload) {
    public LangPortPairsPayload(byte[] dgData) {
		/*
        int numLangPortPairs = Util.getIntFromByteArray(dgDataPayload, LPP_NUM_PAIRS_START, LPP_NUM_PAIRS_LENGTH, false);
		
		for (int i = 0; i < numLangPortPairs; i++) {
			String language = Util.getNullTermStringFromByteArray(dgDataPayload, LPP_LANG0_START + (i*(LPP_LANG_LENGTH+LPP_PORT_LENGTH)), LPP_LANG_LENGTH);
			int port = Util.getIntFromByteArray(dgDataPayload, LPP_PORT0_START + (i*(LPP_LANG_LENGTH+LPP_PORT_LENGTH)), LPP_PORT_LENGTH, false);
			LppList.add(new LangPortPair(language, port));
		}
		*/
        int numLangPortPairs = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_LENGTH + LPP_NUM_PAIRS_START, LPP_NUM_PAIRS_LENGTH, false);

        for (int i = 0; i < numLangPortPairs; i++) {
            String language = Util.getNullTermStringFromByteArray(dgData, DG_DATA_HEADER_LENGTH + LPP_LANG0_START + (i*(LPP_LANG_LENGTH+LPP_PORT_LENGTH)), LPP_LANG_LENGTH);
            int port = Util.getIntFromByteArray(dgData, DG_DATA_HEADER_LENGTH + LPP_PORT0_START + (i*(LPP_LANG_LENGTH+LPP_PORT_LENGTH)), LPP_PORT_LENGTH, false);
            LppList.add(new LangPortPair(language, port));
        }
	}
}
