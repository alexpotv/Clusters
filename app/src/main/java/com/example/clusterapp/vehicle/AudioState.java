package com.example.clusterapp.vehicle;

/** Audio source, volume, and mute state. */
public final class AudioState {
    /** Current audio source from IAvApService TX_AV_GET_SOURCE. -1 = not received. */
    public final int     source;
    /** Current volume from IAvApService or AudioManager. */
    public final int     volume;
    /** Maximum volume. */
    public final int     volumeMax;
    /** True when muted. */
    public final boolean mute;
    /** True when AudioManager reports active media playback. */
    public final boolean musicPlaying;

    private AudioState(Builder b) {
        source      = b.source;
        volume      = b.volume;
        volumeMax   = b.volumeMax;
        mute        = b.mute;
        musicPlaying = b.musicPlaying;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        int     source      = -1;
        int     volume      = 0;
        int     volumeMax   = 1;
        boolean mute        = false;
        boolean musicPlaying = false;

        public Builder source(int v)         { source = v;       return this; }
        public Builder volume(int v)         { volume = v;       return this; }
        public Builder volumeMax(int v)      { volumeMax = v;    return this; }
        public Builder mute(boolean v)       { mute = v;         return this; }
        public Builder musicPlaying(boolean v){ musicPlaying = v; return this; }
        public AudioState build()            { return new AudioState(this); }
    }
}
