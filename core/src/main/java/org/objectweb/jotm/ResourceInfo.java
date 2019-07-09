/*
 * @(#) ResourceInfo.java 
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
 * $Id: ResourceInfo.java,v 1.11 2003-12-10 20:06:26 trentshue Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

/**
 * Information about 1 Resource logged
 */
class ResourceInfo {

    private Resource myres;
	int mystate;
	
    /**
     * Constructor 
     * called at prepare if VOTE_COMMIT
     */
    ResourceInfo(Resource res) {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("res=" + res);
        }
        myres = res;
        mystate = PREPARED;
    }

	Resource getResource() {
		if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("res="+ myres);
        }
        return myres;		
	}
	
    // Resource state
    static final int REGISTERED = 1;
    static final int PREPARED = 2;
    static final int COMMITTED = 3;
    static final int ROLLEDBACK = 4;
    static final int HEURISTIC_COMMIT = 5;
    static final int HEURISTIC_ROLLBACK = 6;
    static final int HEURISTIC_MIXED = 7;
    static final int HEURISTIC_HAZARD = 8;
}
