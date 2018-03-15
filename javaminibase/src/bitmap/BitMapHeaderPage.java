package bitmap;

import btree.ConstructPageException;
import diskmgr.Page;
import global.PageId;
import global.SystemDefs;

import java.io.IOException;

public class BitMapHeaderPage extends BMPage {
    //TODO: Need to discuss this and complete
    /**
     * pin the page with pageno, and get the corresponding SortedPage
     */
    public BitMapHeaderPage(PageId pageno)
            throws ConstructPageException {
        super();
        try {

            SystemDefs.JavabaseBM.pinPage(pageno, this, false/*Rdisk*/);
        } catch (Exception e) {
            throw new ConstructPageException(e, "pinpage failed");
        }
    }

    /**
     * associate the SortedPage instance with the Page instance
     */
    public BitMapHeaderPage(Page page) {
        super(page);
    }

    /**
     * new a page, and associate the SortedPage instance with the Page instance
     */
    public BitMapHeaderPage() throws ConstructPageException {
        super();
        try {
            Page apage = new Page();
            PageId pageId = SystemDefs.JavabaseBM.newPage(apage, 1);
            if (pageId == null)
                throw new ConstructPageException(null, "new page failed");
            this.init(pageId, apage);

        } catch (Exception e) {
            throw new ConstructPageException(e, "construct header page failed");
        }
    }

    PageId getPageId() throws IOException {
        return getCurPage();
    }

    void setPageId(PageId pageno)
            throws IOException {
        setCurPage(pageno);
    }

    /**
     * get the magic0
     */
    int get_magic0()
            throws IOException {
        return getPrevPage().pid;
    }

    /**
     * set the magic0
     *
     * @param magic magic0 will be set to be equal to magic
     */
    void set_magic0(int magic)
            throws IOException {
        setPrevPage(new PageId(magic));
    }

    /**
     * get the rootId
     */
    PageId get_rootId()
            throws IOException {
        return getNextPage();
    }

    /**
     * set the rootId
     */
    void set_rootId(PageId rootID)
            throws IOException {
        setNextPage(rootID);
    }

    /**
     * get the key type
     */
    short get_keyType()
            throws IOException {
        return (short) getSlotLength(3);
    }

    /**
     * set the max keysize
     */
    int get_maxKeySize()
            throws IOException {
        return getSlotLength(1);
    }


    /**
     * get the delete fashion
     */
    int get_deleteFashion()
            throws IOException {
        return getSlotLength(2);
    }

     /* get the max keysize
     */
//    void set_maxKeySize(int key_size)
//            throws IOException {
//        setSlot(1, key_size, 0);
//    }
//
//    /**
//     * set the key type
//     */
//    void set_keyType(short key_type)
//            throws IOException {
//        setSlot(3, (int) key_type, 0);
//    }
//
//    /**
//     * set the delete fashion
//     */
//    void set_deleteFashion(int fashion)
//            throws IOException {
//        setSlot(2, fashion, 0);
//    }
}
