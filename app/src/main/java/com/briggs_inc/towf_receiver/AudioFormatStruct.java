package com.briggs_inc.towf_receiver;

public class AudioFormatStruct {
	//public int SampleRate;
    public float SampleRate;
	public int SampleSizeInBits;
	public int Channels;
	public boolean IsSigned;
	public boolean IsBigEndian;
	
	// Derived
	public int SampleSizeInBytes;
	
	
	public AudioFormatStruct(float sampleRate, int sampleSizeInBits, int channels, boolean isSigned, boolean isBigEndian) {
		this.SampleRate = sampleRate;
		this.SampleSizeInBits = sampleSizeInBits;
		this.Channels = channels;
		this.IsSigned = isSigned;
		this.IsBigEndian = isBigEndian;
		
		this.SampleSizeInBytes = sampleSizeInBits / 8; if (sampleSizeInBits % 8 != 0) { this.SampleSizeInBytes++; }
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof AudioFormatStruct) {
			AudioFormatStruct af = (AudioFormatStruct) o;
			if (this.SampleRate == af.SampleRate &&
				this.SampleSizeInBits == af.SampleSizeInBits &&
				this.Channels == af.Channels &&
				this.IsSigned == af.IsSigned &&
				this.IsBigEndian == af.IsBigEndian
			) {
				return true;
			}
		}
		
		return false;
	}
}
