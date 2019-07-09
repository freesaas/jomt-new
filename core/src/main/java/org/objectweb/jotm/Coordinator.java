/*
 * @(#) Coordinator.java
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
 * $Id: Coordinator.java,v 1.7 2003-12-10 20:06:26 trentshue Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * OTS-like Coordinator.
 * 
 * @see <a href="http://java.sun.com/products/jts/javadoc/org/omg/CosTransactions/Coordinator.html">OTS Coordinator</a>
 * @author jmesnil
 */
public interface Coordinator extends Remote {
    
    /**
     * @return status
     * @throws RemoteException if a remote exception occurs
     */
    public int get_status() throws RemoteException;
    
    /**
     * @param tc Coordinator
     * @return <code>true</code> if transaction is the same than for 
     * <code>tc</code>
     * @throws RemoteException if a remote exception occurs
     */
    public boolean is_same_transaction(Coordinator tc) throws RemoteException;
    
    /**
     * @param r Resource
     * @return RecoveryCoordinator
     * @throws RemoteException if a remote exception occurs
     */
    public RecoveryCoordinator register_resource(Resource r)
        throws RemoteException;
    
    /**
     * @param sync RemoteSynnchro
     * @throws RemoteException if a remote exception occurs
     */
    public void register_synchronization(RemoteSynchro sync)
        throws RemoteException;
    
    /**
     * @throws RemoteException if a remote exception occurs
     */
    public void rollback_only() throws RemoteException;
    
    /**
     * @return transaction name
     * @throws RemoteException if a remote exception occurs
     */
    public String get_transaction_name() throws RemoteException;
}
