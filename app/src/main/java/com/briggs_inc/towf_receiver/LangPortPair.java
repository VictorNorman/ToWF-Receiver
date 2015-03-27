package com.briggs_inc.towf_receiver;

public class LangPortPair {
	public String Language;
	public int Port;
	
	public LangPortPair(String language, int port) {
		this.Language = language;
		this.Port = port;
	}
	
	@Override
	public String toString() {
		return Language;
	}
}
