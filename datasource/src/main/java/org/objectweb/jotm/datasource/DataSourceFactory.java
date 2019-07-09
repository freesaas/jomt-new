/*
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
 * $Id: DataSourceFactory.java 1149 2010-03-29 12:54:02Z durieuxp $
 * --------------------------------------------------------------------------
 */


package org.objectweb.jotm.datasource;

import java.util.Enumeration;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.enhydra.jdbc.pool.StandardXAPoolDataSource;
import org.enhydra.jdbc.standard.StandardXADataSource;

import org.objectweb.jotm.Jotm;

/**
 * This class is used when integrating JOTM with a servlet container like tomcat.
 * this factory must be declared for example in a context.xml file:
 *       type="javax.sql.DataSource"
 *       factory="org.objectweb.jotm.datasource.DataSourceFactory"
 * The jotm will be created at first use (static bloc)
 * Datasource are created by the XAPool module. (org.enhydra.jdbc packages)
 * @author jmesnil
 */
public class DataSourceFactory implements ObjectFactory {

    private static Hashtable table = new Hashtable();

    static {
        try {
            jotm = new Jotm(true, false);
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }

    public static Jotm jotm;

    /**
     * @see javax.naming.spi.ObjectFactory#getObjectInstance
     */
    public Object getObjectInstance(Object obj, Name n, Context nameCtx, Hashtable environment)
            throws Exception {
        StandardXAPoolDataSource xads = null;
        StandardXADataSource ds = null;
        try {
            Reference ref = (Reference) obj;
            ds = new StandardXADataSource();
            xads = new StandardXAPoolDataSource(ds);
            Enumeration addrs = ref.getAll();
            while (addrs.hasMoreElements()) {
                RefAddr addr = (RefAddr) addrs.nextElement();
                String name = addr.getType();
                String value = (String) addr.getContent();
                if (name.equals("driverClassName")) {
                    ds.setDriverName(value);
                } else if (name.equals("url")) {
                    ds.setUrl(value);
                } else if (name.equals("username")) {
                    xads.user = value;
                    ds.setUser(value);
                } else if (name.equals("password")) {
                    ds.setPassword(value);
                    xads.password = value;
                } else if (name.equals("min")) {
                    try {
                        int min = Integer.parseInt(value);
                        xads.setMinSize(min);
                    } catch (NumberFormatException e) {
                        // we do nothing (default value will be used)
                    }
                } else if (name.equals("max")) {
                    try {
                        int max = Integer.parseInt(value);
                        xads.setMaxSize(max);
                    } catch (NumberFormatException e) {
                        // we do nothing (default value will be used)
                    }
                }  else if (name.equals("testStmt")) {
                    xads.setJdbcTestStmt(value);
                } else if (name.equals("checkLevel")){
                    try {
                        int max = Integer.parseInt(value);
                        xads.setCheckLevelObject(max);
                    } catch (NumberFormatException e) {
                        // we do nothing (default value will be used)
                    }
                } else if (name.equals("maxWait")) {
                    try {
                        int max = Integer.parseInt(value);
                        xads.setDeadLockMaxWait(max);
                    } catch (NumberFormatException e) {
                        // we do nothing (default value will be used)
                    }
                }

            }
            xads.setTransactionManager(jotm.getTransactionManager());
            xads.setDataSource(ds);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (table.containsKey(ds)) {
            return table.get(ds);
        } else {
            table.put(ds, xads);
            return xads;
        }
    }
}
