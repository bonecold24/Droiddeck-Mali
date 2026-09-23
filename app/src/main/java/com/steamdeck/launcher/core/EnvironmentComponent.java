package com.steamdeck.launcher.core;

import android.content.Context;

/**
 * One piece of a running session - the audio daemon, the network-link file, the session process
 * itself. The session starts them in order and stops them in reverse.
 */
public abstract class EnvironmentComponent {
    protected Context context;

    public void setContext(Context context) { this.context = context; }

    public abstract void start();

    public abstract void stop();
}
