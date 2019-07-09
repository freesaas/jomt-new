/**
 * @(#) OTSInterceptor.java 1.0 02/07/15
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
 * $Id: OTSInterceptor.java 1137 2010-02-11 08:07:09Z durieuxp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm.ots;

import javax.rmi.PortableRemoteObject;
import javax.transaction.xa.Xid;

import org.objectweb.jotm.Coordinator;
import org.objectweb.jotm.InternalTransactionContext;
import org.objectweb.jotm.Terminator;
import org.objectweb.jotm.TraceTm;
import org.objectweb.jotm.TransactionContext;
import org.objectweb.jotm.XidImpl;
import org.omg.CORBA.Any;
import org.omg.CORBA.LocalObject;
import org.omg.CORBA.ORB;
import org.omg.CORBA.TCKind;
import org.omg.CosTransactions.PropagationContext;
import org.omg.CosTransactions.PropagationContextHelper;
import org.omg.CosTransactions.TransIdentity;
import org.omg.CosTransactions.otid_t;
import org.omg.DynamicAny.DynAnyFactory;
import org.omg.DynamicAny.DynAnyFactoryHelper;
import org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode;
import org.omg.IOP.Codec;
import org.omg.IOP.ServiceContext;
import org.omg.PortableInterceptor.ForwardRequest;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ORBInitInfoPackage.InvalidName;
// For JOnAS: import org.ow2.carol.jndi.ns.JacORBCosNaming;

public abstract class OTSInterceptor extends LocalObject {

    /**
     * Allow JOTM interceptors to recognize a JOTM Context.
     */
    private static final String JONAS = "JOnAS";

    /**
     * Used to encode/decode the call.
     */
    private Codec codec;

    /**
     * for identifying client or server.
     */
    private DynAnyFactory dynAnyFactory;

    /**
     * Service context identifier.
     */
    protected static final int TX_CTX_ID = org.omg.IOP.TransactionService.value; //100;

    /**
     * ORB instance.
     */
    private static ORB orb = null;

    /**
     * Constructor.
     * @param info ORB Configuration object
     */
    public OTSInterceptor(final ORBInitInfo info) {

        // Get the codec factory
        org.omg.IOP.CodecFactory codecFactory = info.codec_factory();
        if (codecFactory == null) {
            TraceTm.jta.error("OTSInterceptor: no CodecFactory");
            throw new RuntimeException("OTSInterceptor: no CodecFactory");
        }

        // Create codec
        org.omg.IOP.Encoding how = new org.omg.IOP.Encoding();
        how.major_version = 1;
        how.minor_version = 0;
        how.format = org.omg.IOP.ENCODING_CDR_ENCAPS.value;

        try {
            codec = codecFactory.create_codec(how);
        } catch(org.omg.IOP.CodecFactoryPackage.UnknownEncoding ex) {
            TraceTm.jta.error("OTSInterceptor: UnknownEncoding");
            throw new RuntimeException("OTSInterceptor: UnknownEncoding");
        }
        if (codec == null) {
            TraceTm.jta.error("OTSInterceptor: no Codec");
            throw new RuntimeException("OTSInterceptor: no Codec");
        }

        // Get the dynamic any factory
        DynAnyFactory resolvedDynAnyFactory = null;
        try {
            org.omg.CORBA.Object obj = info.resolve_initial_references("DynAnyFactory");
            resolvedDynAnyFactory = DynAnyFactoryHelper.narrow(obj);
        } catch(InvalidName ex) {
            TraceTm.jta.error("OTSInterceptor: " + ex);
            throw new RuntimeException("OTSInterceptor: InvalidName");
        } catch(org.omg.CORBA.BAD_PARAM ex) {
            TraceTm.jta.error("OTSInterceptor: " + ex);
            throw new RuntimeException("OTSInterceptor: BAD_PARAM");
        }

        this.dynAnyFactory = resolvedDynAnyFactory;
    } // end constructor

    /**
     * Create an {@link Any} from {@link PropagationContext} type.
     * @return {@link Any} instance for {@link PropagationContext}
     * @throws InconsistentTypeCode
     */
    protected Any create_any() throws InconsistentTypeCode {
        org.omg.DynamicAny.DynAny dynAny = dynAnyFactory.create_dyn_any_from_type_code(PropagationContextHelper.type());
        return dynAny.to_any();
    }

    /**
     * Build and returns the CORBA PropagationContext (JTS) from a JOTM {@link TransactionContext}.
     * @param txCtx JOTM {@link TransactionContext} to be "serialized"
     * @return the {@link ServiceContext} built from {@link TransactionContext}
     * @throws ForwardRequest
     */
    protected ServiceContext buildCorbaPropagationContext(final TransactionContext txCtx)
        throws ForwardRequest {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("OTSInterceptor buildCorbaPropagationContext");
        }
        try {

            // Build a new CORBA Propagation context
            // For the moment, JOTM does not implement tx interoperability EJB2.1 ?19.6 and
            // so the fields CosTransactions:Coordinator and CosTransactions:Terminator are set
            // to null in order to generate a 'null transaction ctx'
            // The coordinator/terminator are propagated in the 'implementation_specific_data' field.

            // Get Information of TransactionContext
            Xid xid = txCtx.getXid();
            int timeout = txCtx.getTimeout();
            Coordinator coord = txCtx.getCoordinator();

            // Xid is coded in the otid_t
            byte[] gtrid = xid.getGlobalTransactionId();
            byte[] bqual = xid.getBranchQualifier();
            byte[] tid = new byte[gtrid.length + bqual.length];
            System.arraycopy(bqual, 0, tid, 0, bqual.length);
            System.arraycopy(gtrid, 0, tid, bqual.length, gtrid.length);
            otid_t otid = new otid_t(xid.getFormatId(), bqual.length, tid);

            // current holds only otid_t
            TransIdentity curr = new TransIdentity(null, null, otid);

            // Create the PropagationContext
            PropagationContext pctx = new PropagationContext(timeout,
                                                             curr,
                                                             new TransIdentity[0],
                                                             null);

            // In JOTM, the Coordinator and the terminator interface are implemented
            // by the same class : ControlImpl. The stub is propagated to others JOnAS
            // instances in the implementation_specific_data field. It is coded in an Any
            // field
            if (orb == null) {
                // Get ORB instance
                // For JOnAS: orb = JacORBCosNaming.getOrb();
                orb = ORB.init(new String[]{}, null);
            }

            Any specific = orb.create_any();
            if (coord != null) {
                // Set the JOTM's coordinator in the 'specific' field
                specific.insert_Object((javax.rmi.CORBA.Stub) PortableRemoteObject.toStub(coord));
                pctx.implementation_specific_data = specific;
            } else {
                // If the coordinator is unknown, set a 'JONAS' pattern
                // to allow the other side to recognize a
                // JOTM context
                specific.insert_string(JONAS);
                pctx.implementation_specific_data = specific;
            }

            Any pAny = create_any();
            PropagationContextHelper.insert(pAny, pctx);

            // encode the PropagationContext in a service context
            byte[] propagationContextData = codec.encode_value(pAny);
            return new ServiceContext(TX_CTX_ID, propagationContextData);

        } catch (Exception e) {
            throw new ForwardRequest();
        }
    }

    /**
     * Decode the Corba Propagation Context and build an internal transaction context.
     * @param sCtx ServiceContext
     * @return TransactionContext Rebuilt JOTM {@link TransactionContext} from OTS
     *         transaction {@link ServiceContext}
     */
    protected TransactionContext decodeCorbaPropagationContext(final ServiceContext sCtx) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("OTSInterceptor decodeCorbaPropagationContext");
        }

        if (sCtx == null) {
            TraceTm.jta.debug("OTSInterceptor: no tx ctx");
            return null;
        }
        Xid xid = null;
        int timeout;
        Any specific = null;

        // try to decode the corba tx context
        try {
            // unmarshall the Propagation Context
            Any pctxAny = codec.decode_value(sCtx.context_data, PropagationContextHelper.type());
            PropagationContext pctx = PropagationContextHelper.extract(pctxAny);

            // get the propagation context values
            specific = pctx.implementation_specific_data;
            otid_t otid = pctx.current.otid;
            timeout = pctx.timeout;
            xid = new XidImpl(otid.formatID, otid.bqual_length, otid.tid);

        } catch (Exception e) {
            TraceTm.jta.error("OTSInterceptor: invalid tx ctx");
            return null;
        }

        // JOTM don't be able to detect whether the context is a valid or a null ctx
        // If it isn't a JOTM's context (fail during decoding), it considers it's a null ctx
        boolean isJotmCtx = false;
        Coordinator coord = null;
        Terminator term = null;

        try {
            if ((specific == null)
                || (specific.type().kind() == TCKind.tk_null)
                || (specific.type().kind() == TCKind.tk_octet)) {
                // null ctx or valid ctx but sent from another app server
            } else if (specific.type().kind() == TCKind.tk_string) {
                String pattern = specific.extract_string();
                // Here, maybe we have received a JOTM context with an unknown coordinator
                // In this case, a JONAS pattern has been set
                if (pattern.compareTo(JONAS) == 0) {
                    isJotmCtx = true;
                }
            } else {
                // Last try, it should be a typical JOTM context with the coordinator set in 'specific' field
                coord = (Coordinator) PortableRemoteObject.narrow(specific.extract_Object(),
                                                                  Coordinator.class);
                term = (Terminator) PortableRemoteObject.narrow(specific.extract_Object(),
                                                                Terminator.class);
                isJotmCtx = true;
            }
        } catch (Exception e) {
            TraceTm.jta.debug("OTSInterceptor: null ctx or valid ctx but sent from another app server");
        }

        TransactionContext tCtx = new InternalTransactionContext(timeout,
                                                                 coord,
                                                                 term,
                                                                 xid);
        if (!isJotmCtx) {
            TraceTm.jta.debug("OTSInterceptor: ctx comes from another vendor");
            tCtx.setNotJotmCtx();
        }
        return tCtx;
    }
}

