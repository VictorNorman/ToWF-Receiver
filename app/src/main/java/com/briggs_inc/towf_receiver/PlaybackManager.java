package com.briggs_inc.towf_receiver;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import static com.briggs_inc.towf_receiver.PacketConstants.*;

interface PlaybackManagerListener {
    public void onMissingPacketRequestCreated(List<PcmAudioDataPayload> missingPayloads);
}

public class PlaybackManager {
	private static final String TAG = "PlaybackManager";

    List<PlaybackManagerListener> listeners = new ArrayList<>();
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
	//private SeqId lastPayloadSeqId;
    private SeqId lastQueuedSeqId;
	private int channelMultiplier;
	private long totalNumSamplesWrittenToBuffer;
	//private double desiredDelay;
    private float desiredDelay;
	private Boolean playFaster = false;
	private Boolean playSlower = false;

    // Derived from Audio Format
    int afSampleSizeInBytes;
    int afFrameSize;
    int audioDataMaxValidSize;
    int packetRateMS;

    // Packet Recovery Related
    private boolean sendMissingPacketRequestsEnabled;
    boolean firstPacketReceived;
    PayloadStorageList payloadStorageList;
    boolean isWaitingOnMissingPackets;
    boolean isPrimaryBurstMode;
    boolean isSecondaryBurstMode;
    SeqId highestMissingSeqId;
    //Timer burstModeWatchdogTimer;
    //int burstModeWatchdogTimerTimeoutMS;
    long burstModeTimeoutNS;
    //int delayAfterBurstModeFinishedCtr;
    //boolean isBurstModeJustFinished;
	
	private int playbackSpeed = PLAYBACK_SPEED_NORMAL;
    private long lastPacketReceivedTimeNS;
    private long currPacketReceivedTimeNS;


    public class OnBurstModeFinishedTask extends TimerTask {

        @Override
        public void run() {
            //isBurstModeJustFinished = true;
            /*
            Log.v(TAG, "--- Burst Mode Finished ---");
            delayAfterBurstModeFinishedCtr = 5;
            isPrimaryBurstMode = false;

            // Send Missing Packets (if any), though we must limit our request so that total paylostStorageList size (in secs) doesn't exceed "desiredDelay".
            //      If payloadStorageList is too big, we must cut it down to size here, then request whatever missingPayloads are left.
            if (payloadStorageList.getNumMissingPayloads() > 0) {
                float payloadStorageListSizeSecs = getNumAudioSecondsFromNumAudioBytes(payloadStorageList.getTotalNumPayloads() * audioDataMaxValidSize);

                Log.v(TAG, "======================= ");
                Log.v(TAG, String.format("payloadStorageList.totalNumPayloads: %d", payloadStorageList.getTotalNumPayloads()));
                Log.v(TAG, String.format("payloadStorageListSizeSecs: %f", payloadStorageListSizeSecs));

                if (payloadStorageListSizeSecs > desiredDelay) {
                    float numSecsToCut = payloadStorageListSizeSecs - desiredDelay;
                    Log.v(TAG, String.format("numSecsToCut: %f", numSecsToCut));
                    int numBytesToCut = (int)getNumAudioBytesFromNumAudioSeconds(numSecsToCut);
                    Log.v(TAG, String.format("numBytesToCut: %d", numBytesToCut));
                    int numPayloadsToCut = numBytesToCut / audioDataMaxValidSize;
                    Log.v(TAG, String.format("numPayloadsToCut: %d", numPayloadsToCut));
                    if (numPayloadsToCut > 0) {
                        Log.v(TAG, String.format("payloadStorageList.totalNumPayloads(Before): %d", payloadStorageList.getTotalNumPayloads()));
                        Log.v(TAG, String.format("payloadStorageList.numMissingPayloads(Before): %d", payloadStorageList.getNumMissingPayloads()));

                        payloadStorageList.removeMissingPayloadsInFirstXPayloads(numPayloadsToCut);  // Leave any full payloads 'cuz we already got them and we'll instantly queue them up anyhow with our coming up call to checkForReadyToQueuePacketsInStorageList.

                        Log.v(TAG, String.format("payloadStorageList.totalNumPayloads(After): %d", payloadStorageList.getTotalNumPayloads()));
                        Log.v(TAG, String.format("payloadStorageList.numMissingPayloads(After): %d", payloadStorageList.getNumMissingPayloads()));
                        Log.v(TAG, String.format("payloadStorageList2: %s", payloadStorageList.getAllPayloadsSeqIdsAsHexString()));

                        checkForReadyToQueuePacketsInStorageList();

                        Log.v(TAG, String.format("payloadStorageList.totalNumPayloads(After check for Ready-to-Queue): %d", payloadStorageList.getTotalNumPayloads()));
                        Log.v(TAG, String.format("payloadStorageList3: %s", payloadStorageList.getAllPayloadsSeqIdsAsHexString()));
                    }
                }


                notifyListenersOnMissingPacketRequestCreated(payloadStorageList.getMissingPayloads());
            }
            //delayAfterBurstModeFinishedCtr = 5;
            */
        }
    }

