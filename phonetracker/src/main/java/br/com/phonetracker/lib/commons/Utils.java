package br.com.phonetracker.lib.commons;

public class Utils {
    /**
     * Convert frequency to microseconds.
     * @param hz
     * @return
     */
    public static int hertz2periodUs(double hz) { return (int) (1.0e6 / hz);}
    public static long nano2milli(long nano) {return (long) (nano / 1e6);}
}
