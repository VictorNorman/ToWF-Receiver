package com.briggs_inc.towf_receiver;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class PlaybackManager {
	private static final String TAG = "PlaybackManager";

	private static final float DESIRED_SPEAKER_LINE_BUFFER_SIZE_SECS = 4.0f;
	public static final int PLAYBACK_SPEED_UNCHANGED = -1;
	public static final int PLAYBACK_SPEED_NORMAL = 0;
	public static final int PLAYBACK_SPEED_FASTER = 1;
	public static final int PLAYBACK_SPEED_SLOWER = 2;
	
	public static final float FASTER_PLAYBACK_MULTIPLIER = 1.2F;
	public static final float SLOWER_PLAYBACK_MULTIPLIER = 0.8F;

	AudioFormatStruct audioFormat;
	AudioTrack line;
	
	private short audioDataShort[]; // For a line setup as: AudioFormat.ENCODING_PCM_16BIT
	private int lastPayloadSeqId;
	private int channelMultiplier;
	private long totalNumSamplesWrittenToBuffer;
	private double desiredDelay;
	private Boolean playFaster = false;
	private Boolean playSlower = false;
	
	private int playbackSpeed = PLAYBACK_SPEED_NORMAL;
	
	public PlaybackManager() {
		
	}
	
	public void cleanUp() {
		if (line != null) {
        	line.pause();
        	line.flush();
        	line.release();
            line = null;
        }
	}
	
	public void handleAudioDataPayload(PcmAudioDataPayload pl) {
		
		if (line != null) {
	        int payloadSeqId = pl.SeqId;
	        
	        int numSkippedPackets = 0;
	    	
	        // Watch for irregular changes in: payloadSeqId
	        if (payloadSeqId > lastPayloadSeqId + 1) {
	        	numSkippedPackets = payloadSeqId - lastPayloadSeqId - 1;
	        	Log.w(TAG, numSkippedPackets + " packet(s) SKIPPED! (" + String.format("0x%04x", lastPayloadSeqId) + ", " + String.format("0x%04x", payloadSeqId) + ")");
	        } else if (payloadSeqId < lastPayloadSeqId) {
	        	if (!(payloadSeqId == 0 && lastPayloadSeqId == 65535)) {
	        		Log.w(TAG, "Packet OUT OF ORDER (LastId: " + String.format("0x%04x", lastPayloadSeqId) + ", CurrId: " + String.format("0x%04x", payloadSeqId) + ")");
	        	}
	        } else if (payloadSeqId == lastPayloadSeqId) {
	        	Log.w(TAG, "Packet received with SAME SeqId as last packet! Shouldn't happen! (Id:" + String.format("0x%04x", payloadSeqId) + ")");
	        }
	        
	        lastPayloadSeqId = payloadSeqId;
	
	        // Audio Data Length
	        int audioDataAllocatedBytes = pl.AudioDataAllocatedBytes;
	        //Log.v(TAG, " audioDataLength: " + audioDataLength);
	        if (audioDataAllocatedBytes < 0) {
	            audioDataAllocatedBytes = 0;
	        }
	        
	        // Re-create and fill the audioDataShort buffer (Assuming SampleSizeInBytes == 2)
	        
	    	// Create an array of "shorts" (instead of bytes) & make the mono data into stereo audio data
	        channelMultiplier = audioFormat.Channels == 1 ? 2 : 1;  // If mono data, *2 for stereo
			audioDataShort = new short[audioDataAllocatedBytes/audioFormat.SampleSizeInBytes*channelMultiplier];
			
			// Convert byte[] to short[]. And while we're at it, add same data to other (stereo) channel 
			for (int i = 0; i < audioDataAllocatedBytes; i+=channelMultiplier) {
				audioDataShort[i] = (short)((pl.AudioData[i+1] << 8) + (pl.AudioData[i] & 0xFF));
				for (int j = 1; j < channelMultiplier; j++) {
					audioDataShort[i+j] = audioDataShort[i];
				}
			}
			
			//Log.v(TAG, " audioDataShort: " + audioDataShort[0] + "," + audioDataShort[1] + "," + audioDataShort[2] + "," + audioDataShort[3]);
			
			// Write audio data to Speaker (line) (Assuming sampleSizeInBytes == 2)
			int shortsWritten;
			shortsWritten = line.write(audioDataShort, 0, audioDataShort.length);
			totalNumSamplesWrittenToBuffer += shortsWritten / channelMultiplier;
			
			// If X packets were skipped, fill in that space with a copy(s) of this packet
            //   will keep the buffer fuller, so we don't have to SLOW DOWN so much.
            //   the audio will still "skip" but it did that before, so nothing really lost in audio quality
            if (numSkippedPackets <= 5) {
                for (int i = 0; i < numSkippedPackets; i++) {
                	shortsWritten = line.write(audioDataShort, 0, audioDataShort.length);
        			totalNumSamplesWrittenToBuffer += shortsWritten / channelMultiplier;
                }
            }
		}
	}
	
	public int changePlaybackSpeedIfNeeded() {
		// Return what the playback speed changed to (or -1 if playback speed doesn't need to change)
		
		// Check if playback should be changed to Faster, Slower, or Normal speed - or keep it unchanged
		
        float totalNumSecondsWritten = Util.convertAudioSamplesToSeconds(totalNumSamplesWrittenToBuffer, audioFormat);
        float totalNumSecondsPlayed = Util.convertAudioSamplesToSeconds(line.getPlaybackHeadPosition(), audioFormat);
        float numSecondsInBuffer = totalNumSecondsWritten - totalNumSecondsPlayed;
        
        //Log.d(TAG, "totalNumSecondsWritten: " + totalNumSecondsWritten);
        //Log.d(TAG, "totalNumSecondsPlayed: " + totalNumSecondsPlayed);
        //Log.d(TAG, "secs in buffer: " + numSecondsInBuffer);
        
        if (numSecondsInBuffer > desiredDelay + desiredDelay/2.0) {
        	if (!playFaster) {
        		Log.v(TAG, "Time to play FASTER.");
        		playFaster = true;
        		playSlower = false;
        		line.setPlaybackRate((int)(audioFormat.SampleRate * FASTER_PLAYBACK_MULTIPLIER));
        		playbackSpeed = PLAYBACK_SPEED_FASTER; 
        		return playbackSpeed;
        	}
        } else if (numSecondsInBuffer < desiredDelay - desiredDelay/2.0) {
        	if (!playSlower) {
        		Log.v(TAG, "Time to play SLOWER.");
        		playSlower = true;
        		playFaster = false;
        		line.setPlaybackRate((int)(audioFormat.SampleRate * SLOWER_PLAYBACK_MULTIPLIER));
        		playbackSpeed = PLAYBACK_SPEED_SLOWER;
        		return playbackSpeed;
        	}
        } else {
        	if (playFaster) {
        		if (numSecondsInBuffer < desiredDelay) {
        			Log.v(TAG, "Time to play NORMAL speed (after playing faster)");
            		line.setPlaybackRate(audioFormat.SampleRate);
            		playFaster = false;
            		playSlower = false;
            		playbackSpeed = PLAYBACK_SPEED_NORMAL;
            		return playbackSpeed;
        		}
        	} else if (playSlower) {
        		if (numSecondsInBuffer > desiredDelay) {
        			Log.v(TAG, "Time to play NORMAL speed (after playing slower)");
            		line.setPlaybackRate(audioFormat.SampleRate);
            		playFaster = false;
            		playSlower = false;
            		playbackSpeed = PLAYBACK_SPEED_NORMAL;
            		return playbackSpeed;
        		}
        	}
        }
        
        return PLAYBACK_SPEED_UNCHANGED;  // -1
	}

	public void setDesiredDelay(double desiredDelay) {
		this.desiredDelay = desiredDelay;
	}
	
	public void createNewSpeakerLine(AudioFormatStruct af) {
		cleanUp();
		audioFormat = new AudioFormatStruct(af.SampleRate, af.SampleSizeInBits, af.Channels, af.IsSigned, af.IsBigEndian);
		
		int desiredSpeakerLineBufferSizeInBytes = (int) (DESIRED_SPEAKER_LINE_BUFFER_SIZE_SECS * audioFormat.SampleRate * audioFormat.SampleSizeInBytes * audioFormat.Channels);
        int encoding = audioFormat.SampleSizeInBytes == 2 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        line = new AudioTrack(AudioManager.STREAM_MUSIC, audioFormat.SampleRate, AudioFormat.CHANNEL_OUT_STEREO, encoding, desiredSpeakerLineBufferSizeInBytes, AudioTrack.MODE_STREAM);
        line.play();  // ??? Here, right???
	}

	public int getPlaybackSpeed() {
		return playbackSpeed;
	}
}