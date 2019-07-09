/*
 * @(#) JotmRecovery.java
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
 * $Id: JotmRecovery.java,v 1.9 2005-09-14 22:23:57 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */

package org.objectweb.jotm;

import java.nio.ByteBuffer;
import java.util.Vector;
import java.util.List;
import java.util.Iterator;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.objectweb.howl.log.xa.XALogRecord;
import org.objectweb.howl.log.xa.XACommittingTx;
import javax.transaction.xa.XAException;

/**
 *
 * @author Tony Ortiz
 */

/**
 * Thread class used to manage Resource Manager Registration.
 */
class processResourceManager extends Thread {
    private ThreadGroup xaResourceThreadGroup = null;
    Vector txrecovered = null;
    Vector rminfo = null;
    Vector rmreg = null;
    int rmsize = 0;
    int rmindex = 0;

    processResourceManager (ThreadGroup prmThreadGroup, Vector pvtxrecovered, Vector prminfo, Vector prmreg, int prmsize, int prmindex) {
        xaResourceThreadGroup = new ThreadGroup(prmThreadGroup, "XAResourceTG");
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("new processResourceManager");
        }

        txrecovered = pvtxrecovered;
        rminfo = prminfo;
        rmreg = prmreg;
        rmsize = prmsize;
        rmindex = prmindex;;
    }

    public void run() {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("thread for processResourceManager");
        }

        XAResource myregxares = null;
        RecoverRmInfo myrecoverRmInfo = null;
        String myrm = null;
        String myregrm = null;
        byte[] byxares = null;

        RmRegistration myrmreg = (RmRegistration) rmreg.elementAt(rmindex);
        myregrm = myrmreg.rmGetName();

        for (int j=0; j<rmsize; j++) {
            myrecoverRmInfo = (RecoverRmInfo) rminfo.elementAt(j);
            myrm = myrecoverRmInfo.getRecoverRm();
            byxares = myrecoverRmInfo.getRecoverXaRes();

            if (myregrm.equals(myrm)){
                // checkout the XAResource, this will "lock" the XAResource
                // we can only "unlock" the XAResource after all the threads
                // used to resolve (commit/rollback) the XAResource's XIDs
                // have successfully completed

                try {
                    myregxares = myrmreg.rmCheckoutXARes();
                } catch (XAException e) {
                    if (TraceTm.recovery.isDebugEnabled()) {
                        TraceTm.recovery.debug("rmCheckoutXARes call failed during recovery " + e.getMessage());
                    }
                    myrmreg.rmCheckinXARes();
                    break;
                }

                List recoveredXidList = new java.util.LinkedList();

                try {
                    boolean first = true;
                    javax.transaction.xa.Xid[] javaxids;
                    javaxids = myregxares.recover(first ? XAResource.TMSTARTRSCAN : XAResource.TMNOFLAGS);
                    if (javaxids != null && javaxids.length != 0) {
                        if (TraceTm.recovery.isDebugEnabled()) {
                            TraceTm.recovery.debug("Registered Resource Manager " + myregrm);
                            TraceTm.recovery.debug("Registered XAResource " + myregxares);
                            TraceTm.recovery.debug("Recover Resource Manager " + myrm);
                            TraceTm.recovery.debug("Recover XAResource " + new String(byxares));
                            TraceTm.recovery.debug("XARESOURCE-R " + myregxares);
                            TraceTm.recovery.debug("  LEN-R= " + javaxids.length);
                            for (int ix = 0; ix < javaxids.length; ix++) {
                                TraceTm.recovery.debug("  XID-R= " + javaxids[ix]);
                            }
                        }
                        recoveredXidList.addAll(java.util.Arrays.asList(javaxids));
                    }
                } catch (XAException e) {
                    if (TraceTm.recovery.isDebugEnabled()) {
                        TraceTm.recovery.debug("xaResource.recover call failed during recovery " + e.getMessage());
                    }
                    myrmreg.rmCheckinXARes();
                    break;
                }


                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("recoveredXidList size= " + recoveredXidList.size());
                }

                if (recoveredXidList.size() == 0) {
                    if (TraceTm.recovery.isDebugEnabled()) {
                        TraceTm.recovery.debug("No XIDs to recover for Xares= "+ myregxares);
                    }
                    myrmreg.rmCheckinXARes();
                    break;
                }

                // set the action required for the XIDs located in our
                // txxidRecovered (array), pointed to by TxRecovered (vector)

                settxxidrecoveraction(myregxares, recoveredXidList);

                // We can now walk through TxRecovered and perform the action on
                // each of the Xids stored in TxxidRecovered.

                doActionXidRecover ();

                for (int w = 0; w < 10; w++) {  // only do it 10 times)
                    boolean xaresinthread = false;
                    int numthreads = xaResourceThreadGroup.activeCount();

                    if (TraceTm.recovery.isDebugEnabled()) {
                        TraceTm.recovery.debug("xaResource active count= "+ numthreads);
                    }

                    if (numthreads == 0) {
                        break;
                    }

                    Thread[] listOfThreads = new Thread[numthreads];
                    xaResourceThreadGroup.enumerate(listOfThreads);

                    for (int i = 0; i < numthreads; i++) {
                        if (listOfThreads[i] != null) {
                            if (TraceTm.recovery.isDebugEnabled()) {
                                TraceTm.recovery.debug("xaResource in thread= "+ listOfThreads[i].getName());
                            }

                            try {
                                Thread.sleep(100);  // 100 millisecond
                            } catch (InterruptedException e) {
                                ;
                            }
                            xaresinthread = true;
                            break;
                        }
                    }

                    if (!xaresinthread) {
                        break;
                    }
                }

                // checkin the XAResource, this will "unlock" the XAResource
                myrmreg.rmCheckinXARes();
                break;
            }
        }

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("endthread for processResourceManager");
        }
    }

    private void settxxidrecoveraction(XAResource actxares, List actionxid) {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("settxxidrecoveraction");
        }

        TxxidRecovered mytxxidRecovered = null;
        TxRecovered mytxRecovered = null;

        // for each XID (actionxid) returned for a specific XAResource
        // determine if the XID needs to be marked for commit (1)

        Iterator iter = actionxid.iterator();
        while (iter.hasNext()) {
            boolean xidfound = false;
            Xid myjavaxid = (Xid) iter.next();
            org.objectweb.jotm.Xid myxid = new XidImpl(myjavaxid);
            byte [] mybrqu = myxid.getBranchQualifier();

            if (TraceTm.recovery.isDebugEnabled()) {
            	TraceTm.recovery.debug("txrecovered.size()= " + txrecovered.size());
            }

            for (int j = 0; j < txrecovered.size(); j++){
                mytxRecovered = (TxRecovered) txrecovered.elementAt(j);

                if (mytxRecovered != null) {
                    // We will set every XID found to rollback, if we find
                    // the XID in our tables we will reset the XID to commit
                    // or not one of ours

                    if (TraceTm.recovery.isDebugEnabled()) {
                    	TraceTm.recovery.debug("mytxRecovered.getxidcount()= " + mytxRecovered.getxidcount());
                    }

                    for (int k = 0; k < mytxRecovered.getxidcount(); k++){
                        mytxxidRecovered = mytxRecovered.getRecoverTxXidInfo(k);

                        if (mytxxidRecovered != null) {
                            byte [] mytxxid = mytxxidRecovered.getRecoverxid();
                            if (TraceTm.recovery.isDebugEnabled()) {
                                TraceTm.recovery.debug("before cast mytxxid= " + new String(mytxxid));
                                TraceTm.recovery.debug("before cast myxid  = " + myxid.toString(true));
                            }

                            org.objectweb.jotm.Xid mymytxxid = new XidImpl(mytxxid);

                            if (mymytxxid.isThisOneOfOurs(mybrqu)) {

                                if (TraceTm.recovery.isDebugEnabled()) {
                                    TraceTm.recovery.debug("mymytxxid= " + mymytxxid.toString(true));
                                    TraceTm.recovery.debug("myxid  = " + myxid.toString(true));
                                }

                                if (mymytxxid.toString(true).equals(myxid.toString(true))) {
                                    // reset the RecoveryTxXidInfo entry with the registered
                                    // XAResource and the commit action
                                    xidfound = true;
                                    int myaction = 1;    // Commit
                                    if (TraceTm.recovery.isDebugEnabled()) {
                                    	TraceTm.recovery.debug("Commit Action");
                                    }

                                    mytxxidRecovered.setRecoveraction(myaction);
                                    mytxxidRecovered.setCommitxares(actxares);
                                    mytxxidRecovered.setCommitxid(myjavaxid);
                                    mytxRecovered.setRecoverTxXidInfo(mytxxidRecovered,k);
                                    break;
                                }
                            } else {
                            	// we found an XID but it was not ours, we ignore it
                            	// by not creating a mytxxidRecovered record
                            	xidfound = true;
                                if (TraceTm.recovery.isDebugEnabled()) {
                                    TraceTm.recovery.debug("Xid is not one of ours");
                                }
                            }
                        }
                    }
                }

                if (xidfound) {    // xid found in a TxxidRecovered of the TxRecovered vector
                    break;
                }
            }

            if (!xidfound) {    // xid NOT found in a TxxidRecovered of the TxRecovered vector
                abortimmediate (actxares, myjavaxid);
            }
        } // while
    }

    private void doActionXidRecover () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("doActionXidRecover");
        }

        TxxidRecovered mytxxidRecovered = null;
        TxRecovered mytxRecovered = null;

        for (int i = 0; i < txrecovered.size(); i++) {
            mytxRecovered = (TxRecovered) txrecovered.elementAt(i);

            for (int j = 0; j < mytxRecovered.getxidcount(); j++) {
                mytxxidRecovered = mytxRecovered.getRecoverTxXidInfo(j);

                if (mytxxidRecovered != null) {

                    // If action = 0; XAResource is known but the XID associated
                    // to is was not returned in the xares.recover call. This implies
                    // that the XID was already committed, just ignore.
                    // If action = 1; XAResource is known and the xares.recover call
                    // returned the specifed XID. This implies that we should attempt
                    // to commit the XID (if possible).

                    if (mytxxidRecovered.getRecoveraction() == 1) {  // Commit

                        commitXAResourceXid committhread = new commitXAResourceXid (mytxxidRecovered);
                        Thread commitxa = new Thread (xaResourceThreadGroup, committhread, "commitxid" + "-" + i + "-" + j);
                        commitxa.start();
                    }
                }
            }
        }
    }

    private void abortimmediate(XAResource abortxares, Xid abortxid) {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("abortimmediate");
        }

        abortXAResourceXid abortthread = new abortXAResourceXid (abortxares, abortxid);
        Thread abortxa = new Thread (xaResourceThreadGroup, abortthread, "abortxid");
        abortxa.start();
    }
}

