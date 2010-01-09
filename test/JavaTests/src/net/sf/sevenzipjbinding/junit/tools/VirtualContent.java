package net.sf.sevenzipjbinding.junit.tools;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IArchiveUpdateCallback;
import net.sf.sevenzipjbinding.ISequentialInStream;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.ISevenZipInArchive;
import net.sf.sevenzipjbinding.ISevenZipOutArchive;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.util.ByteArrayStream;

/**
 * The VirtualContent provides methods to generate random virtual content, to create an archive this that content and to
 * verify an archive against the virtual content. It also possible to write the virtual content to the disk and to print
 * a list of virtual files (item). Supported operations:
 * <ul>
 * <li>Generate a virtual content based on a set of parameters
 * <li>Create detached archive with the same content (compress virtual content)
 * <li>Verify archive given to match the virtual content
 * <li>Write content to the disk
 * <li>Print file names and sizes to the system out.
 * </ul>
 * VirtualContent uses ByteArrayStream to represents virtual file content (blob). This simplifies passing virtual file
 * content to the callback method.
 * 
 * @author Boris Brodski
 * @version 4.65-1
 */
public class VirtualContent {
    private class Item {
        private String path;
        private Date creationDate;
        private ByteArrayStream blob;

        Item(byte[] blobData) {
            blob = new ByteArrayStream(blobData, false);
        }

        public VirtualContent getVirtualContent() {
            return VirtualContent.this;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Date getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(Date creationDate) {
            this.creationDate = creationDate;
        }

        public ByteArrayStream getBlob() {
            return blob;
        }
    }

    private class ArchiveUpdateCallback implements IArchiveUpdateCallback {
        // TODO Remove after introducing IArchiveCreateCallback
        public int getOldArchiveItemIndex(int index) {
            return -1;
        }

        // TODO Remove after introducing IArchiveCreateCallback
        public boolean isNewData(int index) {
            return true;
        }

        // TODO Remove after introducing IArchiveCreateCallback
        public boolean isNewProperties(int index) {
            return true;
        }

        public Object getProperty(int index, PropID propID) {
            switch (propID) {
            case PATH:
                return itemList.get(index).getPath();

            case IS_FOLDER:
            case IS_ANTI:
                return Boolean.FALSE;

            case SIZE:
                return Long.valueOf(itemList.get(index).getBlob().getSize());
            }
            return null;
        }

        public ISequentialInStream getStream(int index) {
            return itemList.get(index).getBlob();
        }

        public void setOperationResult(boolean operationResultOk) {
            assertTrue(operationResultOk);
        }

        public void setCompleted(long completeValue) throws SevenZipException {
            // TODO Check this value or remove todo
        }

        public void setTotal(long total) throws SevenZipException {
            // TODO Check this value or remove todo
        }
    }

    private class TestSequentailOutStream implements ISequentialOutStream {
        private final Item item;

        public TestSequentailOutStream(Item item) {
            this.item = item;
            item.getBlob().rewind();
        }

        public int write(byte[] data) throws SevenZipException {
            byte[] expectedData = new byte[data.length];
            assertEquals(Integer.valueOf(data.length), Integer.valueOf(item.getBlob().read(expectedData)));
            assertArrayEquals(expectedData, data);
            System.out.println("Returning " + data.length);
            return data.length;
        }

        void finish() throws SevenZipException {
            assertTrue(item.getBlob().isEOF());
            item.getBlob().rewind();
        }
    }

    private class TextExtractCallback implements IArchiveExtractCallback {
        private TestSequentailOutStream testSequentailOutStream;
        private final boolean[] extracted;
        private final ISevenZipInArchive inArchive;

        TextExtractCallback(ISevenZipInArchive inArchive) {
            this.inArchive = inArchive;
            extracted = new boolean[itemList.size()];
        }

        public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
            System.out.println("Extracting index=" + index);

            String path = (String) inArchive.getProperty(index, PropID.PATH);

            Integer myIndexObjekt = usedNames.get(path.toUpperCase());
            assertNotNull("Directory passed to extraction", myIndexObjekt);
            int myIndex = myIndexObjekt.intValue();
            extracted[myIndex] = true;
            testSequentailOutStream = new TestSequentailOutStream(itemList.get(myIndex));
            return testSequentailOutStream;
        }

        public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
            assertEquals(ExtractAskMode.EXTRACT, extractAskMode);
        }

