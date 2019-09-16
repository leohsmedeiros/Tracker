package br.com.phonetracker.lib;

import android.support.annotation.NonNull;

import java.io.Serializable;

public class TrackerSettings implements Serializable {
    public enum Status { ONLINE, INACTIVE, OFFLINE }

    private String trackedId;
    private final int freqOnlineStatusSender;
    private final int freqInactiveStatusSender;
    final boolean sendSpeed;
    final boolean sendDirection;

    private Status status = Status.ONLINE;
    private boolean onTravel = false;

    String getTrackedId() {
        return trackedId;
    }

    Status getStatus () {
        return status;
    }

    void setStatus (Status status) {
        this.status = status;
    }

    void setStatus (String status) {
        this.status = Status.valueOf(status);
    }

    void setOnTravel (boolean isOnTravel) { this.onTravel = isOnTravel; }

    boolean isOnTravel () { return onTravel; }

    int getFrequency () {
        if (status.equals(Status.ONLINE)) {
            return freqOnlineStatusSender;
        }else {
            return freqInactiveStatusSender;
        }
    }

    private TrackerSettings(String trackedId,
                            int freqOnlineStatusSender, int freqInactiveStatusSender,
                            boolean sendSpeed, boolean sendDirection) {

        this.trackedId = trackedId;
        this.freqOnlineStatusSender = freqOnlineStatusSender;
        this.freqInactiveStatusSender = freqInactiveStatusSender;
        this.sendSpeed = sendSpeed;
        this.sendDirection = sendDirection;
    }


    public static class Builder {
        private String id = null;
        private int freqActiveStatusSender = 5;
        private int freqInactiveStatusSender = 10;
        private boolean sendSpeed = false;
        private boolean sendDirection = false;

        /**
         * Tag that will be used when the sender is active.
         * Default is ONLINE
         */
        public TrackerSettings.Builder trackedId(@NonNull String id) {
            this.id = id;
            return this;
        }

        /**
         * Frequency in seconds to send the information, when Sender is active.
         * Default is 5.
         */
        public TrackerSettings.Builder frequencyOnlineInSeconds(int intervalInSeconds) throws IllegalArgumentException {
            if(intervalInSeconds < 0)
                throw new IllegalArgumentException("Min interval is 0");

            freqActiveStatusSender = intervalInSeconds * 1000;
            return this;
        }

        /**
         * Frequency in seconds to send the information, when Sender is inactive.
         * Default is 10.
         */
        public TrackerSettings.Builder frequencyInactiveInSeconds(int intervalInSeconds) throws IllegalArgumentException {
            if(intervalInSeconds < 0)
                throw new IllegalArgumentException("Min interval is 0");

            freqInactiveStatusSender = intervalInSeconds * 1000;
            return this;
        }

        /**
         * Enable to send the location speed.
         * Default is false.
         */
        public TrackerSettings.Builder enableToSendSpeed() {
            sendSpeed = true;
            return this;
        }

        /**
         * Enable to send the location direction.
         * Default is false.
         */
        public TrackerSettings.Builder enableToSendDirection() {
            sendDirection = true;
            return this;
        }

        public TrackerSettings build() {
            return new TrackerSettings(id, freqActiveStatusSender,
                                       freqInactiveStatusSender,
                                       sendSpeed, sendDirection);
        }
    }

}