/**
 * Thread class used to commit an Xid of an XAResource,
 * XAResource.commit(Xid).
 */
class commitXAResourceXid extends Thread {
    XAResource commitxares = null;
    Xid commitxid = null;
    TxxidRecovered committxxidrecovered = null;

    commitXAResourceXid (TxxidRecovered ptxxidrecovered) {
        committxxidrecovered = ptxxidrecovered;
        commitxares = ptxxidrecovered.getCommitxares();
        commitxid = ptxxidrecovered.getCommitxid();
    }

    public void run() {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("thread for commitXAResourceXid");
            TraceTm.recovery.debug("  Committing xid= " + commitxid);
            TraceTm.recovery.debug("  with XAResource= " + commitxares);
        }

        try {
            commitxares.commit(commitxid, false);
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Commit Xid " + commitxid);
                TraceTm.recovery.debug("successful during Recovery");
            }
        } catch (XAException e) {
            switch (e.errorCode) {
            case XAException.XA_HEURHAZ :
            case XAException.XA_HEURCOM :
            case XAException.XA_HEURRB :
            case XAException.XA_HEURMIX :
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Heuristic condition= " + e.getMessage());
                    TraceTm.recovery.debug("  with XAException= " + e.errorCode);
                }
                break;
            case XAException.XAER_RMERR :
            case XAException.XAER_NOTA :
            case XAException.XAER_INVAL :
            case XAException.XAER_PROTO:
            case XAException.XAER_RMFAIL :
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("RM error= " + e.getMessage());
                    TraceTm.recovery.debug("  with XAException= " + e.errorCode);
                }
                break;
            default :
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Default error= " + e.getMessage());
                    TraceTm.recovery.debug("  with XAException= " + e.errorCode);
                }
            }
            committxxidrecovered.setRecoveraction(2);  // 2 = commit failed during recovery, now heuristic
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Unable to commit Xid " + commitxid);
                TraceTm.recovery.debug("during Recovery " + e.getMessage());
            }
        }

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("endthread for commitXAResourceXid");
        }
    }
}

