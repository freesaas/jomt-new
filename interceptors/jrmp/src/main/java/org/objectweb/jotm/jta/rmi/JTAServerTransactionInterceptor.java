/*
 * @(#) JTAServerTransactionInterceptor
 *
 * JOTM: Java Open Transaction Manager
 *
 * This module was originally developed by
 *  - INRIA inside the ObjectWeb Consortium(http://www.objectweb.org)
 *
 * The original code and portions created by INRIA are
 * Copyright (C) 2002 - INRIA (www.inria.fr)
 * All rights reserved.
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
 * $Id: JTAServerTransactionInterceptor.java 1137 2010-02-11 08:07:09Z durieuxp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm.jta.rmi;

import java.io.IOException;

import org.objectweb.jotm.Current;
import org.objectweb.jotm.TraceTm;
import org.objectweb.jotm.TransactionContext;
import org.ow2.carol.rmi.interceptor.api.JServerRequestInfo;
import org.ow2.carol.rmi.interceptor.spi.JServerRequestInterceptor;

/**
 * Class <code>JTAServerTransactionInterceptor</code> is a JRMP Transaction server interceptor for
 * Transaction Context propagation
 *
 * @author  Guillaume Riviere (Guillaume.Riviere@inrialpes.fr)
 *
 */
public class JTAServerTransactionInterceptor
    implements JServerRequestInterceptor {

    // Commented out all the tracing in this module, generated
    // warnings when integrated with JONAS at JONAS START.
    // Need to resolve whenever JONAS uses log4j.
    // private static Log log =
    //     LogFactory.getLog("org.objectweb.jotm.jta.rmi.server");

    /**
     * transaction context id
     */
    public static int TX_CTX_ID = 0;

    /**
     * current object
     */
    private static Current current = null;

    /**
     * interceptor name
     */
    private static String interceptorName = "JTAServerTransactionInterceptor";

    /**
     * constructor
     */
    public JTAServerTransactionInterceptor() {
    }

    /**
     * Receive request
     * @param jri JServerRequestInfo the jrmp server request information
     * @exception IOException if an exception occur with the ObjectOutput
     */
    public void receiveRequest(JServerRequestInfo jri) throws IOException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAServerTransactionInterceptor.receive_request");
        }
        // log.trace("--> receive request");
        if (current == null) {
            current = Current.getCurrent();
        }
        if (current != null) {
            JTATransactionServiceContext jtasc =
                (JTATransactionServiceContext) jri.getRequestServiceContext(
                    TX_CTX_ID);
            if (jtasc != null) {
                // put into the the Current object (true for client side context
                current.setPropagationContext(
                    jtasc.getTransactionContext(),
                    false);
            }
        }
    }

    /**
     * send reply with context
     * @param jri JServerRequestInfo the jrmp server request information
     * @exception IOException if an exception occur with the ObjectOutput
     */
    public void sendReply(JServerRequestInfo jri) throws IOException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAServerTransactionInterceptor.send_reply");
        }
        if (current == null) {
            current = Current.getCurrent();
        }
        if (current != null) {
            // get the Transaction Context (null if there is no transaction)
            TransactionContext txCtx = current.getPropagationContext(false);
            if (txCtx != null) {
                JTATransactionServiceContext jtasc =
                    new JTATransactionServiceContext();
                jtasc.setContext(txCtx, true);
                jri.addReplyServiceContext(jtasc);
                current.setPropagationContext(null, false);
            }
        }
    }

    /**
    * get the name of this interceptor
    * @return name
    */
    public String name() {
        return interceptorName;
    }

    public void sendException(JServerRequestInfo jri) throws IOException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAServerTransactionInterceptor.sendException");
        }
        if (current == null) {
            current = Current.getCurrent();
        }
        if (current != null) {
            // get the Transaction Context (null if there is no transaction)
            TransactionContext txCtx = current.getPropagationContext(false);
            if (txCtx != null) {
                JTATransactionServiceContext jtasc =
                    new JTATransactionServiceContext();
                jtasc.setContext(txCtx, true);
                jri.addReplyServiceContext(jtasc);
                current.setPropagationContext(null, false);
            }
        }
    }

    public void sendOther(JServerRequestInfo jri) throws IOException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAServerTransactionInterceptor.sendOther");
        }
        if (current == null) {
            current = Current.getCurrent();
        }
        if (current != null) {
            // get the Transaction Context (null if there is no transaction)
            TransactionContext txCtx = current.getPropagationContext(false);
            if (txCtx != null) {
                JTATransactionServiceContext jtasc =
                    new JTATransactionServiceContext();
                jtasc.setContext(txCtx, true);
                jri.addReplyServiceContext(jtasc);
                current.setPropagationContext(null, false);
            }
        }
        // log.trace("<-- sent other");
    }
}
