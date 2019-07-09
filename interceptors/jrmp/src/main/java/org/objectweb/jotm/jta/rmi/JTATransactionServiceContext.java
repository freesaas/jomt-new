/*
 * @(#) JTATransactionServiceContext
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
 * $Id: JTATransactionServiceContext.java 1153 2010-03-30 11:42:15Z durieuxp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm.jta.rmi;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.objectweb.jotm.Coordinator;
import org.objectweb.jotm.InternalTransactionContext;
import org.objectweb.jotm.TransactionContext;
import org.objectweb.jotm.Xid;
import org.objectweb.jotm.XidImpl;
import org.ow2.carol.rmi.interceptor.spi.JServiceContext;

/**
 * Class <code>JTATransactionServiceContext</code> is a JRMP Class for Transaction
 * Context Propagation
 *
 * @author  Guillaume Riviere (Guillaume.Riviere@inrialpes.fr)
 * @version 1.0, 13/09/2002
 */
public class JTATransactionServiceContext
    implements JServiceContext, Externalizable {

    /**
     * Transaction Context
     */
    transient TransactionContext txCtx = null;

    /**
     * true is this is a reply
     */
    transient boolean isReply;

    /**
     * context id
     */
    private transient int context_id;

    /**
     * empty constructor for externalizable
     */
    public JTATransactionServiceContext() {
        this.context_id = JTAClientTransactionInterceptor.TX_CTX_ID;
    }

    /**
     * the JServiceContext id
     */
    public int getContextId() {
        return context_id;
    }

    /**
     * constructor
     * @param txCtx TransactionContext the RMI (Serializable) Transaction Context
     * @param isReply boolean is reply indicator
     */
    public void setContext(TransactionContext txCtx, boolean isReply) {
        this.txCtx = txCtx;
        this.isReply = isReply;
    }

    /**
     * get the transaction context
     * @return TransactionContext the Transaction context
     */
    public TransactionContext getTransactionContext() {
        return txCtx;
    }

    /**
     * readExternal to initialize Transaction context
     * @param in the object input
     */
    public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {
        // read Xid
        int fid = in.readInt();
        byte[] gti = new byte[in.readInt()];
        in.read(gti);
        byte[] bq = new byte[in.readInt()];
        in.read(bq);
        Xid xid = new XidImpl(fid, gti, bq);
        // read Coordinator
        Coordinator coor = (Coordinator) in.readObject();
        // read timeout
        int timeout = in.readInt();
        this.txCtx = new InternalTransactionContext(timeout, coor, xid);
    }

    /**
     * writeExternal to send Transaction context
     * @param out the object output
     */
    public void writeExternal(ObjectOutput out) throws IOException {
        // send Xid
        Xid xid = txCtx.getXid();
        out.writeInt(xid.getFormatId());
        out.writeInt(xid.getGlobalTransactionId().length);
        out.write(xid.getGlobalTransactionId());
        out.writeInt(xid.getBranchQualifier().length);
        out.write(xid.getBranchQualifier());
        // send Coordinator
        out.writeObject(txCtx.getCoordinator());
        // send timeout
        out.writeInt(txCtx.getTimeout());
    }
}