/**
 * Thread class used to abort an Xid of an XAResource,
 * XAResource.rollback(Xid).
 */

class abortXAResourceXid extends Thread {
    XAResource abortxares = null;
    Xid abortxid = null;

    abortXAResourceXid (XAResource pxares, Xid pxid) {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("pxares= " + pxares);
            TraceTm.recovery.debug("pxid= " + pxid);
        }

        abortxares = pxares;
        abortxid = pxid;
    }

    public void run() {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("thread for abortXAResourceXid");
            TraceTm.recovery.debug("  Rolling Back xid= " + abortxid);
            TraceTm.recovery.debug("  with XAResource= " + abortxares);
        }

        try {
            abortxares.rollback(abortxid);
            TraceTm.recovery.debug("Abort Xid " + abortxid);
            TraceTm.recovery.debug("successful during Recovery");
        } catch (XAException e) {
            switch (e.errorCode) {
            case XAException.XA_HEURHAZ :
            case XAException.XA_HEURCOM :
            case XAException.XA_HEURRB :
            case XAException.XA_HEURMIX :
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Heuristic condition= " + e.getMessage());
                    TraceTm.recovery.debug("  with XAException= " + e.errorCode);
                }
                break;
            case XAException.XAER_RMERR :
            case XAException.XAER_NOTA :
            case XAException.XAER_INVAL :
            case XAException.XAER_PROTO:
            case XAException.XAER_RMFAIL :
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("RM error= " + e.getMessage());
                    TraceTm.recovery.debug("  with XAException= " + e.errorCode);
                }
                break;
            default :
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Default error= " + e.getMessage());
                    TraceTm.recovery.debug("  with XAException= " + e.errorCode);
                }
            }
            TraceTm.recovery.debug("Unable to abort Xid " + abortxid);
            TraceTm.recovery.debug("during Recovery " + e.getMessage());
        }

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("endthread for abortXAResourceXid");
        }
    }
}

