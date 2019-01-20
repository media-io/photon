package com.netflix.imflibrary.utils;

import org.phoenixframework.channels.*;

public class WsMessageCallback implements IMessageCallback {
    private String topic;
    private String event;
    private Envelope envelope;
    private boolean received = false;

    public WsMessageCallback(String topic, String event) {
      this.topic = topic;
      this.event = event;
    }

    @Override
    public void onMessage(Envelope env) {
        String t = env.getTopic();
        String e = env.getEvent();
        if (t != null && t.equals(this.topic) && e != null && e.equals(this.event)) {
            this.envelope = env;
            this.received = true;
        }
    }

    public boolean received() {
        return this.received;
    }
    public Envelope getEnvelope() {
        return this.envelope;
    }
}
