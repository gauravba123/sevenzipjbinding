package net.sf.sevenzipjbinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import net.sf.sevenzipjbinding.impl.OutArchiveImpl;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.impl.VolumedArchiveInStream;

/**
 * 7-Zip-JBinding entry point class. Finds and initializes 7-Zip-JBinding native library. Opens archives and returns
 * implementation of {@link IInArchive}
 * 
 * <h3>Initialization of the native library</h3>
 * 
 * Typically the library doesn't need an explicit initialization. The first call to an open archive method will try to
 * initialize the native library by calling {@link #initSevenZipFromPlatformJAR()} method. This initialization process
 * requires a platform jar to be in a class path. The automatic initialization starts before the first access to an
 * archive, if native library wasn't already initialized manually with one of the <code>initSevenZip...</code> methods.
 * If manual or automatic initialization failed, no further automatic initialization attempts will be made. The
 * initialization status and error messages can be obtained by following methods:
 * <ul>
 * <li>{@link #isInitializedSuccessfully()} - get initialization status</li>
 * <li>{@link #isAutoInitializationWillOccur()} - determine, if an initialization attempt was made</li>
 * <li>{@link #getLastInitializationException()} - get last thrown initialization exception</li>
 * </ul>
 * <br>
 * The platform jar is a additional jar file <code>sevenzipjbinding-<i>Platform</i>.jar</code> with one or more native
 * libraries for respective one or more platforms. Here is some examples of 7-Zip-JBinding platform jar files.
 * <ul>
 * <li><code>sevenzipjbinding-Linux-i386.jar</code> with native library for exact one platform: Linux, 32 bit</li>
 * <li><code>sevenzipjbinding-AllWindows.jar</code> with native libraries for two platforms: Windows 32 and 64 bit</li>
 * <li><code>sevenzipjbinding-AllPlatforms.jar</code> with native libraries for all available platforms</li>
 * </ul>
 * The single and multiple platform jar files can be determined by counting dashes in the filename. Single platform jar
 * files always contain two dashes in their names.<br>
 * <br>
 * Here is a schema of the different initialization processes:
 * 
 * <ul>
 * <li>Initialization using platform jar
 * <ul>
 * <li>First, the list of available native libraries is read out of the
 * <code>/sevenzipjbinding-platforms.properties</code> file in the class path by calling {@link #getPlatformList()}
 * method. The list is cached in a static variable.</li>
 * <li>The platform is chosen by calling {@link #getPlatformBestMatch()} method. If the list of available platforms
 * contains exact one platform the platform is always considered the best match. If more, that one platforms are
 * available to choose from, the system properties <code>os.arch</code> and <code>os.name</code> (first part) are used
 * to make the choice.</li>
 * <li>The list of the native libraries is determined by reading
 * <code>/ChosenPlatform/sevenzipjbinding-lib.properties</code> in the class path. The list contains names of the
 * dynamic libraries situated in the <code>/ChosenPlatform/</code> directory in the same jar.</li>
 * <li>The dynamic libraries for the chosen platform are copied (if not already) to the unique temporary directory using
 * "build-ref" postfix. If not passed as a parameter for one of <code>initSevenZipFromPlatformJAR(...)</code> methods,
 * the temporary directory is determined using system property <code>java.io.tmpdir</code>.</li>
 * <li>The dynamic libraries are loaded into JVM using {@link System#load(String)} method.</li>
 * <li>7-Zip-JBinding native initialization method called to complete initialization process.</li>
 * </ul>
 * <li>Manual initialization
 * <ul>
 * <li>User loads 7-Zip-JBinding native dynamic libraries manually into JVM using {@link System#load(String)} or
 * {@link System#loadLibrary(String)}</li>
 * <li>User calls {@link #initLoadedLibraries()} method to initialize manually loaded native library</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * <br>
 * By default the initialization occurred within the
 * {@link AccessController#doPrivileged(java.security.PrivilegedAction)} block. This can be overruled by setting
 * <code>sevenzip.no_doprivileged_initialization</code> system property. For example: <blockquote>
 * <code>java -Dsevenzip.no_doprivileged_initialization=1 ...</code> </blockquote>
 * 
 * <h3>Temporary artifacts</h3>
 * 
 * During initialization phase of the 7-Zip-JBinding the native libraries from the platform jar must be extracted to the
 * disk in order to be loaded into the JVM. Since the count of the native libraries (depending on the platform) can be
 * greater than one, a temporary sub-directory is created to hold those native libraries. The path to the directory for
 * the temporary artifacts will determined according to following rules (see {@link #createOrVerifyTmpDir(File)}:
 * <ul>
 * <li>If path specified directly using <code>tmpDirectory</code> parameter of
 * {@link #initSevenZipFromPlatformJAR(File)} or {@link #initSevenZipFromPlatformJAR(String, File)} it will be used
 * <li>If no path specified directly, the system property <code>java.io.tmpdir</code> get used
 * <li>If the system property <code>java.io.tmpdir</code> isn't set, an exception get raised
 * </ul>
 * <br/>
 * The list of the temporary created artifact can be obtained with {@link #getTemporaryArtifacts()}. By default,
 * 7-Zip-JBinding doesn't delete those artifacts trying to reduce subsequent initialization overhead. If 7-Zip-JBinding
 * finds the native libraries within the temporary directory, it uses those without further verification. In order to
 * allow smoothly updates, the temporary sub-directory with the native libraries named with a unique build reference
 * number. If 7-Zip-JBinding get updated, a new temporary sub-directory get created and the new native libraries get
 * copied and used.
 * 
 * <h3>Opening archives</h3>
 * 
 * The methods for opening archive files (read-only):
 * <ul>
 * <li>{@link #openInArchive(ArchiveFormat, IInStream)} - simple open archive method.</li>
 * <li>{@link #openInArchive(ArchiveFormat, IInStream, IArchiveOpenCallback)} - generic open archive method. It's
 * possible to open all kinds of archives providing call back object that implements following interfaces
 * <ul>
 * <li>{@link IArchiveOpenCallback} - base interface. Must be implemented by all call back classes</li>
 * <li>{@link ICryptoGetTextPassword} - (optional) Provides password encrypted index</li>
 * <li>{@link IArchiveOpenVolumeCallback} - (optional) Provides information about volumes in multipart archives.
 * Currently used only for multipart <code>RAR</code> archives. For opening multipart <code>7z</code> archives use
 * {@link VolumedArchiveInStream}.</li>
 * </ul>
 * </li>
 * <li>{@link #openInArchive(ArchiveFormat, IInStream, String)} a shortcut method for opening archives with an encrypted
 * index.</li>
 * </ul>
 * 
 * 
 * @author Boris Brodski
 * @version 4.65-1
 */
