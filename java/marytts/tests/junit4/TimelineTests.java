/**
 * Copyright 2000-2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.tests.junit4;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import marytts.exceptions.MaryConfigurationException;
import marytts.tools.voiceimport.TimelineWriter;
import marytts.unitselection.data.Datagram;
import marytts.unitselection.data.TimelineReader;
import marytts.util.Pair;

/**
 * Provides the actual timeline test case for the timeline reading/writing symmetry.
 */
public class TimelineTests extends TimelineReader {
    private static TimelineTests tlr;
    private static String hdrContents;
    private static int NUMDATAGRAMS;
    private static int MAXDATAGRAMBYTESIZE;
    private static int MAXDATAGRAMDURATION;
    private static int sampleRate;
    private static Datagram[] origDatagrams;
    private static final String tlFileName = "timelineTest.bin";
    
    public TimelineTests() throws Exception {
        super(tlFileName);
    }
    
    @BeforeClass
    public static void setUp() throws Exception {
        Random rand = new Random(); // New random number generator
        
        NUMDATAGRAMS = rand.nextInt( 87 ) + 4; // Number of datagrams to test with, between 4 and 100.
        System.out.println( "Testing with [" + NUMDATAGRAMS + "] random datagrams." );
        MAXDATAGRAMBYTESIZE = 64; // Maximum datagram length in bytes
        MAXDATAGRAMDURATION = 20; // Maximum datagram duration (in samples)
        hdrContents = "Blah This is the procHeader Blah";
        sampleRate = 1000;
        
        origDatagrams = new Datagram[NUMDATAGRAMS]; // An array of datagrams with random length
        int len = 0;
        long dur = 0l;
        
        
        /* Fill the array of random datagrams */
        long lenCumul = 74;
        long durCumul = 0l;
        for ( int i = 0; i < NUMDATAGRAMS; i++ ) {
            /* Make a random length */
            len = rand.nextInt(MAXDATAGRAMBYTESIZE) + 1;
            lenCumul += (len + 12);
            /* Allocate the corresponding byte array */
            byte[] buff = new byte[len];
            /* Fill all the bytes with the datagram index */
            for ( int l = 0; l < len; l++ ) {
                buff[l] = (byte) i;
            }
            /* Make a random datagram duration */
            dur = (long)( rand.nextInt(MAXDATAGRAMDURATION) + 1 );
            durCumul += dur;
            /* Store the datagram */
            origDatagrams[i] = new Datagram( dur, buff );
            /* Check */
            System.out.println( "[ " + len + " , " + dur + " ]\t( " + lenCumul + " , " + durCumul + " )" );
        }
        
        /* Write the datagram array in a timeline */
        System.out.println( "Opening new timeline file..." );
        TimelineWriter tlw = new TimelineWriter( tlFileName , hdrContents, sampleRate, 0.1d );
        System.out.println( "Feeding..." );
        tlw.feed( origDatagrams, sampleRate );
        System.out.println( "Closing..." );
        tlw.close();
        System.out.println( "Done." );
        
        System.out.println( "Datagram zone pos. = " + tlw.getDatagramsBytePos() );
        System.out.println( "WRITTEN INDEX:" );
        tlw.getIndex().print();
        /* Testing the readonly file opening */
        System.out.println( "Testing the TimelineReader construction..." );
        /* Re-read the datagrams */
        tlr = new TimelineTests();
    }
    
    
    @Test
    public void procHeader() throws IOException {
        /* Check the procHeader */
        Assert.assertEquals( "The procHeader is out of sync.", tlr.getProcHeaderContents(), hdrContents );
    }

    @Test
    public void numDatagrams() throws IOException {
        /* Check the number of datagrams */
        Assert.assertEquals( "numDatagrams is out of sync.", tlr.getNumDatagrams(), NUMDATAGRAMS );
    }


    @Test
    public void testSkip() throws IOException {
        System.out.println( "READ INDEX:" );
        tlr.getIndex().print();
        /* Testing skip */
        System.out.println( "Testing skip..." );
        Datagram[] readDatagrams = new Datagram[NUMDATAGRAMS];
        long timeNow = 0;
        long timeBefore = 0;
        Pair<ByteBuffer, Long> p = tlr.getByteBufferAtTime(timeNow);
        ByteBuffer bb = p.getFirst();
        int byteNow = bb.position();
        int byteBefore = 0;
        for ( int i = 0; i < NUMDATAGRAMS; i++ ) {
            timeBefore = timeNow;
            byteBefore = byteNow;
            try {
                long skippedDuration = tlr.skipNextDatagram(bb);
                timeNow += skippedDuration;
            } catch (BufferUnderflowException e) {
                // reached end of byte buffer
                break;
            }
            byteNow = bb.position();
            Assert.assertEquals( "Skipping fails on datagram [" + i + "].", (long)(origDatagrams[i].getLength()) + 12l, (byteNow - byteBefore) );
            Assert.assertEquals( "Time is out of sync after skipping datagram [" + i + "].", origDatagrams[i].getDuration(), (timeNow - timeBefore) );
        }
        
        /* Testing the EOF trap for skip */
        try {
            tlr.skipNextDatagram(bb);
            Assert.fail("should have thrown BufferUnderflowException to indicate end of byte buffer");
        } catch(BufferUnderflowException e) {
            // OK, expected
        }

    }



