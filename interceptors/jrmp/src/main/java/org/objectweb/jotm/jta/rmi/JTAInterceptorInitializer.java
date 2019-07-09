/*
 * @(#) JTAInterceptorInitializer
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
 * $Id: JTAInterceptorInitializer.java 1021 2009-02-06 09:43:39Z durieuxp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm.jta.rmi;

import org.objectweb.jotm.TraceTm;
import org.ow2.carol.rmi.interceptor.spi.JInitializer;
import org.ow2.carol.rmi.interceptor.api.JInitInfo;

/**
 * Class <code>JTAInterceptorInitializer</code> is a JRMP Initiliazer
 * for Transaction context propagation
 *
 * @author  Guillaume Riviere (Guillaume.Riviere@inrialpes.fr)
 * @version 1.0, 13/09/2002
 */
public class JTAInterceptorInitializer implements JInitializer {

    // Commented out all the tracing in this module, generated
    // warnings when integrated with JONAS at JONAS START.
    // Need to resolve whenever JONAS uses log4j.
    // private static Log log =
    //     LogFactory.getLog("org.objectweb.jotm.jta.rmi.server");

    /**
     * In JRMP the 2 method( per and post init have the same
     * consequences ...
     * @param info  JInitInfo the JInit Information
     */
    public void preInit(JInitInfo info) {
        if (TraceTm.jta.isDebugEnabled()) {
            TraceTm.jta.debug("JTAInterceptorInitializer.pre_init");
        }

        try {
            info.addClientRequestInterceptor(
                new JTAClientTransactionInterceptor());
            info.addServerRequestInterceptor(
                new JTAServerTransactionInterceptor());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * In JRMP the 2 method( per and post init have the same
     * consequences ...
     * @param info  JInitInfo the JInit Information
     */
    public void postInit(JInitInfo info) {
        // do nothing
    }

}