public class SevenZip {
    private static final String SYSTEM_PROPERTY_TMP = "java.io.tmpdir";
    private static final String SYSTEM_PROPERTY_SEVEN_ZIP_NO_DO_PRIVILEGED_INITIALIZATION = "sevenzip.no_doprivileged_initialization";
    private static final String PROPERTY_SEVENZIPJBINDING_LIBNAME = "sevenzipjbinding.libname.%s";
    private static final String PROPERTY_BUILD_REF = "build.ref";
    private static final String SEVENZIPJBINDING_LIB_PROPERTIES_FILENAME = "sevenzipjbinding-lib.properties";
    private static final String SEVENZIPJBINDING_PLATFORMS_PROPRETIES_FILENAME = "/sevenzipjbinding-platforms.properties";

    private static boolean autoInitializationWillOccur = true;
    private static boolean initializationSuccessful = false;
    private static SevenZipNativeInitializationException lastInitializationException = null;
    private static List<String> availablePlatforms = null;
    private static String usedPlatform = null;
    private static File[] temporaryArtifacts = null;

    /**
     * Hide default constructor
     */
    private SevenZip() {

    }

    /**
     * Tests native library initialization status of SevenZipJBinding. Use {@link #getLastInitializationException()}
     * method to get more information in case of initialization failure.
     * 
     * @return <code>true</code> 7-Zip-JBinding native library was initialized successfully. Native library wasn't
     *         initialized successfully (yet).
     * @see #getLastInitializationException()
     * @see #isAutoInitializationWillOccur()
     */
    public static boolean isInitializedSuccessfully() {
        return initializationSuccessful;
    }

    /**
     * Returns last native library initialization exception, if occurs.
     * 
     * @return <code>null</code> - no initialization exception occurred (yet), else initialization exception
     * @see SevenZip#isInitializedSuccessfully()
     */
    public static Throwable getLastInitializationException() {
        return lastInitializationException;
    }

    /**
     * Returns weather automatic initialization will occur or not. Automatic initialization starts before opening an
     * archive, if native library wasn't already initialized manually with one of the <code>initSevenZip...</code>
     * methods. If manual or automatic initialization failed, no further automatic initialization attempts will be made.
     * 
     * @return <code>true</code> automatic initialization will occur, <code>false</code> automatic initialization will
     *         not occur
     * 
     * @see #isInitializedSuccessfully()
     * @see #getLastInitializationException()
     */
    public static boolean isAutoInitializationWillOccur() {
        return autoInitializationWillOccur;
    }