/**
 * Vector used to hold all Resource Managers that were
 * registered when the system crashed, may have XIDs that
 * need to be recovered.
 */
class RecoverRmInfo {

    private String recoverRm = null;
    private byte[] recoverXares = null;
    private String recoverxaresName = null;
    private int recoverIndex = 0;

    public void addRecoverRmXaRes (String rrmName, byte[] rrmXares, String rrmxaresName, int rrmIndex) {

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("RecoverRm Resource Manager= " + rrmName);
            TraceTm.recovery.debug("RecoverRm XAResource = " + new String(rrmXares));
            TraceTm.recovery.debug("RecoverRm Index = " + rrmIndex);
        }

        recoverRm = rrmName;
        recoverXares = rrmXares;
        recoverxaresName = rrmxaresName;
        recoverIndex = rrmIndex;
    }

    public String getRecoverRm () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("recoverRm= " + recoverRm);
        }
        return recoverRm;
    }

    public byte[] getRecoverXaRes () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("recoverXares= " + new String(recoverXares));
        }
        return recoverXares;
    }

    public String getRecoverXaResName () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("recoverXares= " + new String(recoverxaresName));
        }
        return recoverxaresName;
    }

    public int getRecoverIndex () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("recoverIncex= " + recoverIndex);
        }
        return recoverIndex;
    }
}

/**
 * Vector and Array used to hold all Transactions that were
 * in a commit pending status when the system crashed, these
 * transactions (XIDs) may need to be recovered.
 */
class TxxidRecovered {

    private int txxidIndex = 0;
    private byte[] txxidXares = null;
    private String txxidXaresname = null;
    private byte[] txxidXid = null;
    private int txxidStatus = 0;
    private int txxidAction = 0;    // 0-Do nothing, implied already committed
                                    // 1-Commit
                                    // 2-Heuristics condition, administrator action required
    private XAResource commitxares = null;
    private Xid commitxid = null;

    public void addXidInfo (int pindex, byte[] pxares, String pxaresname, byte[] pxid, int pstatus) {

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Txxid index= " + pindex);
            TraceTm.recovery.debug("Txxid xares= " + new String(pxares));
            TraceTm.recovery.debug("Txxid xid= " + new String(pxid));
            TraceTm.recovery.debug("Txxid status= " + pstatus);
        }

        txxidIndex = pindex;
        txxidXares = pxares;
        txxidXaresname = pxaresname;
        txxidXid = pxid;
        txxidStatus = pstatus;
    }

    public int getRecoverindex () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("txxidIndex= " + txxidIndex);
        }
        return txxidIndex;
    }

    public byte[] getRecoverxares () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("txxidXares= " + new String(txxidXares));
        }
        return txxidXares;
    }

    public String getRecoverxaresname () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("txxidXaresname= " + txxidXaresname);
        }
        return txxidXaresname;
    }

    public byte[] getRecoverxid () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("txxidXid= " + new String(txxidXid));
        }
        return txxidXid;
    }

    public int getRecoverstatus () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("txxidStatus= " + txxidStatus);
        }
        return txxidStatus;
    }

    public void setRecoverstatus (int pstatus) {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("pstatus= " + pstatus);
        }
        txxidStatus = pstatus;
    }

    public int getRecoveraction () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("txxidAction= " + txxidAction);
        }
        return txxidAction;
    }

    public void setRecoveraction (int paction) {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("paction= " + paction);
        }
        txxidAction = paction;
    }

    public XAResource getCommitxares () {
        return commitxares;
    }

    public void setCommitxares (XAResource pcommitxares) {
        commitxares = pcommitxares;
    }

    public Xid getCommitxid () {
        return commitxid;
    }

    public void setCommitxid (Xid pcommitxid) {
        commitxid = pcommitxid;
    }
}

class TxRecovered {
    private long recoverydatetime = 0L;
    private byte[] txxid = null;
    private String txdatetime = null;
    private int xidcount = 0;
    private XACommittingTx xacommittingtx = null;

    private TxxidRecovered [] xidinfo;

