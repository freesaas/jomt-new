/*
 * @(#) JavaXidImpl.java
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
 * $Id: JavaXidImpl.java,v 1.2 2005-04-22 17:49:37 tonyortiz Exp $
 * --------------------------------------------------------------------------
 */
package org.objectweb.jotm;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.transaction.xa.Xid;

/**
 * Xid implementation for JTA
 *
 * XID has the following format as defined by X/Open Specification:
 *
 *     XID
 *         long formatId              format identifier
 *         long gtrid_length          value 1-64
 *         long bqual_length          value 1-64
 *         byte data [XIDDATASIZE]    where XIDDATASIZE = 128
 *
 *     The data field comprises at most two contiguous components:
 *     a global transaction identifier (gtrid) and a branch qualifier (bqual)
 *     which are defined as:
 *
 *         byte gtrid [1-64]          global transaction identfier
 *         byte bqual [1-64]          branch qualifier
 *
 */
public class JavaXidImpl implements Xid, Serializable  {

    public static final int JOTM_FORMAT_ID = 0xBB14;

    // these cells are gated by this.getClass()
    private static SecureRandom rand = null;    // (also used as first-time flag)
    private final  byte internalVersId = 1;
    private static int count = 1;
    private static long uuid0;
    private static long uuid1;
    private static boolean uuidsRecovered = false;
    private static byte[] gtrid_base = null;   // created by makeGtridBase()
    private static String host, server;

    private String fullString = "";
    private String shortString = "";

    private boolean hashcodevalid = false;
    private int myhashcode;

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
     * format id
     * @serial
     */
    private int formatId;

    /**
     * gtrid length
     * @serial
     */
    private int gtrid_length;

    /**
     * bqual length
     * @serial
     */
    private int bqual_length;

    /**
     * global transaction id
     * @serial
     */
    private byte[] gtrid;

    /**
     * branch qualifier
     * @serial
     */
    private byte[] bqual;

    // -------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------

    /**
     * Build an javax.transaction.xa.Xid from the org.objectweb.jotm.Xid
     */
    public JavaXidImpl(org.objectweb.jotm.Xid jotmXid ) {
        if (TraceTm.jotm.isDebugEnabled()) {
            TraceTm.jotm.debug("jotmXID= " + jotmXid);
        }

        formatId = jotmXid.getFormatId();
        gtrid = jotmXid.getGlobalTransactionId();
        gtrid_length = gtrid.length;
        bqual = jotmXid.getBranchQualifier();
        bqual_length = bqual.length;
    }

    // -------------------------------------------------------------------
    // Xid implementation
    // -------------------------------------------------------------------

    /**
     * Get the format id for that Xid
     */
    public int getFormatId() {
        return formatId;
    }

    /**
     * Get the Global Id for that Xid
     */
    public byte[] getGlobalTransactionId() {
        return (byte[]) gtrid.clone();
    }

    /**
     * Get the Branch Qualifier for that Xid
     */
    public byte[] getBranchQualifier() {
        return (byte[]) bqual.clone();
    }

    // -------------------------------------------------------------------
    // other methods
    // -------------------------------------------------------------------

    /**
     * Hex Dump of byte
     */
    static final void byteToHex( byte inbyte, StringBuffer str_buff ) {

        int myByte = 0xFF & inbyte;

        str_buff.append( HexDigits[myByte] );
        return;
    }

    /**
     * String form
     * default toString() compresses Xid's
     */
    public String toString() {
        return this.toString( false );
    }

    public String toString( boolean Full ) {

        byte[] gtrid_local = null;
        byte[] bqual_local = null;

        if (Full && (fullString.length() != 0) ) {
            return fullString;
        } else if (!Full && (shortString.length() != 0) ) {
            return shortString;
        }

        // Buffers need two hex characters per byte
        StringBuffer str_buff_gtrid = new StringBuffer(MAXGTRIDSIZE * 2);
        StringBuffer str_buff_bqual = new StringBuffer(MAXBQUALSIZE * 2);

        gtrid_local = new byte[MAXGTRIDSIZE];
        ByteBuffer aa = ByteBuffer.wrap(gtrid_local);

        System.arraycopy(gtrid, 0, gtrid_local, 0, gtrid_length);

        for (int i=0; i < gtrid_length; i++) {
            byteToHex(aa.get(), str_buff_gtrid );
        }

        bqual_local = new byte[MAXBQUALSIZE];
        ByteBuffer bb = ByteBuffer.wrap(bqual_local);

        if (bqual != null) {
            System.arraycopy(bqual, 0, bqual_local, 0, bqual_length);

            for (int i=0; i < bqual_length; i++) {
                byteToHex(bb.get(), str_buff_bqual);
            }
        }

        if ((gtrid_length > 30) && !Full ) {   // be prepared to reduce output string
            int strlen = str_buff_gtrid.length();
            str_buff_gtrid.replace( (strlen / 6), (strlen / 6) + 2, "...");
            str_buff_gtrid.delete( (strlen / 6) + 3, strlen - 5);
        }

        if ((bqual_length > 30) && !Full ) {   // be prepared to reduce output string
            int strlen = str_buff_bqual.length();
            str_buff_bqual.replace( (strlen / 6), (strlen / 6) + 2, "...");
            str_buff_bqual.delete( (strlen / 6) + 3, strlen - 5);
        }

        if (Full) {
            fullString = Long.toHexString(formatId)     + ":" +
                Long.toHexString(gtrid_length) + ":" +
                Long.toHexString(bqual_length) + ":" +
                str_buff_gtrid.toString()      + ":" +
                str_buff_bqual.toString();
            return fullString;
        }

        shortString = Long.toHexString(formatId) + ":" +
            Long.toHexString(gtrid_length) + ":" +
            Long.toHexString(bqual_length) + ":" +
            str_buff_gtrid.toString()      + ":" +
            str_buff_bqual.toString();
        return shortString;
    }