    public PlaybackManager() {
        lastQueuedSeqId = new SeqId(0x0000);
        // Packet Recovery Related
        sendMissingPacketRequestsEnabled = false;
        firstPacketReceived = false;  // To specify that we just started (so we don't think that we just skipped 100 or 1000 packets)
        payloadStorageList = new PayloadStorageList();
        isWaitingOnMissingPackets = false;
        isPrimaryBurstMode = false;
        highestMissingSeqId = new SeqId(0x0000);
        //burstModeWatchdogTimerTimeoutMS = 1;
        //delayAfterBurstModeFinishedCtr = 5;
        //isBurstModeJustFinished = false;

        desiredDelay = 1.1f;  // Note: this should be set from constructor parameter if possible. (along with: audioFormat, )

	}
	
	public void cleanUp() {
        Log.v(TAG, "PlaybackManager::cleanUp");
		if (line != null && line.getState() != AudioTrack.STATE_UNINITIALIZED) {
        	line.pause();
        	line.flush();
        	line.release();
            line = null;
        }
	}
	
	public void handleAudioDataPayload(PcmAudioDataPayload pcmAudioDataPayload) {
		/*
		if (line != null) {
	        SeqId payloadSeqId = pl.SeqId;
	        
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
		*/



        currPacketReceivedTimeNS = System.nanoTime();

        // Check if primary or secondary burst modes is FINISHED
        if (isPrimaryBurstMode || isSecondaryBurstMode) {
            if (currPacketReceivedTimeNS - lastPacketReceivedTimeNS > burstModeTimeoutNS) {
                if (isPrimaryBurstMode) {
                    // PRIMARY burstMode finished
                    //Log.v(TAG, "===PRIMARY burstMode finished.===");
                    isPrimaryBurstMode = false;
                    onPrimaryBurstModeFinished();
                    //Log.v(TAG, "---Secondary burstMode started---.");
                    isSecondaryBurstMode = true;
                } else {
                    // SECONDARY burstMode finished
                    //Log.v(TAG, "===SECONDARY burstMode finished.===");
                    isSecondaryBurstMode = false;
                }
            }
        }

        lastPacketReceivedTimeNS = currPacketReceivedTimeNS;


        /*
        if (isBurstModeJustFinished) {
            isBurstModeJustFinished = false;
            onPrimaryBurstModeFinished();
        }
        */

        /*
        delayAfterBurstModeFinishedCtr--;
        if (delayAfterBurstModeFinishedCtr < 0) { delayAfterBurstModeFinishedCtr = 0; }
        */

        /*
        // Reset burstMode Watchdog Timer (if burstMode is true)
        if (isPrimaryBurstMode) {
            if (burstModeWatchdogTimer != null) {
                burstModeWatchdogTimer.cancel();
                burstModeWatchdogTimer.purge();
            }
            burstModeWatchdogTimer = new Timer();
            burstModeWatchdogTimer.schedule(new OnBurstModeFinishedTask(), burstModeWatchdogTimerTimeoutMS);
        }
        */

        if (line != null) {
            SeqId currSeqId = pcmAudioDataPayload.SeqId;

            //Log.v(TAG, "---------------------");
            //Log.v(TAG, String.format("Received Audio Packet: (0x%04x) {%s}", currSeqId.intValue, pcmAudioDataPayload instanceof PcmAudioDataMissingPayload ? "Missing" : "Regular"));

            // === Fill the Play Queue, based on sendMissingPacketsRequestsSwitch ===
            if (sendMissingPacketRequestsEnabled) {
                if (!firstPacketReceived) {
                    Log.d(TAG, String.format("First Packet (0x%04x) JUST received (not counting any SKIPPED packets)", currSeqId.intValue));
                    firstPacketReceived = true;
                    queueThisPayload(pcmAudioDataPayload);
                } else if (currSeqId.isLessThanOrEqualToSeqId(lastQueuedSeqId)) {
                    //Log.d(TAG, String.format("This Packet (0x%04x) has already been received & Queued to be played. Not doing anything with the packet.", currSeqId.intValue));
                } else if (currSeqId.isEqualToSeqId(new SeqId(lastQueuedSeqId.intValue + 1))) {
                    //Log.v(TAG, String.format("This packet (0x%04x) is next in line for the Play Queue.", currSeqId.intValue));
                    // Check if payloadStorageList has this one at its 1st element
                    if (payloadStorageList.hasMissingPayloadAtFirstElementWithThisSeqId(currSeqId)) {
                        //Log.v(TAG, String.format(" Looks like it (0x%04x) was a 'missing packet' - GREAT, the Server sent it again! Regular or Missing: (%s). Queueing it up & Removing it from missingPacketsSeqIdsList", currSeqId.intValue, pcmAudioDataPayload instanceof PcmAudioDataMissingPayload ? "M" : "R"));
                        payloadStorageList.popFirstPayload();
                        queueThisPayload(pcmAudioDataPayload);

                        checkForReadyToQueuePacketsInStorageList();  // This function also queues them up for playing if they exist.

                        if (payloadStorageList.getTotalNumPayloads() == 0) {
                            isWaitingOnMissingPackets = false;
                            //Log.v(TAG, String.format("NOT WAITING on Missing Packet(s) anymore!"));
                        } else {
                            //Log.v(TAG, String.format("STILL WAITING on Missing Packet(s)!"));
                        }
                    } else {
                        // Check that we're in a good state
                        if (payloadStorageList.hasMissingPayloadAtFirstElement()) {
                            Log.v(TAG, String.format(" !!!  Don't think we should get here! This packet (0x%04x) is next in line for Play Queue, but we have a missing payload (0x%04x) at the front of the StorageList. Anything 'missing' at the front of the StorageList should be holding up audio playback.", currSeqId.intValue, payloadStorageList.getFirstPayload().SeqId.intValue));
                        } else {
                            //Log.v(TAG, String.format(" there are NO missing packets at front of StorageList. Queueing it (0x%04x) up to be played.", currSeqId.intValue));
                            queueThisPayload(pcmAudioDataPayload);
                        }
                    }
                } else if (currSeqId.isGreaterThanSeqId(new SeqId(lastQueuedSeqId.intValue + 1))) {
                    int numSkippedPackets = currSeqId.numSeqIdsExclusivelyBetweenMeAndSeqId(lastQueuedSeqId);
                    //Log.v(TAG, String.format("%d packet(s) between NOW (0x%04x) and LAST_QUEUED (0x%04x). Updating payloadStorageList as appropriate", numSkippedPackets, currSeqId.intValue, lastQueuedSeqId.intValue));

                    /*
                    if (!isPrimaryBurstMode) {
                        Log.v(TAG, String.format("---Burst Mode Started---"));
                    }
                    isPrimaryBurstMode = true;  // In case we just got a whole "burst" of packets (i.e. received faster than the packet rate for the given sample rate)
                    */

                    /*
                    if (delayAfterBurstModeFinishedCtr == 0) {
                        if (!isPrimaryBurstMode) {
                            //Log.v(TAG, String.format("---Burst Mode Started---"));
                        }
                        isPrimaryBurstMode = true;  // In case we just got a whole "burst" of packets (i.e. received faster than the packet rate for the given sample rate)
                    }
                    */
                    if (!isPrimaryBurstMode && !isSecondaryBurstMode) {
                        //Log.v(TAG, "---Primary Burst Mode Started---");
                        isPrimaryBurstMode = true;  // In case we just got a whole "burst" of packets (i.e. received faster than the packet rate for the given sample rate)
                    }


                    // === Add the "Missing Packets" if they're not already in the list ===
                    boolean addMissingPackets = false;
                    SeqId currHighestMissingSeqId = new SeqId(currSeqId.intValue - 1);
                    if (!isWaitingOnMissingPackets) {
                        // Up to this point, we have NOT been waiting on any missing packets
                        addMissingPackets = true;
                        highestMissingSeqId = currHighestMissingSeqId;
                    } else {
                        // We are already waiting on 1 or more missing packets
                        if (currHighestMissingSeqId.isGreaterThanSeqId(new SeqId(highestMissingSeqId.intValue + 1))) {
                            addMissingPackets = true;
                        }
                        if (currHighestMissingSeqId.isGreaterThanSeqId(highestMissingSeqId)) {
                            highestMissingSeqId = currHighestMissingSeqId;
                        }
                    }

                    if (addMissingPackets) {
                        /*
                        if (!isPrimaryBurstMode) {
                            Log.v(TAG, String.format("---Burst Mode Started---"));
                        }
                        isPrimaryBurstMode = true;  // In case we just got a whole "burst" of packets (i.e. received faster than the packet rate for the given sample rate)
                        */

                        //Log.v(TAG, "ADDING Missing Packets Range to payloadStorageList ");
                        List<PcmAudioDataPayload> incrMissingPayloadsArr = new ArrayList<>();
                        for (int i = 0; i < numSkippedPackets; i++) {
                            SeqId missingSeqId = new SeqId(lastQueuedSeqId.intValue + 1 + i);
                            incrMissingPayloadsArr.add(new PcmAudioDataRegularPayload(missingSeqId));
                        }
                        payloadStorageList.addIncrementingMissingPayloads(incrMissingPayloadsArr);
                    } else {
                        //Log.v(TAG, "NOT ADDING Missing Packets Range to payloadStorageList");
                    }

                    // === Add the "Received" packet ===
                    pcmAudioDataPayload.storeAudioData();  // First STORE the AudioData (becuase up to now, it just had a pointer to netMan's dgData buffer.
                    payloadStorageList.addFullPayload(pcmAudioDataPayload);

                    isWaitingOnMissingPackets = true;
                }
            } else {
                // sendMissingPacketRequestsEnabled == false
                //  so just send packets to play queue as they arrive. Only exception is if only 1-3 packets are skipped, queue the received packet and repeat 1-3 times 'cuz it's "pretty likely" they were truly lost & not just out of order.

                // Check if this is a <Regular> or <Missing> packet. We only want to care about <Regular> packets.
                if (pcmAudioDataPayload instanceof PcmAudioDataRegularPayload) {
                    // Queue the payload
                    queueThisPayload(pcmAudioDataPayload);
                    //lastQueuedSeqId = currSeqId;

                    // If X packets were skipped, fill in that space with a copy(s) of this packet
                    //   will keep the buffer fuller, so we don't have to SLOW DOWN so much.
                    //   the audio will still "skip" but it did that before, so nothing really lost in audio quality
                    if (currSeqId.isGreaterThanSeqId(new SeqId(lastQueuedSeqId.intValue + 1))) {
                        int numSkippedPackets = currSeqId.numSeqIdsExclusivelyBetweenMeAndSeqId(lastQueuedSeqId);
                        //Log.v(TAG, "SKIPPED (" + numSkippedPackets + ") packets ");
                        if (numSkippedPackets <= 3) {
                            for (int i = 0; i < numSkippedPackets; i++) {
                                queueThisPayload(pcmAudioDataPayload);  // Again
                            }
                        }
                    }
                }
            }
        }
	}

