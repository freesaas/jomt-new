/*
 * @(#) TimerManager.java
 *
 * JOTM: Java Open Transaction Manager
 *
 *
 * This module was originally developed by
 *
 *  - Bull S.A. as part of the JOnAS application server code released in
 *    July 1999 (www.bull.com)
 *
 * --------------------------------------------------------------------------
 *  The original code and portions created by Bull SA are
 *  Copyright (c) 1999 BULL SA
 *  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * -Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * --------------------------------------------------------------------------
 * $Id: TimerManager.java,v 1.4 2005-03-15 00:06:05 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.util.Vector;

/**
 * Clock thread for a TimerManager
 * Every second, decrement timers and launch action if expired
 */
class Clock extends Thread {

    private TimerManager tmgr;

    public Clock(TimerManager tmgr) {
        super("JotmClock");
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Clock constructor");
        }

        this.tmgr = tmgr;
    }

    public void run() {
        tmgr.clock();
    }
}

/**
 * Batch thread for a TimerManager
 * pocess all expired timers
 */
class Batch extends Thread {

    private TimerManager tmgr;

    public Batch(TimerManager tmgr) {
        super("JotmBatch");
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Batch constructor");
        }

        this.tmgr = tmgr;
    }

    public void run() {
        tmgr.batch();
    }
}

/**
 * A timer manager manages 2 lists of timers with 2 threads
 * One thread is a clock which decrements timers every second
 * and passes them when expired in a list of expired timers.
 * The other thread looks in the list of expired timers to process
 * them.
 */
public class TimerManager {

    // threads managing the service.
    private static Batch batchThread;
    private static Clock clockThread;

    // lists
    private Vector timerList = new Vector();
    private Vector expiredList = new Vector();

    private static TimerManager unique = null;
    private static boolean shuttingdown = false;

    /**
     * Constructor
     */
    private TimerManager() {
        // launch threads for timers
        batchThread = new Batch(this);
        batchThread.setDaemon(true);
        clockThread = new Clock(this);
        clockThread.setDaemon(true);
    }

    /**
     * Get an instance of the TimerManager
     */
    public static synchronized TimerManager getInstance() {
        if (unique == null) {
            unique = new TimerManager();
            // threads should not be started inside the constructor
            batchThread.start();
            clockThread.start();
        }
        return unique;
    }

    /**
     * stop the service
     * @param force tell the manager NOT to wait for the timers to be completed
     */
    public static void stop(boolean force) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Stop TimerManager");
        }

        shuttingdown = true;
        while (clockThread.isAlive() || batchThread.isAlive()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("TimerManager has stopped");
        }
    }

    public static void stop() {
        stop(true);
    }

    /**
     * cney speed up the clock x1000 when shutting down
     * update all timers in the list
     * each timer expired is put in a special list of expired timers
     * they will be processed then by the Batch Thread.
     */
    public void clock() {
        while (true) {
            try {
                Thread.sleep(shuttingdown?1:1000);  // 1 second or 1ms shen shuttingdown
                // Thread.currentThread().sleep(shuttingdown?1:1000);	// 1 second or 1ms shen shuttingdown
                synchronized(timerList) {
                    int found = 0;
                    boolean empty = true;
                    for (int i = 0; i < timerList.size(); i++) {
                        TimerEvent t = (TimerEvent) timerList.elementAt(i);
                        if (!t.isStopped()) {
                            empty = false;
                        }
                        if (t.update() <= 0) {
                            timerList.removeElementAt(i--);
                            if (t.valid()) {
                                expiredList.addElement(t);
                                found++;
                                if (t.ispermanent() && !shuttingdown) {
                                    t.restart();
                                    timerList.addElement(t);
                                }
                            }
                        }
                        // Be sure there is no more ref on bean in this local variable.
                        t = null;
                    }
                    if (found > 0) {
                        timerList.notify();
                    } else {
                        if (empty && shuttingdown) {
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                TraceTm.jta.error("Timer interrupted");
            }
        }
        synchronized(timerList) { // notify batch so that function can return.
            timerList.notify();
        }
    }

    /**
     * process all expired timers
     */
    public void batch() {

        while (!(shuttingdown && timerList.isEmpty() && expiredList.isEmpty())) {
            TimerEvent t;
            synchronized(timerList) {
                while (expiredList.isEmpty()) {
                    if (shuttingdown) return;
                    try {
                        timerList.wait();
                    } catch (Exception e) {
                        TraceTm.jta.error("Exception in Batch: ", e);
                    }
                }
                t = (TimerEvent) expiredList.elementAt(0);
                expiredList.removeElementAt(0);
            }
            // Do not keep the lock during the processing of the timer
            t.process();
        }
    }

    /**
     * add a new timer in the list
     * @param tel Object that will be notified when the timer expire.
     * @param timeout nb of seconds before the timer expires.
     * @param arg info passed with the timer
     * @param permanent true if the timer is permanent.
     */
    public TimerEvent addTimer(TimerEventListener tel, long timeout, Object arg, boolean permanent) {
        TimerEvent te = new TimerEvent(tel, timeout, arg, permanent);
        synchronized(timerList) {
            timerList.addElement(te);
        }
        return te;
    }

    /**
     * remove a timer from the list. this is not very efficient.
     * A better way to do this is TimerEvent.unset()
     * @deprecated
     */
    public void removeTimer(TimerEvent te) {
        synchronized(timerList) {
            timerList.removeElement(te);
        }
    }
}
