package org.ciisa.tpw;

import com.github.junrar.exception.RarException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;

public class App {
    public static void main(String[] args) throws IOException {
        File rootDir = new File("D:\\Descargas\\IF101IINF_T1-2024_53_ON_O-EVALUACIÓN DE UNIDAD 1-185483");
        File[] directories = rootDir.listFiles(File::isDirectory);

        CSVWriter csvWriter = new CSVWriter("output.csv");
        csvWriter.writeHeader();

        for (File dir : directories) {
            processDirectory(dir, csvWriter);
        }

        csvWriter.close();
    }

    private static void processDirectory(File dir, CSVWriter csvWriter) {
        try {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".zip") || name.endsWith(".rar"));
            for (File file : files) {
                extractAndProcessFile(file, csvWriter, dir.getName());
            }
        } catch (Exception e) {
            System.out.println("Error processing directory " + dir.getName() + ": " + e.getMessage());
        }
    }

    private static void extractAndProcessFile(File file, CSVWriter csvWriter, String dirName) throws InterruptedException, IOException {
        // El destino se establece como el directorio padre del archivo comprimido
        File destDir = file.getParentFile();

        // Limpieza inicial: eliminar todo excepto .zip y .rar
        File[] filesInDestDir = destDir.listFiles();
        if (filesInDestDir != null) {
            for (File existingFile : filesInDestDir) {
                if (!existingFile.getName().endsWith(".zip") && !existingFile.getName().endsWith(".rar")) {
                    deleteFileOrDirectory(existingFile);
                }
            }
        }

        // Intentamos primero con el comando de 7zip
        String command = String.format("7z x \"%s\" -o\"%s\" -y", file.getAbsolutePath(), destDir.getAbsolutePath());
        Process process = Runtime.getRuntime().exec(command);
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to extract archive using 7z with exit code " + exitCode+" de:"+file);
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("7z failed, trying with Apache Commons Compress: " + e.getMessage());
            if (file.getName().endsWith(".zip")) {
                unzipUsingCommonsCompress(file, destDir);
            } else {
                throw new IOException("Unsupported archive format for fallback: " + file.getName());
                // For RAR files, an additional method with support for RAR extraction should be implemented.
            }
        }

        // Identificar si los archivos HTML están directamente en tempDir o en una subcarpeta
        File[] htmlFiles = destDir.listFiles((d, name) -> name.endsWith(".html"));
        File validationDirectory = destDir;

        if (htmlFiles == null || htmlFiles.length == 0) {
            // No HTML files at the root, check for a single directory ignoring __MACOSX
            File[] directories = destDir.listFiles(File::isDirectory);
            File[] validDirectories = Arrays.stream(directories)
                    .filter(dir -> !dir.getName().equals("__MACOSX"))
                    .toArray(File[]::new);
            if (validDirectories.length == 1) {
                validationDirectory = validDirectories[0]; // Use the single valid directory
            } else {
                throw new IOException("Expected HTML files at the root or a single valid folder within the temporary directory.");
            }
        }

        HTMLValidator validator = new HTMLValidator(validationDirectory);
        Score score = validator.validate();
        csvWriter.writeLine(dirName, score);
    }

    // Método de utilidad para eliminar archivos y directorios
// Utiliza recursividad para eliminar directorios no vacíos
    private static void deleteFileOrDirectory(File fileOrDirectoryToDelete) {
        if (fileOrDirectoryToDelete.isDirectory()) {
            File[] allContents = fileOrDirectoryToDelete.listFiles();
            if (allContents != null) {
                for (File file : allContents) {
                    deleteFileOrDirectory(file);
                }
            }
        }
        if (!fileOrDirectoryToDelete.delete()) {
            System.err.println("Unable to delete file or directory: " + fileOrDirectoryToDelete.getAbsolutePath());
        }
    }

    // Método agregado para descomprimir archivos ZIP usando Apache Commons Compress
    private static void unzipUsingCommonsCompress(File zipFile, File outputDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File entryDestination = new File(outputDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryDestination.toPath());
                } else {
                    File parent = entryDestination.getParentFile();
                    if (parent != null) {
                        Files.createDirectories(parent.toPath());
                    }
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = Files.newOutputStream(entryDestination.toPath())) {
                        IOUtils.copy(in, out);
                    }
                }
            }
        }
    }
}