    /**
     * Return the platform used for the initialization. The Platform is one element out of the list of available
     * platforms returned by {@link #getPlatformList()}.
     * 
     * @return the platform used for the initialization or <code>null</code> if initialization wasn't performed yet.
     * @see SevenZip#getPlatformList()
     */
    public static String getUsedPlatform() {
        return usedPlatform;
    }

    /**
     * Load list of the available platforms out of <code>sevenzipjbinding-<i>Platform</i>.jar</code> in the class path.
     * 
     * @return list of the available platforms
     * 
     * @throws SevenZipNativeInitializationException
     *             indicated problems finding or parsing platform property file
     */
    public static List<String> getPlatformList() throws SevenZipNativeInitializationException {
        if (availablePlatforms != null) {
            return availablePlatforms;
        }

        InputStream propertiesInputStream = SevenZip.class
                .getResourceAsStream(SEVENZIPJBINDING_PLATFORMS_PROPRETIES_FILENAME);
        if (propertiesInputStream == null) {
            throw new SevenZipNativeInitializationException("Can not find 7-Zip-JBinding platform property file "
                    + SEVENZIPJBINDING_PLATFORMS_PROPRETIES_FILENAME
                    + ". Make sure the 'sevenzipjbinding-<Platform>.jar' file is "
                    + "in the class path or consider initializing SevenZipJBinding manualy using one of "
                    + "the offered initialization methods: 'net.sf.sevenzipjbinding.SevenZip.init*()'");
        }

        Properties properties = new Properties();
        try {
            properties.load(propertiesInputStream);
        } catch (IOException e) {
            throwInitException(e, "Error loading existing property file "
                    + SEVENZIPJBINDING_PLATFORMS_PROPRETIES_FILENAME);
        }

        List<String> platformList = new ArrayList<String>();
        for (int i = 1;; i++) {
            String platform = properties.getProperty("platform." + i);
            if (platform == null) {
                break;
            }
            platformList.add(platform);
        }

        return availablePlatforms = platformList;
    }

    /**
     * Returns list of the temporary created artifacts (one directory and one or more files within this directory). The
     * directory is always the last element in the array.
     * 
     * @return array of {@link File}s.
     */
    public static File[] getTemporaryArtifacts() {
        return temporaryArtifacts;
    }

    /**
     * Initialize native SevenZipJBinding library assuming <code>sevenzipjbinding-<i>Platform</i>.jar</code> on the
     * class path. The platform depended library will be extracted from the jar file and copied to the temporary
     * directory. Then it will be loaded into JVM using {@link System#load(String)} method. Finally the library specific
     * native initialization method will be called. Please see JavaDoc of {@link SevenZip} for detailed information.<br>
     * <br>
     * If libraries for more that one platform exists, the choice will be made by calling
     * {@link #getPlatformBestMatch()} method. Use {@link #initSevenZipFromPlatformJAR(String)} to set platform
     * manually.
     * 
     * @throws SevenZipNativeInitializationException
     *             indicated problems finding a native library, coping it into the temporary directory or loading it.
     * 
     * @see SevenZip
     * @see #initSevenZipFromPlatformJAR(File)
     * @see #initSevenZipFromPlatformJAR(String)
     * @see #initSevenZipFromPlatformJAR(String, File)
     */
    public static void initSevenZipFromPlatformJAR() throws SevenZipNativeInitializationException {
        initSevenZipFromPlatformJARIntern(null, null);
    }

    /**
     * Initialize native SevenZipJBinding library assuming <code>sevenzipjbinding-<i>Platform</i>.jar</code> on the
     * class path. The platform depended library will be extracted from the jar file and copied to the temporary
     * directory. Then it will be loaded into JVM using {@link System#load(String)} method. Finally the library specific
     * native initialization method will be called. Please see JavaDoc of {@link SevenZip} for detailed information.<br>
     * <br>
     * If libraries for more that one platform exists, the choice will be made by calling
     * {@link #getPlatformBestMatch()} method. Use {@link #initSevenZipFromPlatformJAR(String)} to set platform
     * manually.
     * 
     * @param tmpDirectory
     *            temporary directory to copy native libraries to. This directory must be writable and contain at least
     *            2 MB free space.
     * 
     * @throws SevenZipNativeInitializationException
     *             indicated problems finding a native library, coping it into the temporary directory or loading it.
     * 
     * @see SevenZip
     * @see #initSevenZipFromPlatformJAR()
     * @see #initSevenZipFromPlatformJAR(String)
     * @see #initSevenZipFromPlatformJAR(String, File)
     */
    public static void initSevenZipFromPlatformJAR(File tmpDirectory) throws SevenZipNativeInitializationException {
        initSevenZipFromPlatformJARIntern(null, tmpDirectory);
    }

