/**
 * @(#) OTSORBInitializer.java 1.0 02/07/15
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
 * $Id: OTSORBInitializer.java 1104 2010-01-12 14:58:06Z durieuxp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm.ots;

import org.omg.CORBA.LocalObject;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ORBInitializer;

/**
 * Class <code>OTSORBInitializer</code> is a OTS Interceptor initialisation
 * for JOTM.
 *
 * @author  Guillaume Riviere (Guillaume.Riviere@inrialpes.fr)
 */
public class OTSORBInitializer extends LocalObject implements ORBInitializer {


    /**
     * Do nothing.
     * {@inheritDoc}
     */
    public void pre_init(final ORBInitInfo info) {
        // do nothing
    }

    /**
     * Register our Transaction interceptors (Client and Server).
     * @param info ORB object to configure
     */
    public void post_init(final ORBInitInfo info) {
        try {
            info.add_client_request_interceptor(new OTSClientTransactionInterceptor(info));
            info.add_server_request_interceptor(new OTSServerTransactionInterceptor(info));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
