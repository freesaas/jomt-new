/*
 * @(#) TransactionRecovery.java 
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
 * $Id: TransactionRecovery.java,v 1.3 2005-04-18 16:12:46 rogerperey Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.XAException;
import java.util.Vector;
import org.objectweb.howl.log.LogException;
import java.util.Properties;

public interface TransactionRecovery {
    
    public JotmRecovery getJotmRecovery();
    
    public Vector getRmRegistration();

    /**
     * Log all the Resource Managers with the JOTM Transaction Manager.
     * 
     * @exception XAException if an error occurs
     */

    public void startResourceManagerRecovery () throws XAException;

    /**
     * Register a Resource Manager with the JOTM Transaction Manager.
     * 
     * @param rmName The Resource Manager to be registered.
     * @param rmXares XAResource associated with the Resource Manager
     * @param info String of information for display with admin interface
     * @param trm TransactionResourceManager to return the registered XAResource
     * @exception XAException if an error occurs
     */

    public void registerResourceManager (String rmName, XAResource rmXares, String info,
                                         TransactionResourceManager trm) throws XAException;

    /**
     * Added 3/30/05
     * Register a Resource Manager with the JOTM Transaction Manager with Recovery properties.
     * 
     * @param rmName The Resource Manager to be registered.
     * @param rmXares XAResource associated with the Resource Manager
     * @param info String of information for display with admin interface
     * @param rmProperties - Strings specifying recovery properties for resource
     * @param trm TransactionResourceManager to return the registered XAResource
     * @exception XAException if an error occurs
     */

    public void registerResourceManager (String rmName, XAResource rmXares, String info,
                                         Properties rmProperties,
                                         TransactionResourceManager trm) throws XAException;


    /**
     * Provide information regarding the status and state of the XAResource.
     * 
     * @return The XAResource to be reported upon.
     * 
     * @exception XAException if an error occurs
     */

    XAResource reportResourceManager (String rmName) throws XAException;

    /**
     * Unregister a Resource Manager from the JOTM Transaction Manager.
     * 
     * @param rmName The Resource Manager to be unregistered.
     * @param rmXares XAResource associated with the Resource Manager
     * @exception XAException if an error occurs
     */

    public void unregisterResourceManager (String rmName, XAResource rmXares) throws XAException;

    public void forget() throws LogException, Exception;
}
