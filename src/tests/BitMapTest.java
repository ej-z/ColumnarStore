package tests;

import bitmap.BM;
import bitmap.BitMapFile;
import columnar.Columnarfile;
import columnar.TID;
import columnar.TupleScan;
import columnar.ValueInt;
import global.AttrType;
import global.SystemDefs;
import heap.Tuple;

import static global.GlobalConst.NUMBUF;

class BitMapDriver extends TestDriver {

    private int numPages = 1000;

    //private boolean delete = true;
    public BitMapDriver() {
        super("bitmaptest");
    }

    public boolean runTests() {
        System.out.println("\n" + "Running " + testName() + " tests...." + "\n");
        SystemDefs sysdef = new SystemDefs(dbpath, numPages, NUMBUF, "Clock");

        boolean _pass = runAllTests();
        try {
            SystemDefs.JavabaseBM.flushAllPages();
            SystemDefs.JavabaseDB.closeDB();
        } catch (Exception e) {
            System.err.println("error: " + e);
        }

        System.out.print("\n" + "..." + testName() + " tests ");
        System.out.print(_pass == OK ? "completely successfully" : "failed");
        System.out.print(".\n\n");

        return _pass;
    }

    protected boolean test1() {
        try {
            String name = "file1";
            int numColumns = 3;
            AttrType[] types = new AttrType[numColumns];
            types[0] = new AttrType(AttrType.attrInteger);
            types[1] = new AttrType(AttrType.attrReal);
            types[2] = new AttrType(AttrType.attrString);
            short[] sizes = new short[1];
            sizes[0] = 20;
            Columnarfile cf = new Columnarfile(name, numColumns, types, sizes);

            for (int i = 0; i < 2000; i++) {
                Tuple t = new Tuple();
                t.setHdr((short) 3, types, sizes);
                int s = t.size();
                t = new Tuple(s);
                t.setHdr((short) 3, types, sizes);
                t.setIntFld(1, i % 5);
                t.setFloFld(2, (float) (i * 1.1));
                t.setStrFld(3, "A" + i);
                cf.insertTuple(t.getTupleByteArray());
            }

            BitMapFile bitMapFile = new BitMapFile("bitmap_file1", cf, 1, new ValueInt(4));
            TupleScan scan = cf.openTupleScan();
            TID tid = new TID();
            Tuple t = scan.getNext(tid);
            Integer count = 1;
            while (t != null) {
                if (t.getIntFld(1) == 4) {
                    bitMapFile.insert(count);
                } else {
                    bitMapFile.delete(count);
                }
                count++;
                t = scan.getNext(tid);
            }
            scan.closetuplescan();
            BM bm = new BM();
            bm.printBitMap(bitMapFile.getHeaderPage());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    protected boolean test2() {
        return true;
    }

    protected boolean test3() {
        return true;
    }

    protected boolean test4() {
        return true;
    }

    protected boolean test5() {
        return true;
    }

    protected boolean test6() {
        return true;
    }

    protected String testName() {
        return "Bit Map file";
    }
}

public class BitMapTest {
    public static void runTests() {
        BitMapDriver bm = new BitMapDriver();
        bm.runTests();
    }

    public static void main(String[] argvs) {
        try {
            BitMapTest bitMapTest = new BitMapTest();
            bitMapTest.runTests();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error encountered during buffer manager tests:\n");
            Runtime.getRuntime().exit(1);
        }
    }
}