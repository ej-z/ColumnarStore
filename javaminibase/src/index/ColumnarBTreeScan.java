package index;

import btree.*;
import columnar.Columnarfile;
import diskmgr.Page;
import global.*;
import heap.*;
import iterator.*;

import java.io.IOException;

public class ColumnarBTreeScan extends Iterator implements GlobalConst{

    private final String indName;
    private final AttrType indexAttrType;
    private final short str_sizes;
    private Columnarfile columnarfile;
    private Heapfile[] targetHeapFiles = null;
    private AttrType[] targetAttrTypes = null;
    private short[] targetShortSizes = null;
    private short[] givenTargetedCols = null;
    private BTreeFile btIndFile;
    private IndexFileScan btIndScan;
    private CondExpr[] _selects;
    private Heapfile columnFile;
    private boolean index_only;

    public ColumnarBTreeScan (String relName,
                             String indName,
                             AttrType indexAttrType,
                             short str_sizes,
                             CondExpr[] selects,
                             boolean indexOnly,
                             short[] targetedCols) throws IndexException {

        targetHeapFiles = new Heapfile[targetedCols.length];
        targetAttrTypes = new AttrType[targetedCols.length];
        targetShortSizes = new short[targetedCols.length];
        givenTargetedCols = targetedCols;
        this.indName = indName;
        this.indexAttrType = indexAttrType;
        this.str_sizes = str_sizes;
        _selects = selects;
        index_only = indexOnly;

        try {

            columnarfile = new Columnarfile(relName);
            setTargetHeapFiles(relName, targetedCols);
            setTargetColumnAttributeTypes(targetedCols);
            setTargetColumnStringSizes(targetedCols);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            btIndFile = new BTreeFile(indName);
        } catch (Exception e) {
            throw new IndexException(e, "IndexScan.java: BTreeFile exceptions caught from BTreeFile constructor");
        }

        try {
            btIndScan = IndexUtils.BTree_scan(selects, btIndFile);
            int columnNo = Integer.parseInt(indName.substring(columnarfile.getColumnarFileName().length() + 4));
            columnFile = columnarfile.getColumn(columnNo);
        } catch (Exception e) {
            throw new IndexException(e, "IndexScan.java: BTreeFile exceptions caught from IndexUtils.BTree_scan().");
        }
    }

    @Override
    public Tuple get_next() throws IndexException, UnknownKeyTypeException {
        return get_next_BT();
    }

    public boolean delete_next() throws IndexException, UnknownKeyTypeException {

            return delete_next_BT();
    }

    private Tuple get_next_BT() throws IndexException, UnknownKeyTypeException {
        RID rid;
        KeyDataEntry nextentry = new KeyDataEntry();
        int position = -1;
        try {
            position = getNextBTPosition(nextentry);
        } catch (Exception e) {
            throw new IndexException(e, "IndexScan.java: BTree error");
        }

        if (position < 0)
            return null;
        Tuple JTuple = null;
        try {
            JTuple = new Tuple();
            JTuple.setHdr((short) givenTargetedCols.length, targetAttrTypes, targetShortSizes);
            JTuple = new Tuple(JTuple.size());
            JTuple.setHdr((short) givenTargetedCols.length, targetAttrTypes, targetShortSizes);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }

        if (index_only) {
            if (indexAttrType.attrType == AttrType.attrInteger) {
                try {
                    JTuple.setIntFld(1, ((IntegerKey) nextentry.key).getKey().intValue());
                } catch (Exception e) {
                    throw new IndexException(e, "IndexScan.java: Heapfile error");
                }
            } else if (indexAttrType.attrType == AttrType.attrString) {
                try {
                    JTuple.setStrFld(1, ((StringKey) nextentry.key).getKey());
                } catch (Exception e) {
                    throw new IndexException(e, "IndexScan.java: Heapfile error");
                }
            } else {
                // attrReal not supported for now
                throw new UnknownKeyTypeException("Only Integer and String keys are supported so far");
            }
            return JTuple;
        }

        try {
            for (int i = 0; i < targetHeapFiles.length; i++) {
                RID r = targetHeapFiles[i].recordAtPosition(position);
                Tuple record = targetHeapFiles[i].getRecord(r);
                switch (targetAttrTypes[i].attrType) {
                    case AttrType.attrInteger:
                        // Assumed that col heap page will have only one entry
                        JTuple.setIntFld(i + 1,
                                Convert.getIntValue(0, record.getTupleByteArray()));
                        break;
                    case AttrType.attrString:
                        JTuple.setStrFld(i + 1,
                                Convert.getStrValue(0, record.getTupleByteArray(),targetShortSizes[i]+2));
                        break;
                    default:
                        throw new Exception("Attribute indexAttrType not supported");
                }
            }
            return JTuple;
        } catch (Exception e) {
            throw new IndexException(e, "IndexScan.java: getRecord failed");
        }

        //return null;
    }

    private boolean delete_next_BT() throws IndexException, UnknownKeyTypeException {
        KeyDataEntry nextentry = new KeyDataEntry();
        int position = -1;
        try {
            position = getNextBTPosition(nextentry);
        } catch (Exception e) {
            throw new IndexException(e, "IndexScan.java: BTree error");
        }

        if (position < 0)
            return false;

        return columnarfile.markTupleDeleted(position);
    }

    private int getNextBTPosition(KeyDataEntry key) throws IndexException, UnknownKeyTypeException {
        RID rid;
        KeyDataEntry nextentry;
        try {
            nextentry = btIndScan.get_next();
        } catch (Exception e) {
            throw new IndexException(e, "IndexScan.java: BTree error");
        }

        if (nextentry == null)
            return -1;

        if (index_only) {
            key.key = nextentry.key;
            return -1;
        }

        try {
            rid = ((LeafData) nextentry.data).getData();
            int position = columnFile.positionOfRecord(rid);
            return position;
        } catch (Exception e) {
            throw new IndexException(e, "IndexScan.java: getRecord failed");
        }
    }


    public void close() throws IOException, JoinsException, SortException, IndexException, HFBufMgrException {
        if (!closeFlag) {
            closeFlag = true;
            try {
                btIndFile.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            btIndScan = null;
        }
    }

    /*
    * Gets the attribute string sizes from the coulumar file
    * and required for the seting the tuple header for the projection
    * */
    private void setTargetColumnStringSizes(short[] targetedCols) {
        short[] attributeStringSizes = columnarfile.getAttrSizes();

        for(int i=0; i < targetAttrTypes.length; i++) {
            targetShortSizes[i] = attributeStringSizes[targetedCols[i]];
        }
    }

    /*
    * Gets the attribute types of the target columns for the columnar file
    * Is used while setting the Tuple header for the projection
    *
    * */
    private void setTargetColumnAttributeTypes(short[] targetedCols) {
        AttrType[] attributes = columnarfile.getAttributes();

        for(int i=0; i < targetAttrTypes.length; i++) {
            targetAttrTypes[i] = attributes[targetedCols[i]];
        }
    }

    // open the targeted column heap files and store those reference for scanning
    private void setTargetHeapFiles(String relName, short[] targetedCols) throws HFException, HFBufMgrException, HFDiskMgrException, IOException {
        for(int i=0; i < targetedCols.length; i++) {
            targetHeapFiles[i] = new Heapfile(relName + Short.toString(targetedCols[i]));
        }
    }
}