        public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
            assertEquals(ExtractOperationResult.OK, extractOperationResult);
            testSequentailOutStream.finish();
        }

        public void setCompleted(long completeValue) throws SevenZipException {
            // TODO Check this value or remove todo
        }

        public void setTotal(long total) throws SevenZipException {
            // TODO Check this value or remove todo
        }

        void finish() {
            for (int i = 0; i < extracted.length; i++) {
                assertTrue("Item with id " + i + " wasn't extracted", extracted[i]);
            }
        }
    }

    /**
     * Constant seed is used here. The test should be deterministic. This way is it better debuggable and testable.
     */
    private static final Random random = new Random(0);
    private static final char[] SYMBOLS = new char[] { ' ', '_', '-', '+', '=' };
    private static final char[] SYMBOLS_FIRST_CHAR = new char[] { '_', '-', '+', '=' };

    private List<Item> itemList = new ArrayList<Item>();
    private Map<String, Integer> usedNames = new HashMap<String, Integer>();

    public void writeToDirectory(File directory) throws Exception {
        directory.mkdirs();
        for (Item item : itemList) {
            File itemFile = new File(directory.getName() + File.separator + item.getPath()).getAbsoluteFile();
            itemFile.getParentFile().mkdirs();
            item.getBlob().writeToOutputStream(new FileOutputStream(itemFile), true);
        }
    }

    public void print() {
        List<Item> values = new ArrayList<Item>(itemList);
        Collections.sort(values, new Comparator<Item>() {
            public int compare(Item item1, Item item2) {
                return item1.getPath().compareTo(item2.getPath());
            }
        });
        for (Item item : values) {
            System.out.println(item.getPath() + "   (" + item.getBlob().getSize() + " bytes)");
        }
    }

    public void updateOutArchive(ISevenZipOutArchive outArchive, ISequentialOutStream outputStream) {
        outArchive.updateItems(outputStream, itemList.size(), new ArchiveUpdateCallback());
    }

    public void verifyInArchive(ISevenZipInArchive inArchive) throws SevenZipException {
        TextExtractCallback testExtractCallback = new TextExtractCallback(inArchive);
        inArchive.extract(null, false, testExtractCallback);
        testExtractCallback.finish();

    }

    public void fillRandomly(int countOfFiles, int directoriesDepth, int maxSubdirectories, int averageFileLength,
            int deltaFileLength) {
        itemList.clear();
        List<String> directoryList = getRandomDirectory(directoriesDepth, maxSubdirectories, countOfFiles);
        for (int i = 0; i < countOfFiles; i++) {
            int fileLength = averageFileLength + random.nextInt(deltaFileLength + 1);
            byte[] fileContent = getRandomFileContent(fileLength);

            String directory = directoryList.get(random.nextInt(directoryList.size()));

            Item item = new Item(fileContent);
            for (int j = 0; j < 50; j++) {
                String filename = getRandomFilename();
                if (!usedNames.containsKey((directory + filename).toUpperCase())) {
                    item.setPath(directory + filename);
                    break;
                }
            }
            if (item.getPath() == null) {
                throw new RuntimeException("It wasn't possible to generate a random file name after 50 iterations.");
            }

            usedNames.put(item.getPath().toUpperCase(), Integer.valueOf(itemList.size()));
            itemList.add(item);

        }
    }

    private byte[] getRandomFileContent(int length) {
        byte[] content = new byte[length];
        switch (random.nextInt(3)) {
        case 0:
            // Random content 
            random.nextBytes(content);
            break;

        case 1:
            // Good compressible content
            for (int i = random.nextInt(256); i < length; i++) {
                content[i] = (byte) i;
            }
            break;

        case 2:
            // Very good compressible content
            byte c = (byte) random.nextInt(256);
            Arrays.fill(content, c);
        }
        return content;
    }

    private List<String> getRandomDirectory(int directoriesDepth, int maxSubdirectories, int maxDirectories) {
        List<String> result = new ArrayList<String>();
        result.add(""); // Root directory
        BigDecimal countOfDirectories;
        if (maxSubdirectories > 1 && directoriesDepth > 1) {
            // count of directories = \[ \frac{maxSubdirectories^{directoriesDepth + 1} - 1}{maxSubdirectories - 1} \]$ (maxSubdirectories > 1)
            countOfDirectories = BigDecimal.valueOf(maxSubdirectories).pow(directoriesDepth + 1).subtract(
                    BigDecimal.ONE).divide(BigDecimal.valueOf(maxSubdirectories - 1));
        } else {
            countOfDirectories = BigDecimal.valueOf(Math.max(directoriesDepth, maxSubdirectories));
        }

        if (countOfDirectories.compareTo(BigDecimal.valueOf(maxDirectories)) < 0) {
            createFullDirectoryStructure(result, "", 1, directoriesDepth, maxSubdirectories);
        } else {
            throw new IllegalStateException("Not implemented yet: " + countOfDirectories); // TODO
        }

        return result;
    }

    private void createFullDirectoryStructure(List<String> result, String root, int depth, int maxDepth,
            int maxSubdirectories) {
        for (int i = 0; i < maxSubdirectories; i++) {
            String name = null;
            for (int j = 0; j < 50; j++) {
                name = getRandomFilename();
                if (usedNames.containsKey(name)) {
                    name = null;
                } else {
                    break;
                }
            }
            if (name == null) {
                throw new RuntimeException(
                        "It wasn't possible to generate a random directory name after 50 iterations.");
            }
            usedNames.put((root + name).toUpperCase(), null);
            String fullDirectoryName = root + name + "/";
            result.add(fullDirectoryName);
            if (depth < maxDepth) {
                createFullDirectoryStructure(result, fullDirectoryName, depth + 1, maxDepth, maxSubdirectories);
            }
        }
    }

    private static String getRandomFilename() {
        int length;
        switch (random.nextInt(3)) {
        case 0:
            length = 1;
            break;
        case 1:
            // Length: 2-10
            length = 2 + random.nextInt(9);
            break;
        default:
            // Length: 20-30
            length = 20 + random.nextInt(11);
        }
        char[] filenameArray = new char[length];
        for (int j = 0; j < length; j++) {
            filenameArray[j] = getRandomFilenameChar(j == 0);
        }
        return new String(filenameArray);
    }

    private static char getRandomFilenameChar(boolean firstChar) {
        int i = Math.abs(random.nextInt());
        switch (i % 4) {
        case 0:
            // Upper case letters
            return (char) ('A' + (i / 4) % 26);

        case 1:
            // Lower case letters
            return (char) ('a' + (i / 4) % 26);

        case 2:
            // Digits
            return (char) ('0' + (i / 4) % 10);

        default:
            // Symbols
            if (firstChar) {
                return SYMBOLS_FIRST_CHAR[(i / 4) % SYMBOLS_FIRST_CHAR.length];
            }
            return SYMBOLS[(i / 4) % SYMBOLS.length];
        }
    }
}