package com.microsoft.CNTK;

import com.sun.org.apache.bcel.internal.util.ClassLoader;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * A helper class for loading native CNTK libraries from Java
 *
 * <p>The Java interface to CNTK depends on CNTK native libraries that need to be loaded at runtime.
 * This class is a simple utility that can load the native libraries from a jar in one of two ways:</p>
 *
 * <ul>
 *     <li>By name: If a particular native library is needed, it will extract it to a temp folder
 *     (along with its dependencies) and load it from there.</li>
 *     <li>All libraries: If there is a manifest containing the libraries to load from the jar file,
 *     all libraries will be extracted to a temp folder and the ones in the manifest will be loaded
 *     (along with dependencies). </li>
 * </ul>
 *
 * <p>The jar with the CNTK native libraries must contain a file name 'NATIVE_MANIFEST' that lists
 * all native files (one per line, full name) to be extracted. The native libraries should be
 * in folders describing the OS they run on: linux, windows, mac. This class assumes that all native
 * libraries were compiled with the 'rpath' flag. </p>
 * */
public class CNTKNativeUtils {

    private static final String manifestName = "NATIVE_MANIFEST";
    private static final String loadManifestName = "NATIVE_LOAD_MANIFEST";
    private static String OS = System.getProperty("os.name").toLowerCase();
    private static String resourcesPath = getResourcesPath();
    private static Boolean extractionDone = false;
    private static File tempDir;
    static{
        try{
            tempDir = Files.createTempDirectory("tmp").toFile();
            tempDir.deleteOnExit();
        }
        catch (IOException e){
            throw new IOError(e);
        }
    }

    /**
     * Loads all CNTK native libraries from the jar file, if the jar contains a plain text file
     * named 'NATIVE_LOAD_MANIFEST'.
     *
     * <p>The NATIVE_LOAD_MANIFEST contains what libraries to be loaded (one per line, full name)
     * and the order in which they should be loaded. Note that the dependencies of each library
     * will be loaded automatically when the top-level library is loaded so they shouldn't
     * be specified in this file.</p>
     * */
    public static void loadAllLibraries(){
        try{
            extractNativeLibraries();
            String[] librariesToLoad = getResourceLines(loadManifestName);
            for (String libName: librariesToLoad){
                System.load(tempDir.getAbsolutePath() + File.separator + libName);
            }
        }
        catch (Exception e){
            throw new UnsatisfiedLinkError(String.format("Could not load all CNTK libraries because " +
                    "we encountered the following error: %s", e.getMessage()));
        }
    }

    /**
     * Loads a named CNTK native library from the jar file
     *
     * <p>This method will first try to load the library from java.library.path system property.
     * Only if that fails, the named native library and its dependencies will be extracted to
     * a temporary folder and loaded from there.</p>
     * */
    public static void loadLibraryByName(String libName){
        try{
            // First try loading by name
            // It's possible that the native library is already on a path java can discover
            System.loadLibrary(libName);
        }
        catch (UnsatisfiedLinkError e){
            try{
                extractNativeLibraries();
                // Get the OS specific library name
                libName = System.mapLibraryName(libName);
                // Try to load library from extracted native resources
                System.load(tempDir.getAbsolutePath() + File.separator + libName);
            }
            catch (Exception ee){
                throw new UnsatisfiedLinkError(String.format(
                        "Could not load CNTK native libraries because " +
                        "we encountered the following problems: %s and %s",
                        e.getMessage(), ee.getMessage()));
            }
        }
    }

    private static void extractNativeLibraries() throws IOException{
        if (!extractionDone) {
            String[] libNames = getResourceLines(manifestName);
            // Extract all OS specific native libraries to temporary location
            for (String libName: libNames) {
                extractResourceFromPath(libName, resourcesPath);
            }
        }
        extractionDone = true;
    }

    private static String normalizePath(String path) {
       return "/"+(path).replace("\\","/");
    }

    private static String[] getResourceLines(String resourceName) throws IOException{
        // Read resource file if it exists

        String path = normalizePath(resourcesPath+resourceName);
        InputStream inStream = CNTKNativeUtils.class.getResourceAsStream(path);
        if (inStream == null) {
            throw new FileNotFoundException("Could not find native resources in jar. " +
                    "Make sure the CNTK jar containing the native libraries was added to the classpath.");
        }
        BufferedReader resourceReader = new BufferedReader(
                new InputStreamReader(inStream, "UTF-8")
        );
        ArrayList<String> lines = new ArrayList<String>();
        for (String line; (line = resourceReader.readLine()) != null; ) {
            lines.add(line);
        }
        resourceReader.close();
        inStream.close();
        return lines.toArray(new String[lines.size()]);
    }

    private static String getResourcesPath(){
        String sep = System.getProperty("file.separator");
        String CNTKPrefix = "com" + sep
                + "microsoft" + sep
                + "CNTK" + sep
                + "lib" + sep
                + "%s" + sep;
        if (OS.contains("linux")){
            return String.format(CNTKPrefix, "linux");
        }
        else if (OS.contains("windows")){
            return String.format(CNTKPrefix, "windows");
        }
        else if (OS.contains("mac")|| OS.contains("darwin")){
            return String.format(CNTKPrefix, "mac");
        }
        else{
            throw new UnsatisfiedLinkError(
                    String.format("CNTK doesn't currently have native support for OS: %s", OS)
            );
        }
    }

    private static void extractResourceFromPath(String libName, String prefix) throws IOException{

        File temp = new File(tempDir.getPath() + File.separator + libName);
        temp.createNewFile();
        temp.deleteOnExit();

        if (!temp.exists()) {
            throw new FileNotFoundException(String.format(
                    "Temporary file %s could not be created. Make sure you can write to this location.",
                    temp.getAbsolutePath())
            );
        }

        String path = normalizePath(prefix + libName);
        InputStream inStream = CNTKNativeUtils.class.getResourceAsStream(path);
        if (inStream == null) {
            throw new FileNotFoundException(String.format("Could not find resource %s in jar.", path));
        }

        FileOutputStream outStream = new FileOutputStream(temp);
        byte[] buffer = new byte[1 << 18];
        int bytesRead;

        try {
            while ((bytesRead = inStream.read(buffer)) >= 0) {
                outStream.write(buffer, 0, bytesRead);
            }
        } finally {
            outStream.close();
            inStream.close();
        }
    }

}