    /**
     * Initialize native SevenZipJBinding library assuming <code>sevenzipjbinding-<i>Platform</i>.jar</code> on the
     * class path. The platform depended library will be extracted from the jar file and copied to the temporary
     * directory. Then it will be loaded into JVM using {@link System#load(String)} method. Finally the library specific
     * native initialization method will be called. Please see JavaDoc of {@link SevenZip} for detailed information.<br>
     * <br>
     * If libraries for more that one platform exists, the choice will be made by calling
     * {@link #getPlatformBestMatch()} method. Use {@link #initSevenZipFromPlatformJAR(String)} to set platform
     * manually.
     * 
     * @param tmpDirectory
     *            temporary directory to copy native libraries to. This directory must be writable and contain at least
     *            2 MB free space.
     * 
     * @param platform
     *            Platform to load native library for. The platform must be one of the elements of the list of available
     *            platforms returned by {@link #getPlatformList()}.
     * 
     * @throws SevenZipNativeInitializationException
     *             indicated problems finding a native library, coping it into the temporary directory or loading it.
     * 
     * @see SevenZip
     * @see #initSevenZipFromPlatformJAR()
     * @see #initSevenZipFromPlatformJAR(File)
     * @see #initSevenZipFromPlatformJAR(String)
     * @see #getPlatformList()
     */
    public static void initSevenZipFromPlatformJAR(String platform, File tmpDirectory)
            throws SevenZipNativeInitializationException {
        initSevenZipFromPlatformJARIntern(platform, tmpDirectory);
    }

    /**
     * Initialize native SevenZipJBinding library assuming <code>sevenzipjbinding-<i>Platform</i>.jar</code> on the
     * class path. The platform depended library will be extracted from the jar file and copied to the temporary
     * directory. Then it will be loaded into JVM using {@link System#load(String)} method. Finally the library specific
     * native initialization method will be called. Please see JavaDoc of {@link SevenZip} for detailed information.<br>
     * <br>
     * If libraries for more that one platform exists, the choice will be made by calling
     * {@link #getPlatformBestMatch()} method. Use {@link #initSevenZipFromPlatformJAR(String)} to set platform
     * manually.
     * 
     * @param platform
     *            Platform to load native library for. The platform must be one of the elements of the list of available
     *            platforms returned by {@link #getPlatformList()}.
     * 
     * @throws SevenZipNativeInitializationException
     *             indicated problems finding a native library, coping it into the temporary directory or loading it.
     * 
     * @see SevenZip
     * @see #initSevenZipFromPlatformJAR()
     * @see #initSevenZipFromPlatformJAR(File)
     * @see #initSevenZipFromPlatformJAR(String, File)
     * @see #getPlatformList()
     */
    public static void initSevenZipFromPlatformJAR(String platform) throws SevenZipNativeInitializationException {
        initSevenZipFromPlatformJARIntern(platform, null);
    }

    /**
     * Perform the initialization: read platform property jar and retrieve list of available platforms. Pick best
     * platform or use chosen by parameter platform. Locate <code>sevenzipjbinding-lib.properties</code> property file,
     * read list of native libraries needed to load. Copy native library to the temporary directory passed by parameter
     * or using <code>java.io.tmpdir</code> system property. Load native libraries into JVM. Call 7-Zip-JBinding native
     * library initialization function.
     * 
     * @param platform
     *            (optional) platform to use or <code>null</code>.
     * @param tmpDirectory
     *            (optional) temporary directory to use or <code>null</code>.
     * @throws SevenZipNativeInitializationException
     *             by initialization failure
     */
    private static void initSevenZipFromPlatformJARIntern(String platform, File tmpDirectory)
            throws SevenZipNativeInitializationException {
        try {
            autoInitializationWillOccur = false;
            if (initializationSuccessful) {
                // Native library was already initialized successfully. No need for further initialization.
                return;
            }

            determineAndSetUsedPlatform(platform);
            Properties properties = loadSevenZipJBindingLibProperties();
            File tmpDirFile = createOrVerifyTmpDir(tmpDirectory);
            File sevenZipJBindingTmpDir = getOrCreateSevenZipJBindingTmpDir(tmpDirFile, properties);
            List<File> nativeLibraries = copyOrSkipLibraries(properties, sevenZipJBindingTmpDir);
            loadNativeLibraries(nativeLibraries);
            nativeInitialization();
        } catch (SevenZipNativeInitializationException sevenZipNativeInitializationException) {
            lastInitializationException = sevenZipNativeInitializationException;
            throw sevenZipNativeInitializationException;
        }
    }

    private static void determineAndSetUsedPlatform(String platform) throws SevenZipNativeInitializationException {
        if (platform == null) {
            usedPlatform = getPlatformBestMatch();
        } else {
            usedPlatform = platform;
        }
    }