    /*
     *  make a unique but recognizable gtrid.
     *
     *  format:
     *  1.  internal version identifier - 1 byte
     *  2.  uuid                        - 16 bytes
     *  3.  host name                   - 16 bytes
     *  4.  server name                 - 15 bytes
     *  5.  timestamp                   - 8 bytes
     *  6.  [reserved for use by bqual  - 8 bytes]
     *
     *  Items 1 thru 4 are generated by makeGtridBase and comprise the recognizable
     *  portion of a gtrid or bqual. Together, these serve to make Xids generated on
     *  this system unique from those generated on other systems.
     *  Item 5 is generated by this routine and serves to make gtrids unique.
     *  Item 6 serves to distinguish different bquals belonging
     *  to the same Xid, and is generated elsewhere.
     *
     *  Items 1 thru 4 are used to determine if we (JOTM) generated this gtrid/bqual.
     */
    private byte[] makeGtrid() {
        makeGtridBase();
        long uniqueTimeStamp;

        synchronized(getClass()) {
            uniqueTimeStamp = System.currentTimeMillis() * 1024 + count;
            count++;
        }

        ByteBuffer bb = ByteBuffer.allocate(gtrid_base.length+8);
        bb.put(gtrid_base);
        bb.putLong(uniqueTimeStamp);
        return bb.array();
    }

    /**
     * acquire the configured uuid, host and server name.
     * fabricate a uuid if one does not yet exist.
     * and append a unique timestamp.
     */
    private void makeGtridBase() {

        synchronized(getClass()) {
            if (rand == null) {
                rand = new SecureRandom();

                if (uuidsRecovered == false) {    // first time or no journal
                    //uuid0 = Long.parseLong(System.getProperty("jotm.uuid.part1"),16);
                    //uuid1 = Long.parseLong(System.getProperty("jotm.uuid.part2"),16);
                    uuid0 = rand.nextLong();
                    uuid1 = rand.nextLong();

                    // We build the Inique ID Record in makeGtridBase
                    // Store the Unique ID Record using HOWL so it can manage for us.
                    // It will never have a DONE record written.
                    //
                    // The Unique ID record consists of two fields:
                    //     1. uuid0
                    //     2. uuid1
                    //
                    // The XA Unique ID record format:
                    //     Unique ID record type1 (byte[3]) - 'RU1'
                    //     Unique ID record stored uuido (long) - 8 bytes
                    //     Unique ID record stored uuid1 (long) - 8 bytes
                    //

                    byte [] UniqueID = new byte[3+8+8];
                    byte [] [] UniqueIDRecord = new byte [1][3+8+8];

                    String rt1 = "RU1";

                    ByteBuffer rr1 = ByteBuffer.wrap(UniqueID);
                    rr1.put(rt1.getBytes());
                    rr1.putLong(uuid0);
                    rr1.putLong(uuid1);

                    UniqueIDRecord [0] = UniqueID;
                }

                host = "";
                server = "";
                // make host & server names fixed length, as defined above
                host = (host+"                ").substring(0,15);
                server = (server+"               ").substring(0,14);
                gtrid_base = new byte[1+8+8+16+15];
                ByteBuffer	bb = ByteBuffer.wrap(gtrid_base);
                bb.put(internalVersId);
                bb.putLong(uuid0);
                bb.putLong(uuid1);
                bb.put(host.getBytes());
                bb.put(server.getBytes());
            }
        }
    }

    // -------------------------------------------------------------------
    // equals and hashCode
    // -------------------------------------------------------------------

    /**
     * return true if objects are identical
     */
    public boolean equals(Object obj2) {
        if ((obj2 == null) || (!(obj2 instanceof JavaXidImpl))) {
            return false;
        }
        JavaXidImpl xid2 = (JavaXidImpl) obj2;

        if (formatId == xid2.getFormatId()
            && java.util.Arrays.equals(bqual, xid2.getBranchQualifier())
            && java.util.Arrays.equals(gtrid, xid2.getGlobalTransactionId())) {
            return true;
        }
        return false;
    }

    /**
     * return a hashcode value for this object
     */
    @Override
        public int hashCode() {

        int hc = 0;

        if (hashcodevalid == false) {

            for (int i = 0; i < gtrid.length; i++) {
                hc = hc * 37 + gtrid[i];
            }

            for (int i = 0; i < bqual.length; i++ ) {
                hc = hc * 37 + bqual[i];
            }

            myhashcode = hc;
            hashcodevalid = true;
        }
        return myhashcode;
    }
}