    @Test
    public void testGet() throws IOException {

        /* Testing get */
        System.out.println( "Testing get..." );
        Datagram[] readDatagrams = new Datagram[NUMDATAGRAMS];
        Pair<ByteBuffer, Long> p = tlr.getByteBufferAtTime(0);
        ByteBuffer bb = p.getFirst();
        for ( int i = 0; i < NUMDATAGRAMS; i++ ) {
            readDatagrams[i] = tlr.getNextDatagram(bb);
            if (readDatagrams[i] == null) {
                System.err.println("Could read "+i+" datagrams");
                break;
            }
            Assert.assertTrue( "Datagram [" + i + "] is out of sync.", areEqual( origDatagrams[i].getData(), readDatagrams[i].getData() ) );
            Assert.assertEquals( "Time for datagram [" + i + "] is out of sync.", origDatagrams[i].getDuration(), readDatagrams[i].getDuration() );
        }
        /* Testing the EOF trap for get */
        Assert.assertEquals( null, tlr.getNextDatagram(bb) );
    }
    
    @Test
    public void timeDrivenAccess() throws IOException {
        /* Testing the time-driven access */
        final int testIdx = NUMDATAGRAMS / 2;
        long onTime = 0l;
        for( int i = 0; i < testIdx; i++ ) {
            onTime += origDatagrams[i].getDuration();
        }
        long afterTime = onTime + origDatagrams[testIdx].getDuration();
        long midTime = onTime + ((afterTime - onTime) / 2);
        
        System.out.println( "Testing gotoTime 1 ..." );
        ByteBuffer bb = tlr.getByteBufferAtTime(onTime).getFirst();
        Datagram d = tlr.getNextDatagram(bb);
        Assert.assertTrue( d.equals( origDatagrams[testIdx] ) );
        
        System.out.println( "Testing gotoTime 2 ..." );
        bb = tlr.getByteBufferAtTime(midTime).getFirst();
        d = tlr.getNextDatagram(bb);
        Assert.assertTrue( d.equals( origDatagrams[testIdx] ) );
        
        System.out.println( "Testing gotoTime 3 ..." );
         bb = tlr.getByteBufferAtTime(afterTime).getFirst();
         d = tlr.getNextDatagram(bb);
        Assert.assertTrue( d.equals( origDatagrams[testIdx+1] ) );

        /* Testing time-spanned access */
        System.out.println( "Testing getDatagrams  1 ..." );
        Datagram[] D = null;
        long span = origDatagrams[testIdx].getDuration();
        long[] offset = new long[1];
        D = tlr.getDatagrams( onTime, span, sampleRate, offset );
        Assert.assertEquals( 1, D.length );
        Assert.assertTrue( D[0].equals( origDatagrams[testIdx] ) );
        Assert.assertEquals( 0l, offset[0] );
        
        System.out.println( "Testing getDatagrams  2 ..." );
        span = origDatagrams[testIdx].getDuration() / 2;
        D = tlr.getDatagrams( onTime, span, sampleRate );
        Assert.assertEquals( 1, D.length );
        Assert.assertTrue( D[0].equals( origDatagrams[testIdx] ) );
        
        System.out.println( "Testing getDatagrams  3 ..." );
        span = origDatagrams[testIdx].getDuration() / 2;
        D = tlr.getDatagrams( midTime, span, sampleRate );
        Assert.assertEquals( 1, D.length );
        Assert.assertTrue( D[0].equals( origDatagrams[testIdx] ) );
        
        System.out.println( "Testing getDatagrams  4 ..." );
        span = origDatagrams[testIdx].getDuration() + 1;
        D = tlr.getDatagrams( onTime, span, sampleRate );
        Assert.assertEquals( 2, D.length );
        Assert.assertTrue( D[0].equals( origDatagrams[testIdx] ) );
        Assert.assertTrue( D[1].equals( origDatagrams[testIdx+1] ) );
        
        System.out.println( "Testing getDatagrams  5 ..." );
        span = origDatagrams[testIdx].getDuration() + origDatagrams[testIdx+1].getDuration();
        D = tlr.getDatagrams( onTime, span, sampleRate );
        Assert.assertEquals( 2, D.length );
        Assert.assertTrue( D[0].equals( origDatagrams[testIdx] ) );
        Assert.assertTrue( D[1].equals( origDatagrams[testIdx+1] ) );
        
        System.out.println( "Testing getDatagrams  6 ..." );
        span = origDatagrams[testIdx].getDuration() + origDatagrams[testIdx+1].getDuration();
        D = tlr.getDatagrams( onTime+1, span, sampleRate, offset );
        Assert.assertEquals( 3, D.length );
        Assert.assertTrue( D[0].equals( origDatagrams[testIdx] ) );
        Assert.assertTrue( D[1].equals( origDatagrams[testIdx+1] ) );
        Assert.assertTrue( D[2].equals( origDatagrams[testIdx+2] ) );
        Assert.assertEquals( 1l, offset[0] );
        
        System.out.println( "Testing getDatagrams  7 ..." );
        span = origDatagrams[testIdx].getDuration() / 2;
        D = tlr.getDatagrams( midTime, 2, sampleRate, offset );
        Assert.assertEquals( 2, D.length );
        Assert.assertTrue( D[0].equals( origDatagrams[testIdx] ) );
        Assert.assertTrue( D[1].equals( origDatagrams[testIdx+1] ) );
        Assert.assertEquals( ((afterTime - onTime) / 2), offset[0] );
        
        /* Testing time-spanned access with alternate sample rate */
        System.out.println( "Testing getDatagrams with alternate sample rate ..." );
        span = origDatagrams[testIdx].getDuration();
        D = tlr.getDatagrams( onTime*2, span*2, sampleRate/2 );
        Assert.assertEquals( 1, D.length );
        Assert.assertTrue( areEqual(D[0].getData(), origDatagrams[testIdx].getData()));
        Assert.assertTrue( D[0].getDuration() != origDatagrams[testIdx].getDuration());
        
    }