    private void queueThisPayload(PcmAudioDataPayload payload) {

        //Log.v(TAG, String.format("queueThisPayload: 0x%04x", payload.SeqId.intValue));

        // Audio Data Length
        int audioDataAllocatedBytes = payload.AudioDataAllocatedBytes;
        if (audioDataAllocatedBytes < 0) {
            audioDataAllocatedBytes = 0;
        }
        //Log.v(TAG, " audioDataAllocatedBytes: " + audioDataAllocatedBytes);

        // Create an array of "shorts" (instead of bytes) & make the mono data into stereo audio data
        //channelMultiplier = audioFormat.Channels == 1 ? 2 : 1;  // If mono data, *2 for STEREO
        //audioDataShort = new short[audioDataAllocatedBytes/audioFormat.SampleSizeInBytes*channelMultiplier];
        // ^ ==== Note: moved the above lines to createNewSpeakerLine() ==== ^

        // Convert byte[] to short[]. And while we're at it, add same data to other (stereo) channel
        for (int i = 0; i < audioDataAllocatedBytes; i+=channelMultiplier) {
            //audioDataShort[i] = (short)((payload.AudioData[i+1] << 8) + (payload.AudioData[i] & 0xFF));
            // Determine whether the payload has StoredAudioData or just a pointer to netMan's dgData buffer. If STORED, use it, otherwise, use pointer.
            if (payload.StoredAudioData != null) {
                audioDataShort[i] = (short) ((payload.StoredAudioData[i+1] << 8) + (payload.StoredAudioData[i] & 0xFF));
            } else {
                audioDataShort[i] = (short) ((payload.DgData[DG_DATA_HEADER_LENGTH + PcmAudioDataPayload.ADPL_HEADER_LENGTH + i + 1] << 8) + (payload.DgData[DG_DATA_HEADER_LENGTH + PcmAudioDataPayload.ADPL_HEADER_LENGTH + i] & 0xFF));
            }
            for (int j = 1; j < channelMultiplier; j++) {
                audioDataShort[i + j] = audioDataShort[i];
            }
        }

        //Log.v(TAG, String.format(" audioDataShortArr: (0x%04x, 0x%04x, 0x%04x, 0x%04x ... 0x%04x, 0x%04x, 0x%04x, 0x%04x)",
        //        audioDataShort[0], audioDataShort[1] , audioDataShort[2], audioDataShort[3],
        //        audioDataShort[audioDataAllocatedBytes-4], audioDataShort[audioDataAllocatedBytes-3] , audioDataShort[audioDataAllocatedBytes-2], audioDataShort[audioDataAllocatedBytes-1]));

        // Write audio data to Speaker (line) (Assuming sampleSizeInBytes == 2)
        int shortsWritten;
        //shortsWritten = line.write(audioDataShort, 0, audioDataShort.length);
        shortsWritten = line.write(audioDataShort, 0, audioDataAllocatedBytes);
        //Log.v(TAG, String.format("shortsWritten: %d", shortsWritten));
        totalNumSamplesWrittenToBuffer += shortsWritten / channelMultiplier;

        lastQueuedSeqId = payload.SeqId;
    }

