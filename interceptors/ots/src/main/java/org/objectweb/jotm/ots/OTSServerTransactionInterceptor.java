/**
 * @(#) OTSServerTransactionInterceptor.java 1.0 02/07/15
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
 * $Id: OTSServerTransactionInterceptor.java 1137 2010-02-11 08:07:09Z durieuxp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm.ots;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.jotm.Current;
import org.objectweb.jotm.TraceTm;
import org.objectweb.jotm.InternalTransactionContext;
import org.objectweb.jotm.TransactionContext;
import org.omg.IOP.ServiceContext;
import org.omg.PortableInterceptor.ForwardRequest;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ServerRequestInfo;
import org.omg.PortableInterceptor.ServerRequestInterceptor;

/**
 * Class <code>OTSServerTransactionInterceptor</code> is a Server Interceptor for OTS Java Server
 * of JOTM. This Interceptor translate the Standart OTS Propagation Context to a Internal JOTM
 * Transaction context
 *
 * @author  Guillaume Riviere (Guillaume.Riviere@inrialpes.fr)
 * @version 1.0, 13/09/2002
 */
public class OTSServerTransactionInterceptor extends OTSInterceptor implements ServerRequestInterceptor {

    /**
     * Current object.
     */
    private static Current current = null;

    /**
     * Transaction context.
     */
    private Map contexts = Collections.synchronizedMap(new HashMap());
    // private TransactionContext txCtx = null;

    /**
     * Interceptor name.
     */
    private static final String NAME = "OTSServerTransactionInteceptor";

    /**
     * Constructor.
     * @param info ORB Configuration object
     */
    public OTSServerTransactionInterceptor(final ORBInitInfo info) {
        super(info);
    }

    /**
     * Return the name of this interceptor.
     * @return the name of this interceptor
     */
    public String name() {
        return NAME;
    }

    /**
     * Receive request context.
     * @param jri {@link ServerRequestInfo} the server request information
     * @throws ForwardRequest if an exception occur with the ObjectOutput
     */
    public void receive_request_service_contexts(final ServerRequestInfo jri)
        throws ForwardRequest {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("SRI="+jri.request_id()+":"+jri.operation());
        }
        try {
            ServiceContext serviceContext = jri.get_request_service_context(TX_CTX_ID);
            TransactionContext txCtx = decodeCorbaPropagationContext(serviceContext);
            if (txCtx != null) {
                Integer key = new Integer(jri.request_id());
                contexts.put(key, txCtx);
            }
        } catch (org.omg.CORBA.BAD_PARAM b) {
            // else we do nothing -> no transaction context for this call
            TraceTm.jta.debug("SRI="+jri.request_id()+" no Tx Ctx");
        } catch (Exception e) {
            TraceTm.jta.debug("SRI="+jri.request_id()+e);
            throw new ForwardRequest();
        }
    }

    /**
     * Receive request.
     * @param jri {@link ServerRequestInfo} the server request information
     * @throws ForwardRequest if an exception occur with the ObjectOutput
     */
    public void receive_request(final ServerRequestInfo jri) throws ForwardRequest {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("SRI="+jri.request_id()+":"+jri.operation());
        }
        if (current == null) {
            current = Current.getCurrent();
        }

        if (current != null) {
            Object key = new Integer(jri.request_id());
            InternalTransactionContext txCtx = null;
            txCtx = (InternalTransactionContext) contexts.remove(key);

            // put into the the Current object (false for the server side context)
            current.setPropagationContext(txCtx, false);
        }
    }

    /**
     * Send reply with context.
     * @param jri {@link ServerRequestInfo} the server request information
     */
    public void send_reply(final ServerRequestInfo jri)  {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("SRI="+jri.request_id()+":"+jri.operation());
        }

        if (current == null) {
            current = Current.getCurrent();
        }

        if (current != null) {
            try {
                // get the Transaction Context (null if there is no transaction)
                TransactionContext txCtx = current.getPropagationContext(false);
                ServiceContext pContext = null;

                if (txCtx != null) {
                    // get the TransactionContext and build the Corba PropagtionContext
                    pContext = buildCorbaPropagationContext(txCtx);
                    jri.add_reply_service_context(pContext, true);
                    current.setPropagationContext(null, false);
                } else {
                  // if no active global transaction, the container does not include a tx context
                  // in the request message
                }
            } catch (Exception e) {
                TraceTm.jta.debug("SRI="+jri.request_id()+":"+e);
            }
        }
    }

    /**
     * Send an Exception.
     * @param jri {@link ServerRequestInfo} the server request information
     * @throws ForwardRequest
     */
    public void send_exception(final ServerRequestInfo jri) throws ForwardRequest {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("SRI="+jri.request_id()+":"+jri.operation());
        }
        if (current == null) {
            current = Current.getCurrent();
        }

        if (current != null) {
            try {
                // get the Transaction Context (null if there is no transaction)
                TransactionContext txCtx = current.getPropagationContext(false);
                ServiceContext pContext = null;

                if (txCtx != null) {

                    // get the TransactionContext and build the Corba PropagtionContext
                    pContext = buildCorbaPropagationContext(txCtx);
                    jri.add_reply_service_context(pContext, true);
                    current.setPropagationContext(null, false);
                } else {

                    // if no active global transaction, the container does not include a tx context
                    // in the request message
                }
            } catch (Exception e) {
                TraceTm.jta.debug("SRI="+jri.request_id()+":"+e);
            }
        }
    }

    /**
     * Do nothing.
     * {@inheritDoc}
     */
    public void send_other(final ServerRequestInfo jri) throws ForwardRequest {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("SRI="+jri.request_id()+":"+jri.operation());
        }
    }

    /**
     * Have nothing to release ...
     */
    public void destroy() {
        TraceTm.jta.debug("");
    }

}
