/**
 * @(#) OTSClientTransactionInterceptor.java 1.0 02/07/15
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
 * $Id: OTSClientTransactionInterceptor.java 1137 2010-02-11 08:07:09Z durieuxp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm.ots;

import org.objectweb.jotm.Current;
import org.objectweb.jotm.TraceTm;
import org.objectweb.jotm.TransactionContext;
import org.omg.IOP.ServiceContext;
import org.omg.PortableInterceptor.ClientRequestInfo;
import org.omg.PortableInterceptor.ClientRequestInterceptor;
import org.omg.PortableInterceptor.ForwardRequest;
import org.omg.PortableInterceptor.ORBInitInfo;

/**
 * Class <code>OTSClientTransactionInterceptor</code> is a Client Interceptor for OTS Java Client
 * of JOTM. This Interceptor translate the Standart OTS Propagation Context to a Internal JOTM
 * Transaction context
 *
 * @author  Guillaume Riviere (Guillaume.Riviere@inrialpes.fr)
 */
public class OTSClientTransactionInterceptor
    extends OTSInterceptor implements ClientRequestInterceptor {

    /**
     * Current object.
     */
    private static Current current = null;

    /**
     * Interceptor name.
     */
    private static final String NAME = "OTSClientTransactionInteceptor";

    /**
     * Constructor.
     * @param info ORB Configuration object
     */
    public OTSClientTransactionInterceptor(final ORBInitInfo info) {
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
     * Nothing to release ...
     */
    public void destroy() {
    }

    /**
     * Send client transaction context with the request, if existed.
     *
     * @param jri {@link ClientRequestInfo} iiop client info
     * @throws ForwardRequest if an exception occurred with the ObjectOutput
     */
    public void send_request(final ClientRequestInfo jri) throws ForwardRequest {

        if (current == null) {
            current = Current.getCurrent();
        }
        if (current != null) {
            try {
                // get the Transaction Context (null if there is no transaction)
                TransactionContext txCtx = current.getPropagationContext(true);
                ServiceContext pContext = null;

                if (txCtx != null) {

                    // get the TransactionContext and build the Corba PropagtionContext
                    pContext = buildCorbaPropagationContext(txCtx);
                    jri.add_request_service_context(pContext, true);
                } else {

                // if no active global transaction, the container does not include a tx context
                // in the request message
                }

            } catch (Exception e) {
                throw new ForwardRequest();
            }
        }
    }


    /**
     * Receive reply interception.
     * @param jri {@link ClientRequestInfo} jri client info
     */
    public void receive_reply(final ClientRequestInfo jri) {

        if (current == null) {
            current = Current.getCurrent();
        }

        if (current != null) {

            try {
                TransactionContext txCtx = decodeCorbaPropagationContext(jri.get_reply_service_context(TX_CTX_ID)) ;

                if (txCtx != null) {

                    // put into the the Current object (true for client side context)
                    current.setPropagationContext(txCtx, true);
                }
            } catch (org.omg.CORBA.BAD_PARAM b) {
                // else we do nothing -> no transaction context for this call
                TraceTm.jta.debug("SRI="+jri.request_id()+": no transaction context for this call");
            } catch (Exception e) {
                TraceTm.jta.debug("SRI="+jri.request_id()+":"+e);
            }
        }
    }

    /**
     * Unused for OTS.
     * {@inheritDoc}
     */
    public void send_poll(final ClientRequestInfo jri) {
    }

    /**
     * Receive Exception on the client.
     * @param jri {@link ClientRequestInfo} jri client info
     */
    public void receive_exception(final ClientRequestInfo jri) throws ForwardRequest {
        if (current == null) {
            current = Current.getCurrent();
        }

        if (current != null) {
            try {
                ServiceContext serviceContext = jri.get_reply_service_context(TX_CTX_ID);
                TransactionContext txCtx = decodeCorbaPropagationContext(serviceContext);

                if (txCtx != null) {
                    // put into the the Current object (true for client side context)
                    current.setPropagationContext(txCtx, true);
                }
            } catch (org.omg.CORBA.BAD_PARAM b) {
                // else we do nothing -> no transaction context for this call
                TraceTm.jta.debug("SRI="+jri.request_id()+": no transaction context for this call");
            } catch (Exception e) {
                TraceTm.jta.debug("SRI="+jri.request_id()+":"+e);
            }
        }
    }

    /**
     * Unused for OTS.
     * {@inheritDoc}
     */
    public void receive_other(final ClientRequestInfo jri) throws ForwardRequest {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("SRI="+jri.request_id()+":"+jri.operation());
        }
    }

}
