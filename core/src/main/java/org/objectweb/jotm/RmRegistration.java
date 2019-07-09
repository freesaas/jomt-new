/*
 * @(#) RmRegistration.java
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
 * $Id: RmRegistration.java,v 1.4 2005-06-16 23:35:46 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.XAException;

/**
 * Resource Managers currently registered.
 *
 * @author Tony Ortiz
 *
 */

public class RmRegistration {

	private String rmName = null;
	private XAResource rmXares = null;
    private String rmXaName = null;
    private boolean rmReference = false;
    private TransactionResourceManager rmTranRm = null;
    private boolean rmRecovered = true;

	public void rmAddRegistration (String prmName, XAResource prmXares, String prmxaName,
                                   TransactionResourceManager prmTranRm) {
	    if (TraceTm.recovery.isDebugEnabled()) {
	        TraceTm.recovery.debug("Add registration Resource manager= " + prmName);
	        TraceTm.recovery.debug("Add registration XAResource = " + prmXares);
            TraceTm.recovery.debug("Add registration XAResource Name = " + prmxaName);
            TraceTm.recovery.debug("Add registration TransactionResourceManager = " + prmTranRm);
	    }

	    rmName = prmName;
	    rmXares = prmXares;
        rmXaName = prmxaName;
        rmTranRm = prmTranRm;
    }

	public void rmAddName (String prmName) {
	    rmName = prmName;
	}

	public void rmAddXaRes (XAResource prmXares) {
	    rmXares = prmXares;
    }

	public String rmGetName () {
	    if (TraceTm.recovery.isDebugEnabled()) {
	        TraceTm.recovery.debug("rmName= " + rmName);
	    }
	    return rmName;
    }

	public XAResource rmGetXaRes () {
	    if (TraceTm.recovery.isDebugEnabled()) {
	        TraceTm.recovery.debug("rmXares= " + rmXares);
	    }
	    return rmXares;
    }

    public String rmGetXaResName () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("rmXaName= " + rmXaName);
        }
        return rmXaName;
    }

    public TransactionResourceManager rmGetTranRm () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("rmTranRm= " + rmTranRm);
        }
        return rmTranRm;
    }

    public void rmSetRmRecovered (boolean prmRecovered) {
        rmRecovered = prmRecovered;
    }

    public boolean rmGetRmRecovered () {
        return rmRecovered;
    }

    public XAResource rmCheckoutXARes () throws XAException {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("rmCheckoutXares= " + rmXares);
        }

        if (rmXares == null) {
            throw new XAException("Attempt to use unregistered Resource Manager");
        }

        synchronized (this) {
            while (true) {
                if (rmReference == false) {
                    rmReference = true;
                    return rmXares;
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                    ;
                }
            }
        }
    }

    public void rmCheckinXARes () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("rmCheckinXares= " + rmXares);
        }

        synchronized(this) {
            rmReference = false;   // leave the name with the last using thread
            notifyAll();
        }
    }
}