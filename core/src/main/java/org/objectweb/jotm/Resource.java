/*
 * @(#) Resource.java 
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
 * $Id: Resource.java,v 1.6 2003-12-10 20:06:26 trentshue Exp $
 * --------------------------------------------------------------------------
 */


package org.objectweb.jotm;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface must be implemented by objects that will be
 * registered to the JTM Coordinator. They may be remote.
 */
public interface Resource extends Remote {

    /** 
     * phase 1 of the 2PC.
     *
     * @return int vote commit, rollback, or readonly.
     */
    public int prepare() throws RemoteException;
    public final static int VOTE_COMMIT = 0;
    public final static int VOTE_ROLLBACK = 1;
    public final static int VOTE_READONLY = 2;

    /** 
     * rollback transaction
     */
    public void rollback() throws RemoteException;

    /** 
     * phase 2 of the 2PC.
     */
    public void commit() throws RemoteException;

    /** 
     * commit 1 phase.
     */
    public void commit_one_phase() throws RemoteException;

    /** 
     * forget heuristics about this transaction.
     */
    public void forget() throws RemoteException;
}
