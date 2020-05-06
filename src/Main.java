import java.io.*;

/**
 * Usage: compander.jar encode|decode filename
 *
 * Encoding file must be raw linear PCM with 16-bit, little endian, monoaural.
 *
 * - Preferred Sampling rate: 32000 or lower
 * - Amplify the audio so that highest peak would be +1.184 dB (+1.146 linear amplitude) (input audio will contain clipping. This is done to maximise the bits usage)
 */
public class Main {

    private static short[] enlawTable = {30607,26699,23282,20294,17681,15396,13397,11650,10122,8786,7618,6596,5703,4921,4238,3641,3118,2662,2262,1913,1607,1340,1107,902,724,568,431,312,207,116,36,0,-37,-117,-208,-313,-432,-569,-725,-903,-1108,-1341,-1608,-1914,-2263,-2663,-3119,-3642,-4239,-4922,-5704,-6597,-7619,-8787,-10123,-11651,-13398,-15397,-17682,-20295,-23283,-26700,-30608,-32768};
    private static short[] delawTable = {0,75,160,258,369,497,643,810,1001,1220,1469,1755,2082,2455,2882,3371,3929,4568,5299,6134,7090,8182,9432,10860,12494,14363,16500,18943,21738,24933,28588,32767,-32768,-28589,-24934,-21739,-18944,-16501,-14364,-12495,-10861,-9433,-8183,-7091,-6135,-5300,-4569,-3930,-3372,-2883,-2456,-2083,-1756,-1470,-1221,-1002,-811,-644,-498,-370,-259,-161,-76,-1};

    private static int _enlaw(short x) {
        for (int i = 0; i < enlawTable.length - 1; i++) {
            if (x >= enlawTable[i]) return i;
        }
        return enlawTable.length - 1;
    }

    /**
     *
     * @param x
     * @return 0..31
     */
    private static int enlaw(short x) {
        int index = _enlaw(x);
        // flip the table
        if (index < 32)
            return 31 - index;
        else
            return 95 - index;

    }

    /**
     *
     * @param y 0..31
     * @return
     */
    private static short delaw(int y) {
        return delawTable[y];
    }

    private static float shortToFloat(short s) {
        return ((float) (s)) / 32767f;
    }

    private static short floatToShort(float f) {
        return (short) (f * 32767f);
    }

    private static int[] outQueue = new int[4];
    private static int outQueueCount = 0;

    private static short[] outWords = new short[4];
    private static int outWordsCount = 0;

    private static void addOutQueue(int element) {
        outQueue[outQueueCount] = element;
        outQueueCount += 1;
    }

    private static void addOutWords(short element) {
        outWords[outWordsCount] = element;
        outWordsCount += 1;
    }

    private static int readCount = 0;

    private static void encode(File infile, File outfile) throws FileNotFoundException, IOException {
        System.out.println("Encoding...");

        BufferedInputStream fis = new BufferedInputStream(new FileInputStream(infile));
        BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(outfile));
        boolean eofReached = false;

        readCount = 0;
        long filesize = infile.length();
        while (!eofReached) {

            System.out.println("Read "+((readCount * 1600f) / filesize)+" %");

            outQueueCount = 0;

            // read two bytes (one short) and enlaw and quantise it
            for (int k = 0; k < 4; k++) {
                int byte1 = fis.read();
                int byte2 = fis.read();

                fis.read(); fis.read();

                if (byte1 < 0 || byte2 < 0) eofReached = true;
                if (byte2 < 0) byte2 = 0;
                if (byte1 < 0) byte1 = 0;

                short inShort = (short) ((byte1 & 0xff) | ((byte2 & 0xff) << 8));
                int outQuantised = enlaw(inShort);
                addOutQueue(outQuantised);
            }

            // 0b 111111 222222 333333 444444
            int outShort = (outQueue[0] << 18) | (outQueue[1] << 12) | (outQueue[2] << 6) | outQueue[3];

            // 0b11111122 0b22223333 0b33444444
            fos.write((outShort >> 16) & 0xFF);
            fos.write((outShort >> 8) & 0xFF);
            fos.write(outShort & 0xFF);


            readCount += 1;
        }

        fos.flush();
        fos.close();
        fis.close();
    }


    private static short oldShort = 0;
    private static void decode(File infile, File outfile) throws FileNotFoundException, IOException {
        System.out.println("Decoding...");

        BufferedInputStream fis = new BufferedInputStream(new FileInputStream(infile));
        BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(outfile));
        boolean eofReached = false;

        readCount = 0;
        long filesize = infile.length();
        while (!eofReached) {

            System.out.println("Read "+((readCount * 300f) / filesize)+" %");

            int byte1 = fis.read();
            int byte2 = fis.read();
            int byte3 = fis.read();

            if (byte1 < 0 || byte2 < 0 || byte3 < 0) eofReached = true;
            if (byte3 < 0) byte3 = 0;
            if (byte2 < 0) byte2 = 0;
            if (byte1 < 0) byte1 = 0;

            // 0b11111122 0b22223333 0b33444444
            int inint = (byte3 & 0xff) | ((byte2 & 0xff) << 8) | ((byte1 & 0xff) << 16);

            int[] inQs = {
                    (inint & 0b111111_000000_000000_000000) >> 18,
                    (inint & 0b000000_111111_000000_000000) >> 12,
                    (inint & 0b000000_000000_111111_000000) >> 6,
                    (inint & 0b000000_000000_000000_111111)

            };

            // max. 4 shorts
            outWordsCount = 0;

            for (int i : inQs) {
                addOutWords(delaw(i));
            }

            for (short s : outWords) {
                short s1 = (short) ((oldShort + s) >> 1);
                byte lowbyte = (byte) (s & 0xFF);
                byte highbyte = (byte) ((s >> 8) & 0xFF);
                byte lowbyte1 = (byte) (s1 & 0xFF);
                byte highbyte1 = (byte) ((s1 >> 8) & 0xFF);

                fos.write(lowbyte1);
                fos.write(highbyte1);
                fos.write(lowbyte);
                fos.write(highbyte);

                oldShort = s;
            }

            readCount += 1;
        }

        fos.flush();
        fos.close();
        fis.close();

    }
    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Usage: encode|decode infile");

            System.exit(0);
        }

        File infile = new File(args[1]);
	    File outfile = new File(args[1]+"."+args[0]+"d");

	    try {
            if (args[0].equals("encode")) {
                encode(infile, outfile);
            }
            else if (args[0].equals("decode")) {
                decode(infile, outfile);
            }
        }
	    catch (Throwable e) {
	        e.printStackTrace();
        }
    }
}