    private void checkForReadyToQueuePacketsInStorageList() {
        //Log.v(TAG, "checking for ready-to-queue packets in StorageList");

        while (payloadStorageList.hasFullPayloadAtFirstElement()) {
            //Log.v(TAG, String.format("  Great! We've got a packet (0x%04x) in the StorageList that we can queue up now. Queueing it & Removing from the StorageList.", payloadStorageList.getFirstPayload().SeqId.intValue));
            queueThisPayload(payloadStorageList.popFirstPayload());
        }
    }

    public void setSendMissingPacketRequestsEnabled(boolean enabled) {
        this.sendMissingPacketRequestsEnabled = enabled;
    }
	
	public int changePlaybackSpeedIfNeeded() {
		// Return what the playback speed changed to (or -1 if playback speed doesn't need to change)
		
		// Check if playback should be changed to Faster, Slower, or Normal speed - or keep it unchanged

        if (line != null) {  // only if we have a line to work with
            //float totalNumSecondsWritten = Util.convertAudioSamplesToSeconds(totalNumSamplesWrittenToBuffer, audioFormat);
            //float totalNumSecondsPlayed = Util.convertAudioSamplesToSeconds(line.getPlaybackHeadPosition(), audioFormat);
            float totalNumSecondsWritten = getNumAudioSecondsFromNumAudioBytes(totalNumSamplesWrittenToBuffer * afSampleSizeInBytes);
            float totalNumSecondsPlayed = getNumAudioSecondsFromNumAudioBytes(line.getPlaybackHeadPosition() * afSampleSizeInBytes);
            float numSecondsInBuffer = totalNumSecondsWritten - totalNumSecondsPlayed;

            //Log.d(TAG, "totalNumSecondsWritten: " + totalNumSecondsWritten);
            //Log.d(TAG, "totalNumSecondsPlayed: " + totalNumSecondsPlayed);
            //Log.d(TAG, "secs in buffer: " + numSecondsInBuffer);

            if (numSecondsInBuffer > desiredDelay + desiredDelay / 2.0) {
                if (!playFaster) {
                    Log.v(TAG, "Time to play FASTER.");
                    playFaster = true;
                    playSlower = false;
                    line.setPlaybackRate((int)(audioFormat.SampleRate * FASTER_PLAYBACK_MULTIPLIER));
                    playbackSpeed = PLAYBACK_SPEED_FASTER;
                    return playbackSpeed;
                }
            } else if (numSecondsInBuffer < desiredDelay - desiredDelay / 2.0) {
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
                        line.setPlaybackRate((int)audioFormat.SampleRate);
                        playFaster = false;
                        playSlower = false;
                        playbackSpeed = PLAYBACK_SPEED_NORMAL;
                        return playbackSpeed;
                    }
                } else if (playSlower) {
                    if (numSecondsInBuffer > desiredDelay) {
                        Log.v(TAG, "Time to play NORMAL speed (after playing slower)");
                        line.setPlaybackRate((int)audioFormat.SampleRate);
                        playFaster = false;
                        playSlower = false;
                        playbackSpeed = PLAYBACK_SPEED_NORMAL;
                        return playbackSpeed;
                    }
                }
            }
        }

        return PLAYBACK_SPEED_UNCHANGED;  // -1
	}

	public void setDesiredDelay(float desiredDelay) {
		this.desiredDelay = desiredDelay;
	}
	
	public void createNewSpeakerLine(AudioFormatStruct af) {
		cleanUp();
		audioFormat = new AudioFormatStruct(af.SampleRate, af.SampleSizeInBits, af.Channels, af.IsSigned, af.IsBigEndian);

        // Derived from AudioFormat
        afSampleSizeInBytes = af.SampleSizeInBits / 8; if (af.SampleSizeInBits % 8 != 0) { afSampleSizeInBytes++; }
        afFrameSize = afSampleSizeInBytes * af.Channels;
        audioDataMaxValidSize = (ADPL_AUDIO_DATA_AVAILABLE_SIZE - (ADPL_AUDIO_DATA_AVAILABLE_SIZE % afFrameSize));
        packetRateMS = (int)(1.0 / (af.SampleRate * afFrameSize / audioDataMaxValidSize) * 1000);
        burstModeTimeoutNS = (packetRateMS - 1) * 1000000;  // -1ms so we're not too close to the edge.
        Log.v(TAG, "burstModeTimeoutNS: " + burstModeTimeoutNS);
        channelMultiplier = audioFormat.Channels == 1 ? 2 : 1;  // If mono data, *2 for stereo
        //audioDataShort = new short[audioDataAllocatedBytes/audioFormat.SampleSizeInBytes*channelMultiplier];
        audioDataShort = new short[ADPL_AUDIO_DATA_AVAILABLE_SIZE/audioFormat.SampleSizeInBytes*channelMultiplier];

		
		int desiredSpeakerLineBufferSizeInBytes = (int) (DESIRED_SPEAKER_LINE_BUFFER_SIZE_SECS * audioFormat.SampleRate * audioFormat.SampleSizeInBytes * audioFormat.Channels);
        int encoding = audioFormat.SampleSizeInBytes == 2 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        line = new AudioTrack(AudioManager.STREAM_MUSIC, (int)audioFormat.SampleRate, AudioFormat.CHANNEL_OUT_STEREO, encoding, desiredSpeakerLineBufferSizeInBytes, AudioTrack.MODE_STREAM);
        line.play();  // ??? Here, right???
	}

	public int getPlaybackSpeed() {
		return playbackSpeed;
	}

    public float getNumAudioSecondsFromNumAudioBytes(long numBytes) {
        return numBytes / audioFormat.SampleRate / afSampleSizeInBytes / audioFormat.Channels;
    }

    public long getNumAudioBytesFromNumAudioSeconds(float audioSeconds) {
        return (long)(audioSeconds * audioFormat.SampleRate * afSampleSizeInBytes * audioFormat.Channels);
    }

    public void addListener(PlaybackManagerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PlaybackManagerListener listener) {
        listeners.remove(listener);
    }

    private void notifyListenersOnMissingPacketRequestCreated(List<PcmAudioDataPayload> missingPackets) {
        //Log.v(TAG, "notifyListenersOnMissingPacketRequestCreated");
        for (PlaybackManagerListener listener : listeners) {
            listener.onMissingPacketRequestCreated(missingPackets);
        }
    }

    private void onPrimaryBurstModeFinished() {
        //Log.v(TAG, "--- Burst Mode Finished --- ");
        //delayAfterBurstModeFinishedCtr = 5;
        isPrimaryBurstMode = false;

        // Send Missing Packets (if any), though we must limit our request so that total paylostStorageList size (in secs) doesn't exceed "desiredDelay".
        //      If payloadStorageList is too big, we must cut it down to size here, then request whatever missingPayloads are left.
        if (payloadStorageList.getNumMissingPayloads() > 0) {
            float payloadStorageListSizeSecs = getNumAudioSecondsFromNumAudioBytes(payloadStorageList.getTotalNumPayloads() * audioDataMaxValidSize);

            //Log.v(TAG, "======================= ");
            //Log.v(TAG, String.format("payloadStorageList.totalNumPayloads: %d", payloadStorageList.getTotalNumPayloads()));
            //Log.v(TAG, String.format("payloadStorageListSizeSecs: %f", payloadStorageListSizeSecs));

            if (payloadStorageListSizeSecs > desiredDelay) {
                float numSecsToCut = payloadStorageListSizeSecs - desiredDelay;
                //Log.v(TAG, String.format("numSecsToCut: %f", numSecsToCut));
                int numBytesToCut = (int)getNumAudioBytesFromNumAudioSeconds(numSecsToCut);
                //Log.v(TAG, String.format("numBytesToCut: %d", numBytesToCut));
                int numPayloadsToCut = numBytesToCut / audioDataMaxValidSize;
                //Log.v(TAG, String.format("numPayloadsToCut: %d", numPayloadsToCut));
                if (numPayloadsToCut > 0) {
                    //Log.v(TAG, String.format("payloadStorageList.totalNumPayloads(Before): %d", payloadStorageList.getTotalNumPayloads()));
                    //Log.v(TAG, String.format("payloadStorageList.numMissingPayloads(Before): %d", payloadStorageList.getNumMissingPayloads()));

                    payloadStorageList.removeMissingPayloadsInFirstXPayloads(numPayloadsToCut);  // Leave any full payloads 'cuz we already got them and we'll instantly queue them up anyhow with our coming up call to checkForReadyToQueuePacketsInStorageList.

                    // Update lastQueuedSeqId, even though it hasn't actually been queued. Need to do this so when we compare the next received packet to lastQueuedSeqId, the right path for the packet will be chosen.
                    lastQueuedSeqId = new SeqId(payloadStorageList.getFirstPayload().SeqId.intValue - 1);

                    //Log.v(TAG, String.format("payloadStorageList.totalNumPayloads(After): %d", payloadStorageList.getTotalNumPayloads()));
                    //Log.v(TAG, String.format("payloadStorageList.numMissingPayloads(After): %d", payloadStorageList.getNumMissingPayloads()));
                    //Log.v(TAG, String.format("payloadStorageList2: %s", payloadStorageList.getAllPayloadsSeqIdsAsHexString()));

                    checkForReadyToQueuePacketsInStorageList();

                    //Log.v(TAG, String.format("payloadStorageList.totalNumPayloads(After check for Ready-to-Queue): %d", payloadStorageList.getTotalNumPayloads()));
                    //Log.v(TAG, String.format("payloadStorageList3: %s", payloadStorageList.getAllPayloadsSeqIdsAsHexString()));
                }
            }


            notifyListenersOnMissingPacketRequestCreated(payloadStorageList.getMissingPayloads());
        }
        //delayAfterBurstModeFinishedCtr = 5;
    }
}