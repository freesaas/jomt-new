/*
 * @(#) TransactionSynchronizationRegistryImpl.java	1.2 02/01/15
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
 * 06/08/05 Tony Ortiz
 *
 * --------------------------------------------------------------------------
 * $Id: TransactionSynchronizationRegistryImpl.java,v 1.2 2006-09-07 00:18:06 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import static org.objectweb.jotm.Current.getTransactionManager;

import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.Synchronization;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.NamingException;

public class TransactionSynchronizationRegistryImpl implements Referenceable,
                                                               TransactionSynchronizationRegistry {

	private static TransactionSynchronizationRegistryImpl instanceTSR = new TransactionSynchronizationRegistryImpl();

	/**
	 * Return an opaque object to represent the transaction bound to the current
	 * thread at the time this method is called. The returned object overrides
	 * <code>hashCode</code> and <code>equals</code> methods to allow its
	 * use as the key in a <code>java.util.HashMap</code> for use by the
	 * caller. If there is no transaction currently active, null is returned.
	 *
	 * <P>
	 * The returned object will return the same <code>hashCode</code> and
	 * compare equal to all other objects returned by calling this method from
	 * any component executing in the same transaction context in the same
	 * application server.
	 *
	 * <P>
	 * The <code>toString</code> method returns a <code>String</code> that
	 * might be usable by a human reader to usefully understand the transaction
	 * context. The result of the <code>toString</code> method is otherwise
	 * not defined. Specifically, there is no forward or backward compatibility
	 * guarantee for the result returned by the <code>toString</code> method.
	 *
	 * <P>
	 * The object is not necessarily serializable, and is not useful outside the
	 * virtual machine from which it was obtained.
     * @return the TSR
	 */
	public static TransactionSynchronizationRegistryImpl getInstance() {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("getInstance");
        }
		return instanceTSR;
	}

	/**
	 * @return An object representing the current transaction, or null if no
	 *         transaction is active.
	 */
	public Object getTransactionKey() {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("getTransactionKey");
        }

		try {
			return getTransactionManager().getTransaction();
		} catch (SystemException ex) {
			return null;
		}
	}

	/**
	 * Add an object to the map of resources being managed for the current
	 * transaction. The supplied key must be of a caller- defined class so as
	 * not to conflict with other users. The class of the key must guarantee
	 * that the <code>hashCode</code> and <code>equals</code> methods are
	 * suitable for keys in a map. The key and value are not examined or used by
	 * the implementation.
	 *
	 * @param key
	 *            The key for looking up the associated value object.
	 *
	 * @param value
	 *            The value object to keep track of.
	 *
	 * @exception IllegalStateException
	 *                Thrown if the current thread is not associated with a
	 *                transaction.
	 */
	public void putResource(Object key, Object value) {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("putResource");
        }

        if (key == null) {
        	throw new NullPointerException("key is null");
        }

		try {
			TransactionImpl tran = (TransactionImpl) getTransactionManager().getTransaction();

			if (tran == null) {
				throw new IllegalStateException("Cannot find Transaction for putResource");
			}

			tran.putUserResource(key, value);
		} catch (SystemException ex) {
			throw new IllegalStateException("Cannot getTransaction");
		}
	}

	/**
	 * Get an object from the map of resources being managed for the current
	 * transaction. The key must have been supplied earlier by a call to
	 * <code>putResouce</code> in the same transaction. If the key cannot be
	 * found in the current resource map, null is returned.
	 * @param key
	 *            The key for looking up the associated value object.
	 *
	 * @return The value object, or null if not found.
	 *
	 * @exception IllegalStateException
	 *                Thrown if the current thread is not associated with a
	 *                transaction.
	 */
	public Object getResource(Object key) {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("getResource");
        }
        if (key == null) {
        	throw new NullPointerException("key is null");
        }

		try {
			TransactionImpl tran = (TransactionImpl) getTransactionManager().getTransaction();

			if (tran == null) {
				throw new IllegalStateException("Cannot find Transaction for getResource");
			}
			return tran.getUserResource(key);
		} catch (SystemException ex) {
			throw new IllegalStateException("Cannot getTransaction");
		}
	}

	/**
	 * Register a <code>Synchronization</code> instance with special ordering
	 * semantics. The <code>beforeCompletion</code> method on the registered
	 * <code>Synchronization</code> will be called after all user and system
	 * component <code>beforeCompletion</code> callbacks, but before the
	 * 2-phase commit process starts. This allows user and system components to
	 * flush state changes to the caching manager, during their
	 * <code>SessionSynchronization</code> callbacks, and allows managers to
	 * flush state changes to Connectors, during the callbacks registered with
	 * this method. Similarly, the <code>afterCompletion</code> callback will
	 * be called after 2-phase commit completes but before any user and system
	 * <code>afterCompletion</code> callbacks.
	 *
	 * <P>
	 * The <code>beforeCompletion</code> callback will be invoked in the
	 * transaction context of the current transaction bound to the thread of the
	 * caller of this method, which is the same transaction context active at
	 * the time this method is called. Allowable methods include access to
	 * resources, for example, Connectors. No access is allowed to user
	 * components, for example, timer services or bean methods, as these might
	 * change the state of POJOs, or plain old Java objects, being managed by
	 * the caching manager.
	 *
	 * <P>
	 * The <code>afterCompletion</code> callback will be invoked in an
	 * undefined transaction context. No access is permitted to resources or
	 * user components as defined above. Resources can be closed, but no
	 * transactional work can be performed with them.
	 *
	 * <P>
	 * Other than the transaction context, no component J2EE context is active
	 * during either of the callbacks.
	 *
	 * @param sync
	 *            The synchronization callback object.
	 *
	 * @exception IllegalStateException
	 *                Thrown if the current thread is not associated with a
	 *                transaction.
	 */
	public void registerInterposedSynchronization(Synchronization sync) throws IllegalStateException {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("registerInterposedSynchronization");
        }

		try {
			TransactionImpl tx = (TransactionImpl) getTransactionManager().getTransaction();

			if (tx == null) {
				throw new IllegalStateException("Cannot find Transaction for registerInterposedSynchronization");
			}
			tx.registerInterposedSynchronization(sync);
		} catch (SystemException ex) {
			throw new IllegalStateException("Cannot getTransaction");
		}
	}

	/**
	 * Returns the status of the transaction bound to the current thread. This
	 * is the result of executing <code>getStatus</code> method on the
	 * <code>TransactionManager</code>, in the current transaction context.
	 *
	 * @return The status of the current transaction.
	 */
	public int getTransactionStatus() {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("getTransactionStatus");
        }

		try {
			int mystatus = getTransactionManager().getStatus();

	        if (TraceTm.jta.isDebugEnabled()) {
	            TraceTm.jta.debug("mystatus=" + mystatus);
	        }

			return mystatus;
		} catch (SystemException ex) {
			return Status.STATUS_NO_TRANSACTION;
		}
	}

	/**
	 * Set the <code>rollbackOnly</code> status of the transaction bound to
	 * the current thread.
	 * @exception IllegalStateException
	 *    Thrown if the current thread is not associated with a transaction.
	 */
	public void setRollbackOnly() {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("setRollbackOnly");
        }

		try {
			getTransactionManager().setRollbackOnly();
		} catch (SystemException ex) {
			throw new IllegalStateException("Cannot setRollbackOnly");
		}
	}

	/**
	 * Get the <code>rollbackOnly</code> status of the transaction bound to
	 * the current thread.
	 * @return true, if the current transaction is marked for rollback only.
	 * @exception IllegalStateException
	 *   Thrown if the current thread is not associated with a transaction.
	 */
	public boolean getRollbackOnly() {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("getRollbackOnly");
        }

		int status = getTransactionStatus();

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("status=" + status);
        }

		if (status == Status.STATUS_NO_TRANSACTION) {
			throw new IllegalStateException("Cannot getTransactionStatus");
		}

		return ((status == Status.STATUS_MARKED_ROLLBACK) || (status == Status.STATUS_ROLLING_BACK));
	}

    // ------------------------------------------------------------------
    // Referenceable implementation
    // ------------------------------------------------------------------

    /**
     * Retrieves the <code>Reference</code> of this object.
     * @return  The non-null <code>Reference</code> of this object.
     * @exception javax.naming.NamingException  If a naming exception was encountered while retrieving the reference.
     */
    public Reference getReference() throws NamingException {

        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("TSRImpl.getReference()");
        }
        return new Reference(this.getClass().getName(),
                             "org.objectweb.jotm.TransactionSynchronizationRegistryFactory",
                             null);
    }

}