    private static Properties loadSevenZipJBindingLibProperties() throws SevenZipNativeInitializationException {
        String pathInJAR = "/" + usedPlatform + "/";

        // Load 'sevenzipjbinding-lib.properties'
        InputStream sevenZipJBindingLibProperties = SevenZip.class.getResourceAsStream(pathInJAR
                + SEVENZIPJBINDING_LIB_PROPERTIES_FILENAME);
        if (sevenZipJBindingLibProperties == null) {
            throwInitException("error loading property file '"
                    + pathInJAR
                    + SEVENZIPJBINDING_LIB_PROPERTIES_FILENAME
                    + "' from a jar-file 'sevenzipjbinding-<Platform>.jar'. Is the platform jar-file not in the class path?");
        }

        Properties properties = new Properties();
        try {
            properties.load(sevenZipJBindingLibProperties);
        } catch (IOException e) {
            throwInitException("error loading property file '" + SEVENZIPJBINDING_LIB_PROPERTIES_FILENAME
                    + "' from a jar-file 'sevenzipjbinding-<Platform>.jar'");
        }
        return properties;
    }

    private static File createOrVerifyTmpDir(File tmpDirectory) throws SevenZipNativeInitializationException {
        File tmpDirFile;
        if (tmpDirectory != null) {
            tmpDirFile = tmpDirectory;
        } else {
            String systemPropertyTmp = System.getProperty(SYSTEM_PROPERTY_TMP);
            if (systemPropertyTmp == null) {
                throwInitException("can't determinte tmp directory. Use may use -D" + SYSTEM_PROPERTY_TMP
                        + "=<path to tmp dir> parameter for jvm to fix this.");
            }
            tmpDirFile = new File(systemPropertyTmp);
        }

        if (!tmpDirFile.exists() || !tmpDirFile.isDirectory()) {
            throwInitException("invalid tmp directory '" + tmpDirectory + "'");
        }

        if (!tmpDirFile.canWrite()) {
            throwInitException("can't create files in '" + tmpDirFile.getAbsolutePath() + "'");
        }
        return tmpDirFile;
    }

    private static File getOrCreateSevenZipJBindingTmpDir(File tmpDirFile, Properties properties)
            throws SevenZipNativeInitializationException {
        String buildRef = getOrGenerateBuildRef(properties);
        File tmpSubdirFile = new File(tmpDirFile.getAbsolutePath() + File.separator + "SevenZipJBinding-" + buildRef);
        if (!tmpSubdirFile.exists()) {
            if (!tmpSubdirFile.mkdir()) {
                throwInitException("Directory '" + tmpDirFile.getAbsolutePath() + "' couldn't be created");
            }
        }
        return tmpSubdirFile;
    }

    private static String getOrGenerateBuildRef(Properties properties) {
        String buildRef = properties.getProperty(PROPERTY_BUILD_REF);
        if (buildRef == null) {
            buildRef = Integer.toString(new Random().nextInt(10000000));
        }
        return buildRef;
    }

    private static List<File> copyOrSkipLibraries(Properties properties, File sevenZipJBindingTmpDir)
            throws SevenZipNativeInitializationException {
        List<File> nativeLibraries = new ArrayList<File>(5);
        for (int i = 1;; i++) {
            String propertyName = String.format(PROPERTY_SEVENZIPJBINDING_LIBNAME, Integer.valueOf(i));
            String libname = properties.getProperty(propertyName);
            if (libname == null) {
                if (nativeLibraries.size() == 0) {
                    throwInitException("property file '" + SEVENZIPJBINDING_LIB_PROPERTIES_FILENAME
                            + "' from a jar-file 'sevenzipjbinding-<Platform>.jar' don't contain the property named '"
                            + propertyName + "'");
                } else {
                    break;
                }
            }
            File libTmpFile = new File(sevenZipJBindingTmpDir.getAbsolutePath() + File.separatorChar + libname);

            if (!libTmpFile.exists()) {
                InputStream libInputStream = SevenZip.class.getResourceAsStream("/" + usedPlatform + "/" + libname);
                if (libInputStream == null) {
                    throwInitException("error loading native library '" + libname
                            + "' from a jar-file 'sevenzipjbinding-<Platform>.jar'.");
                }

                copyLibraryToFS(libTmpFile, libInputStream);
            }
            nativeLibraries.add(libTmpFile);
        }
        applyTemporaryArtifacts(sevenZipJBindingTmpDir, nativeLibraries);
        return nativeLibraries;
    }

    private static void applyTemporaryArtifacts(File sevenZipJBindingTmpDir, List<File> nativeLibraries) {
        temporaryArtifacts = new File[nativeLibraries.size() + 1];
        nativeLibraries.toArray(temporaryArtifacts);
        temporaryArtifacts[temporaryArtifacts.length - 1] = sevenZipJBindingTmpDir;
    }

