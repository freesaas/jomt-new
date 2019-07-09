/*
 * @(#) XATerminatorImpl.java	1.2 02/01/15
 *
 * JOTM: Java Open Transaction Manager
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
 * --------------------------------------------------------------------------
 *
 * Contributor:
 * 04/01/15 Tony Ortiz
 *
 * --------------------------------------------------------------------------
 * $Id: XATerminatorImpl.java,v 1.5 2004-11-05 19:54:59 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import javax.transaction.xa.Xid;

import javax.resource.spi.XATerminator;

import javax.transaction.xa.XAException;

/**
 * Implementation of the object that represents an inflow transaction.
 */
public class XATerminatorImpl implements XATerminator {

    /**
     * @serial
     */
    private TransactionImpl iftx = null;

    /**
     * Constructor for create
     */
    public XATerminatorImpl() throws XAException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("create XATerminator");
        }
    }

    // ------------------------------------------------------------------
    // Inflow Transaction implementation
    // ------------------------------------------------------------------

    /**
     * Commits the global transaction specified by xid.
     *
     * @param xid A global transaction identifier
     * @param onePhase If true, the resource manager should use one-phase commit protocol
     * to commit the work done on behalf of xid.
     *
     * @exception XAException An error has occurred. Possible XAExceptions
     * are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB, XA_HEURMIX, XAER_RMERR,
     * XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     * If the resouce manager did not commit the transaction and the parameter onePhase is set to
     * true, the resource manager may throw one of the XA_RB* exceptions. Upon return, the
     * resource manager has rolled back the branch's work and has released all help resources.
     */
    public void commit (Xid xid, boolean onePhase) throws XAException {

        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("commit xid="+ xid + "onePhase="+ onePhase);
        }

        // get the transaction associated with the passed Xid
        // if the Xid passed was not corrected the commit will fail

        XidImpl myxid = new XidImpl(xid);
        iftx = Current.getCurrent().getTxByXid(myxid);

        if (iftx == null) {
            TraceTm.jotm.error("XATerminatorImpl.commit(): unknown xid " + xid);
            throw new XAException(XAException.XAER_NOTA);
        }

        try {
            iftx.commit();
        } catch (Exception e) {
            TraceTm.jotm.error("XATerminatorImpl.commit(): commit raised exception ", e);
        }
    }

    /**
     * Informs the resource manager to roll back work done on behalf of a transaction branch.
     *
     * @param xid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible XAExceptions
     * are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB, XA_HEURMIX, XAER_RMERR,
     * XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     * If the transaction branch is already marked rollback-only the resource manager may throw
     * one of the XA_RB* exceptions. Upon return, the resource manager has rolled back the
     * branch's work and has released all held resources.
     */
    public void rollback (Xid xid) throws XAException {

        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("rollback xid="+ xid);
        }

        // get the transaction associated with the passed Xid
        // if the Xid passed was not corrected the rollback will fail

        XidImpl myxid = new XidImpl(xid);
        iftx = Current.getCurrent().getTxByXid(myxid);

        if (iftx == null) {
            TraceTm.jotm.error("XATerminatorImpl.rollback(): unknown xid " + xid);
            throw new XAException(XAException.XAER_NOTA);
        }

        try {
            iftx.rollback();
        } catch (Exception e) {
            TraceTm.jotm.error("XATerminatorImpl.rollback(): rollback raised exception ", e);
        }
    }

    /**
     * Ask the resource manager to prepare for a transaction commit of the transaction specified in xid.
     *
     * @param xid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible XAExceptions
     * are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB, XA_HEURMIX, XAER_RMERR,
     * XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     * @return A value indicating the resource manager's vote on the outcome of the transaction. The
     * possible values are: XA_RDONLY or XA_OK. These constants are defined in
     * javax.transaction.xa.XAResource interface. If the resource manager wants to roll back
     * the transaction, it should do so by raising an appropriate XAException in the prepare
     * method.
     */
    public int prepare (Xid xid) throws XAException {

        int ret = 0;

        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("prepare xid="+ xid);
        }

        // get the transaction associated with the passed Xid
        // if the Xid passed was not corrected the prepare will fail

        XidImpl myxid = new XidImpl(xid);
        iftx = Current.getCurrent().getTxByXid(myxid);

        if (iftx == null) {
            TraceTm.jotm.error("XATerminatorImpl.prepare(): unknown xid " + xid);
            throw new XAException(XAException.XAER_NOTA);
        }

        try {
            ret = iftx.prepare();
        } catch (Exception e) {
            TraceTm.jotm.error("XATerminatorImpl.prepare(): prepare raised exception ", e);
        }

        return ret;
    }

    /**
     * Tells the resource manager to forget about a heuristically completed transaction branch.
     *
     * @param xid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible XAExceptions
     * are XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     */
    public void forget (Xid xid) throws XAException {

        TraceTm.jotm.debug("XATerminatorImpl.forget()");

        // get the transaction associated with the passed Xid
        // if the Xid passed was not corrected the forget will fail
        XidImpl myxid = new XidImpl(xid);

        try {
            Current.getCurrent().forgetTx(myxid);
        } catch (Exception e) {
            TraceTm.jotm.error("XATerminatorImpl.forget(): forget raised exception ", e);
        }
    }

    /**
     * Obtains a list of prepared transaction branches from a resource manager. The transaction manager
     * calls this method during recovery to obtain the list of transaction branches that are currently in
     * prepared or heuristically completed states.
     *
     * @param flag One of TMSTARTRSCAN, TMENDRSCAN, TMNOFLAGS. TMHOFLAGS must
     * be used when no other flags are set in the parameter. These constants are defined in
     * javax.transaction.xa.XAResource interface.
     *
     * @exception XAException An error has occurred. Possible values
     * are XAER_RMERR, XAER_RMFAIL, XAER_INVAL, or XAER_PROTO.
     *
     * @return The resource manager returns zero or more XIDs of the transaction branches that are
     * currently in a prepared or heuristically completed state. If an error occurs during the
     * operation, the resource manager should throw the appropriate XAException.
     */
    public Xid[] recover (int flag) throws XAException {

        TraceTm.jotm.debug("XATerminatorImpl.recover()");

        // get a list of transaction in the prepared or heuristically complete states
        return (Xid []) Current.getCurrent().getPreparedHeuristicXid();
    }

}
