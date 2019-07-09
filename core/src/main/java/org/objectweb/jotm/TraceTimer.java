/*
 * @(#) TraceTimer.java 
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
 * $Id: TraceTimer.java,v 1.1 2004-01-23 20:42:19 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.io.PrintWriter;

/**
 * Traces for module timer
 * @author Sebastien Chassande-Barrioz sebastien.chassande@inrialpes.fr
 */
public class TraceTimer {
    
    static private boolean isDebug = false;	// if the timer is logged
    static private boolean isVerbose = false;	// if we print the verbose message
    static private PrintWriter logWriter = null;	// our log writer

    /**
     * set the debug timer
     */
    static public void setDebug(boolean set) {
        isDebug = set;
    }

    /**
     * set the verbose flag
     */
    static public void setVerbose(boolean set) {
        isVerbose = set;
    }

    /**
     * set the log writer
     */
    static public void setLogWriter(PrintWriter log) {
        logWriter = log;
    }

    /**
     * print the verbose message if the logger is not null
     */
    static public void verbose(String msg) {
        if ((isVerbose) && (logWriter != null))
            logWriter.println(msg);
    }

    /**
     * print the debug timer message if the logger is not null
     */
    static public void debug(String msg) {
        if ((isDebug) && (logWriter != null))
            logWriter.println(msg);
    }

    /**
     * print the error message if the logger is not null
     */
    static public void error(String msg) {
        if (logWriter != null)
            logWriter.println(msg);
    }

    /**
     * print the throwing message if the logger is not null
     */
    static public void error(String msg, Throwable th) {
        if (logWriter != null) {
            logWriter.println(msg);
            th.printStackTrace(logWriter);
        }
    }

}
