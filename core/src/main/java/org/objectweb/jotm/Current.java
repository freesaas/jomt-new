/*
 * @(#) Current.java
 *
 * JOTM: Java Open Transaction Manager
 *
 *
 * This module was originally developed by
 *
 *  - Bull S.A. as part of the JOnAS application server code released in
 *    July 1999 (www.bull.com)
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
 * Contributor(s):
 * 01/11/06 Christophe Ney cney@batisseurs.com
 *          Added ResourceManagerListener mechanism to remove ThreadData
 *          dependency.
 * 01/12/03 Dean Jennings - synchronizedMap for txXids
 *
 * --------------------------------------------------------------------------
 * $Id: Current.java,v 1.61 2006-09-06 20:44:58 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.io.Serializable;
import java.util.*;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.resource.spi.XATerminator;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.objectweb.howl.log.xa.XACommittingTx;

/**
 * <code>Current</code> is the common implementation for
 * <code>UserTransaction</code>.
 * <ul> 
 * <li><code>UserTransaction</code> is used by clients that want to demarcate
 * transactions themselves. It is referenceable through JNDI.</li>
 * 
 * <p>This object is unique in a VM, i. e. each application server has
 * <em>ONE</em> <code>Current</code> object and each client program should
 * normally issue only <em>ONE</em> lookup on JNDI.</p>
 * 
 * <p><code>Current</code> also implements <code>Referenceable</code> and
 * <code>Serializable</code> because of JNDI.</p>
 */
public class Current implements UserTransaction, TransactionManager, Referenceable, Serializable {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 67411825766929272L;

    /*
     * transaction associated to the thread
     */
    private static transient ThreadLocal<TransactionImpl> threadTx = new ThreadLocal<TransactionImpl>();

    /*
     * per thread transaction timeout value
     */
    private static transient ThreadLocal<Integer> threadTimeout = new ThreadLocal<Integer>();
 
    /*
     * Static hashtable: Xid ---> transaction
     * All accesses must be synchronized (we use the Current lock monitor)
     */
    private transient static Map<Xid, TransactionImpl> txXids = new HashMap<Xid, TransactionImpl>();

    // Must be init at null, for clients that do not get UserTransaction
    private transient static Current unique = null;

    private transient static TimerManager timermgr = null; // local
    private transient static TransactionFactory tm = null; // local or remote
    private transient static TransactionRecovery tr = null; // transaction recovery object

    private static final int DEFAULT_TIMEOUT = 60;
    private int defaultTimeout = DEFAULT_TIMEOUT;
    private static final boolean DEFAULT_RECOVERY = false;
    private static boolean transactionRecovery = DEFAULT_RECOVERY;
    private static boolean appServer = true;
    
    // management counter
    private transient int nb_bg_tx = 0;
    private transient int nb_rb_tx = 0;
    private transient int nb_cm_tx = 0;
    private transient int nb_to = 0;

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /**
     * Default constructor.
     * A client does not need the TMFactory.
     */
    public Current() {
        unique = this;
        timermgr = TimerManager.getInstance();
        transactionRecovery();
    }

    /**
     * Constructor for an application server. 
     * Typically called form the Jotm constructor.
     * The TM factory is passed as an argument. Note that the TM factory can be
     * either local or remote.
     * @param tmfact TM Factory to use
     */
    public Current(TransactionFactory tmfact) {
        unique = this;
        setTMFactory(tmfact);
        timermgr = TimerManager.getInstance();
        transactionRecovery();
    }

    /**
     * @return the unique TransactionManager for the current server.
     */
    public static TransactionManager getTransactionManager() {
        return (TransactionManager) unique;
    }

    /**
     * @return the unique UserTransaction.
     */
    public static UserTransaction getUserTransaction() {
        return (UserTransaction) unique;
    }

