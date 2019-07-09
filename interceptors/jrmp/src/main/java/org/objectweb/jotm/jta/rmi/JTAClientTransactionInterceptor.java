/*
 * @(#) JTAClientTransactionInterceptor
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
 * $Id: JTAClientTransactionInterceptor.java 1137 2010-02-11 08:07:09Z durieuxp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm.jta.rmi;

// java import
import java.io.IOException;

import org.objectweb.jotm.Current;
import org.objectweb.jotm.TraceTm;
import org.objectweb.jotm.TransactionContext;
import org.ow2.carol.rmi.interceptor.api.JClientRequestInfo;
import org.ow2.carol.rmi.interceptor.spi.JClientRequestInterceptor;

/**
 * Class <code>JTAClientTransactionInterceptor</code> is a JRMP Transaction client interceptor for
 * Transaction Context propagation
 *
 * @author  Guillaume Riviere (Guillaume.Riviere@inrialpes.fr)
 */
public class JTAClientTransactionInterceptor
    implements JClientRequestInterceptor {

    // Commented out all the tracing in this module, generated
    // warnings when integrated with JONAS at JONAS START.
    // Need to resolve whenever JONAS uses log4j.
    // private static Log log =
    //     LogFactory.getLog("org.objectweb.jotm.jta.rmi.client");

    /**
     *
     */
    private static final long serialVersionUID = 22772127590L;

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
    private static String interceptorName = "JTAClientTransactionInterceptor";

    /**
     * constructor
     */
    public JTAClientTransactionInterceptor() {
    }

    /**
     * get the name of this interceptor
     * @return name
     */
    public String name() {
        return interceptorName;
    }

    /**
     * send client context with the request. The sendingRequest method of the JPortableInterceptors
     * is called prior to marshalling arguments and contexts
     * @param jri  JClientRequestInfo the jrmp client info
     * @exception IOException if an exception occur with the ObjectOutput
     */
    public void sendRequest(JClientRequestInfo jri) throws IOException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAClientTransactionInterceptor.sendRequest");
        }
        try {
            if (current == null) {
                current = Current.getCurrent();
            }
            if (current != null) {
                // get the Transaction Context (null if there is no transaction)
                TransactionContext txCtx = current.getPropagationContext(true);
                if (txCtx != null) {
                    JTATransactionServiceContext jtasc =
                        new JTATransactionServiceContext();
                    jtasc.setContext(txCtx, false);
                    jri.addRequestServiceContext(jtasc);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Receive reply interception
     * @param jri JClientRequestInfo the jrmp client info
     * @exception IOException if an exception occur with the ObjectOutput
     */
    public void receiveReply(JClientRequestInfo jri) throws IOException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAClientTransactionInterceptor.receiveReply");
        }
        if (current == null) {
            current = Current.getCurrent();
        }
        if (current != null) {
            JTATransactionServiceContext jtasc =
                (JTATransactionServiceContext) jri.getReplyServiceContext(
                    TX_CTX_ID);
            if (jtasc != null) {
                // put into the the Current object (true for client side context
                current.setPropagationContext(
                    jtasc.getTransactionContext(),
                    true);
            }
        }
    }

    // empty method
    public void sendPoll(JClientRequestInfo jri) throws IOException {
    }

    public void receiveException(JClientRequestInfo jri) throws IOException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAClientTransactionInterceptor.receiveException");
        }
        if (current == null) {
            current = Current.getCurrent();
        }
        if (current != null) {
            JTATransactionServiceContext jtasc =
                (JTATransactionServiceContext) jri.getReplyServiceContext(
                    TX_CTX_ID);
            if (jtasc != null) {
                // put into the the Current object (true for client side context
                current.setPropagationContext(
                    jtasc.getTransactionContext(),
                    true);
            }
        }
    }

    public void receiveOther(JClientRequestInfo jri) throws IOException {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAClientTransactionInterceptor.receiveOther");
        }
        if (current == null) {
            current = Current.getCurrent();
        }
        if (current != null) {
            JTATransactionServiceContext jtasc =
                (JTATransactionServiceContext) jri.getReplyServiceContext(
                    TX_CTX_ID);
            if (jtasc != null) {
                // put into the the Current object (true for client side context
                current.setPropagationContext(
                    jtasc.getTransactionContext(),
                    true);
            }
        }
    }
}
