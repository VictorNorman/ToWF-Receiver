package com.briggs_inc.towf_receiver;

import java.net.Inet4Address;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

public class ClientListeningPayload extends Payload {
	private static final String TAG = "ClientListeningPayload";
	
	// Constants
	public static final int CLPL_IS_LISTENING_START = 0;
    public static final int CLPL_IS_LISTENING_LENGTH = 1;
    public static final int CLPL_OS_TYPE_START = 1;
    public static final int CLPL_OS_TYPE_LENGTH = 1;
    public static final int CLPL_PORT_START = 2;
    public static final int CLPL_PORT_LENGTH = 2;
    public static final int CLPL_OS_VERSION_STR_START = 4;
    public static final int CLPL_OS_VERSION_STR_LENGTH = 8;
    public static final int CLPL_HW_MANUFACTURER_STR_START = 12;
    public static final int CLPL_HW_MANUFACTURER_STR_LENGTH = 16;
    public static final int CLPL_HW_MODEL_STR_START = 28;
    public static final int CLPL_HW_MODEL_STR_LENGTH = 16;
    public static final int CLPL_USERS_NAME_START = 44;
    public static final int CLPL_USERS_NAME_LENGTH = 32;
    
    // "Struct" Variables
    public Boolean IsListening;
    public int OsType;
    public int Port;
    public String MACAddress;
    public int IPAddress;
    public String OsVersion;
    public String HwManufacturer;
    public String HwModel;
    public String UsersName;
    
    byte dgDataPayload[] = new byte[UDP_DATA_PAYLOAD_SIZE];
    
    public ClientListeningPayload(Boolean isListening, int osType, int port, String macAddress, int ipAddress, String osVersion, String hwManufacturer, String hwModel, String usersName) {
		this.IsListening = isListening;
		this.OsType = osType;
		this.Port = port;
		this.MACAddress = macAddress;
		this.IPAddress = ipAddress;
		this.OsVersion = osVersion;
		this.HwManufacturer = hwManufacturer;
		this.HwModel = hwModel;
		this.UsersName = usersName;
		
		// IsListening
		Util.putIntInsideByteArray(IsListening ? 1 : 0, dgDataPayload, CLPL_IS_LISTENING_START, CLPL_IS_LISTENING_LENGTH, false);
		
		// OsType
		Util.putIntInsideByteArray(OsType, dgDataPayload, CLPL_OS_TYPE_START, CLPL_OS_TYPE_LENGTH, false);
		
		// Port
		Util.putIntInsideByteArray(Port, dgDataPayload, CLPL_PORT_START, CLPL_PORT_LENGTH, false);
		
		// OS Version
		Util.putStringInsideByteArray(OsVersion, dgDataPayload, CLPL_OS_VERSION_STR_START, CLPL_OS_VERSION_STR_LENGTH);
		
		// HW Manufacturer
		Util.putStringInsideByteArray(HwManufacturer, dgDataPayload, CLPL_HW_MANUFACTURER_STR_START, CLPL_HW_MANUFACTURER_STR_LENGTH);
		
		// HW Model
		Util.putStringInsideByteArray(HwModel, dgDataPayload, CLPL_HW_MODEL_STR_START, CLPL_HW_MODEL_STR_LENGTH);
		
		// Users Name
		Util.putStringInsideByteArray(UsersName, dgDataPayload, CLPL_USERS_NAME_START, CLPL_USERS_NAME_LENGTH);
	}

	public byte[] getDgDataPayloadBytes() {
		return dgDataPayload;
	}
}
