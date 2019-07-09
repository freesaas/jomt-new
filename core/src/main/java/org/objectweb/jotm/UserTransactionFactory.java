/* 
 * @(#) UserTransactionFactory.java 
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
 * $Id: UserTransactionFactory.java,v 1.12 2006-09-05 18:05:44 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */

package org.objectweb.jotm;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

public class UserTransactionFactory implements ObjectFactory {

    public Object getObjectInstance(Object objref, Name name, Context ctx, Hashtable env) throws Exception {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("UserTransactionFactory.getObjectInstance");
        }
        
        Reference ref = (Reference) objref;
        Current ut = null;

        if (ref.getClassName().equals("javax.transaction.UserTransaction")
                || ref.getClassName().equals("org.objectweb.jotm.Current")) {
            // create the UserTransaction object
            // No need to init a TMFactory in the client!
            // ut = Current.getInstance();
            ut = Current.getCurrent();

            if (ut == null) {
                ut = new Current();

                // Get the timeout default value that was configured in the server
                String timeoutStr = (String) ref.get("jotm.timeout").getContent();
                Integer i = new Integer(timeoutStr);
                int timeout = i.intValue();
                ut.setDefaultTimeout(timeout);
            }
        }
        
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("UserTransactionFactory.getObjectInstance ut= " + ut);
        }
        return ut;
    }
}