    private static void loadNativeLibraries(List<File> libraryList) throws SevenZipNativeInitializationException {
        // Load native libraries in to reverse order
        for (int i = libraryList.size() - 1; i != -1; i--) {
            String libraryFileName = libraryList.get(i).getAbsolutePath();
            try {
                System.load(libraryFileName);
            } catch (Throwable t) {
                throw new SevenZipNativeInitializationException(
                        "7-Zip-JBinding initialization failed: Error loading native library: '" + libraryFileName + "'",
                        t);
            }
        }
    }

    /**
     * Initialize 7-Zip-JBinding native library without loading libraries in JVM first. Prevent automatic loading of
     * 7-Zip-JBinding native libraries into JVM. This method will only call 7-Zip-JBinding internal initialization
     * method, considering all needed native libraries as loaded. It method is useful, if the java application wants to
     * load 7-Zip-JBinding native libraries manually.
     * 
     * @throws SevenZipNativeInitializationException
     */
    public static void initLoadedLibraries() throws SevenZipNativeInitializationException {
        if (initializationSuccessful) {
            return;
        }
        autoInitializationWillOccur = false;
        nativeInitialization();
    }

    private static void nativeInitialization() throws SevenZipNativeInitializationException {
        String doPrivileged = System.getProperty(SYSTEM_PROPERTY_SEVEN_ZIP_NO_DO_PRIVILEGED_INITIALIZATION);
        final String errorMessage[] = new String[1];
        final Throwable throwable[] = new Throwable[1];
        if (doPrivileged == null || doPrivileged.trim().equals("0")) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        errorMessage[0] = nativeInitSevenZipLibrary();
                    } catch (Throwable e) {
                        throwable[0] = e;
                    }
                    return null;
                }
            });
        } else {
            errorMessage[0] = nativeInitSevenZipLibrary();
        }
        if (errorMessage[0] != null || throwable[0] != null) {
            String message = errorMessage[0];
            if (message == null) {
                message = "No message";
            }
            lastInitializationException = new SevenZipNativeInitializationException(
                    "Error initializing 7-Zip-JBinding: " + message, throwable[0]);
            throw lastInitializationException;
        }
        initializationSuccessful = true;
    }

    /**
     * Open archive of type <code>archiveFormat</code> from the input stream <code>inStream</code> using 'archive open
     * call back' listener <code>archiveOpenCallback</code>. To open archive from the file, use
     * {@link RandomAccessFileInStream}.
     * 
     * @param archiveFormat
     *            format of archive
     * @param inStream
     *            input stream to open archive from
     * @param archiveOpenCallback
     *            archive open call back listener to use. You can optionally implement {@link ICryptoGetTextPassword} to
     *            specify password to use.
     * @return implementation of {@link IInArchive} which represents opened archive.
     * 
     * @throws SevenZipException
     *             7-Zip or 7-Zip-JBinding intern error occur. Check exception message for more information.
     * @throws NullPointerException
     *             is thrown, if inStream is null
     * 
     * @see #openInArchive(ArchiveFormat, IInStream, IArchiveOpenCallback)
     * @see #openInArchive(ArchiveFormat, IInStream, String)
     */
    public static IInArchive openInArchive(ArchiveFormat archiveFormat, IInStream inStream,
            IArchiveOpenCallback archiveOpenCallback) throws SevenZipException {
        ensureLibraryIsInitialized();
        IArchiveOpenCallback openArchiveCallbackToUse = archiveOpenCallback;
        if (openArchiveCallbackToUse == null) {
            // TODO Test this!
            openArchiveCallbackToUse = new DummyOpenArchiveCallback();
        }
        if (archiveFormat != null) {
            return callNativeOpenArchive(archiveFormat, inStream, openArchiveCallbackToUse);
        }
        return callNativeOpenArchive(null, inStream, openArchiveCallbackToUse);
    }

    /**
     * Open archive of type <code>archiveFormat</code> from the input stream <code>inStream</code> using 'archive open
     * call-back' listener <code>archiveOpenCallback</code>. To open archive from the file, use
     * {@link RandomAccessFileInStream}.
     * 
     * @param archiveFormat
     *            format of archive
     * @param inStream
     *            input stream to open archive from
     * @param passwordForOpen
     *            password to use. Warning: this password will not be used to extract item from archive but only to open
     *            archive. (7-zip format supports encrypted filename)
     * @return implementation of {@link IInArchive} which represents opened archive.
     * 
     * @throws SevenZipException
     *             7-Zip or 7-Zip-JBinding intern error occur. Check exception message for more information.
     * @throws NullPointerException
     *             is thrown, if inStream is null
     * 
     * @see #openInArchive(ArchiveFormat, IInStream)
     * @see #openInArchive(ArchiveFormat, IInStream, IArchiveOpenCallback)
     */
    public static IInArchive openInArchive(ArchiveFormat archiveFormat, IInStream inStream, String passwordForOpen)
            throws SevenZipException {
        ensureLibraryIsInitialized();
        if (archiveFormat != null) {
            return callNativeOpenArchive(archiveFormat, inStream, new ArchiveOpenCryptoCallback(passwordForOpen));
        }
        return callNativeOpenArchive(null, inStream, new ArchiveOpenCryptoCallback(passwordForOpen));
    }

    /**
     * Open archive of type <code>archiveFormat</code> from the input stream <code>inStream</code>. To open archive from
     * the file, use {@link RandomAccessFileInStream}.
     * 
     * @param archiveFormat
     *            (optional) format of archive. If <code>null</code> archive format will be auto-detected.
     * @param inStream
     *            input stream to open archive from
     * @return implementation of {@link IInArchive} which represents opened archive.
     * 
     * @throws SevenZipException
     *             7-Zip or 7-Zip-JBinding intern error occur. Check exception message for more information.
     * @throws NullPointerException
     *             is thrown, if inStream is null
     * 
     * @see #openInArchive(ArchiveFormat, IInStream)
     * @see #openInArchive(ArchiveFormat, IInStream, String)
     */
    public static IInArchive openInArchive(ArchiveFormat archiveFormat, IInStream inStream) throws SevenZipException {
        ensureLibraryIsInitialized();
        if (archiveFormat != null) {
            return callNativeOpenArchive(archiveFormat, inStream, new DummyOpenArchiveCallback());
        }
        return callNativeOpenArchive(null, inStream, new DummyOpenArchiveCallback());
    }

    private static void ensureLibraryIsInitialized() {
        if (autoInitializationWillOccur) {
            autoInitializationWillOccur = false;
            try {
                initSevenZipFromPlatformJAR();
            } catch (SevenZipNativeInitializationException exception) {
                lastInitializationException = exception;
                throw new RuntimeException("SevenZipJBinding couldn't be initialized automaticly using initialization "
                        + "from platform depended JAR and the default temporary directory. Please, "
                        + "make sure the correct 'sevenzipjbinding-<Platform>.jar' file is "
                        + "in the class path or consider initializing SevenZipJBinding manualy using one of "
                        + "the offered initialization methods: 'net.sf.sevenzipjbinding.SevenZip.init*()'", exception);
            }
        }
        if (!initializationSuccessful) {
            throw new RuntimeException("SevenZipJBinding wasn't initialized successfully last time.",
                    lastInitializationException);
        }
    }

    private static void throwInitException(String message) throws SevenZipNativeInitializationException {
        throwInitException(null, message);
    }

    private static void throwInitException(Exception exception, String message)
            throws SevenZipNativeInitializationException {
        throw new SevenZipNativeInitializationException("Error loading SevenZipJBinding native library into JVM: "
                + message + " [You may also try different SevenZipJBinding initialization methods "
                + "'net.sf.sevenzipjbinding.SevenZip.init*()' in order to solve this problem] ", exception);
    }

    private static void copyLibraryToFS(File toLibTmpFile, InputStream fromLibInputStream) {
        FileOutputStream libTmpOutputStream = null;
        try {
            libTmpOutputStream = new FileOutputStream(toLibTmpFile);
            byte[] buffer = new byte[65536];
            while (true) {
                int read = fromLibInputStream.read(buffer);
                if (read > 0) {
                    libTmpOutputStream.write(buffer, 0, read);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error initializing SevenZipJBinding native library: "
                    + "can't copy native library out of a resource file to the temporary location: '"
                    + toLibTmpFile.getAbsolutePath() + "'", e);
        } finally {
            try {
                fromLibInputStream.close();
            } catch (IOException e) {
                // Ignore errors here
            }
            try {
                if (libTmpOutputStream != null) {
                    libTmpOutputStream.close();
                }
            } catch (IOException e) {
                // Ignore errors here
            }
        }
    }

    /**
     * Return best match for the current platform out of available platforms <code>availablePlatform</code>
     * 
     * @param availablePlatform
     *            list of the platforms to choose from
     * @return platform
     * @throws SevenZipNativeInitializationException
     *             is no platform could be chosen
     */
    private static String getPlatformBestMatch() throws SevenZipNativeInitializationException {
        List<String> availablePlatform = getPlatformList();
        if (availablePlatform.size() == 1) {
            return availablePlatform.get(0);
        }

        String arch = System.getProperty("os.arch");
        String system = System.getProperty("os.name").split(" ")[0];
        if (availablePlatform.contains(system + "-" + arch)) {
            return system + "-" + arch;
        }

        // TODO allow fuzzy matches
        StringBuilder stringBuilder = new StringBuilder("Can't find suited platform for os.arch=");
        stringBuilder.append(arch);
        stringBuilder.append(", os.name=");
        stringBuilder.append(system);
        stringBuilder.append("... Available list of platforms: ");

        for (String platform : availablePlatform) {
            stringBuilder.append(platform);
            stringBuilder.append(", ");
        }
        stringBuilder.setLength(stringBuilder.length() - 2);
        throwInitException(stringBuilder.toString());
        return null; // Will never happen
    }

    private static IInArchive callNativeOpenArchive(ArchiveFormat archiveFormat, IInStream inStream,
            IArchiveOpenCallback archiveOpenCallback) throws SevenZipException {
        if (inStream == null) {
            throw new NullPointerException("SevenZip.callNativeOpenArchive(...): inStream parameter is null");
        }
        return nativeOpenArchive(archiveFormat, inStream, archiveOpenCallback);
    }

    private static native IInArchive nativeOpenArchive(ArchiveFormat archiveFormat, IInStream inStream,
            IArchiveOpenCallback archiveOpenCallback) throws SevenZipException;

    private static native void nativeCreateArchive(OutArchiveImpl<?> outArchiveImpl, ArchiveFormat archiveFormat)
            throws SevenZipException;

    private static native String nativeInitSevenZipLibrary() throws SevenZipNativeInitializationException;

    public static IOutCreateArchiveZip openOutArchiveZip() throws SevenZipException {
        return (IOutCreateArchiveZip) openOutArchiveIntern(ArchiveFormat.ZIP);
    }

    public static IOutCreateArchive7z openOutArchive7z() throws SevenZipException {
        return (IOutCreateArchive7z) openOutArchiveIntern(ArchiveFormat.SEVEN_ZIP);
    }

    public static IOutCreateArchiveTar openOutArchiveTar() throws SevenZipException {
        return (IOutCreateArchiveTar) openOutArchiveIntern(ArchiveFormat.TAR);
    }

    public static IOutCreateArchiveBZip2 openOutArchiveBZip2() throws SevenZipException {
        return (IOutCreateArchiveBZip2) openOutArchiveIntern(ArchiveFormat.BZIP2);
    }

    public static IOutCreateArchiveGZip openOutArchiveGZip() throws SevenZipException {
        return (IOutCreateArchiveGZip) openOutArchiveIntern(ArchiveFormat.GZIP);
    }

    @SuppressWarnings("unchecked")
    public static IOutCreateArchive<IOutItemCallback> openOutArchive(ArchiveFormat archiveFormat)
            throws SevenZipException {
        return (IOutCreateArchive<IOutItemCallback>) openOutArchiveIntern(archiveFormat);
    }

    private static OutArchiveImpl<?> openOutArchiveIntern(ArchiveFormat archiveFormat) throws SevenZipException {
        ensureLibraryIsInitialized();
        if (!archiveFormat.isOutArchiveSupported()) {
            throw new IllegalStateException("Archive format '" + archiveFormat + "' doesn't support archive creation.");
        }

        OutArchiveImpl<?> outArchiveImpl;
        try {
            outArchiveImpl = archiveFormat.getOutArchiveImplementation().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Internal error: Can't create new instance of the class "
                    + archiveFormat.getOutArchiveImplementation() + " using default constructor.");
        }

        nativeCreateArchive(outArchiveImpl, archiveFormat);
        return outArchiveImpl;
    }

    private static class DummyOpenArchiveCallback implements IArchiveOpenCallback, ICryptoGetTextPassword {
        /**
         * {@inheritDoc}
         */

        public void setCompleted(Long files, Long bytes) {
        }

        /**
         * {@inheritDoc}
         */

        public void setTotal(Long files, Long bytes) {
        }

        /**
         * {@inheritDoc}
         */

        public String cryptoGetTextPassword() throws SevenZipException {
            throw new SevenZipException("No password was provided for opening protected archive.");
        }
    }

    private static final class ArchiveOpenCryptoCallback implements IArchiveOpenCallback, ICryptoGetTextPassword {
        private final String passwordForOpen;

        public ArchiveOpenCryptoCallback(String passwordForOpen) {
            this.passwordForOpen = passwordForOpen;
        }

        public void setCompleted(Long files, Long bytes) {
        }

        public void setTotal(Long files, Long bytes) {
        }

        public String cryptoGetTextPassword() throws SevenZipException {
            return passwordForOpen;
        }
    }
}
