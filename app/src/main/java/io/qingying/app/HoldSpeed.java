package io.qingying.app;

/** Exactly-once restoration across release, cancellation, focus loss and disposal. */
public final class HoldSpeed {
    private boolean active;
    private float previous;
    public boolean active(){return active;}
    public float begin(float speed){if(!active){previous=speed;active=true;}return Math.max(2f,previous);}
    public float end(float current){if(!active)return current;active=false;return previous;}
}
