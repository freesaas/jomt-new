/*
 * @(#) TimerEvent.java 
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
 * $Id: TimerEvent.java,v 1.2 2005-03-15 00:05:38 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

public class TimerEvent {

    private TimerEventListener listener = null;
    private Object arg = null;
    private long reminding = 0;
    private long startvalue;
    private boolean permanent = false;
    private boolean stopped = false;

    /**
     * Constructor
     * @param l Object that will be notified when the timer expire.
     * @param timeout nb of seconds before the timer expires.
     * @param a info passed with the timer
     * @param p true if the timer is permanent.
     */
    public TimerEvent(TimerEventListener l, long timeout, Object a, boolean p) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("TimerEvent.new("+l+","+timeout+","+a+","+p+")");
        }
        
        listener = l;
        reminding = timeout;
        startvalue = timeout;
        arg = a;
        permanent = p;
    }


    /**
     * Update timer every second. Used by clock.
     * - this must be called with the timerList monitor.
     */
    public long update() {
        return --reminding;
    }

    /**
     * Restart timer to its initial value
     */
    public void restart() {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("TimerEvent.new("+listener+").restart");
        }

        stopped = false;
        reminding = startvalue;
    }

    /**
     * Process the Timer
     */
    public void process() {
        if (listener != null) {
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.jta.debug("TimerEvent.new("+listener+".process");
            }

            listener.timeoutExpired(arg);
        }
    }

    public void change(long timeout, Object a) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("TimerEvent("+listener+").change("+timeout+","+a+")");
        }

        stopped = false;
        startvalue = timeout;
        reminding = startvalue;
        arg = a;
    }

    /**
     * Unvalidate the timer. It will be removed by the timer manager.
     */
    public void unset() {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("TimerEvent("+listener+").unset");
        }

        // the timerlist is not locked.
        reminding = 1;
        arg = null;
        listener = null;
        permanent = false;
        stopped = false;
    }

    /**
     * stop the timer, but keep it for further reuse (See change())
     */
    public void stop() {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("TimerEvent("+listener+").stop");
        }

        // the timerlist is not locked.
        reminding = 1000000;
        stopped = true;
    }

    /**
     * Is this timer valid ?
     */
    public boolean valid() {
        return (listener != null);
    }

    /**
     * Is this timer permanent ?
     */
    public boolean ispermanent() {
        return permanent;
    }

    /**
     * Is this timer stopped ?
     */
    public boolean isStopped() {
        return stopped;
    }
}

