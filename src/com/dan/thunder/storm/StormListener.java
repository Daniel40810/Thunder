package com.dan.thunder.storm;

/** Benachrichtigungen des {@link StormDirector}. Aufruf im Thread, der {@code update} ruft. */
public interface StormListener {

    /** Ein neuer Blitz beginnt. */
    default void strikeStarted(Strike strike) { }

    /** Der Donner eines Blitzes erreicht den Strand. */
    default void thunderArrived(Strike strike) { }
}
