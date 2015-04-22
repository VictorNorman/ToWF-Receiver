package com.briggs_inc.towf_receiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by briggsm on 4/19/15.
 */
public class PayloadStorageList {
    // Stores PcmAudioDataPayload's, but some are "full payloads" and some are "missing payloads" (missing => have no audiodata, ie. DgData == null)

    // I think all the methods need to be "synchronized" because the thread that write to this list is a different thread than the one that reads from this list.

    List<PcmAudioDataPayload> payloadStorageList;

    public PayloadStorageList () {
        payloadStorageList = new ArrayList<>();
    }

    public synchronized void addIncrementingMissingPayloads(List<PcmAudioDataPayload> incrMissingPayloadsList) {
        // missingPayloadsList must have Incrementing seqId's

        if (incrMissingPayloadsList.size() == 0) {
            return;
        }

        boolean listChanged = false;
        if (payloadStorageList.size() == 0) {
            // Add them all right now.
            payloadStorageList.addAll(incrMissingPayloadsList);
            listChanged = false;  // Technically, the list DID change, but setting to false, because we don't need to sort the array since incrMissingPayloadsList is already in Incrementing order.
        } else if ( incrMissingPayloadsList.get(0).SeqId.isGreaterThanSeqId( payloadStorageList.get(payloadStorageList.size() - 1).SeqId ) ) {
            // Add them all right now.
            payloadStorageList.addAll(incrMissingPayloadsList);
            listChanged = false;  // Technically, the list DID change, but setting to false, because we don't need to sort the array since incrMissingPayloadsList is already in Incrementing order.
        } else {
            // Add each 1 at a time (if not already there)
            for (int i = 0; i < incrMissingPayloadsList.size(); i++) {
                if (!this.hasPayloadAnywhereWithThisSeqId( incrMissingPayloadsList.get(i).SeqId ) ) {
                    payloadStorageList.add(incrMissingPayloadsList.get(i));
                    listChanged = true;
                }
            }
        }

        // Sort (if list has more than 1 element && listChanged)
        if (payloadStorageList.size() > 1 && listChanged) {
            Collections.sort(payloadStorageList);
        }
    }

    public synchronized void addFullPayload(PcmAudioDataPayload payload) {
        if (!hasFullPayloadAnywhereWithThisSeqId(payload.SeqId)) {
            if (!hasMissingPayloadAnywhereWithThisSeqId(payload.SeqId)) {
                payloadStorageList.add(payload);
                Collections.sort(payloadStorageList);
            } else {
                // Replace the missing one
                replaceMissingPayloadWithFullPayload(payload);
            }
        }
    }

    public synchronized boolean hasPayloadAnywhereWithThisSeqId(SeqId seqId) {
        // Missing OR Full
        for (int i = 0; i < payloadStorageList.size(); i++) {
            if (payloadStorageList.get(i).SeqId.isEqualToSeqId(seqId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasMissingPayloadAtFirstElement() {
        if (payloadStorageList.size() > 0 && payloadStorageList.get(0).DgData == null) {
            return true;
        } else {
            return false;
        }
    }

    public synchronized boolean hasMissingPayloadAtFirstElementWithThisSeqId(SeqId seqId) {
        return hasMissingPayloadAtFirstElement() && payloadStorageList.get(0).SeqId.isEqualToSeqId(seqId);
    }

    public synchronized boolean hasMissingPayloadAnywhereWithThisSeqId(SeqId seqId) {
        for (int i = 0; i < payloadStorageList.size(); i++) {
            if (payloadStorageList.get(i).DgData == null && payloadStorageList.get(i).SeqId.isEqualToSeqId(seqId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasFullPayloadAtFirstElement() {
        return payloadStorageList.size() > 0 && payloadStorageList.get(0).DgData != null;
    }

    public synchronized boolean hasFullPayloadAnywhereWithThisSeqId(SeqId seqId) {
        for (int i = 0; i < payloadStorageList.size(); i++) {
            if (payloadStorageList.get(i).DgData != null && payloadStorageList.get(i).SeqId.isEqualToSeqId(seqId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized PcmAudioDataPayload getFirstPayload() {
        if (payloadStorageList.size() > 0) {
            return payloadStorageList.get(0);
        }
        return null;
    }

    public synchronized PcmAudioDataPayload popFirstPayload() {
        if (payloadStorageList.size() > 0) {
            return payloadStorageList.remove(0);
        }
        return null;
    }

    public synchronized void removeAllPayloads() {
        payloadStorageList.clear();
    }

    public synchronized boolean replaceMissingPayloadWithFullPayload(PcmAudioDataPayload fullPayload) {
        for (int i = 0; i < payloadStorageList.size(); i++) {
            if (payloadStorageList.get(i).DgData == null && payloadStorageList.get(i).SeqId.isEqualToSeqId(fullPayload.SeqId)) {
                payloadStorageList.set(i, fullPayload);
                return true;
            }
        }
        return false;
    }

    public synchronized String getAllPayloadsSeqIdsAsHexString() {
        String s = "";
        for (int i = 0; i < payloadStorageList.size(); i++) {
            s += String.format("0x%04x", payloadStorageList.get(i).SeqId.intValue);
            if (payloadStorageList.get(i).DgData == null) {
                s += "{M}, ";
            } else {
                s += "{F}, ";
            }
        }
        return s;
    }

    public synchronized int getTotalNumPayloads() {
        return payloadStorageList.size();
    }

    public synchronized int getNumMissingPayloads() {
        int num = 0;
        for (int i = 0; i < payloadStorageList.size(); i++) {
            if (payloadStorageList.get(i).DgData == null) {
                num++;
            }
        }
        return num;
    }

    public synchronized List<PcmAudioDataPayload> getMissingPayloads() {
        List<PcmAudioDataPayload> missingPayloads = new ArrayList<>();
        for (int i = 0; i < payloadStorageList.size(); i++) {
            if (payloadStorageList.get(i).DgData == null) {
                missingPayloads.add(payloadStorageList.get(i));
            }
        }
        return missingPayloads;
    }

    public synchronized void removeMissingPayloadsInFirstXPayloads(int numPayloadsToRemove) {
        // First iterate through list to find index's of the ones which need to be removed
        List<PcmAudioDataPayload> payloadsToRemove = new ArrayList<>();
        for (int i = 0; i < numPayloadsToRemove; i++) {
            if (payloadStorageList.get(i).DgData == null) {
                payloadsToRemove.add(payloadStorageList.get(i));
            }
        }

        // Now remove them
        payloadStorageList.removeAll(payloadsToRemove);
    }
}
