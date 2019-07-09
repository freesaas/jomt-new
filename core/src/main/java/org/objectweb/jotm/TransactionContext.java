/*
 * @(#) TransactionContext.java 
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
 * $Id: TransactionContext.java,v 1.9 2004-12-13 19:53:06 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.io.Serializable;

/**
 * This is how the JTA Implementation in JOTM sees the Transaction Context.
 * This interface is used to keep the code independant of the transport layer
 * this context is for internal use only (JOTM and UserTransaction) it has to 
 * be translated for each protocol. 
 * 
 * For the moment this Transaction Context is Serializable 
 * for test suite 
 * 
 * @author jmesnil
 * @version $Id: TransactionContext.java,v 1.9 2004-12-13 19:53:06 tonyortiz Exp $
 */
public interface TransactionContext extends Serializable {
    
    /**
     * get transaction timeout.
     * 
     * @return transaction timeout
     */
    public int getTimeout();
    
    /**
     * get the Coordinator of the transaction.
     * 
     * @return Coordinator
     */
    public Coordinator getCoordinator();
    
    /**
     * set the Coordinator of the transaction.
     * 
     * @param c Coordinator
     */
    public void setCoordinator(Coordinator c);
    
    /**
     * get the Terminator of the transaction.
     * 
     * @return Terminator
     */
    public Terminator getTerminator();
    
    /**
     * set the Terminator of the transaction.
     * 
     * @param t Terminator
     */
    public void setTerminator(Terminator t);
    
    /**
     * get the Control of the Transaction.
     * 
     * @return Control
     */
    public Control getControl();
    
    /**
     * get the Xid of the transaction.
     * 
     * @return Xid
     */
    public org.objectweb.jotm.Xid  getXid();

    /**
     * Set a flag in the context to indicate as coming from another Vendor
     */
    public void setNotJotmCtx();

    /**
     * return true if this context was build from a JOTM's context
     * 
     * @return boolean
     */
    public boolean isJotmCtx();    
    
}
