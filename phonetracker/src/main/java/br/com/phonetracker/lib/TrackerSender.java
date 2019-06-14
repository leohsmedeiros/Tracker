package br.com.phonetracker.lib;

import android.support.annotation.NonNull;

import br.com.phonetracker.lib.interfaces.ISender;

public class TrackerSender {

    final ISender sender;
    private String trackedId;
    private final String activeStatusTag;
    private final String inactiveStatusTag;
    private final int freqActiveStatusSender;
    private final int freqInactiveStatusSender;
    final boolean sendSpeed;
    final boolean sendDirection;

    boolean isActive = true;

    void setTrackedId(String id) {
        trackedId = id;
    }

    String getTrackedId() {
        return trackedId;
    }

    String getStatus () {
        return isActive ? activeStatusTag : inactiveStatusTag;
    }

    int getFrequency () {
        return isActive ? freqActiveStatusSender : freqInactiveStatusSender;
    }

    private TrackerSender (ISender sender, String trackedId,
                           String activeStatusTag, String inactiveStatusTag,
                           int freqActiveStatusSender, int freqInactiveStatusSender,
                           boolean sendSpeed, boolean sendDirection) {

        this.sender = sender;
        this.trackedId = trackedId;
        this.activeStatusTag = activeStatusTag;
        this.inactiveStatusTag = inactiveStatusTag;
        this.freqActiveStatusSender = freqActiveStatusSender;
        this.freqInactiveStatusSender = freqInactiveStatusSender;
        this.sendSpeed = sendSpeed;
        this.sendDirection = sendDirection;
    }


    public static class Builder {
        private ISender sender;

        private String senderId = null;
        private String activeStatusTag = "ACTIVE";
        private String inactiveStatusTag = "INACTIVE";
        private int freqActiveStatusSender = 5;
        private int freqInactiveStatusSender = 10;
        private boolean sendSpeed = false;
        private boolean sendDirection = false;

        public Builder(ISender sender) {
            this.sender = sender;
        }

        /**
         * Tag that will be used when the sender is active.
         * Default is ACTIVE
         */
        public TrackerSender.Builder activeStatusTag(@NonNull String tag) {
            activeStatusTag = tag;
            return this;
        }

        /**
         * Tag that will be used when the sender is inactive.
         * Default is INACTIVE
         */
        public TrackerSender.Builder inactiveStatusTag(@NonNull String tag) {
            inactiveStatusTag = tag;
            return this;
        }

        /**
         * Frequency in seconds to send the information, when Sender is active.
         * Default is 5.
         */
        public TrackerSender.Builder frequencySenderActiveInSeconds(int intervalInSeconds) throws IllegalArgumentException {
            if(intervalInSeconds < 0)
                throw new IllegalArgumentException("Min interval is 0");

            freqActiveStatusSender = intervalInSeconds * 1000;
            return this;
        }

        /**
         * Frequency in seconds to send the information, when Sender is inactive.
         * Default is 10.
         */
        public TrackerSender.Builder frequencySenderInactiveInSeconds(int intervalInSeconds) throws IllegalArgumentException {
            if(intervalInSeconds < 0)
                throw new IllegalArgumentException("Min interval is 0");

            freqInactiveStatusSender = intervalInSeconds * 1000;
            return this;
        }

        /**
         * Enable to send the location speed.
         * Default is false.
         */
        public TrackerSender.Builder enableToSendSpeed() {
            sendSpeed = true;
            return this;
        }

        /**
         * Enable to send the location direction.
         * Default is false.
         */
        public TrackerSender.Builder enableToSendDirection() {
            sendDirection = true;
            return this;
        }

        public TrackerSender build() {
            return new TrackerSender(sender, senderId, activeStatusTag, inactiveStatusTag,
                                    freqActiveStatusSender, freqInactiveStatusSender,
                                    sendSpeed, sendDirection);
        }
    }

}
