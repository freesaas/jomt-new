/*
 * @(#) TransactionRecoveryImpl.java 
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
 * $Id: TransactionRecoveryImpl.java,v 1.10 2005-10-27 19:31:38 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */

package org.objectweb.jotm;

import java.io.IOException;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import javax.transaction.SystemException;
import javax.transaction.xa.XAResource;

import org.objectweb.howl.log.Configuration;
import org.objectweb.howl.log.LogConfigurationException;
import org.objectweb.howl.log.LogException;
import org.objectweb.howl.log.LogRecord;
import org.objectweb.howl.log.LogRecordType;
import org.objectweb.howl.log.ReplayListener;
import org.objectweb.howl.log.xa.XACommittingTx;
import org.objectweb.howl.log.xa.XALogRecord;
import org.objectweb.howl.log.xa.XALogger;

import javax.transaction.xa.XAException;

/**
 * 
 * @author Tony Ortiz
 */

public class TransactionRecoveryImpl implements TransactionRecovery {

    private transient static TransactionRecoveryImpl unique = null;

    private transient static Map<String, XAResource> nameResourceManager =
        Collections.synchronizedMap(new HashMap<String, XAResource>());

    // Jotm Recovery
    private transient static JotmRecovery tmrecovery = null;
    //private transient static boolean dorecovery = false;
    private transient static boolean startrecoverycalled = false;

    // XALogger info
    private transient static XALogger xaLog = null;

    // Vector used to hold all registered Resource Managers
    // until they can be written to the Howl Logger.
    private transient static Vector vRmRegistration = new Vector();
    private RmRegistration myrmRegistration = null;

    private final byte [] RESM1 = "RM1".getBytes();
    private final byte [] RESM2 = "RM2".getBytes();
    
    /**
     * -Djotm.base property
     */
    private static final String JOTM_BASE = "jotm.base";
    private static final String JOTM_HOME = "jotm.home";

    /**
     * jonas.base property
     */
    private static final String JONAS_BASE = "jonas.base";
    
    /**
     * configuration directory name
     */
    private static final String CONFIG_DIR = "conf";

    /**
     * System properties
     */
    private static Properties systEnv = System.getProperties();  

    /**
     * JONAS_BASE
     */
    private static String jonasBase = systEnv.getProperty(JONAS_BASE);
    
    /**
     * JOTM_HOME
     */
    private static String jotmHome = systEnv.getProperty(JOTM_HOME);
    private static String jotmBase = systEnv.getProperty(JOTM_BASE);

    /**
     * Separator of file
     */
    private static String fileSeparator = systEnv.getProperty("file.separator");

    /**
     * Default constructor.
     */
    public TransactionRecoveryImpl() {

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("TransactionRecoveryImpl constructor");
        }

        unique = this;