    public void addtxrecovered (long prdt, byte[] ptxxid, String ptdt, int pxcnt, XACommittingTx pxacmtx) {

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Recover tx prdt= " + prdt);
            TraceTm.recovery.debug("Recover tx ptxxid= " + new String(ptxxid));
            TraceTm.recovery.debug("Recover tx ptdt= " + ptdt);
            TraceTm.recovery.debug("Recover tx pxcnt= " + pxcnt);
            TraceTm.recovery.debug("Recover tx pxacmtx= " + pxacmtx);
        }

        recoverydatetime = prdt;
        txxid = ptxxid;
        txdatetime = ptdt;
        xidcount = pxcnt;
        xacommittingtx = pxacmtx;

        xidinfo = new TxxidRecovered [xidcount];
    }

    public void setRecoverTxXidInfo (TxxidRecovered ptxxidr, int rindx) {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("addRecoverTxXidInfo");
        }
        xidinfo[rindx] = ptxxidr;
    }

    public long getrecoverdatetime () {
        return recoverydatetime;
    }

    public byte [] gettxxid () {
        return txxid;
    }

    public String gettxdatetime () {
        return txdatetime;
    }

    public int getxidcount () {
        return xidcount;
    }

    public TxxidRecovered getRecoverTxXidInfo (int rindx) {

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("getRecoverTxXidInfo");
        }
        return xidinfo[rindx];
    }

    public XACommittingTx getXACommittingTx () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("getXACommittingTx");
        }
        return xacommittingtx;
    }
}

public class JotmRecovery {

    private static JotmRecovery unique = null;
    private static Vector vTxRecovered = new Vector ();
    private static Vector vRecoverRmInfo = new Vector ();
    private Vector userRecoveryRecords = new Vector ();

