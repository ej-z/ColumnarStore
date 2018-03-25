package index;

import bitmap.BMPage;
import bitmap.BitMapFile;
import btree.*;
import bufmgr.PageNotReadException;
import btree.PinPageException;
import columnar.Columnarfile;
import columnar.TID;
import columnar.ValueClass;
import diskmgr.Page;
import global.*;
import heap.*;
import iterator.*;
import org.w3c.dom.Attr;

import java.io.IOException;


/**
 * Created by dixith on 3/18/18.
 */

public class ColumnIndexScan extends Iterator implements GlobalConst {
    private final String indName;
    private final AttrType indexAttrType;
    private final short str_sizes;
    private BitMapFile bmIndFile;
    private PageId currentPageId;
    private Columnarfile columnarfile;
    private byte[] bitMaps;
    private BMPage currentBMPage;
    private int counter;
    private int scanCounter = 0;
    private Heapfile[] targetHeapFiles = null;
    private AttrType[] targetAttrTypes = null;
    private short[] targetShortSizes = null;
    private short[] givenTargetedCols = null;
    private Boolean isIndexOnlyQuery = false;
    private IndexType indexType;
    private IndexFile btIndFile;
    private IndexFileScan btIndScan;
    private CondExpr[] _selects;
    private Heapfile columnFile;
    private boolean index_only;
    private String value;

