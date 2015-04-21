package com.briggs_inc.towf_receiver;

import static java.lang.Math.abs;

/**
 * Created by briggsm on 4/19/15.
 */
public class SeqId implements Comparable<SeqId> {
    public int intValue;

    public SeqId (int i) {
        if (i < 0) {
            this.intValue = 0xFFFF - (abs(i) & 0xFFFF) + 1;
        } else {
            this.intValue = i & 0xFFFF;
        }
    }

    // Override equals() [and hashCode() optionally] for list.contains() calls.
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof SeqId)) {
            return false;
        }
        return ((SeqId)obj).intValue == this.intValue;
    }
    @Override
    public int hashCode() {
        return intValue;
    }


    // compareTo: Needed for classes that have the "Comparable" interface (which is used for Collections.sort() & Arrays.sort()
    @Override
    public int compareTo(SeqId otherSeqId) {
        if (this.isLessThanSeqId(otherSeqId)) {
            return 0 - Math.abs(this.numSeqIdsExclusivelyBetweenMeAndSeqId(otherSeqId));
        } else if (this.isGreaterThanSeqId(otherSeqId)) {
            return 0;
        } else {
            return Math.abs(this.numSeqIdsExclusivelyBetweenMeAndSeqId(otherSeqId));
        }
    }

    public Boolean isLessThanSeqId(SeqId otherSeqId) {
        if ( (this.intValue < otherSeqId.intValue && otherSeqId.intValue - this.intValue < 0x7FFF) || (this.intValue > otherSeqId.intValue && this.intValue - otherSeqId.intValue >= 0x7FFF ) ) {
            return true;
        } else {
            return false;
        }
    }

    public Boolean isGreaterThanSeqId(SeqId otherSeqId) {
        if ( (this.intValue > otherSeqId.intValue && this.intValue - otherSeqId.intValue < 0x7FFF) || (this.intValue < otherSeqId.intValue && otherSeqId.intValue - this.intValue >= 0x7FFF) ) {
            return true;
        } else {
            return false;
        }
    }

    public Boolean isEqualToSeqId(SeqId otherSeqId) {
        if (this.intValue == otherSeqId.intValue) {
            return true;
        } else {
            return false;
        }
    }

    public Boolean isLessThanOrEqualToSeqId(SeqId otherSeqId) {
        if (this.isLessThanSeqId(otherSeqId) || this.isEqualToSeqId(otherSeqId)) {
            return true;
        } else {
            return false;
        }
    }

    public Boolean isGreaterThanOrEqualToSeqId(SeqId otherSeqId) {
        if (this.isGreaterThanSeqId(otherSeqId) || this.isEqualToSeqId(otherSeqId)) {
            return true;
        } else {
            return false;
        }
    }

    public int numSeqIdsExclusivelyBetweenMeAndSeqId(SeqId otherSeqId) {
        if (this.isEqualToSeqId(otherSeqId)) {
            return 0;
        }

        if (this.isGreaterThanSeqId(otherSeqId)) {
            if (this.intValue > otherSeqId.intValue) {
                return this.intValue - otherSeqId.intValue - 1;
            } else {
                return this.intValue + (0xFFFF - otherSeqId.intValue);
            }
        } else {  // isLessThan
            if (this.intValue < otherSeqId.intValue) {
                return otherSeqId.intValue - this.intValue - 1;
            } else {
                return (0xFFFF - this.intValue) + otherSeqId.intValue;
            }
        }
    }

    public void incr() {
        intValue++;
        intValue = intValue & 0xFFFF;
    }

    public void decr() {
        intValue--;
        if (intValue == -1) { intValue = 0xFFFF; }
    }
}