    /**
     * Constructor.
     */
    public JotmRecovery() {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("JotmRecovery constructor");
        }
        unique = this;
    }

    /**
     * Returns the unique instance of the class or <code>null</code> if not
     * initialized.
     *
     * @return The <code>JotmRecovery</code> object created
     */
    public static JotmRecovery getJotmRecovery() {
        return unique;
    }

    /**
     * Returns the unique instance of the class or <code>null</code> if not
     * initialized.
     *
     * @return The <code>TxRecovered</code> vector created
     */
    public static Vector getTxRecovered() {
        return vTxRecovered;
    }

    /**
     * Returns the unique instance of the class or <code>null</code> if not
     * initialized.
     *
     * @return The <code>RecoverRmInfo</code> vector created
     */
    public static Vector getRecoverRmInfo() {
        return vRecoverRmInfo;
    }

    public Vector getmyRecoverRmInfo() {
        return vRecoverRmInfo;
    }

    /**
     * Returns the index of the Resource Manager's XAResource.
     *
     * @return Index of the Resource Manager's XAResource.
     */
    public int getRmIndex(byte [] pxares) {
        int numRm = vRecoverRmInfo.size();
        RecoverRmInfo myrecoverRmInfo;

        for (int i = 0; i < numRm; i++) {
            myrecoverRmInfo = (RecoverRmInfo) vRecoverRmInfo.elementAt(i);
            byte [] inrmxares = myrecoverRmInfo.getRecoverXaRes();

            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("XAResource param " + pxares);
                TraceTm.recovery.debug("XAResource in rm " + inrmxares);
            }

            if (inrmxares == pxares) {
                return myrecoverRmInfo.getRecoverIndex();
            }
        }
        return 99;
    }

    public Vector getUserRecoveryVector() { // currently used by test suite
        return userRecoveryRecords;
    }

    /**
     * Processes an XACOMMIT entry (putCommit) that does not have an associated
     * XADONE entry (putDone).
     *
     * @param lr LogRecord that was passed to onRecord() method.
     */
    public void rebuildTransaction(XALogRecord lr) {

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("rebuildTransaction");
        }

        RecoverRmInfo myrecoverRmInfo = null;
        TxxidRecovered myrecoverTxInfo = null;
        TxRecovered mytxRecovered = null;

        // Temporary Recovery Record
        byte [] tempRec;
        byte [] rt = new byte [3];  // Applies to all Records

        // Resource Manager Record Type 1 fields
        long rmdatetime;
        int rmcount;

        // Resource Manager Record Type 2
        byte [] resmgr2;

        // Resource Manager Record Type 2 fields
        int rmlength;
        byte [] rmname = null;

        int rmindx;

        // Recovery Record Type 1 fields
        long rcdatetime;
        byte [] txXid = null;
        int txdatelength;
        byte [] txdatetime;
        int xarescount = 0;

        // Check if there already exist a recovery record in our array with the
        // same txXid but a older datetime. If so remove it and store this one,
        // otherwise throw this one away.

        // Recovery Record Type 2
        byte [] recov2;

        // Recovery Record Type 2 fields
        byte [] rt2 = new byte [3];
        int xaresindex;
        int xareslength;
        int xaresnamelength;
        byte [] xares;
        byte [] xaresname;
        byte [] recoveryxid;
        int xidstatus;

        XACommittingTx myxacommittx = lr.getTx();
        tempRec = lr.getFields() [0];

        ByteBuffer rr = ByteBuffer.wrap(tempRec);

        rr.get(rt, 0, 3);
        String trt = new String(rt);

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Recovery Record type= " + trt);
        }

        if (trt.equals("RM1")) {
            rmdatetime = rr.getLong();
            rmcount = rr.getInt();

            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Resource Manager count= " + rmcount);
            }

            for (int i = 1; i <= rmcount; i++) {
                resmgr2 = lr.getFields()[i];

                ByteBuffer resm2 = ByteBuffer.wrap(resmgr2);
                resm2.get(rt2, 0, 3);
                trt = new String (rt2);

                if (trt.equals("RM2")) {
                    rmlength = resm2.getInt();
                    rmname = new byte[rmlength];
                    resm2.get(rmname, 0, rmlength);
                    xareslength = resm2.getInt();
                    xares = new byte[xareslength];
                    resm2.get(xares, 0, xareslength);
                    xaresnamelength = resm2.getInt();
                    xaresname = new byte[xaresnamelength];
                    resm2.get(xaresname, 0, xaresnamelength);
                    rmindx = resm2.getInt();

                    String myrmname = new String(rmname);
                    String myxaresname = new String(xaresname);
                    myrecoverRmInfo = new RecoverRmInfo();

                    myrecoverRmInfo.addRecoverRmXaRes (myrmname, xares, myxaresname, rmindx);
                    vRecoverRmInfo.addElement(myrecoverRmInfo);
                }
            }
        } else if (trt.equals("RR1")) {

            rcdatetime = rr.getLong();
            int intmfi = rr.getInt();
            int gtilen = rr.getInt();
            byte [] mgti = new byte[gtilen];
            rr.get(mgti, 0, gtilen);
            int bqlen = rr.getInt();
            byte [] mbq = new byte[bqlen];
            rr.get(mbq, 0, bqlen);

            byte[] gtrid_local = new byte[64];
            byte[] bqual_local = new byte[64];

            // Buffers need two hex characters per byte
            StringBuffer str_buff_gtrid = new StringBuffer(64 * 2);
            StringBuffer str_buff_bqual = new StringBuffer(64 * 2);

            ByteBuffer aa = ByteBuffer.wrap(gtrid_local);
            System.arraycopy(mgti, 0, gtrid_local, 0, gtilen);

            for (int i=0; i < gtilen; i++) {
                byteToHex(aa.get(), str_buff_gtrid );
            }

            if (bqlen != 0) {
                bqual_local = new byte[64];
                ByteBuffer bb = ByteBuffer.wrap(bqual_local);
                System.arraycopy(mbq, 0, bqual_local, 0, bqlen);

                for (int i=0; i < bqlen; i++) {
                    byteToHex(bb.get(), str_buff_bqual);
                }
            }

            byte[] bc = ":".getBytes();
            int bclen = bc.length;
            String sxid = Long.toHexString(intmfi) + ":" +
                Long.toHexString(gtilen) + ":" +
                Long.toHexString(bqlen) + ":" +
                str_buff_gtrid.toString() + ":" +
                str_buff_bqual.toString();
            txXid = new byte[4+bclen+4+bclen+4+gtilen+bclen+bqlen];
            txXid = sxid.getBytes();

            txdatelength = rr.getInt();
            txdatetime = new byte[txdatelength];
            rr.get(txdatetime, 0, txdatelength);
            xarescount = rr.getInt();

            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Rebuilt tx prdt= " + rcdatetime);
                TraceTm.recovery.debug("Rebuilt tx ptxxid= " + new String(txXid));
                TraceTm.recovery.debug("Rebuilt tx ptdt= " + new String(txdatetime));
            }

            String mytxXid = new String(txXid);
            String mytxdatetime = new String(txdatetime);

            mytxRecovered = new TxRecovered();

            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("MyTxXid= " + mytxXid);
                TraceTm.recovery.debug("XAResource count= " + xarescount);
            }

            mytxRecovered.addtxrecovered (rcdatetime, txXid, mytxdatetime, xarescount, myxacommittx);

            for (int i=1; i <= xarescount; i++) {
                myrecoverTxInfo = new TxxidRecovered();
                recov2 = lr.getFields()[i];

                ByteBuffer rr2 = ByteBuffer.wrap(recov2);
                rr2.get(rt2, 0, 3);
                trt = new String (rt2);

                if (trt.equals("RR2")) {
                    xaresindex = rr2.getInt();
                    xareslength = rr2.getInt();
                    xares = new byte[xareslength];
                    rr2.get(xares, 0, xareslength);
                    xaresnamelength = rr2.getInt();
                    xaresname = new byte[xaresnamelength];
                    rr2.get(xaresname, 0, xaresnamelength);
                    int intmfi2 = rr2.getInt();
                    int gtilen2 = rr2.getInt();
                    byte [] mgti2 = new byte[gtilen2];
                    rr2.get(mgti2, 0, gtilen2);
                    int bqlen2 = rr2.getInt();
                    byte [] mbq2 = new byte[bqlen2];
                    rr2.get(mbq2, 0, bqlen2);
                    xidstatus = rr2.getInt();

                    byte[] gtrid_local2 = new byte[64];
                    byte[] bqual_local2 = new byte[64];

                    // Buffers need two hex characters per byte
                    StringBuffer str_buff_gtrid2 = new StringBuffer(64 * 2);
                    StringBuffer str_buff_bqual2 = new StringBuffer(64 * 2);

                    ByteBuffer aa2 = ByteBuffer.wrap(gtrid_local2);
                    System.arraycopy(mgti2, 0, gtrid_local2, 0, gtilen2);

                    for (int i2=0; i2 < gtilen2; i2++) {
                        byteToHex(aa2.get(), str_buff_gtrid2 );
                    }

                    if (bqlen2 != 0) {
                        bqual_local2 = new byte[64];
                        ByteBuffer bb2 = ByteBuffer.wrap(bqual_local2);

                        System.arraycopy(mbq2, 0, bqual_local2, 0, bqlen2);

                        for (int i2=0; i2 < bqlen2; i2++) {
                            byteToHex(bb2.get(), str_buff_bqual2);
                        }
                    }

                    String sxid2 = Long.toHexString(intmfi2) + ":" +
                        Long.toHexString(gtilen2) + ":" +
                        Long.toHexString(bqlen2) + ":" +
                        str_buff_gtrid2.toString() + ":" +
                        str_buff_bqual2.toString();
                    recoveryxid = new byte[4+bclen+4+bclen+4+gtilen2+bclen+bqlen2];
                    recoveryxid = sxid2.getBytes();

                    String myxaresname = new String(xaresname);

                    myrecoverTxInfo.addXidInfo (xaresindex, xares, myxaresname, recoveryxid, xidstatus);
                    mytxRecovered.setRecoverTxXidInfo (myrecoverTxInfo, i-1);
                }
            }
            vTxRecovered.addElement(mytxRecovered);
        } else if (trt.equals("RU1")) {
            XidImpl.setUuids( rr.getLong(), rr.getLong());
        } else {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Unknown record type during replay = " + trt);
            }
            // add this byteBuffer of this record to a vector for user recovery
            rr.rewind();
            userRecoveryRecords.add( rr );
            userRecoveryRecords.add( myxacommittx );
        }
    }

    public void recoverTransactions (Vector rmreg) throws XAException {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("recoverTransactions");
        }

        int rmregsize = rmreg.size();  // number of Resource Managers registered

        // read the tmRecovered object and determine if any of the xaResources
        // require recovery (xares.recover call)

        int rmsize = vRecoverRmInfo.size();    // number of Resource Managers when system crashed

        if (rmsize == 0) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Nothing to recover");
            }
            return;
        }

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("number Resource Manager recover= " + rmsize);
        }

        // we can only recover Resource Managers that are in our log
        // (RecoverRmInfo) and have been registered (RmRegistered)

        ThreadGroup resourceManagerThreadGroup = new ThreadGroup("ResourceManagerTG");

        for (int i = 0; i < rmregsize; i++) {
            RmRegistration myrmreg = (RmRegistration) rmreg.elementAt(i);
            String myregrm = myrmreg.rmGetName();
            processResourceManager rmthread = new processResourceManager(resourceManagerThreadGroup, vTxRecovered, vRecoverRmInfo, rmreg, rmsize, i);
            Thread processrm = new Thread (resourceManagerThreadGroup, rmthread, myregrm + "-" + i);
            processrm.start();
        }

        for (int w = 0; w < 10; w++) {  // only do it 10 times)
            boolean rmsinthread = false;
            int numThreads = resourceManagerThreadGroup.activeCount();

            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("resourceManager active count= "+ numThreads);
            }

            if (numThreads == 0) {
                break;
            }

            Thread[] listOfThreads = new Thread[numThreads];
            resourceManagerThreadGroup.enumerate(listOfThreads);

            for (int i = 0; i < numThreads; i++) {
                if (listOfThreads[i] != null) {
                    if (TraceTm.recovery.isDebugEnabled()) {
                        TraceTm.recovery.debug("resourceManager in thread= "+ listOfThreads[i].getName());
                    }

                    try {
                        Thread.sleep(1000);  // 1 second
                    } catch (InterruptedException e) {
                        ;
                    }
                    rmsinthread = true;
                    break;
                }
            }

            if (!rmsinthread) {
                break;
            }
        }

        // We can now walk through TxRecovered and delete (howl done) any TxRecovered
        // entries that have had all their Xids (TxxidRecovered) committed or rolled
        // back successfully.
        // If any Xids have not been committed or rolledback, create a new TxRecovered
        // entry (howl commit) with the Xids (TxxidRecovered) that are still pending.

        doCleanupXidRecover ();

        for (int i = 0; i < rmregsize; i++) {
            RmRegistration myrmreg = (RmRegistration) rmreg.elementAt(i);
            myrmreg.rmSetRmRecovered(true);
        }
    }

    private void doCleanupXidRecover () {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("doCleanupXidRecover");
        }

        TxxidRecovered myTxxidRecovered = null;
        TxRecovered mytxRecovered = null;

        // We must go upward (last element to first) since we may remove
        // elements of the vTxRecovered vector.

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("vTxRecovered.size= " +vTxRecovered.size());
        }

        for (int i = vTxRecovered.size() - 1; i >= 0; i--) {
            boolean possibleheuristic = false;
            XACommittingTx myxacommittingtx = null;
            mytxRecovered = (TxRecovered) vTxRecovered.elementAt(i);

            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("mytxRecovered.xidcount= " +mytxRecovered.getxidcount());
            }

            for (int j = 0; j < mytxRecovered.getxidcount(); j++) {
                myTxxidRecovered = mytxRecovered.getRecoverTxXidInfo(j);

                if (myTxxidRecovered != null) {
                    if ((myTxxidRecovered.getRecoveraction() == 0) ||  // Xid already committed, ignore
                        (myTxxidRecovered.getRecoveraction() == 1)) {  // Xid just committed
                        ;
                    } else {
                        if (TraceTm.recovery.isDebugEnabled()) {
                            TraceTm.recovery.debug("possibleheuristic");
                        }

                        possibleheuristic = true;
                    }
                }
            }

            // We cannot remove the vTxRecovered vector element if there
            // exists the possibility of a heuristic transaction.

            if (!possibleheuristic) {

                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("write howlDonelog");
                }

            	myxacommittingtx = mytxRecovered.getXACommittingTx();
                byte [] rmDone = new byte [11];
                byte [] [] rmDoneRecord = new byte [1] [11];

                rmDone = "RR3JOTMDONE".getBytes();

                try {
                    rmDoneRecord [0] = rmDone;
                    TransactionRecoveryImpl.getTransactionRecovery().howlDoneLog (rmDoneRecord, myxacommittingtx);
                } catch (Exception f) {
                    String howlerror =
                        "Cannot howlDoneLog:"
                        + f
                        + "--"
                        + f.getMessage();
                    if (TraceTm.recovery.isDebugEnabled()) {
                        TraceTm.jotm.debug("Got LogException from howlDoneLog: "+ howlerror);
                    }
                }

                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("remove txRecovered entry");
                }

                vTxRecovered.remove(i);
            }
        }
    }

    static String HexDigits[] = {
        "00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
        "0a", "0b", "0c", "0d", "0e", "0f",
        "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
        "1a", "1b", "1c", "1d", "1e", "1f",
        "20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
        "2a", "2b", "2c", "2d", "2e", "2f",
        "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
        "3a", "3b", "3c", "3d", "3e", "3f",
        "40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
        "4a", "4b", "4c", "4d", "4e", "4f",
        "50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
        "5a", "5b", "5c", "5d", "5e", "5f",
        "60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
        "6a", "6b", "6c", "6d", "6e", "6f",
        "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
        "7a", "7b", "7c", "7d", "7e", "7f",
        "80", "81", "82", "83", "84", "85", "86", "87", "88", "89",
        "8a", "8b", "8c", "8d", "8e", "8f",
        "90", "91", "92", "93", "94", "95", "96", "97", "98", "99",
        "9a", "9b", "9c", "9d", "9e", "9f",
        "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9",
        "aa", "ab", "ac", "ad", "ae", "af",
        "b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9",
        "ba", "bb", "bc", "bd", "be", "bf",
        "c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9",
        "ca", "cb", "cc", "cd", "ce", "cf",
        "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9",
        "da", "db", "dc", "dd", "de", "df",
        "e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9",
        "ea", "eb", "ec", "ed", "ee", "ef",
        "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9",
        "fa", "fb", "fc", "fd", "fe", "ff"
    };

    /**
     * Hex Dump of byte
     */
    static final void byteToHex( byte inbyte, StringBuffer str_buff ) {

        int myByte = 0xFF & inbyte;

        str_buff.append( HexDigits[myByte] );
        return;
    }

}