    /**
     * The TM factory is passed as an argument. Note that the TM factory can be
     * either local or remote.
     * @param tmfact TM Factory to use
     */
    public static void setTMFactory(TransactionFactory tmfact) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("TransactionFactory="+ tmfact);
        }
        tm = tmfact;   
    }
    
    /**
     * Initiate transaction recovery if enabled.
     */
    public static void transactionRecovery() {
        if (tr == null) {
            try {
                tr = new TransactionRecoveryImpl();
            } catch (Exception e) {
                setDefaultRecovery(false);
                TraceTm.recovery.error("Cannot open Howl Log");
                TraceTm.recovery.error("JOTM Recovery is being disabled");
            }
        }
    }
    
    // ------------------------------------------------------------------
    // UserTransaction implementation
    // ------------------------------------------------------------------

    /**
     * Creates a new transaction and associate it with the current thread.
     *
     * @exception NotSupportedException Thrown if the thread is already
     *    associated with a transaction. (nested transaction are not
     *    supported)
     * @exception SystemException Thrown if the transaction manager 
     *    encounters an unexpected error condition
     */
    public void begin() throws NotSupportedException, SystemException {

        // checks that no transaction is already associated with this thread.
        TransactionImpl tx = threadTx.get();
        if (tx != null) {
            synchronized(this) {
                if (txXids.containsValue(tx)) {
                    if (! txcanrollback(tx)) {
                        TraceTm.jta.debug("Nested transactions not supported");
                        throw new NotSupportedException("Nested transactions not supported");
                    }
                } else {
                    if (TraceTm.jta.isDebugEnabled()) {
                        TraceTm.jta.debug("Resetting current tx = " + tx + " since it is already completed.");
                    }
                }
            }
        }

        // Set the timeout
        Integer mytimeobj = threadTimeout.get();
        int transactionTimeout = defaultTimeout;
        if (mytimeobj != null) {
            if (mytimeobj == 0) {  // never set, use defaultTimeout
                transactionTimeout = defaultTimeout;   
            } else {
                transactionTimeout = mytimeobj;
                threadTimeout.set(0);  // reset to "not set"
            }
        }
        
        // builds a new Xid
        // - should pass servername + ip addr. (LATER)
        XidImpl otid = new XidImpl();

        // creates a new TransactionImpl object
        // - May raise SystemException
        tx = new TransactionImpl(otid, transactionTimeout);
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("new tx=" + tx);
        }

        // clear suspended transaction.
        try {
            tx.doAttach(XAResource.TMJOIN);
        } catch (RollbackException e) {
            // never.
            TraceTm.jotm.error("doAttach: RollbackException");
            throw new SystemException("RollbackException in occured in begin() " + e.getMessage());
        }

        // associates transaction with current thread
        threadTx.set(tx);
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("threadTx set to " + tx);
        }

        // associates this Tx with the Xid
        putTxXid(otid, tx);

        // sets a timer for the transaction
        if (timermgr != null) {
            tx.setTimer(timermgr.addTimer(tx, transactionTimeout, null, false));
        }
        
        // sets the time stamp for the transaction
        tx.setTxDate(new Date());

    }

    // ------------------------------------------------------------------
    // Inflow Transaction implementation
    // ------------------------------------------------------------------

    /**
     * Creates a new inflow transaction and associates it with the current thread.
     *
     * @param passxid <code>Xid</code> of the inflow transaction.
     *
     * @exception NotSupportedException Thrown if the thread is already
     *    associated with a transaction. (nested transaction are not
     *    supported)
     *
     * @exception SystemException Thrown if the transaction manager 
     *    encounters an unexpected error condition
     */
    public void begin(javax.transaction.xa.Xid passxid) throws NotSupportedException, SystemException {
        Integer mytimeobj = threadTimeout.get();
        if (mytimeobj == null || mytimeobj == 0) {
            // setTransactionTimeout not called, use defaultTimeout
            begin(passxid, (long) defaultTimeout);   
        } else {
            begin(passxid, mytimeobj);
            threadTimeout.set(0);  // resets to "not set"
        }
    }

    /**
     * Creates a new inflow transaction and associates it with the current thread.
     *
     * @param passxid <code>Xid</code> of the inflow transaction.
     *
     * @param timeout value of the timeout (in seconds). If the value is less than 
     * or equal to zero, the value will be set to the default value.
     *
     * @exception NotSupportedException Thrown if the thread is already
     *    associated with a transaction. (nested transaction are not
     *    supported)
     *
     * @exception SystemException Thrown if the transaction manager 
     *    encounters an unexpected error condition
     */
    public void begin(javax.transaction.xa.Xid passxid, long timeout) throws NotSupportedException, SystemException {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("begin inflow transaction, timeout = " + timeout);
        }
        if (timeout <= 0) {
            timeout = defaultTimeout;
        }
        
        // checks that no transaction is already associated with this thread.
        TransactionImpl tx = threadTx.get();
        if (tx != null) {
            synchronized(this) {
                if (txXids.containsValue(tx)) {
                    if (! txcanrollback(tx)) {
                        throw new NotSupportedException("Nested transactions not supported");
                    }
                }
                if (TraceTm.jta.isDebugEnabled()) {
                    TraceTm.jta.debug("Resetting current tx = " + tx + " since it is already completed.");
                }
            }
        }

        // stores the passed xid components
        XidImpl pxid = new XidImpl(passxid);

        // creates a new TransactionImpl object
        // - May raise SystemException
        tx = new TransactionImpl(pxid, (int) timeout);

        // associates transaction with current thread
        threadTx.set(tx);

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("threadTx set to " + tx);
        }

        // associates this Tx with the Xid
        putTxXid(pxid, tx);

        // sets a timer for the transaction
        if (timermgr != null) {
            tx.setTimer(timermgr.addTimer(tx, (int) timeout, null, false));
        }

        // sets the time stamp for the transaction
        tx.setTxDate(new Date());
    }

    /**
     * Gets the inflow transaction object that represents the transaction context of
     * the calling thread.
     *
     * @return the XATerminator object representing the inflow transaction 
     * associated with the calling thread. If the calling thread is
     * not associated with an inflow transaction, a null object reference
     * is returned.
     *
     * @exception XAException Thrown if the transaction manager 
     * encounters an unexpected error condition
     */
    public XATerminator getXATerminator() throws XAException {

        XATerminator xaterm = null;

        try {
            xaterm = new XATerminatorImpl();
        } catch (XAException e) {
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.jta.debug("Cannot create XATerminatorImpl"+ e);
            }
        }
        return xaterm;
    }

    // ------------------------------------------------------------------
    // End of Inflow Transaction implementation
    // ------------------------------------------------------------------


    /**
     * Commits the transaction associated with the current thread. When this
     * method completes, the thread becomes associated with no transaction.
     *
     * @exception RollbackException Thrown to indicate that    the transaction
     * has been rolled back rather than committed.
     *
     * @exception HeuristicMixedException Thrown to indicate that a heuristic
     * decision was made and that some relevant updates have been committed
     * while others have been rolled back.
     *
     * @exception HeuristicRollbackException Thrown to indicate that a
     * heuristic decision was made and that some relevant updates have been
     * rolled back.
     *
     * @exception SecurityException Thrown to indicate that the thread is not
     * allowed to commit the transaction.
     *
     * @exception IllegalStateException Thrown if the current thread is    not
     * associated with a transaction.
     *
     * @exception SystemException Thrown if the transaction manager encounters
     * an unexpected error condition
     */
    public void commit()
        throws
            RollbackException,
            HeuristicMixedException,
            HeuristicRollbackException,
            SecurityException,
            IllegalStateException,
            SystemException {

        // Get Transaction
        TransactionImpl tx = (TransactionImpl) getTransaction();
        if (tx == null) {
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.jta.debug("Cannot get Transaction for commit");
            }
            throw new IllegalStateException("Cannot get Transaction for commit");
        }
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("tx=" + tx);
        }
        
        // Commit Transaction. Exceptions may be raised!
        try {
            tx.commit();
        } finally {
            // Dissociates the transaction from current thread
            // Has not been done in doDetach because we need to
            // be in the transactional context for beforeCompletion
            threadTx.set(null);
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.jta.debug("threadTx.set null");
            }
        }
    }

    /**
     * Rolls back the transaction associated with the current thread. When this
     * method completes, the thread becomes associated with no transaction.
     *
     * @exception SecurityException Thrown to indicate that the thread is not
     * allowed to roll back the transaction.
     *
     * @exception IllegalStateException Thrown if the current thread is not
     * associated with a transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     * encounters an unexpected error condition
     */
    public void rollback() throws IllegalStateException, SecurityException, SystemException {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Current.rollback()");
        }

        // Get Transaction
        TransactionImpl tx = (TransactionImpl) getTransaction();
        if (tx == null) {
            throw new IllegalStateException("Cannot get Transaction for rollback");
        }
        threadTx.set(null);
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("threadTx.set= null");
        }

        // Roll back the transaction. Exceptions may be raised!
        tx.rollback();
    }

    /**
     * Modify the transaction associated with the current thread such that the
     * only possible outcome of the transaction is to roll back the transaction.
     *
     * @exception IllegalStateException Thrown if the current thread is    not
     * associated with a transaction.
     *
     * @exception javax.transaction.SystemException Thrown if the transaction manager
     * encounters an unexpected error condition
     */
    public void setRollbackOnly() throws IllegalStateException, SystemException {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Current.setRollbackOnly()");
        }
        // Get Transaction
        TransactionImpl tx = (TransactionImpl) getTransaction();
        if (tx == null) {
            throw new IllegalStateException("Cannot get Transaction for setRollbackOnly");
        }

        // Set transaction rollback only. Exceptions may be raised!
        tx.setRollbackOnly();
    }

    /**
     * Returns the status of the transaction associated with the current
     * thread.
     * 
     * @return transaction status. If no transaction is associated with the
     * current thread, this method returns the Status.NoTransaction value.
     *
     * @exception SystemException Thrown if the transaction manager
     * encounters an unexpected error condition
     */
    public int getStatus() throws SystemException {

        // Get Transaction
        TransactionImpl tx = (TransactionImpl) getTransaction();
        if (tx == null) {
            TraceTm.jta.debug("Current.getStatus(): NO_TRANSACTION");
            return Status.STATUS_NO_TRANSACTION;
        }

        // Get TX status. Exceptions may be raised!
        return tx.getStatus();
    }

    /**
     * Modifies the value of the timeout value that is associated with the
     * transactions started by the current thread with the begin method.
     *
     *  If an application has not called this method, the transaction
     * service uses some default value for the transaction timeout.
     *
     * @param timeout value of the timeout (in seconds). If the value is zero,
     * the transaction service restores the default value.
     *
     * @exception SystemException Thrown if the transaction manager
     * encounters an unexpected error condition
     */
    public void setTransactionTimeout(int timeout) throws SystemException {
        
        // checks that no transaction is already associated with this thread.
        // If one is, then we have a running transaction (ut.begin) and we must
        // wait until transaction completes (ut.commit or ut.rollback)
        
        TransactionImpl tx = threadTx.get();

        if (tx != null) {
            synchronized(this) {
                if (txXids.containsValue(tx)) {
                    if (TraceTm.jta.isDebugEnabled()) {
                        TraceTm.jta.debug("Cannot reset transaction timeout, tx in execution");
                    }
                    // Cannot reset transaction timeout, tx (ut.begin)in execution, ignore.
                    return;
                }
            }
        }

        threadTimeout.set(timeout);
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Resetting transaction timeout= " + timeout);
        }
    }

    /**
     * Gets the transaction timeout value
     * @return transaction timeout value (in seconds)
     */
    public int getTransactionTimeout() {
        Integer mytimeobj = threadTimeout.get();
        if (mytimeobj == null || mytimeobj == 0) {
            // setTransactionTimeout not called, use defaultTimeout
            return defaultTimeout;
        } else {
            return mytimeobj;
        }
    }

    /**
     * Modifies the value of the recovery value that is associated with the
     * transactions started by the current thread with the begin method.
     *
     * If an application has not called this method, the transaction
     * service uses the default value of 'false' for recovery.
     *
     * @param recovery value of the recovery (true or false). If the value is
     * false, recovery of transactions is disabled.
     *
     * @exception SystemException Thrown if the transaction manager
     * encounters an unexpected error condition
     */
    public void setTransactionRecovery(boolean recovery) throws SystemException {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("recovery="+ recovery);
        }
        transactionRecovery = recovery;
    }

    // ------------------------------------------------------------------
    // TransactionManager implementation
    // (only the methods that are not already in UserTransaction)
    // ------------------------------------------------------------------

    /**
     * Gets the transaction object that represents the transaction context of
     * the calling thread.
     *
     * @return the Transaction object representing the transaction 
     * associated with the calling thread. If the calling thread is
     * not associated with a transaction, a null object reference
     * is returned.
     *
     * @exception SystemException Thrown if the transaction manager 
     * encounters an unexpected error condition
     */
    public Transaction getTransaction() throws SystemException {
        return threadTx.get();
    }

    /**
     * Resumes the transaction context association of the calling thread with
     * the transaction represented by the supplied Transaction object. When this
     * method returns, the calling thread is associated with the transaction
     * context specified. 
     * <p><em>Warning</em>: No XA start is done here. We suppose it is already
     * done after a <code>getConnection()</code>. 
     * </p>
     * The supposed programming model is: <ol>
     * 	<li><code>getConnection()</code></li>
     * 	<li>SQL code</li>
     * 	<li><code>connection.close()</code</li>
     * 	</ol>
     *
     * @param tobj The <code>Transaction</code> object that represents the
     * transaction to be resumed.
     *
     * @exception InvalidTransactionException Thrown if the parameter
     * transaction object contains an invalid transaction
     *
     * @exception IllegalStateException Thrown if the thread is already
     * associated with another transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     * encounters an unexpected error condition
     */
    public void resume(Transaction tobj) throws InvalidTransactionException, IllegalStateException, SystemException {

        // invalid Transaction
        if (tobj == null) {
            // This case is described in the spec as not an error.
            // Just detach thread from any transaction.
            TraceTm.jotm.debug("resume(null): associate thread with no transaction");
            threadTx.set(null);
            return;
        }

        // Checks that the thread is not already associated to ANOTHER transaction
        Transaction mytx = threadTx.get();
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("mytx= " + mytx);
        }
        if (mytx != null) {
            if (mytx.equals(tobj)) {
                if (TraceTm.jta.isDebugEnabled()) {
                    TraceTm.jta.debug("nothing to do");
                }
                return;
            }
            TraceTm.jotm.error("resume: already associated with another transaction.");
            throw new IllegalStateException("the thread is already associated with another transaction.");
        }

        // test for type before cast
        if (!(tobj instanceof TransactionImpl)) {
            TraceTm.jotm.error("resume: non TransactionImpl arg.");
            throw new InvalidTransactionException("resume(" + tobj.getClass().getName() + ") is not valid");
        }
        TransactionImpl tx = (TransactionImpl) tobj;

        // Check the case where the Transaction is Invalid, usually because
        // the transaction has already been rolledback or committed.
        // Status may be preparing or committing in case of distributed transaction:
        // ControlImpl is switching to the correct transaction context while doing beforeCompletion
        // (See SubCoordinator.java)
        if ((tx.getStatus() != Status.STATUS_ACTIVE) && 
            (tx.getStatus() != Status.STATUS_COMMITTING) &&
            (tx.getStatus() != Status.STATUS_PREPARING)) {
            TraceTm.jotm.error("resume: Invalid Transaction Status:" + StatusHelper.getStatusName(tx.getStatus()));
            InvalidTransactionException e = new InvalidTransactionException("Invalid resume " + tobj.getClass().getName());
            throw e;
        }
        TraceTm.jotm.debug("status = " + StatusHelper.getStatusName(tx.getStatus()));

        // Associates this Tx with the current thread
        threadTx.set(tx);
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("threadTx set to " + tx);
        }

        if (!appServer) {
            // attach suspended resources
            try {
                tx.doAttach(XAResource.TMRESUME);
            } catch (RollbackException e) {
                // never.
                TraceTm.jotm.error("RollbackException occured in resume()");
                throw new SystemException("RollbackException in occured in resume() " + e.getMessage());
            }
        }
    }

    /**
     * Suspends the transaction currently associated with the calling thread
     * and return a <code>Transaction</code> object that represents the
     * transaction context being suspended.
     * If the calling thread is not
     * associated with a transaction, the method returns
     * <code>null</code>. When this method returns, the calling thread is
     * associated with no transaction.
     * <p><em>Warning</em>: No XA start is done here. We suppose it is already
     * done after a <code>getConnection()</code>.
     * </p>
     * The supposed programming model is: <ol>
     * 	<li><code>getConnection()</code></li>
     * 	<li>SQL code</li>
     * 	<li><code>connection.close()</code></li>
     * 	</ol>
     *
     * @return Transaction object representing the suspended transaction.
     *
     * @exception SystemException Thrown if the transaction manager
     * encounters an unexpected error condition
     */
    public Transaction suspend() throws SystemException {

        TransactionImpl tx = threadTx.get();
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("suspend transaction = " + tx);
        }
        if (tx != null) {
            if (!appServer) {
                tx.doDetach(XAResource.TMSUSPEND);
            }
            threadTx.set(null);
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.jta.debug("threadTx.set= null");
            }
        }
        return tx;
    }

    // ------------------------------------------------------------------
    // Referenceable implementation
    // ------------------------------------------------------------------

    /**
     * Retrieves the <code>Reference</code> of this object.
     * @return  The non-null <code>Reference</code> of this object.
     * @exception  NamingException  If a naming exception was encountered while retrieving the reference.  
     */
    public Reference getReference() throws NamingException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Current.getReference()");
        }

        // create the reference
        Reference ref = new Reference(this.getClass().getName(), "org.objectweb.jotm.UserTransactionFactory", null);

        // get the timeout
        Integer i = threadTimeout.get();
        if (i == null || i == 0) {
            i = defaultTimeout;
        }

        ref.add(new StringRefAddr("jotm.timeout", i.toString()));
        return ref;
    }

    // ------------------------------------------------------------------
    // Other public methods
    // ------------------------------------------------------------------

    /**
     * Returns the unique instance of the class or <code>null</code> if not
     * initialized in case of plain client.
     *
     * @return The <code>Current</code> object created 
     */
    public static Current getCurrent() {
        return unique;
    }

    /**
     * Returns the TMFactory (in JTM)
     * 
     * @return TransactionFactory
     */
    public static TransactionFactory getJTM() {
        if (tm == null) {
            TraceTm.jotm.error("Current: TMFactory is null!");
        }
        return tm;
    }
    
    /**
     * Returns the Transaction Recovery object
     * @return TransactionRecovery
     */
    public static TransactionRecovery getTransactionRecovery() {
        if (tr == null) {
            TraceTm.jotm.error("Current: Transaction Recovery is null!");
        }
        return tr;
    }
    
    /**
     * Sets the default timeout value
     * @param timeout timeout value (in seconds)
     */
    public void setDefaultTimeout(int timeout) {
        if (timeout != 0) {
            defaultTimeout = timeout;
        }
        
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("default timeout= " + defaultTimeout);
        }
    }

    /**
     * Gets the default timeout value
     * @return default timeout value (in seconds)
     */
    public int getDefaultTimeout() {
        return defaultTimeout;
    }
    
    /**
     * Sets the default recovery value
     * @param recovery recovery value (true or false)
     */
    public static void setDefaultRecovery(boolean recovery) {
        TraceTm.recovery.info("Jotm Recovery= " + recovery);
        transactionRecovery = recovery;
    }

    /**
     * Gets the default recovery value
     * @return default recovery value (true or false)
     */
    public static boolean getDefaultRecovery() {
        return transactionRecovery;
    }
    
    /**
     * Sets indicator if running with Application Server
     * @param appserver application server enabled value (true or false)
     */
    public static void setAppServer(boolean appserver) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Application Server enabled= " + appserver);
        }
        appServer = appserver;
    }

    /**
     * Gets the Application Server enabled value
     * @return application server enabled value (true or false)
     */
    public static boolean getAppServer() {
        return appServer;
    }

    /**
     * Associate to the current thread a transaction represented by its
     * transaction context.
     * This is used internally by the implicit propagation of the
     * transactional context.
     * Server side: called with isReply=false when receiving a request.
     * Server side: called with pctx=null when sending a reply.
     * Client side: called with isReply=true when receiveing the reply.
     * @param pctx TransactionContext. May be null to clean the thread (server side)
     * @param isReply <code>true</code> when receiving a reply (client side)
     */
    public void setPropagationContext(TransactionContext pctx, boolean isReply) {

        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("pctx=" + pctx + ", isReply=" + isReply);
        }

        if (pctx == null) {
            // Server side: Sending a reply to the client.
            // -------------------------------------------
            TransactionImpl tx = threadTx.get();
            if (tx != null) {
                // Just detaches thread from any transaction
                // Cannot remove Tx here, because it can be used in a Stateful
                // bean, even if it has no resource involved yet.
                // See bug #313564.
                // Moreover, iiop would not work because remote called are done
                // even inside the ots interceptor (_get_reference)
                // Must only remove it if transaction was already rolled back
                // when the request has arrived.
                if (tx.toRemove()) {
                    forgetTx(tx.getXid());
                }
                threadTx.set(null);
                if (TraceTm.jta.isDebugEnabled()) {
                    TraceTm.jta.debug("threadTx.set= null");
                }
            }
            return;
        }

        // Get the Transaction matching this Xid if it exists
        Xid xid = pctx.getXid();
        TransactionImpl tx = getTxXid(xid);

        if (tx == null) {
            // New Tx.
            if (!isReply) {
                // Server side: Receiving a request with a TransactionContext.
                // ----------------------------------------------------------
                if (TraceTm.jta.isDebugEnabled()) {
                    TraceTm.jta.debug("new Tx");
                }
                tx = new TransactionImpl(pctx);
                // In case the Tx is already rolled back, this has to be undone
                // at some time.
                putTxXid(xid, tx);

                // sets the time stamp for the transaction
                tx.setTxDate(new Date());
            }

        } else {
            // Old Tx.
            if (isReply) {
                // Client Side: Receiving a reply.
                // ------------------------------
                // This thread is already associated to a transaction.
                // A proper PropagationContext exists yet, but it may have been changed
                // in case of distributed transaction.

                if (TraceTm.jta.isDebugEnabled()) {
                    TraceTm.jta.debug("updating Xid=" + xid);
                }

                tx.updatePropagationContext(pctx);
            } else {
                // Server side: Receiving a request with an already known tx
                // ---------------------------------------------------------
                if (TraceTm.jta.isDebugEnabled()) {
                    TraceTm.jta.debug("transaction already known:" + xid);
                }
            }
        }
        if (!isReply) {
            // Associates this Tx with the current thread (= resume)
            // Assumes we are in the thread that will run the request.
            threadTx.set(tx);
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.jta.debug("threadTx set to " + tx);
            }
        }

    }

    /**
     * Get the transaction context associated with the current thread or null
     * if the thread is not involved in a transaction.
     * @param hold true if hold the object while used. (Not used any longer)
     * @return the propagation context for the current transaction.
     */
    public TransactionContext getPropagationContext(boolean hold) {
        try {
            TransactionImpl tx = (TransactionImpl) getTransaction();
            if (tx != null) {
                if (TraceTm.jotm.isDebugEnabled()) {
                    TraceTm.jotm.debug("valid tx");
                }
                return tx.getPropagationContext(hold);
            }
        } catch (SystemException e) {
            TraceTm.jotm.error("getPropagationContext system exception:", e);
        }
        // will return null if Tx not valid.
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("no tx");
        }
        return null;
    }

    /**
     * Forget all about this transaction.
     * References to <code>TransactionImpl</code> must be destroyed to allow
     * the garbage collector to free memory allocated to this transaction.
     *
     * @param xid <code>Xid</code> of the transaction
     */
    public synchronized void forgetTx(Xid xid) {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("xid=" + xid);
        }

        // Only clear threadTx if this thread is working on its Xid
        TransactionImpl txCur = txXids.get(xid);

        if (txCur != null && txCur.equals(threadTx.get())) {
            threadTx.set(null);
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.jta.debug("threadTx.set = null");
            }
        }
        if (txCur != null) {
            txCur.cleanup();
        }
        removeTxXid(xid);
    }

    /**
     * Get the transaction referenced by Xid.
     * @param xid <code>Xid</code> of the transaction
     * @return the TransactionImpl
     */
    public synchronized TransactionImpl getTxByXid(Xid xid) {
        return txXids.get(xid);
    }

    /**
     * Get the Xid's of all prepared transactions.
     * @return array of all Xids in the prepared state
     */
    public synchronized javax.transaction.xa.Xid[] getPreparedHeuristicXid() {
        if (txXids.size() <= 0) {
            return null;
        }
        ArrayList<Xid> xidlist = new ArrayList<Xid>();
        for (Xid key : txXids.keySet()) {
            TransactionImpl tx = txXids.get(key);
            try {
                if (tx.getStatus() == Status.STATUS_PREPARED) {
                    xidlist.add(tx.getXid());
                }
            } catch (SystemException e) {
                TraceTm.jotm.error("getPreparedHeuristicsXid system exception:", e);
            }
        }
        return xidlist.toArray(new Xid[1]);
    }

    /**
     * Get all Xid's associated with this transaction.
     * @return array of all Xids
     */
    public synchronized javax.transaction.xa.Xid[] getAllXid() {
        int xidCount = txXids.size();
        if (xidCount == 0) {
            return null;
        }

        ArrayList<javax.transaction.xa.Xid> xidlist = new ArrayList<javax.transaction.xa.Xid>();
        for (javax.transaction.xa.Xid key : txXids.keySet()) {
            xidlist.add(key);
        }
        return (javax.transaction.xa.Xid[]) xidlist.toArray();
    }

    /**
     * Get all executing transactions.
     * @return array of all Transactions in execution
     */
    public synchronized String [] getAllTx() {
        int txCount = txXids.size();
        if (txCount <= 0) {
            return null;
        }

        ArrayList<String> txList = new ArrayList<String>();
        List        txResourceList;
        int         txResourceCount;
        String      txStatusName;
        for (Xid key : txXids.keySet()) {
            TransactionImpl mytx = txXids.get(key);
            
            try {
                txStatusName = StatusHelper.getStatusName(mytx.getStatus());
            } catch (SystemException e) {
                txStatusName = "No State Defined";
            }
            txResourceList = mytx.getEnlistedXAResource();
            txResourceCount = txResourceList.size();
            
            if (txResourceCount == 0) {
                txList.add(mytx.getTxDate().toString() + "????" +
                           mytx.toString() + "????" +
                           "NO Resource Defined" + "????" +
                           txStatusName);
            } else {
                for (int i = 0; i < txResourceCount; i++) {
                    txList.add(mytx.getTxDate().toString() + "????" +
                               mytx.toString() + "????" +
                               txResourceList.get(i).toString() + "????" +
                               txStatusName);
                }
            }
        }

        String [] myTxString = new String[txCount];
        for (int i = 0; i < txCount; i++) {
            myTxString[i] = txList.get(i);
        }

        return myTxString;
    }

    /**
     * Get all Transactions that may require recovery.
     * @return array of all Transactions that may require recovery
     */
    public synchronized String [] getAllRcTx() {

        // First check that recovery is enabled
        if (tr == null) {
            TraceTm.recovery.debug("tr= null");
            return null;
        }
        JotmRecovery myjr = tr.getJotmRecovery();
        if (myjr == null) {
            return null;
        }

        Vector vTxRecovered = JotmRecovery.getTxRecovered();

        int txCount = vTxRecovered.size();
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("txcount= " + txCount);
        }
        if (txCount <= 0) {
            return null;
        }

        ArrayList<String> txList = new ArrayList<String>();

        for (int i = 0; i < txCount; i++) {
            TxRecovered mytxRecovered = (TxRecovered) vTxRecovered.elementAt(i);

            Xid temptxxid = new XidImpl(mytxRecovered.gettxxid());

            // We must send the complete transaction Xid string along
            // with the truncated. The complete will be used for further
            // searching while the truncated will be used in the display
            // of the JSP.

            txList.add(new String(mytxRecovered.gettxxid()) + "????" +
                       temptxxid.toString() + "????" +
                       mytxRecovered.gettxdatetime() + "????" +
                       mytxRecovered.getxidcount());
        }

        String [] myTxString = new String[txCount];

        for (int i = 0; i < txCount; i++) {
            myTxString[i] = txList.get(i);
        }

        return myTxString;
    }

    /**
     * Get all XAResources that may require recovery.
     * @param stx identifies the transaction
     * @return array of all XAResources that may require recovery
     */
    public synchronized String [] getAllXaTx(String stx) {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("stx=" + stx);
        }

        // First check that recovery is enabled
        if (tr == null || tr.getJotmRecovery() == null) {
            TraceTm.recovery.debug("no recovery");
            return null;
        }

        // search my transaction among the list of recovered transactions
        TxRecovered txr = null;
        boolean mytxfound = false;
        Vector vTxRecovered = JotmRecovery.getTxRecovered();
        int txCount = vTxRecovered.size();
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("txcount= " + txCount);
        }
        for (int i = 0; i < txCount; i++) {
            txr = (TxRecovered) vTxRecovered.elementAt(i);
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("gettxxid= " + new String(txr.gettxxid()));
            }
            if (new String(txr.gettxxid()).equals(stx)) {
                mytxfound = true;
                break;
            }
        }
        if (! mytxfound) {
            // transaction not found in the list -> no resource.
            return new String[0];
        }

        Vector vRecoverRmInfo = JotmRecovery.getRecoverRmInfo();

        String myxares;

        int myxacount = txr.getxidcount();
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("myxacount= " + myxacount);
        }

        // Build a list of XA Resource info
        ArrayList<String> xaList = new ArrayList<String>();
        for (int i = 0; i < myxacount; i++) {
            TxxidRecovered infoxid = txr.getRecoverTxXidInfo(i);

            // No info: Nothing can be done.
            if (infoxid == null) {
                xaList.add("NotFound" + "????" +
                           "NotFound" + "????" +
                           "NotFound" + "????" +
                           "NotFound" + "????" +
                           "NotFound");
                continue;
            }

            // Search in the RecoverRmInfo list for my RecoverXaRes (in infoxid)
            int rmiCount = vRecoverRmInfo.size();
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("rmiCount= " + rmiCount);
            }
            String myrm = "NotFound";
            for (int j = 0; j < rmiCount; j++) {
                RecoverRmInfo rmInfo = (RecoverRmInfo) vRecoverRmInfo.elementAt(j);

                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("getRecoverXaResName()= " + rmInfo.getRecoverXaResName());
                    TraceTm.recovery.debug("getRecoverxaresname()=" + infoxid.getRecoverxaresname());
                    TraceTm.recovery.debug("getRecoverXaRes()= " + new String(rmInfo.getRecoverXaRes()));
                    TraceTm.recovery.debug("getRecoverxares()=" + new String(infoxid.getRecoverxares()));
                }

                if (rmInfo.getRecoverXaResName().equals(infoxid.getRecoverxaresname())) {
                    myrm = rmInfo.getRecoverRm();
                    myxares = new String(infoxid.getRecoverxares());

                    if (TraceTm.recovery.isDebugEnabled()) {
                        TraceTm.recovery.debug("myrm= " + myrm);
                        TraceTm.recovery.debug("myxares= " + myxares);
                    }
                    break;
                }
            }

            // Search registration
            Vector vRmRegistration = tr.getRmRegistration();
            myxares = "NotRegistered";
            if (vRmRegistration == null) {
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("vRmRegistration is null");
                }
            } else {
                int rmregcount = vRmRegistration.size();
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("rmregcount= " + rmregcount);
                }
                for (int j = 0; j < rmregcount; j++) {
                    RmRegistration myRmRegistration = (RmRegistration) vRmRegistration.elementAt(j);
                    if (TraceTm.recovery.isDebugEnabled()) {
                        TraceTm.recovery.debug("myrm= " + myrm);
                        TraceTm.recovery.debug("rmGetName= " + myRmRegistration.rmGetName());
                    }
                    if (myrm.equals(myRmRegistration.rmGetName())) {
                        if (myRmRegistration.rmGetXaRes() == null) {
                            myxares = "IsNull";
                        } else {
                            myxares = myRmRegistration.rmGetXaRes().toString();
                        }
                        break;
                    }
                }
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("myxares= " + myxares);
                }
            }

            Xid tempxid = new XidImpl(infoxid.getRecoverxid());

            // We must send the complete Xid string along with the truncated.
            // The complete will be used for further searching while the
            // truncated will be used in the display of the JSP.
            xaList.add(myrm + "????" +
                       myxares + "????" +
                       infoxid.getRecoverxid() + "????" +
                       tempxid.toString() + "????" +
                       StatusHelper.getStatusName (infoxid.getRecoverstatus()));
        }

        String [] myTxString = new String[myxacount];
        for (int i = 0; i < myxacount; i++) {
            myTxString[i] = xaList.get(i);
        }
        return myTxString;
    }

    /**
     * @param xaAction String "commit" "rollback" or "forget"
     * @param xatx String representing the transaction
     * @return 0
     */
    public int actionXAResource(String xaAction, String xatx) {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("xaAction=" + xaAction + " xatx=" + xatx);
        }

        // First check that recovery is enabled
        if (tr == null || tr.getJotmRecovery() == null) {
            TraceTm.recovery.warn("no recovery");
            return 0;
        }

        Vector vRmRegistration = tr.getRmRegistration();
        if (vRmRegistration == null) {
            return 0;
        }

        // Given the passed Resource Manager, find it in the RMRegistered vector.
        // When the Resource Manager is found, use the XAResource associated with
        // the RMRegistered Resource Manager to perform a XAResource.recover call
        // to return all XIDs that require recovery.
        // With the list of XIDs to be recovered, verify that the Xid passes in
        // the list. If the Xid passed is in the recover list, then we can attempt
        // the commit of the Xid (i.e., XAResource.Commit(Xid)).
        // When the commit is completed, we can remove the TxXidRecovered array
        // entry associated with the Xid and decrement the count in its respective
        // TxRecovered vector entry. If the count in the TxRecovered vector entry
        // is zero, we can then remove the TxRecovered entry from the vector.

        // Get the name of resource manager
        int myix1 = xatx.indexOf('\n');
        String sResmgr = xatx.substring(0, myix1);

        int rmregcount = vRmRegistration.size();

        // Find XAResource
        XAResource xaresource = null;
        for (int i = 0; i < rmregcount; i++) {
            RmRegistration myRmRegistration = (RmRegistration) vRmRegistration.elementAt(i);

            if (sResmgr.equals(myRmRegistration.rmGetName())) {
                xaresource = myRmRegistration.rmGetXaRes();
                break;
            }
        }
        if (xaresource == null) {
            TraceTm.recovery.error("xaResource is null");
            return 0;
        }

        // Get the full Xid
        int myix2 = xatx.indexOf('\n', myix1 + 1);
        int myix3 = xatx.indexOf('\n', myix2 + 1);
        String sFullXid = xatx.substring(myix2 + 1, myix3);

        // recover XAresource to get the list of Xid's
        List<javax.transaction.xa.Xid> recoveredXidList = new LinkedList<javax.transaction.xa.Xid>();
        try {
            // recover returns javax.transaction.xa.Xid objects
            javax.transaction.xa.Xid[] javaxids = xaresource.recover(XAResource.TMSTARTRSCAN);
            if (javaxids != null && javaxids.length > 0) {
                recoveredXidList.addAll(Arrays.asList(javaxids));
            }
        } catch (XAException e) {
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.recovery.debug("xaResource.recover call failed during recovery " + e.getMessage());
            }
        }
        if (recoveredXidList.size() == 0) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("No XIDs to recover for Xares= "+ xaresource);
            }
            cleanuptxrecovery(sFullXid);
            return 0;
        }

        // Perform action on the Xid, if found in the list of recevered Xids.
        for (javax.transaction.xa.Xid recoveredXid : recoveredXidList) {
            if (recoveredXid.toString().equals(sFullXid)) {
                if (xaAction.equals("commit")) {
                    try {
                        xaresource.commit(recoveredXid, false);
                    } catch (XAException e) {
                        TraceTm.recovery.error("Unable to commit Xid during Admin Recovery " + e.getMessage());
                    }
                } else if (xaAction.equals("rollback")) {
                    try {
                        xaresource.rollback(recoveredXid);
                    } catch (XAException e) {
                        TraceTm.recovery.error("Unable to rollback Xid during Admin Recovery " + e.getMessage());
                    }
                } else if (xaAction.equals("forget")) {
                    try {
                        xaresource.forget(recoveredXid);
                    } catch (XAException e) {
                        TraceTm.recovery.error("Unable to forget Xid during Admin Recovery " + e.getMessage());
                    }
                }
                break;
            }
        }

        cleanuptxrecovery(sFullXid);
        return 0;
    }

    private void cleanuptxrecovery(String pFullXid) {

        // We can now remove the TxXidRecovered array entry associated with the
        // Xid and decrement the count in its respective TxRecovered vector entry.
        // If the count in the TxRecovered vector entry is zero, we can then remove
        // the TxRecovered entry from the vector.

        TxRecovered txr;
        TxxidRecovered infoxid;
        int myxacount;
        boolean mytxxidrecoveredfound = false;

        byte [] [] jotmDoneRecord = new byte [1] [11];
        byte [] jotmDone = "RR3JOTMDONE".getBytes();

        Vector vTxRecovered = JotmRecovery.getTxRecovered();
        int txCount = vTxRecovered.size();

        for (int i = 0; i < txCount; i++) {
            txr = (TxRecovered) vTxRecovered.elementAt(i);
            myxacount = txr.getxidcount();

            for (int j = 0; j < myxacount; j++) {
                infoxid = txr.getRecoverTxXidInfo(j);

                if (infoxid != null) {

                    if (pFullXid.equals(new String(infoxid.getRecoverxid()))) {
                        infoxid.setRecoverstatus(Status.STATUS_COMMITTED);
                        mytxxidrecoveredfound = true;
                        break;
                    }
                }
            }

            boolean allcompleted = true;

            for (int j = 0; j < myxacount; j++) {
                infoxid = txr.getRecoverTxXidInfo(j);
                if (infoxid.getRecoverstatus() != Status.STATUS_COMMITTED) {
                    allcompleted = false;
                    break;
                }
            }

            if (allcompleted) {
                XACommittingTx xaCommitTx = txr.getXACommittingTx();
                jotmDoneRecord [0] = jotmDone;

                if (Current.getDefaultRecovery()) {
                    try {
                        if (TraceTm.recovery.isDebugEnabled()) {
                            TraceTm.recovery.debug("Done howl log, after admin action");
                        }

                        TransactionRecoveryImpl.getTransactionRecovery().howlDoneLog(jotmDoneRecord, xaCommitTx);
                    } catch (Exception f) {
                        String howlerror =
                            "Cannot howlDoneLog:"
                            + f
                            + "--"
                            + f.getMessage();
                        TraceTm.jotm.error("Got LogException from howlDoneLog: "+ howlerror);
                    }
                }

                vTxRecovered.remove(i);
                break;
            }

            if (mytxxidrecoveredfound) {
                break;
            }
        }
    }

    /**
     * Clear transaction from this thread if not known.
     * Useful when another thread completes the current thread's transaction
     */
    public void clearThreadTx() {

        TransactionImpl tx = threadTx.get();

        if (tx != null) {
            threadTx.set(null);
            if (TraceTm.jta.isDebugEnabled()) {
                TraceTm.jta.debug("threadTx.set=null");
            }
        }
    }

    // ------------------------------------------------------------------
    // private methods
    // ------------------------------------------------------------------

    /**
     * Actually rollback old transaction marked rollback_only
     * This fix bug 312360 (partly)
     * @param tx transaction to check
     * @return true if transaction rollback occured
     * @throws SystemException error while rolling back transaction
     */
    private boolean txcanrollback(TransactionImpl tx) throws SystemException {
        switch (tx.getStatus()) {
        case Status.STATUS_MARKED_ROLLBACK:
            tx.rollback();
            return true;
        default:
            return false;
        }
    }

    /*
     * put the Tx/Xid mapping into the hashtable.
     */
    private synchronized void putTxXid(Xid xid, TransactionImpl tx) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("Associate tx to xid (xid=" + xid + ") tx =" + tx);
        }
        txXids.put(xid, tx);
    }

    /*
     * given the Xid, get the corresponding Tx from the hashtable.
     */
    private synchronized TransactionImpl getTxXid(Xid xid) {
        TransactionImpl tx = (TransactionImpl) txXids.get(xid);
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("get tx from xid (xid="+ xid +") tx =" + tx);
        }
        return tx;
    }

    /*
     * removeTxXid method.
     */
    private synchronized void removeTxXid(Xid xid) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("remove tx from xid (xid="+ xid +")");
        }
        txXids.remove(xid);
    }

    /**
     * remove the Transaction from the ThreadLocal
     */
    void forget() {
        threadTx.set(null);
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("threadTx.set= null");
        }
    }

    /* Management methods */

    /**
     * Returns the current number of transactions.
     *
     * @return current number of transaction
     */
    public synchronized int getTotalCurrentTransactions() {
        return txXids.size();
    }

    /**
     * Increments number of begun transactions by one.
     */
    synchronized void incrementBeginCounter() {
        nb_bg_tx++;
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("nb_bg_tx="+nb_bg_tx);
        }
    }

    /**
     * Returns the total number of begun transactions.
     * @return total number of begun transactions
     */
    public int getTotalBegunTransactions() {
        return nb_bg_tx;
    }

    /**
     * Increments the number of rolled back transaction by one.
     */
    synchronized void incrementRollbackCounter() {
        nb_rb_tx++;
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("nb_rb_tx="+nb_rb_tx);
        }
    }

    /**
     * Returns the total number of rolled back transactions.
     * @return total number of rolled back transactions
     */
    public int getTotalRolledbackTransactions() {
        return nb_rb_tx;
    }

    /**
     * Increments the number of of committed transactions by one.
     */
    synchronized void incrementCommitCounter() {
        nb_cm_tx++;
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("nb_cm_tx="+nb_cm_tx);
        }
    }

    /**
     * Returns the total number of committed transactions.
     *
     * @return total number of commited transactions
     */
    public int getTotalCommittedTransactions() {
        return nb_cm_tx;
    }

    /**
     * Resets total number of transactions.
     */
    public synchronized void resetAllTxTotalCounters() {
        nb_bg_tx = 0;
        nb_cm_tx = 0;
        nb_rb_tx = 0;
        nb_to = 0;
    }

    /**
     * Increments number of rolled back transactions due to timeout by one.
     */
    synchronized void incrementExpiredCounter() {
        nb_to++;
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("nb_to="+nb_to);
        }
    }

    /**
     * Returns the total number of  rolled back transactions due to timeout.
     * @return number of rolled back transactions due to timeout
     */
    public int getTotalExpiredTransactions() {
        return nb_to;
    }

    /**
     * Returns all counters.
     * @return an array of all counters (current tx, begun tx, committed tx,
     * rolled back tx, timeouted tx)
     */
    public synchronized Integer[] getTransactionCounters() {
        Integer[] result = new Integer[5];
        result[0] = txXids.size();
        result[1] = nb_bg_tx;
        result[2] = nb_cm_tx;
        result[3] = nb_rb_tx;
        result[4] = nb_to;
        return result;
    }

}
