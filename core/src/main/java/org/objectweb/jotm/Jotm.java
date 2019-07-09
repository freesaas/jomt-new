/*
 * @(#) Jotm.java
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
 * $Id: Jotm.java,v 1.6 2003/12/05 18:14:44 trentshue Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import org.ow2.carol.util.configuration.ConfigurationRepository;
import org.ow2.carol.util.configuration.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *  This  class represents an instance of JOTM.
 *
 * @author jeff mesnil
*/
public class Jotm {

    /**
     * Current instance
     */
    Current current = null;

    /**
     * TransactionRecovery instance
     */
    TransactionRecovery transactionrecovery = null;

    /**
     * TransactionSynchronizationRegistry instance
     */
    TransactionSynchronizationRegistryImpl transactionsynchronizationregistry = null;

    /**
     * JNDI name of the TransactionFactory
     */
    private static final String TMFACTORY_NAME = "TMFactory";

    /**
     * TransactionFactory
     */
    private TransactionFactory tf = null;

    /**
     * is the TransactionFactory is local or not
     */
    private boolean local;

    /**
     * is the TransactionFacotory bound to JNDI or not
     */
    private boolean bound;

    /**
     * has binding of TransactionFactory fails
     */
    private boolean boundFailed = false;

    /**
     * Logger
     */
    public static final Log log = LogFactory.getLog("org.objectweb.jotm");

    /**
     * Public constructor for Jotm.
     *
     * If Jotm is created with a <code>local</code> transaction factory which is
     * not <code>bound</code> to a registry, Jotm would be able to manage
     * transactions only inside the same JVM.
     * @param local <code>true</code> to create an instance of JOTM with a
     * local transaction factory, <code>false</code> else
     * @param bound <code>true</code> if the transaction factory is to be
     *  bound in a registry, <code>false</code> else (ignored if
     * <code>local<code> is <code>false</code>)
     *
     * @throws NamingException thrown if the transaction factory can't be bound
     * or looked up in a registry
     */
    public Jotm(boolean local, boolean bound) throws NamingException {
        this.local = local;
        this.bound = bound;

        // CAROL initialization
        log.info("CAROL initialization");
        try {
            ConfigurationRepository.init();
        } catch (ConfigurationException e) {
            log.error("CAROL initialization failed", e);
        }

        if (local) {
            log.info("JOTM started with a local transaction factory");
            try {
                tf = new TransactionFactoryImpl();
            } catch (RemoteException e) {
                // should not happen: TransactionFactoryImpl throws
                // RemoteException only because it extends Remote interface
                log.error("Instanciation of TransactionFactory failed", e);
            }

            if (bound) {
                Context ictx;
                try {
                	Hashtable env = new Hashtable();
                	env.put(Context.INITIAL_CONTEXT_FACTORY, "org.ow2.carol.jndi.spi.MultiOrbInitialContextFactory");

                    ictx = new InitialContext(env);
                    ictx.rebind(TMFACTORY_NAME, tf);
                    log.info("TransactionFactory bound with name " + TMFACTORY_NAME);
                } catch (NamingException e) {
                    log.error("TransactionFactory rebind failed", e);
                    boundFailed = true;
                    throw e;
                }
            }
        } else {
            log.info("JOTM started with a remote transaction factory");
            Context ictx;

            try {
                ictx = new InitialContext();
                tf = (TransactionFactory) ictx.lookup(TMFACTORY_NAME);
            } catch (NamingException e) {
                log.error("TransactionFactory lookup failed", e);
                throw e;
            }
        }

        try {
            // Uses the constructor dedicated to a Server, with the TransactionFactory
            // passed as argument.
            current = new Current(tf);
            transactionsynchronizationregistry = TransactionSynchronizationRegistryImpl.getInstance();
        } catch (Exception e) {
            log.error("cannot init Current", e);
        }

    }

    public TransactionManager getTransactionManager() {
        return Current.getTransactionManager();
    }

    public UserTransaction getUserTransaction() {
        return Current.getUserTransaction();
    }

    /**
     */
    public TransactionSynchronizationRegistryImpl getTransactionSynchronizationRegistry() {
        if (log.isDebugEnabled()) {
            log.debug("TransactionSynchronizationRegistry=" + transactionsynchronizationregistry);
        }
        return transactionsynchronizationregistry;
    }

    /**
     */
    public void stop() {
        log.info("stop JOTM");
        // remove current transaction if there's still one in ThreadLocal

        try {
            current.forget();
        } catch (Exception e) {
            log.warn("cannot stop Current", e);
        }

        try {
            if (transactionrecovery != null) {
                transactionrecovery.forget();
            }
        } catch (Exception e) {
            log.warn("cannot stop Recovery", e);
        }

        // unexport remote objects
        if (local) {
            if (bound && !boundFailed) {
                try {
                    InitialContext ictx = new InitialContext();
                    ictx.unbind(TMFACTORY_NAME);

                    if (log.isDebugEnabled()) {
                        log.debug("TransactionFactory unbound");
                    }
                } catch (Exception e) {
                    log.warn("Cannot unbound the TransactionFactory", e);
                }
            }

            if (tf != null) {
                try {
                    PortableRemoteObject.unexportObject(tf);

                    if (log.isDebugEnabled()) {
                        log.debug("TransactionFactory unexported");
                    }
                } catch (NoSuchObjectException e) {
                    log.warn("Cannot unexport the TransactionFactory", e);
                }
            }
        }
        tf = null;
    }
}
