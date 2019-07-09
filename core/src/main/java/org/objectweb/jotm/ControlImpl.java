/*
 * @(#) ControlImpl.java	1.2 02/01/15
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
 * 02/01/15 Dean Jennings - ArrayList for resourceList and synchronizationList
 * 02/09/10 Riviere Guillaume (Guillaume.Riviere@inrialpes.fr) - RemoteControl added
 *
 * --------------------------------------------------------------------------
 * $Id: ControlImpl.java,v 1.30 2005-04-22 17:47:44 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.ServerException;

import javax.rmi.PortableRemoteObject;

import java.util.List;
import java.util.ArrayList;
import javax.transaction.Status;
import javax.transaction.TransactionRolledbackException;
import javax.transaction.xa.Xid;

import org.ow2.carol.util.configuration.ConfigurationRepository;
import org.ow2.carol.rmi.exception.RmiUtility;

/**
 * Implementation of the object that represents a transaction.
 * This remote object has been created by a TransactionFactory.
 * It extends The RemoteControl Remote Interface
 *
 * @see	   org.objectweb.jotm.TransactionFactory
 *
 */
public class ControlImpl
    extends PortableRemoteObject
    implements Control, Resource, Coordinator, Terminator, RecoveryCoordinator, TimerEventListener {

    /**
     * @serial
     */
    private List<Resource> resourceList = new ArrayList<Resource>();
    /**
     * @serial
     */
    private List<RemoteSynchro> synchronizationList = new ArrayList<RemoteSynchro>();
    /**
     * @serial
     */
    private int mystatus = Status.STATUS_UNKNOWN;
    /**
     * @serial
     */
    private boolean hasSupCoord = false;
    /**
     * @serial
     */
    private TimerEvent mytimer = null;
    /**
     * @serial
     */
    private Xid xid;
    /**
     * @serial
     */
    private Log mylog;

    // ---------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------

    /**
     * Constructor for create or recreate
     *
     * @param timeout		Timeout in seconds for this transaction
     * @param x	     	Xid allocated to this transaction
     * @param supco		Superior coordinator (null if create); must be a
     * org.objectweb.jotm.Coordinator or a org.omg.CosTransactions.Coordinator
     */
    ControlImpl(int timeout, Xid x, Object supco) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("timeout="+ timeout +", xid="+ x +", supco="+ supco);
        }

        // init object state
        synchronized(this) {
            mystatus = Status.STATUS_ACTIVE;
            xid = x;
            hasSupCoord = (supco != null);

            // XXX In case of sub-coordinator, should we arm a timer for rollback ???
            mytimer = TimerManager.getInstance().addTimer(this, timeout, Integer.valueOf(1), false);
        }

        // If sub-coord, register resource
        if (supco != null) {
            try {
                if (supco instanceof Coordinator)
                    ((Coordinator) supco).register_resource(this);
                else
                    // XXX CORBA Cordinator not taken into account
                    // register the control as a CORBA resource in the remote CORBA Coordinator (we use a wraper)
                    //((org.omg.CosTransactions.Coordinator) supco).register_resource(new ControlResourceImpl(this));
                    throw new RemoteException("Not Implemented");
            } catch (Exception e) {
                TraceTm.jotm.error("Cannot register sub-coordinator:\n", e);
            }
        }
    }

    // ---------------------------------------------------------------
    // Control Interface
    // ---------------------------------------------------------------

    /**
     * Gets the Terminator object for this transaction
     *
     * @return		Terminator for this transaction
     */
    public Terminator get_terminator() throws RemoteException {

        return this;
    }

    /**
     * Gets the Coordinator object for this transaction
     * @return		Coordinator for this transaction
     */
    public Coordinator get_coordinator() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("ControlImpl.get_coordinator");
        }
        return this;
    }

    // ---------------------------------------------------------------
    // Coordinator Interface
    // ---------------------------------------------------------------

    /**
     * Gets the current status of this transaction
     * @return		current transaction status
     */
    public synchronized int get_status() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("ControlImpl.get_status()");
        }
        return mystatus;
    }

    /**
     * Tests if the given coordinator represents this transaction
     * @param tc	Coordinator to be compared to this transaction
     * @return			true if it is the same transaction
     */
    public boolean is_same_transaction(Coordinator tc) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("ControlImpl.is_same_transaction(Coordinator)");
        }

        String other = null;

        try {
            other = tc.get_transaction_name();
        } catch (Exception e) {
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.error("ControlImpl.is_same_transaction() raised exception:\n", e);
            }
            return false;
        }

        return other.equals(get_transaction_name());
    }

    /**
     * Registers a Resource object for this transaction
     * @param res		Resource to be registered
     * @return			RecoveryCoordinator used for replay_completion
     */
    public synchronized RecoveryCoordinator register_resource(Resource res) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("resource="+ res);
            TraceTm.jotm.debug("mystatus="+ StatusHelper.getStatusName(mystatus));
        }

        switch (mystatus) {

        case Status.STATUS_ACTIVE :
            // Normal case
            resourceList.add(res);
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("Resource registered:" + res);
            }
            break;

        case Status.STATUS_MARKED_ROLLBACK :
        case Status.STATUS_ROLLEDBACK :
        case Status.STATUS_ROLLING_BACK :
            TraceTm.jotm.error("ControlImpl.register_resource(): Transaction Rolled back");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            throw new TransactionRolledbackException();

        default :
            TraceTm.jotm.error("ControlImpl.register_resource(): Transaction Inactive");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            throw new InactiveException("Cannot register resource, status " + StatusHelper.getStatusName(mystatus));
        }
        return this;
    }

    /**
     * Registers a Synchronization object for this transaction
     * @param sync		RemoteSynchro to be registered
     */
    public synchronized void register_synchronization(RemoteSynchro sync) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("sync="+ sync);
            TraceTm.jotm.debug("mystatus="+ StatusHelper.getStatusName(mystatus));
        }

        switch (mystatus) {

        case Status.STATUS_ACTIVE :
            // Normal case
            synchronizationList.add(sync);
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.register_synchronization(): RemoteSynchro registered");
            }
            break;

        case Status.STATUS_MARKED_ROLLBACK :
        case Status.STATUS_ROLLEDBACK :
        case Status.STATUS_ROLLING_BACK :
            TraceTm.jotm.error("ControlImpl.register_synchronization(): Transaction Rolled back");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            throw new TransactionRolledbackException();

        default :
            TraceTm.jotm.error("ControlImpl.register_synchronization(): Transaction Inactive");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            throw new InactiveException("Cannot register synchronization, status " + StatusHelper.getStatusName(mystatus));
        }
    }

    /**
     * Asks to rollback the transaction
     * @throws RemoteException Cannot rollback transaction
     */
    public synchronized void rollback_only() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("mystatus="+ StatusHelper.getStatusName(mystatus));
        }

        switch (mystatus) {

        case Status.STATUS_MARKED_ROLLBACK :
            // nothing to do: Already marked rolledback.
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.rollback_only(): Already marked rolledback");
            }
            break;

        case Status.STATUS_ACTIVE :
            // case Status.STATUS_COMMITTING :
            mystatus = Status.STATUS_MARKED_ROLLBACK;
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.rollback_only(): Marked rollback");
            }
            break;

        default :
            // already prepared
            TraceTm.jotm.error("ControlImpl.rollback_only(): Inactive");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            throw new InactiveException("Cannot rollback transaction, status " + StatusHelper.getStatusName(mystatus));
        }
    }

    /**
     * Gets a String that represents the transaction name.
     * Only the Format Id and the Global Id are used to build it :
     * The Branch Qualifier is not used.
     * @return			Transaction Name
     */
    public String get_transaction_name() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("ControlImpl.get_transaction_name()");
        }
        return xid.toString();
    }

    // ---------------------------------------------------------------
    // Terminator Interface
    // ---------------------------------------------------------------

    /**
     * Commits this transaction
     * @param report_heuristics want to report heuristics if any
     * @exception TransactionRolledbackException the transaction has been rolled back
     * @exception HeuristicMixed Resources have rolled back
     * @exception HeuristicHazard Resources may have rolled back
     */
    public synchronized void commit(boolean report_heuristics) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("report_heuristics="+ report_heuristics);
            TraceTm.jotm.debug("mystatus="+ StatusHelper.getStatusName(mystatus));
        }

        String protocol = ConfigurationRepository.getCurrentConfiguration().getProtocol().getName();

        // Stops the Timer.
        if (mytimer != null) {
            mytimer.stop();
        }

        // Checks status
        switch (mystatus) {

        case Status.STATUS_ACTIVE :
            // normal case
            break;

        case Status.STATUS_COMMITTED :
            TraceTm.jotm.error("ControlImpl.commit(boolean): already done");
            return;

        case Status.STATUS_ROLLEDBACK :
            TraceTm.jotm.error("ControlImpl.commit(boolean): already rolled back");
            // We should be here in case of timeout expired.
            // In this case, completed() was not called.
            completed(true);

            if (protocol == null || !(protocol.equals("iiop"))) {
                throw new TransactionRolledbackException();
            }

            RmiUtility.rethrowRmiException(new TransactionRolledbackException());

        case Status.STATUS_MARKED_ROLLBACK :
            // Synchronization objects
            int errors = do_before_completion();

            if (errors > 0) {
                TraceTm.jotm.info("ControlImpl.commit(boolean): before completion error at rollback");
            }

            do_rollback(report_heuristics);
            completed(true);

            if (protocol == null || !(protocol.equals("iiop"))) {
                throw new TransactionRolledbackException();
            }

            RmiUtility.rethrowRmiException(new TransactionRolledbackException());

        default :
            TraceTm.jotm.error("ControlImpl.commit(boolean): bad status");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));

            if (protocol == null || !(protocol.equals("iiop"))) {
                throw new HeuristicMixed();
            }

            RmiUtility.rethrowRmiException(new HeuristicMixed());
        }

        // Only one resource -> one phase commit.
        // If Resource raises an heuristic, forward it to the client.
        if (resourceList.size() == 1) {
            TraceTm.jotm.debug("1 resource");

            // Synchronization objects
            int errors = do_before_completion();

            if (errors > 0) {
                TraceTm.jotm.info("before completion error -> rollback");
                do_rollback(report_heuristics);
                completed(true);

                if (protocol == null || !(protocol.equals("iiop"))) {
                    throw new TransactionRolledbackException();
                }

                RmiUtility.rethrowRmiException(new TransactionRolledbackException());
            }

            mystatus = Status.STATUS_COMMITTING;

            try {
                Resource res = (Resource) resourceList.get(0);
                res.commit_one_phase();
                mystatus = Status.STATUS_COMMITTED;
            } catch (TransactionRolledbackException e) {
                TraceTm.jotm.info("commit_one_phase = TransactionRolledbackException");
                mystatus = Status.STATUS_ROLLEDBACK;
            } catch (HeuristicHazard e) {
                TraceTm.jotm.info("commit_one_phase = HeuristicException");
                mystatus = Status.STATUS_UNKNOWN;
            } catch (NoSuchObjectException e) {
                // nothing done: can rollback transaction
                TraceTm.jotm.info("commit_one_phase = NoSuchObjectException");
                mystatus = Status.STATUS_ROLLEDBACK;
            } catch (ServerException e) { // with RMI, may encapsulate TransactionRolledbackException
                TraceTm.jotm.info("commit_one_phase = ServerException: "+ e);
                mystatus = Status.STATUS_ROLLEDBACK;
            } catch (Exception e) {
                TraceTm.jotm.error("commit_one_phase = Unexpected exception: ", e);
                mystatus = Status.STATUS_UNKNOWN;
            }

            // Synchronization objects
            do_after_completion();

            // Possibly report heuristics to the caller
            switch (mystatus) {
            case Status.STATUS_COMMITTED :
                completed(true);
                break;
            case Status.STATUS_ROLLEDBACK :
                completed(true);

                if (protocol == null || !(protocol.equals("iiop"))) {
                    throw new TransactionRolledbackException();
                }

                RmiUtility.rethrowRmiException(new TransactionRolledbackException());
            case Status.STATUS_UNKNOWN :
                completed(false);

                if (report_heuristics) {
                    if (protocol == null || !(protocol.equals("iiop"))) {
                        throw new HeuristicHazard();
                    }

                    RmiUtility.rethrowRmiException(new HeuristicHazard());
                }
            }
            return;
        }

        // 2PC - phase 1
        int v = do_prepare(report_heuristics);

		if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("Vote = " + v);
        }

        // Depending on vote, commits or rollbacks transaction
        switch (v) {
        case Resource.VOTE_COMMIT :
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.commit(boolean): committing Tx");
            }
            break;
        case Resource.VOTE_ROLLBACK :
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.commit(boolean): rolling back Tx");
            }
            do_rollback(report_heuristics);
            completed(true);

            if (protocol == null || !(protocol.equals("iiop"))) {
                throw new TransactionRolledbackException();
            }

            RmiUtility.rethrowRmiException(new TransactionRolledbackException());
        case Resource.VOTE_READONLY :
            // No commit phase.
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.commit(boolean): readonly resources");
            }
            mystatus = Status.STATUS_COMMITTED;
            completed(true);
            return;
        }

        // Prepare was OK: Decision to commit.
        mylog.flushLog(Log.DECISION_TO_COMMIT);

        // 2PC - phase 2
        if (do_commit(report_heuristics) == 0) {
            completed(true);
        } else {
            completed(false);
        }
    }

    // ---------------------------------------------------------------
    // Resource or Terminator Interface
    // rollback() has the same signature in Terminator and Resource!
    // ---------------------------------------------------------------

    /**
     * Rolls back this transaction branch. Can be a sub-coordinator or
     * a normal coordinator.
     * @throws RemoteException rollback: bad status
     */
    public synchronized void rollback() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("mystatus="+ StatusHelper.getStatusName(mystatus));
        }

        String protocol = ConfigurationRepository.getCurrentConfiguration().getProtocol().getName();

        // Stops the Timer.
        if (mytimer != null) {
            mytimer.stop();
        }

        // Checks status
        switch (mystatus) {

        case Status.STATUS_ACTIVE :
        case Status.STATUS_MARKED_ROLLBACK :
            // normal case
            break;

        case Status.STATUS_ROLLEDBACK :
            TraceTm.jotm.error("ControlImpl.rollback(): already rolled back");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            return;

        default :
            TraceTm.jotm.error("ControlImpl.rollback(): rollback: bad status");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            completed(false);

            if (protocol == null || !(protocol.equals("iiop"))) {
                throw new HeuristicMixed("rollback: bad status");
            }

            RmiUtility.rethrowRmiException(new TransactionRolledbackException());
        }

        // Superior coordinator.
        if (!hasSupCoord) {
            // Synchronization objects
            int errors = do_before_completion();
            if (errors > 0) {
                TraceTm.jotm.info("ControlImpl.rollback(): before completion error at rollback");
            }
        }

        // rollback Tx
        try {
            do_rollback(false);
        } catch (Exception e) {
            TraceTm.jotm.error("ControlImpl.rollback(): rollback raised exception ", e);
        }
        completed(true);
    }

    // ---------------------------------------------------------------
    // Resource Interface (for sub-coordinators)
    // ---------------------------------------------------------------

    /**
     * Sub-coordinator has received prepare from its superior.
     * It must more or less do the same things that the phase 1 of the 2PC.
     * @return		Vote : commit, roollback or read-only.
     */
    public synchronized int prepare() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("mystatus="+ StatusHelper.getStatusName(mystatus));
        }

        // Stops the Timer.
        if (mytimer != null) {
            mytimer.stop();
        }

        // Checks status
        switch (mystatus) {

        case Status.STATUS_ACTIVE :
            // normal case
            break;

        case Status.STATUS_COMMITTED :
            // Should not occur ?
            TraceTm.jotm.error("ControlImpl.prepare(): transaction already commited");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            return Resource.VOTE_COMMIT;

        case Status.STATUS_ROLLEDBACK :
            // Should not occur ?
            TraceTm.jotm.error("ControlImpl.prepare(): transaction already rolled back");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            return Resource.VOTE_ROLLBACK;

        case Status.STATUS_MARKED_ROLLBACK :
            do_rollback(false);
            completed(true);
            return Resource.VOTE_ROLLBACK;

        default :
            // Don't know what to do here...
            TraceTm.jotm.error("ControlImpl.prepare(): bad status");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            completed(false);
            return Resource.VOTE_ROLLBACK;
        }

        int ret = do_prepare(false);

        switch (ret) {
        case Resource.VOTE_COMMIT :
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.prepare(): vote commit");
            }
            break;
        case Resource.VOTE_ROLLBACK :
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.prepare(): vote rollback");
            }
            do_rollback(false);
            completed(true);
            return ret;
        case Resource.VOTE_READONLY :
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.prepare(): vote readonly");
            }
            mystatus = Status.STATUS_COMMITTED;
            completed(true);
            return ret;
        }

        // Flush log + recovery coordinator
        // TODO

        return ret;
    }

    /**
     * Sub-coordinator received commit from its superior.
     * It must more or less do the same things that the phase 2 of the 2PC.
     */
    public synchronized void commit() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("mystatus="+ StatusHelper.getStatusName(mystatus));
        }

        // check status
        switch (mystatus) {
        case Status.STATUS_PREPARED :
            // normal case
            break;
        default :
            // Don't know what to do here...
            TraceTm.jotm.error("ControlImpl.commit(): commit: bad status");
            TraceTm.jotm.error("mystatus= "+ StatusHelper.getStatusName(mystatus));
            completed(false);
            return;
        }

        // send commit to resources
        if (do_commit(true) == 0) {
            completed(true);
        } else {
            completed(false);
        }
    }

    /**
     * Sub-coordinator received commit_one_phase from its superior.
     * It is more or less a Terminator.commit().
     */
    public void commit_one_phase() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("ControlImpl.commit_one_phase()");
        }

        // Just call Terminator.commit
        commit(true);
    }

    /**
     * forget transaction
     *
     */
    public synchronized void forget() throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("ControlImpl.forget()");
        }

        completed(true);
    }

    // ---------------------------------------------------------------
    // RecoveryCoordinator Interface
    // ---------------------------------------------------------------

    /**
     * Asks the status of this transaction, after recovery of a Resource
     * @param res		Resource recovering
     */
    public synchronized int replay_completion(Resource res) throws RemoteException {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("res="+ res);
        }
        return mystatus;
    }

    // ===============================================================
    // TimerEventListener implementation
    // ===============================================================

    /**
     * The transaction timeout has expired
     * Do not synchronize this method to avoid deadlocks!
     */
    public void timeoutExpired(Object arg) {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("arg="+ arg);
        }

        int argvalue = ((Integer) arg).intValue();

        switch (argvalue) {
        case 1 :
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.timeoutExpired(Object): timeout expired");
            }

            try {
                do_rollback(false);
            } catch (Exception e) {
                TraceTm.jotm.error("ControlImpl.timeoutExpired(Object): rollback raised exception ", e);
            }
            break;
        case 2 :
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("ControlImpl.timeoutExpired(Object): removing ControlImpl");
            }
            explicit_destroy();
            break;
        default :
            TraceTm.jotm.error("ControlImpl.timeoutExpired(Object): timeoutExpired bad value="+ argvalue);
            break;
        }
    }

    // ===============================================================
    // Private methods
    // ===============================================================

    /**
     * timeout expired on this transaction.
     * This method is not private, because it's used by Timers
     */
    void ding() {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("");
        }
    }

    /**
     * Phase 1 of the 2-Phase-Commit:
     * Sends prepare to each registered resource
     * This internal routine should be used either for prepare on sub-coordinator
     *  or for commit on Terminator.
     *
     * @return			global vote
     */
    private synchronized int do_prepare(boolean report_heuristics) {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("report_heuristics="+ report_heuristics);
        }

        int errors = 0;
        int ret = Resource.VOTE_READONLY;

        // Synchronization objects
        errors = do_before_completion();

        if (errors > 0) {
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("before_completion failed -> rollback");
            }
            return Resource.VOTE_ROLLBACK;
        }

        // No resource -> just forget transaction.
        if (resourceList.size() == 0) {
            TraceTm.jotm.error("commit: no resource");
            mystatus = Status.STATUS_COMMITTED;
            do_after_completion();
            completed(true);
            return ret;
        }

        // Creates a log for that transaction, where we will add all the
        // resources that replied VOTE_COMMIT to prepare.
        // Do not flush the log on disk before decision to commit.
        mylog = new Log();

        // Sends prepare to each resource.
        // In case of prepare on sub-coord. we may have only 1 resource.
        // In case of phase 1 of the 2PC, we have several resources, because
        // the case of 1 resource is treated with commit_one_phase (optimization)
        mystatus = Status.STATUS_PREPARING;

        for (int i = 0; i < resourceList.size(); i++) {
            Resource res = (Resource) resourceList.get(i);

            if (errors > 0) {
            	if (TraceTm.jotm.isWarnEnabled()) {
                    TraceTm.jotm.warn("Vote stopped: at least one resource has voted rollback.");
                }
                break;
            } else {
                // No error yet: Send prepare to the resource.
                try {
                    if (TraceTm.jotm.isDebugEnabled()) {
                        TraceTm.jotm.debug("send prepare to resource:" + res);
                    }

                    switch (res.prepare()) {
                    case Resource.VOTE_COMMIT :
                        // Log resource
                        mylog.addResource(res);
                        TraceTm.jotm.info("Resource replied commit to prepare");
                        ret = Resource.VOTE_COMMIT;
                        break;
                    case Resource.VOTE_ROLLBACK :
                        TraceTm.jotm.info("Resource replied rollback to prepare");
                        ret = Resource.VOTE_ROLLBACK;
                        errors++;
                        break;
                    case Resource.VOTE_READONLY :
                        if (TraceTm.jotm.isDebugEnabled()) {
                            TraceTm.jotm.debug("Resource replied readonly to prepare");
                        }
                        break;
                    }
                } catch (HeuristicHazard e) { // Subcoordinator only
                    TraceTm.jotm.error("HeuristicHazard on prepare");
                    ret = Resource.VOTE_ROLLBACK;
                    errors++;
                } catch (HeuristicMixed e) { // Subcoordinator only
                    TraceTm.jotm.error("HeuristicMixed on prepare");
                    ret = Resource.VOTE_ROLLBACK;
                    errors++;
                } catch (Exception e) {
                    TraceTm.jotm.error("exception on prepare: ", e);
                    ret = Resource.VOTE_ROLLBACK;
                    errors++;
                }
            }
        }

        if (ret == Resource.VOTE_READONLY) {
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("All resources returned Readonly");
            }
            mystatus = Status.STATUS_COMMITTED;
            // Synchronization objects
            do_after_completion();
        }
        if (ret == Resource.VOTE_COMMIT) {
            mystatus = Status.STATUS_PREPARED;
        }

        if (TraceTm.jotm.isDebugEnabled()) {
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("Vote = " + ret);
            }
        }
        return ret;
    }

    /**
     * Phase 2 of the 2-Phase-Commit:
     * Sends commit to each registered resource
     * This internal routine should be used either for commit on sub-coordinator
     *  or for commit on Terminator.
     *
     * @return		0 if commit OK.
     */
    private synchronized int do_commit(boolean report_heuristics)
        throws TransactionRolledbackException, HeuristicMixed, HeuristicHazard, HeuristicRollback {

        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("report_heuristics="+ report_heuristics);
        }

        // First check that a log is initialized
        if (mylog == null) {
            TraceTm.jotm.error("no log");
            return -1;
        }

        // Status Transaction = committing
        mystatus = Status.STATUS_COMMITTING;

        // Sends commit to each resource "prepared".
        int commitnb = 0;
        int heuristicnb = 0;
        int errors = 0;
        int heuristicstate = 0;

        for (int i = 0; i < mylog.resourceLogged.size(); i++) {
            ResourceInfo resinfo = (ResourceInfo) mylog.resourceLogged.elementAt(i);

            if (resinfo.mystate != ResourceInfo.PREPARED) {
                TraceTm.jotm.info("resource not prepared");
                continue;
            }

            // commits resource

            try {
                TraceTm.jotm.debug("Send commit to prepared resource: " + resinfo.getResource());
                resinfo.getResource().commit();
                resinfo.mystate = ResourceInfo.COMMITTED;
                commitnb++;
            } catch (HeuristicRollback e) {
                TraceTm.jotm.error("Heuristic Rollback");
                resinfo.mystate = ResourceInfo.HEURISTIC_ROLLBACK;
                heuristicstate = ResourceInfo.HEURISTIC_ROLLBACK;
                errors++;
                if (commitnb > 0)
                    heuristicnb++;
            } catch (HeuristicMixed e) {
                TraceTm.jotm.error("Heuristic Mixed");
                resinfo.mystate = ResourceInfo.HEURISTIC_MIXED;
                heuristicstate = ResourceInfo.HEURISTIC_MIXED;
                errors++;
                commitnb++;
                heuristicnb++;
            } catch (HeuristicHazard e) {
                TraceTm.jotm.error("Heuristic Hazard");
                resinfo.mystate = ResourceInfo.HEURISTIC_HAZARD;
                heuristicstate = ResourceInfo.HEURISTIC_HAZARD;
                errors++;
                commitnb++;
                heuristicnb++;
            } catch (NotPreparedException e) {
                TraceTm.jotm.error("Resource Not Prepared");
                resinfo.mystate = ResourceInfo.REGISTERED;
                errors++;
            } catch (NoSuchObjectException e) {
                TraceTm.jotm.error("invalid objref - assume committed");
                resinfo.mystate = ResourceInfo.COMMITTED;
                commitnb++;
            } catch (Exception e) {
                TraceTm.jotm.error("exception on commit: ", e);
                errors++;
            }
        }

        if (errors == 0) {
            // Everything's fine.
            if (TraceTm.jotm.isDebugEnabled()) {
                TraceTm.jotm.debug("transaction committed");
            }

            mystatus = Status.STATUS_COMMITTED;
            mylog.forgetLog();
            // Synchronization Objects
            do_after_completion();
            return 0;
        }

        if (heuristicnb == 0) {
            // Transaction has been eventually rolled back
            TraceTm.jotm.info("transaction rolled back");
            mystatus = Status.STATUS_ROLLEDBACK;
            mylog.forgetLog();
            // Synchronization Objects
            do_after_completion();
            throw new TransactionRolledbackException();
        }

        // Heuristics must be logged
        TraceTm.jotm.info("Heuristics must be logged");
        mystatus = Status.STATUS_UNKNOWN;
        mylog.updateLog();

        // Synchronization Objects
        do_after_completion();

        if (report_heuristics) {
            switch (heuristicstate) {
            case (ResourceInfo.HEURISTIC_ROLLBACK):
                throw new HeuristicRollback();
            case (ResourceInfo.HEURISTIC_MIXED):
                throw new HeuristicMixed();
            case (ResourceInfo.HEURISTIC_HAZARD):
                throw new HeuristicHazard();
            }
        }

        return -1;
    }

    /**
     * Rollbacks the transaction
     * This internal routine should be used either for rollback on sub-coordinator
     * or for commit on Terminator when prepare returned a vote RollBack.
     */
    private synchronized void do_rollback(boolean report_heuristics) throws HeuristicMixed {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("report_heuristics="+ report_heuristics);
        }

        mystatus = Status.STATUS_ROLLEDBACK;

        // Rollback resources
        int commitnb = 0;

        for (int i = 0; i < resourceList.size(); i++) {
            Resource res = (Resource) resourceList.get(i);
            try {
                if (TraceTm.jotm.isDebugEnabled()) {
                    TraceTm.jotm.debug("Send rollback to Resource:" + res);
                }
                res.rollback();
            } catch (HeuristicCommit e) {
                TraceTm.jotm.error("Rollback raised HeuristicCommit");
                commitnb++;
            } catch (Exception e) {
                TraceTm.jotm.error("Cannot rollback resource: ", e);
            }
        }

        // Synchronization objects
        do_after_completion();

        if (commitnb > 0 && report_heuristics) {
            // May be should throw HeuristicCommit instead
            throw new HeuristicMixed();
        }
    }

    /**
     * Destroys transaction object.
     */
    private void explicit_destroy() {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("");
        }

        // unexportObject explicitly
        try {
            unexportObject(this);
        } catch (Exception e) {}
    }

    /**
     * Delay destroy of the ControlImpl object.
     * We must keep alive this object a little while for recovery reasons.
     */
    private synchronized void completed(boolean removeit) {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("removeit="+ removeit);
        }

        if (mytimer != null) {
            if (removeit) {
                // Convert the timer to make a delayed removal of this object
                mytimer.change(60, Integer.valueOf(2));
            } else {
                // In case of heuristic, must keep ControlImpl.
                // No more timer is needed.
                mytimer.unset();
                mytimer = null;
            }
        }
    }

    /**
     * Sends before_completion to the registered synchronizations
     *
     * @return		0 if no error
     */
    private int do_before_completion() {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("ControlImpl.do_before_completion()");
            TraceTm.jotm.debug("synchronizationList.size()="+ synchronizationList.size());
        }

        int errors = 0;

        for (int i = 0; i < synchronizationList.size(); i++) {

            RemoteSynchro sync = (RemoteSynchro) synchronizationList.get(i);

            try {
                sync.before_completion(this);
            } catch (Exception e) {
                TraceTm.jotm.error("before_completion raised exception ", e);
                errors++;
            }
        }
        return errors;
    }

    /**
     * Sends after_completion to the registered synchronizations
     */
    private void do_after_completion() {

        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("ControlImpl.do_after_completion()");
            TraceTm.jotm.debug("status="+ mystatus);
            TraceTm.jotm.debug("synchronizationList.size()="+ synchronizationList.size());
        }

        for (int i = 0; i < synchronizationList.size(); i++) {
            RemoteSynchro sync = (RemoteSynchro) synchronizationList.get(i);

            try {
                sync.after_completion(this, mystatus);
            } catch (Exception e) {
                TraceTm.jotm.error("after_completion raised exception ", e);
            }
        }
    }
}
