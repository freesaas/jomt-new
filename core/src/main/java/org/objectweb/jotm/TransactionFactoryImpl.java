/*
 * @(#) TransactionFactoryImpl.java
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
 * $Id: TransactionFactoryImpl.java,v 1.12 2005-04-22 17:51:56 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.net.InetAddress;
import java.util.Vector;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.rmi.RemoteException;
import javax.transaction.TransactionManager;


public class TransactionFactoryImpl
    extends PortableRemoteObject
    implements TransactionFactory {

    /**
     * @serial
     */
    int timeoutMax = 3600; // 1 hour

    private Vector coordinatorList = new Vector();

    /**
     * Constructor of the Transaction Factory
     */
    public TransactionFactoryImpl() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("default constructor");
        }
    }

    /**
     * Create a new Control implementation on JTM.
     *
     * @return		The Control object for the transaction
     */
    public synchronized Control create(int timeout) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("timeout=" + timeout);
        }

        ControlImpl ctrl = null;

        // checks timeout value
        if (timeout == 0 || timeout > timeoutMax)
            timeout = timeoutMax;

        // makes a new xid
        // - should pass servername + ip addr. (LATER)
        XidImpl xid = new XidImpl("TMServer", 0);

        // Creates a new ControlImpl
        try {
            ctrl = new ControlImpl(timeout, xid, null);
        } catch (Exception e) {
            TraceTm.jotm.error("Cannot create ControlImpl", e);
        }
        return ctrl;
    }

    /**
     * Create a new Control implementation on JTM.
     * See bug 314188
     * @return		The Control object for the transaction
     */
    public synchronized Control create(int timeout, Xid xid) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("xid=" + xid);
        }

        ControlImpl ctrl = null;

        // checks timeout value
        if (timeout == 0 || timeout > timeoutMax) {
            timeout = timeoutMax;
        }

        // Creates a new ControlImpl
        try {
            ctrl = new ControlImpl(timeout, xid, null);
            InitialContext ictx = new InitialContext();
            TransactionManager tm = (TransactionManager) ictx.lookup("java:/TransactionManager");
            TransactionImpl tx = ((Current) tm).getTxByXid(xid);
            if (tx != null) {
            	InternalTransactionContext ctx = new InternalTransactionContext(timeout, (Coordinator) ctrl, xid);
            	tx.updatePropagationContext(ctx);
            }
        } catch (Exception e) {
            TraceTm.jotm.error("Cannot create ControlImpl", e);
        }
        return ctrl;
    }

    /**
     * Recreate locally a Control object for an existing transaction. It is
     * possible to call recreate for a transaction already known. In this
     * case, recreate simply returns the existing Control object.
     *
     * @return		The Control object for the transaction
     */
    public synchronized Control recreate(TransactionContext ctx) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("TransactionContext=" + ctx);
        }

        // Check if Control already exists
        ControlImpl ctrl = null;

        synchronized (coordinatorList) {
            for (int i = 0; i < coordinatorList.size(); i++) {
                Coordinator coord = (Coordinator) coordinatorList.elementAt(i);
                if (coord.equals(ctx.getCoordinator())) {
                    if (TraceTm.jotm.isDebugEnabled()) {
                        TraceTm.jotm.debug("recreate: Control already in the list");
                    }
                    ctrl = (ControlImpl) coord;
                    break;
                }
            }
        }
        if (ctrl != null) {
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("recreate twice");
            }
            return ctrl;
        }

        // TODO: Build an xid with same gtrid and a new bqual
        javax.transaction.xa.Xid xid = ctx.getXid();

        // Creates a new ControlImpl and register it to the sup-coord.
        // coordinator may be a JOnAS Coordinator or a org.omg.CosTransactions.Coordinator
        try {
            ctrl = new ControlImpl(ctx.getTimeout(), xid, ctx.getCoordinator());
        } catch (Exception e) {
            TraceTm.jotm.error("Cannot create ControlImpl", e);
        }
        return ctrl;
    }

    /**
     * management method
     * @return the port number
     */
    public int getPortNumber() throws RemoteException {
        // no possibility with portable remote object
        return 0;
    }

    /**
     * management method
     * @return the local host name
     */
    public String getHostName() throws RemoteException {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            throw new RemoteException("" + e);
        }
    }
}
