/*
 * @(#) InternalTransactionContext.java 
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
 * $Id: InternalTransactionContext.java,v 1.12 2005-04-22 17:49:06 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

// javax import (JTA)
import javax.transaction.xa.Xid;

/**
 * Classe <code>InternalTransactionContext</code> is a generic implementation of
 * the JOTM Transaction Context. This Context is used by JOTM and by the Current
 * Object. It can't be propagate, it have to be transalate for each Transport
 * Layer Transaction Prapagation implementation
 * 
 * @author  Guillaume Riviere (Guillaume.Riviere@inrialpes.fr)
 * @version 1.0, 12/09/2002
 */
public class InternalTransactionContext implements TransactionContext {

    /**
     * @serial
     */
    private int timeout;

    /**
     * @serial
     */
    private Coordinator coordinator;
    
    /**
     * @serial
     */
    private Terminator terminator;

    /**
     * @serial
     */
    private Xid xid;
    
    /**
     * false if the Internal Tx Ctx is build from another VI's tx context
     */
    private boolean isJotmCtx=true;

    /**
     * Build a new TransactionContext (from JTA layer)
     */
    public InternalTransactionContext(int t, Coordinator c, Terminator term, Xid x) {
        timeout = t;
        coordinator = c;
        terminator=term;
        xid = x;
        
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("xid=" + xid + ", timeout=" + timeout);
        }
    }

    public InternalTransactionContext(int t, Coordinator c, Xid x) {
        timeout = t;
        coordinator = c;
        terminator=(Terminator)c;
        xid = x;
        
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("xid=" + xid + ", timeout=" + timeout);
        }
    }

    /**
     * Get the timeout associated with the transaction
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Get the coordinator associated with the transaction
     */
    public Coordinator getCoordinator() {
        return coordinator;
    }

    /**
     * Set the coordinator associated with the transaction
     */
    public void setCoordinator(Coordinator coord) {
        this.coordinator = coord;
    }

    /**
     * Get the Terminator associated with the transaction
     */
    public Terminator getTerminator() {
        return terminator ;
    }

    /**
     * Set the termiantor associated with the transaction
     */
    public void setTerminator(Terminator term) {
        this.terminator = term;
    }

    /**
     * Get the control associated with the transaction
     */
    public Control getControl() {
        return (Control) coordinator;
    }

    /**
     * Get the Xid associated with the transaction
     */
    public org.objectweb.jotm.Xid  getXid() {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("xid=" + xid);
        }
        return  (org.objectweb.jotm.Xid) xid;
    }

    /**
     * Set a flag in the context to indicate as coming from another Vendor
     * 
     * @return boolean
     */
    public void setNotJotmCtx() {
        isJotmCtx= false;
    }

    /**
     * return true if this context was build from a JOTM's context
     * 
     * @return boolean
     */
    public boolean isJotmCtx() {
        return isJotmCtx;
    }

}