        // Check the JOTM_HOME, JONAS_BASE and JOTM_BASE environment properties
        // If on of them is not found, check the transactionRecovery value
        // from Current to see if recovery is enabled(true)/disabled(false).
        // JOTM_BASE is looked first, in case we want a particular configuration.
        // For use in jonas, either set JOTM_BASE to JONAS_BASE, or unset JOTM_BASE
        // and JOTM_HOME.
        String myBase;
        if (jotmBase != null) {
            myBase = jotmBase.trim();
        } else if (jotmHome != null) {
            myBase = jotmHome.trim();
        } else if (jonasBase != null) {
            myBase = jonasBase.trim();
        } else {
            if (!Current.getDefaultRecovery()) {
                // transaction recovery is disabled
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("JOTM Recovery is disabled");
                }
            } else {
                TraceTm.recovery.error("Both jonasBase and jotmBase are not defined");
            }
            return;
        }

        String fileFullPathname = myBase + fileSeparator + CONFIG_DIR + fileSeparator + "jotm.properties";

        TraceTm.jotm.info("JOTM properties file= " + fileFullPathname);

        Properties howlprop = new Properties();
        FileInputStream inStr = null;
        try {
            inStr = new FileInputStream(fileFullPathname);
            systEnv.load(inStr);
        } catch (Exception e){
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Cannot Open jotm.properties= " +e);
            }
            Current.setDefaultRecovery(false);
            return;
        } finally {
            if (inStr != null) {
                try {
                    inStr.close();
                } catch (IOException e) {
                    TraceTm.recovery.error("cannot close inputStream");
                }
            }
        }

        // Check property jotm.appserver.Enable
        // This was added to fix bugs 100308 and 100315
        try {
            if (systEnv.getProperty("jotm.appserver.Enabled").trim().equalsIgnoreCase("true")) {
                // appserver enabled
                Current.setAppServer(true);
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Application Server is enabled");
                }
            } else {
                // appserver disabled
                Current.setAppServer(false);
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Application Server is disabled");
                }
            }
        } catch (Exception e) {
            // enable Application Server
            Current.setAppServer(true);
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Application Server is enabled");
            }
        }

        try {
            if (systEnv.getProperty("jotm.recovery.Enabled").trim().equalsIgnoreCase("true")) {
                // recovery enabled
                Current.setDefaultRecovery(true);
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("JOTM Recovery is enabled");
                }
            } else {
                // recovery disabled
                Current.setDefaultRecovery(false);
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("JOTM Recovery is disabled");
                }
                return;
            }
        } catch (Exception e) {
            // disable recovery
            Current.setDefaultRecovery(false);
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("JOTM Recovery is disabled");
            }
            return;
        }

        String myhowlprop;
        myhowlprop = systEnv.getProperty ("howl.log.ListConfiguration", "false");
        howlprop.put("listConfig", myhowlprop);
        myhowlprop = systEnv.getProperty ("howl.log.BufferSize", "4");
        howlprop.put("bufferSize", myhowlprop);
        myhowlprop = systEnv.getProperty ("howl.log.MinimumBuffers", "16");
        howlprop.put("minBuffers", myhowlprop);
        myhowlprop = systEnv.getProperty ("howl.log.MaximumBuffers", "16");
        howlprop.put("maxBuffers", myhowlprop);
        myhowlprop = systEnv.getProperty ("howl.log.MaximumBlocksPerFile", "200");
        howlprop.put("maxBlocksPerFile", myhowlprop);
        myhowlprop = systEnv.getProperty ("howl.log.FileDirectory", systEnv.getProperty ("basedir", "."));
        howlprop.put("logFileDir", myhowlprop);
        myhowlprop = systEnv.getProperty ("howl.log.FileName", "howl");
        howlprop.put("logFileName", myhowlprop);
        myhowlprop = systEnv.getProperty ("howl.log.MaximumFiles", "2");
        howlprop.put("maxLogFiles", myhowlprop);

        try {
            howlOpenLog (howlprop);
        } catch (Exception e) {
            TraceTm.jotm.warn("howlOpenLog: LogException occured in howlOpenLog() " + e.getMessage());
            Current.setDefaultRecovery(false);
            TraceTm.recovery.warn("JOTM Recovery is disabled");
        }
    }

    /**
     * Returns the unique instance of the class or <code>null</code> if not
     * initialized in case of plain client.
     *
     * @return The <code>TransactionRecovery</code> object created 
     */

    public static TransactionRecoveryImpl getTransactionRecovery() {

        return unique;
    }

    public JotmRecovery getJotmRecovery() {

        return tmrecovery;
    }
    
    public Vector getRmRegistration() {

        return vRmRegistration;
    }

    // ------------------------------------------------------------------
    // JOTM Recovery Support Methods
    // ------------------------------------------------------------------

    /**
     * Register a Resource Manager with the JOTM Transaction Manager.
     * @param rmName The Resource Manager to be registered.
     */
    public void registerResourceManager (String rmName, XAResource rmXares, String info,
                                         Properties rmProperties,
                                         TransactionResourceManager trm) throws XAException {
        if ( TraceTm.recovery.isDebugEnabled() ) {
            TraceTm.recovery.debug("Register Resource Manager Properties " + 
                                   rmName + rmProperties + " to Connection " + rmXares);
        } 
        this.registerResourceManager (rmName, rmXares, info, trm);
    }

    public void registerResourceManager(String rmName, XAResource rmXares, String info,
                                        TransactionResourceManager tranrm) throws XAException {

        if (!Current.getDefaultRecovery()) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("JOTM Recovery is disabled");
            }

            if (tranrm != null) {  // initial implementation, call back immediately
                if ( TraceTm.recovery.isDebugEnabled() ) {
                    TraceTm.recovery.debug("Callback " + rmName + " to Connection " + rmXares);
                }
                tranrm.returnXAResource(rmName, rmXares);
            }
            return;    // transaction recovery is disabled   
        }

        // put the Resource Manager/XAResource mapping into the hashtable

        if ( TraceTm.recovery.isDebugEnabled() ) {
            TraceTm.recovery.debug("Register Resource Manager " + rmName + " to Connection " + rmXares);
        }

        XAResource xares = nameResourceManager.get(rmName);

        if (xares == null) {
            nameResourceManager.put(rmName, rmXares);
        } else {

            // check equality based on the XAResource object

            if( TraceTm.recovery.isDebugEnabled() ) {
                TraceTm.recovery.debug("First  xares= " + xares.getClass().getName());
                TraceTm.recovery.debug("Second xares= " + rmXares.getClass().getName());
            }

            if (xares.isSameRM(rmXares)) {
                if( TraceTm.recovery.isDebugEnabled() ) {
                    TraceTm.recovery.debug(rmName + " already registered");
                }

                if (tranrm != null) {  // initial implementation, call back immediately
                    if ( TraceTm.recovery.isDebugEnabled() ) {
                        TraceTm.recovery.debug("Callback " + rmName + " to Connection " + rmXares);
                    }
                    tranrm.returnXAResource(rmName, rmXares);
                }
                return;
            } else {
                nameResourceManager.put(rmName, rmXares);
            }
        }

        myrmRegistration = new RmRegistration();
        myrmRegistration.rmAddRegistration (rmName, rmXares, rmXares.getClass().getName(), tranrm);
        vRmRegistration.addElement(myrmRegistration);

        // if startResourceManagerRecovery has been called before
        // (e.g. during Jonas startup)it must be called when a
        // Resource Manager registered

        if (startrecoverycalled) {
            try {
                startResourceManagerRecovery ();
            } catch (XAException e) {
                throw new XAException ("startResourceManagerRecovery failed" + e.getMessage());
            }

            if (myrmRegistration.rmGetRmRecovered()) {
                TransactionResourceManager regtranrm = myrmRegistration.rmGetTranRm();

                if (regtranrm != null) {  // release the transaction resource manager, call back
                    if ( TraceTm.recovery.isDebugEnabled() ) {
                        TraceTm.recovery.debug("Callback " + rmName + " to Connection " + rmXares);
                    }
                    regtranrm.returnXAResource(rmName, rmXares);
                }
            }
        }
    }

    /**
     * Provide information regarding the status and state of the XAResource.
     * @param rmName The Resource Manager to be reported upon.
     * @return XAResource The XAResource assigned to the Resource Managere.
     */
    public XAResource reportResourceManager (String rmName) throws XAException {

        if (!Current.getDefaultRecovery()) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("JOTM Recovery is disabled");
            }
            return null;    // transaction recovery is disabled   
        }

        // given the name, get the corresponding Connection from the hashtable.

        if( TraceTm.recovery.isDebugEnabled() ) {
            TraceTm.recovery.debug("get Connection from Resource Manager " + rmName);
        }

        XAResource myXares = nameResourceManager.get(rmName);

        if (myXares == null) {
            throw new XAException ("Named Resource Manager " + rmName + " does not exist");
        }

        return myXares;
    }

    /**
     * Unregister a Resource Manager from the JOTM Transaction Manager.
     * @param rmName The Resource Manager to be unregistered.
     */
    public void unregisterResourceManager(String rmName, XAResource rmXares) throws XAException {

        if (!Current.getDefaultRecovery()) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("JOTM Recovery is disabled");
            }
            return;    // transaction recovery is disabled   
        }

        // Unregister the Resource Manager from the name (hash table).

        if ( TraceTm.recovery.isDebugEnabled() ) {
            TraceTm.recovery.debug("Remove Resource Manager " + rmName + " from Connection " + rmXares);
        }

        XAResource myrmXares = nameResourceManager.get(rmName);

        if (myrmXares.equals(rmXares)) {
            nameResourceManager.remove(rmName);
        } else {
            throw new XAException ("Resource Manager " + rmName + " not associated to " + rmXares);
        }
    }

    /**
     * Log (in Howl) every Resource Manager (XAResource) that has been
     * registered.
     *
     * @exception XAException Thrown if the transaction manager 
     * encounters an unexpected error condition
     */
    public void startResourceManagerRecovery () throws XAException {
        if (!Current.getDefaultRecovery()) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("JOTM Recovery is disabled");
            }
            return;    // transaction recovery is disabled   
        }

        int rmcount = vRmRegistration.size();
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("LogResourceManager count= " +rmcount);
        }

        if (rmcount == 0) {
            // We set startrecoverycalled to true just in case
            // JOTM is called to recover before any RM are registered.
            // This is okay, since we perform recovery when a RM is
            // registered.
            startrecoverycalled = true;
            return;
        }

        try {
            recoverResourceManager();
        } catch (XAException e) {
            throw new XAException("Cannot perform recovery " + e.getMessage());
        }

        for (int i = 0; i < rmcount; i++) {
            myrmRegistration = (RmRegistration)vRmRegistration.elementAt(i);

            if (myrmRegistration.rmGetRmRecovered()) {
                String rmName = myrmRegistration.rmGetName();
                XAResource xaRes = myrmRegistration.rmGetXaRes();
                String xaResName = myrmRegistration.rmGetXaResName();
                TransactionResourceManager regtranrm = myrmRegistration.rmGetTranRm();

                // Determine if we have to write out a new RM1 record.
                // If Resource Manager being registered already exists in the
                // RecoverRMInfo vector, then it exists in a RM1 record;
                // therefore no need to write a RM1 record.

                Vector vRecoverRmInfo = tmrecovery.getmyRecoverRmInfo();
                int numRm = vRecoverRmInfo.size();
                RecoverRmInfo myrecoverRmInfo;

                if (numRm == 0) {
                    logNewResourceManagerRM1(rmName, xaRes, xaResName);
                } else {
                    boolean rmfound = false;

                    for (int j = 0; j < numRm; j++) {
                        myrecoverRmInfo = (RecoverRmInfo) vRecoverRmInfo.elementAt(j);
                        String chrm = myrecoverRmInfo.getRecoverRm();

                        if (rmName.equals(chrm)) {
                            rmfound = true;
                            break;
                        }
                    }

                    if (!rmfound) {
                        logNewResourceManagerRM1(rmName, xaRes, xaResName);
                    }
                }

                if (regtranrm != null) {  // release the transaction resource manager, call back
                    if ( TraceTm.recovery.isDebugEnabled() ) {
                        TraceTm.recovery.debug("Callback " + rmName + " to Connection " + xaResName);
                    }
                    regtranrm.returnXAResource(rmName, xaRes);
                }
            }
        }

        startrecoverycalled = true;
    }

    /**
     * Log a ResourceManager
     * @param rmName ResourceManager name
     * @param xaRes  XAResource
     * @param xaresName name of the XAResource
     * @throws XAException Cannot write the log
     */
    public void logNewResourceManagerRM1(String rmName, XAResource xaRes, String xaresName) throws XAException {

        byte [] [] rmBuffer = new byte [2] [];  // two entries (RM1 entry + RM2 entry)

        long rmdate = System.currentTimeMillis();

        byte [] rmRecord1 = new byte[3+8+4];

        ByteBuffer rm1 = ByteBuffer.wrap(rmRecord1);
        rm1.put(RESM1);
        rm1.putLong(rmdate);
        rm1.putInt(1);

        rmBuffer[0] = rm1.array();

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("LogResourceManager rmName= " + rmName);
            TraceTm.recovery.debug("    xaRes= " + xaresName);
        }

        int rmlength = rmName.length();
        String sxaRes = xaRes.toString();
        int xaReslength= sxaRes.length();
        int xaResNamelength = xaresName.length();

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("rm length=" + rmlength);
            TraceTm.recovery.debug("xaRes length= " + xaReslength);
        }

        byte [] rmRecord2 = new byte[3+4+rmlength+4+xaReslength+4+xaResNamelength+4];
        ByteBuffer rm2 = ByteBuffer.wrap(rmRecord2);

        rm2.put(RESM2);
        rm2.putInt(rmlength);
        rm2.put(rmName.getBytes());
        rm2.putInt(xaReslength);
        rm2.put(sxaRes.getBytes());
        rm2.putInt(xaResNamelength);
        rm2.put(xaresName.getBytes());
        rm2.putInt(1);

        rmBuffer[1] = rm2.array();    // First record (0) is always rm1

        try {
            howlCommitLog(rmBuffer);
        } catch (Exception e) {
            // If we cannot write the Log, we cannot perform recovery

            String howlerror =
                "Cannot howlCommitLog:"
                + e
                + " --"
                + e.getMessage();
            TraceTm.jotm.error("Got LogException from howlCommitLog: "+ howlerror);

            throw new XAException(howlerror);
        }

    }

    /**
     * Recover a Resource Manager with the JOTM Transaction Manager.
     *
     * @exception XAException Thrown if the transaction manager 
     * encounters an unexpected error condition
     */
    public void recoverResourceManager () throws XAException {

        if (!Current.getDefaultRecovery()) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("JOTM Recovery is disabled");
            }
            return;    // transaction recovery is disabled   
        }

        if ( TraceTm.recovery.isDebugEnabled() ) {
            TraceTm.recovery.debug("recoverResourceManager");
        }

        if (vRmRegistration.size()== 0){
            if ( TraceTm.recovery.isDebugEnabled() ) {
                TraceTm.recovery.debug("Nothing to recover");
            }
            return;
        }

        // If incomplete commit records were found in Howl, we may need to
        // recover transactions (xids).

        try {
            tmrecovery.recoverTransactions(vRmRegistration);
        } catch (XAException e) {
            throw new XAException("Unable to recover transactions" + e.getMessage());
        }
    }

    /* HOWL Support Methods */

    private class xaReplayListener implements ReplayListener {

        public void onRecord (LogRecord lr) {

            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("LogRecord type= " + lr.type);
            }

            switch(lr.type) {
            case LogRecordType.EOB:
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Howl End of Buffer Record");
                }
                break;
            case LogRecordType.END_OF_LOG:
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Howl End of Log Record");
                }
                break;
            case LogRecordType.XACOMMIT:
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Howl XA Commit Record");
                }
                tmrecovery.rebuildTransaction ((XALogRecord) lr);
                break;
            case LogRecordType.XADONE:
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Howl XA Done Record");
                }
                break;
            case LogRecordType.USER:
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Howl User Record");
                }
                break;
            default:
                if (TraceTm.recovery.isDebugEnabled()) {
                    TraceTm.recovery.debug("Unknown Howl LogRecord");
                }
                break;
            }
        }

        public void onError(LogException exception) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("onError");
            }
        }

        public LogRecord getLogRecord() {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("getLogRecord");
            }
            return new XALogRecord(120);
        }
    }

    /**
     * open  the Howl Log
     * @param phowlprop Configuration Properties
     * @throws SystemException Could not create the Howl Log
     */
    synchronized void howlOpenLog(Properties phowlprop) throws SystemException {

        if (xaLog != null) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("Howl Log already opened");
            }
            return;
        }

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Open howl log");
        }

        try {
            Configuration cfg = new Configuration (phowlprop);
            xaLog = new XALogger(cfg);
        } catch (LogConfigurationException e) {
            TraceTm.jotm.error("XALogger: LogConfigurationException" + e.getMessage());
            throw new SystemException("LogConfigurationException occured in XALogger() " + e.getMessage());
        } catch (IOException e) {
            TraceTm.jotm.error("XALogger: IOException" + e.getMessage());
            throw new SystemException("IOException occured in XALogger() " + e.getMessage());
        } catch (Exception e) {
            TraceTm.jotm.error("XALogger: Exception" + e.getMessage());
            throw new SystemException("Exception occurred in XALogger() " + e.getMessage());
        }

        tmrecovery = new JotmRecovery();

        xaReplayListener myxarl = new xaReplayListener();

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("xaLog.open");
        }

        try {
            xaLog.open(null);
        } catch (LogException e) {
            TraceTm.jotm.error("xaLog.open: LogException" + e.getMessage());
            throw new SystemException("LogException occured in xaLog.open() " + e.getMessage());
        } catch (IOException e) {
            TraceTm.jotm.error("xaLog.open: IOException" + e.getMessage());
            throw new SystemException("IOException occured in xaLog.open() " + e.getMessage());
        } catch (InterruptedException e) {
            TraceTm.jotm.error("xaLog.open: InterruptedException" + e.getMessage());
            throw new SystemException("InterruptedException occured in xaLog.open() " + e.getMessage());
        } catch (ClassNotFoundException e) {
            TraceTm.jotm.error("xaLog.open: ClassNotFoundException" + e.getMessage());
            throw new SystemException("ClassNotFoundException occured in xaLog.open() " + e.getMessage());
        } catch (Exception e) {
            TraceTm.jotm.error("xaLog.open: Exception " + e.getMessage());
            throw new SystemException("Exception occurred in xaLog.open() " + e.getMessage());
        }

        xaLog.replayActiveTx (myxarl);
    }

    /**
     * write the Done record to the Howl Log
     * @throws SystemException COuld not close the Howl Log
     */
    void howlCloseLog() throws SystemException {

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Close howl log");
        }
        if (xaLog == null) {
            TraceTm.recovery.debug("howl log already closed");
            return;
        }

        try {
            xaLog.close();
        } catch (IOException e) {
            TraceTm.jotm.error("xaLog.close: IOException" + e.getMessage());
            throw new SystemException("IOException occured in xaLog.close() " + e.getMessage());
        } catch (InterruptedException e) {
            TraceTm.jotm.error("xaLog.close: InterruptedException" + e.getMessage());
            throw new SystemException("InterruptedException occured in xaLog.close() " + e.getMessage());
        } catch (Exception e) {
            TraceTm.jotm.error("xaLog.open: Exception " + e.getMessage());
            throw new SystemException("Exception occurred in xaLog.open() " + e.getMessage());
        }
        xaLog = null;

        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Howl log closed");
        }
    }

    /**
     * write the Commit record to the Howl Log
     * @param xaCmRec the Commit Record
     * @return the XACommittingTx returned by putCommit
     * @throws LogException could not log the record
     * @throws IOException
     * @throws InterruptedException
     */
    public XACommittingTx howlCommitLog(byte [][] xaCmRec) throws LogException, IOException, InterruptedException {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Commit howl log");
        }
        return xaLog.putCommit(xaCmRec);
    }

    /**
     * write the Done record to the Howl Log
     * @param xaDnRec the Done Record
     * @param xaCmTx the XACommittingTx
     * @throws LogException could not log the record
     * @throws IOException
     * @throws InterruptedException
     */
    public void howlDoneLog(byte [][] xaDnRec, XACommittingTx xaCmTx) throws LogException, IOException, InterruptedException {
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Done howl log");
        }
        xaLog.putDone(xaDnRec, xaCmTx);
    }

    /**
     * close Transaction Recovery Log
     */
    public void forget() throws LogException, SystemException {
        if (! Current.getDefaultRecovery()) {
            if (TraceTm.recovery.isDebugEnabled()) {
                TraceTm.recovery.debug("JOTM Recovery is disabled");
            }
            return;
        }
        if (TraceTm.recovery.isDebugEnabled()) {
            TraceTm.recovery.debug("Closing Howl Log");
        }
        howlCloseLog();
    }

}