    public ColumnIndexScan(IndexType index,
                           String relName,
                           String indName, // R.A.5
                           AttrType indexAttrType,
                           short str_sizes,
                           CondExpr[] selects, // buils R.A.5
                           boolean indexOnly,
                           short[] targetedCols) throws IndexException, UnknownIndexTypeException {


        targetHeapFiles = new Heapfile[targetedCols.length];
        targetAttrTypes = new AttrType[targetedCols.length];
        targetShortSizes = new short[targetedCols.length];
        givenTargetedCols = targetedCols;
        this.indName = indName;
        this.indexAttrType = indexAttrType;
        this.str_sizes = str_sizes;
        indexType = index;
        _selects = selects;
        index_only = indexOnly;

        try {

            columnarfile = new Columnarfile(relName);
            setTargetHeapFiles(relName, targetedCols);
            setTargetColumnAttributeTypes(targetedCols);
            setTargetColuumStringSizes(targetedCols);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        switch (index.indexType) {
            case IndexType.B_Index:
                try {
                    btIndFile = new BTreeFile(indName);
                } catch (Exception e) {
                    throw new IndexException(e, "IndexScan.java: BTreeFile exceptions caught from BTreeFile constructor");
                }

                try {
                    btIndScan = IndexUtils.BTree_scan(selects, btIndFile);
                    int columnNo = Integer.parseInt(indName.substring(columnarfile.getColumnarFileName().length() + 2));
                    columnFile = columnarfile.getColumn(columnNo);
                } catch (Exception e) {
                    throw new IndexException(e, "IndexScan.java: BTreeFile exceptions caught from IndexUtils.BTree_scan().");
                }
                break;
            case IndexType.BitMapIndex:
                try {
                    bmIndFile = new BitMapFile(indName);
                    currentPageId = bmIndFile.getHeaderPage().get_rootId();
                    value = bmIndFile.getHeaderPage().getValue();
                    currentBMPage = new BMPage(pinPage(currentPageId));
                    counter = currentBMPage.getCounter();
                    bitMaps = new BMPage(pinPage(currentPageId)).getBMpageArray();
                } catch (Exception e) {
                    // any exception is swalled into a Index Exception
                    throw new IndexException(e, "IndexScan.java: BTreeFile exceptions caught from BTreeFile constructor");
                }

                break;
            case IndexType.None:
            default:
                throw new UnknownIndexTypeException("Only BTree index is supported so far");

        }
    }

    @Override
    public Tuple get_next() throws IndexException, UnknownKeyTypeException {

        if(indexType.indexType == IndexType.B_Index){
            return get_next_BT();
        }
        else if(indexType.indexType == IndexType.BitMapIndex){
            return get_next_BM();
        }
        return null;
    }

    public Tuple get_next_BT() throws IndexException, UnknownKeyTypeException {
        RID rid;
        KeyDataEntry nextentry = null;

        try {
            nextentry = btIndScan.get_next();
        } catch (Exception e) {
            throw new IndexException(e, "IndexScan.java: BTree error");
        }

        if (nextentry == null)
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
            rid = ((LeafData) nextentry.data).getData();
            int postion = columnFile.positionOfRecord(rid);
            for (int i = 0; i < targetHeapFiles.length; i++) {
                RID r = targetHeapFiles[i].recordAtPosition(postion);
                Tuple record = targetHeapFiles[i].getRecord(r);
                switch (targetAttrTypes[i].attrType) {
                    case AttrType.attrInteger:
                        // Assumed that col heap page will have only one entry
                        JTuple.setIntFld(i + 1, record.getIntFld(1));
                        break;
                    case AttrType.attrString:
                        JTuple.setStrFld(i + 1, record.getStrFld(1));
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

    public Tuple get_next_BM(){
        try {

            if (scanCounter > counter) {
                PageId nextPage = currentBMPage.getNextPage();
                unpinPage(currentPageId, false);
                if (nextPage.pid != INVALID_PAGE) {
                    currentPageId.copyPageId(nextPage);
                    currentBMPage = new BMPage(pinPage(currentPageId));
                    counter = currentBMPage.getCounter();
                    bitMaps = currentBMPage.getBMpageArray();
                } else {
                    close();
                    return null;
                }
            }
            // tuple that needs to sent
            Tuple JTuple = new Tuple();
            // set the header which attribute types of the targeted columns
            JTuple.setHdr((short) givenTargetedCols.length, targetAttrTypes, targetShortSizes);
            JTuple = new Tuple(JTuple.size());
            JTuple.setHdr((short) givenTargetedCols.length, targetAttrTypes, targetShortSizes);

            if (bitMaps[scanCounter] == 1) {
                if (index_only) {
                    switch (indexAttrType.attrType) {
                        case AttrType.attrInteger:
                            // Assumed that col heap page will have only one entry
                            JTuple.setIntFld(1, Integer.parseInt(value));
                            break;
                        case AttrType.attrString:
                            JTuple.setStrFld(1, value);
                            break;
                        default:
                            throw new Exception("Attribute indexAttrType not supported");
                    }
                } else {
                    for (int i = 0; i < targetHeapFiles.length; i++) {
                        RID rid = targetHeapFiles[i].recordAtPosition(scanCounter);
                        Tuple record = targetHeapFiles[i].getRecord(rid);
                        switch (targetAttrTypes[i].attrType) {
                            case AttrType.attrInteger:
                                // Assumed that col heap page will have only one entry
                                JTuple.setIntFld(i + 1, record.getIntFld(1));
                                break;
                            case AttrType.attrString:
                                JTuple.setStrFld(i + 1, record.getStrFld(1));
                                break;
                            default:
                                throw new Exception("Attribute indexAttrType not supported");
                        }
                    }
                    // increment the scan counter on every get_next() call
                    scanCounter++;
                    // return the Tuple built by scanning the targeted columns
                }
                return JTuple;
            } else {
                scanCounter++;
            }
        }
        catch (Exception e){
            System.err.println(scanCounter);
            e.printStackTrace();
        }
        return null;
    }

    public void close() throws IOException, JoinsException, SortException, IndexException, HFBufMgrException {
        if (!closeFlag) {
            closeFlag = true;

            if(indexType.indexType == IndexType.B_Index){
                btIndFile = null;
                btIndScan = null;
            }
            else if(indexType.indexType == IndexType.BitMapIndex) {
                unpinPage(currentPageId, false);
            }
        }
    }

    private Page pinPage(PageId pageno) throws PinPageException {
        try {
            Page page = new Page();
            SystemDefs.JavabaseBM.pinPage(pageno, page, false/*Rdisk*/);
            return page;
        } catch (Exception e) {
            e.printStackTrace();
            throw new PinPageException(e, "");
        }
    }

    /**
     * short cut to access the unpinPage function in bufmgr package.
     *
     * @see bufmgr.unpinPage
     */
    private void unpinPage(PageId pageno, boolean dirty)
            throws HFBufMgrException {

        try {
            SystemDefs.JavabaseBM.unpinPage(pageno, dirty);
        } catch (Exception e) {
            throw new HFBufMgrException(e, "Heapfile.java: unpinPage() failed");
        }

    }

    /*
    * Gets the attribute string sizes from the coulumar file
    * and required for the seting the tuple header for the projection
    * */
    private void setTargetColuumStringSizes(short[] targetedCols) {
        short[] attributeStringSizes = columnarfile.getStringSizes();

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