    @Test
    public void getLastDatagram() throws MaryConfigurationException, IOException {
        long totalDur = tlr.getTotalDuration();
        Assert.assertTrue(totalDur > 0);
        
        Datagram d = tlr.getDatagram(totalDur - 1);
        Assert.assertTrue(d != null);
        
        long dur = d.getDuration();
        
        d = tlr.getDatagram(totalDur - dur);
        Assert.assertTrue(d != null);
    }
    
    @Test
    public void getLastDatagrams() throws MaryConfigurationException, IOException {
        long totalDur = tlr.getTotalDuration();
        Assert.assertTrue(totalDur > 0);
        
        Datagram[] ds = tlr.getDatagrams(totalDur - 1, 1);
        Assert.assertTrue(ds != null);
        Assert.assertTrue(ds.length == 1);

        ds = tlr.getDatagrams(totalDur - 1, 2);
        Assert.assertTrue(ds != null);
        Assert.assertTrue(ds.length == 1);
    }

    @Test
    public void cannotGetAfterLastDatagram() throws MaryConfigurationException, IOException {
        long totalDur = tlr.getTotalDuration();
        Assert.assertTrue(totalDur > 0);

        try {
            Datagram d = tlr.getDatagram(totalDur);
            Assert.fail("Should have thrown a BufferUnderflowException");
        } catch (BufferUnderflowException e) {
            // OK, expected
        }
    }

    @Test
    public void cannotGetAfterLastDatagrams() throws MaryConfigurationException, IOException {
        long totalDur = tlr.getTotalDuration();
        Assert.assertTrue(totalDur > 0);

        try {
            Datagram[] ds = tlr.getDatagrams(totalDur, 1);
            Assert.fail("Should have thrown a BufferUnderflowException");
        } catch (BufferUnderflowException e) {
            // OK, expected
        }
    }

    @AfterClass
    public static void tearDown() throws IOException {
        /* Delete the test file */
        File fid = new File( tlFileName );
        fid.delete();
        
        /* ----------- */
        System.out.println("End of the timeline symmetry test.");
   }

    
    
    
    /**
     * Compare two arrays of long.
     * 
     * @param a1 an array of longs
     * @param a2 an array of longs
     * @return true if they are equal, false if not.
     */
    private static boolean areEqual( long[] a1, long[] a2 ) {
        /* Check number of longs */
        if ( a1.length != a2.length ) return false;
        /* Check contents */
        for ( int i = 0; i < a1.length; i++ ) {
            if ( a1[i] != a2[i] ) return false;
        }
        /* If all passed, then the arrays are equal */
        return( true );
    }
    
    /**
     * Compare two arrays of bytes.
     * 
     * @param a1 an array of bytes
     * @param a2 an array of bytes
     * @return true if they are equal, false if not.
     */
    private static boolean areEqual( byte[] a1, byte[] a2 ) {
        /* Check number of longs */
        if ( a1.length != a2.length ) return false;
        /* Check contents */
        for ( int i = 0; i < a1.length; i++ ) {
            if ( a1[i] != a2[i] ) return false;
        }
        /* If all passed, then the arrays are equal */
        return( true );
    }
    
}

