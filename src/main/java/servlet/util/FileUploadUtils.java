package servlet.util;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import servlet.Upload;
import servlet.annotations.UploadConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileUploadUtils {

    public static boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/form-data");
    }

    public static Map<String, List<Upload>> parseUploads(HttpServletRequest request, Method method, Class<?> controllerClass) {
        Map<String, List<Upload>> uploadsMap = new HashMap<>();

        if (!isMultipartRequest(request)) {
            return uploadsMap;
        }

        // Determiner le dossier d'upload via @UploadConfig
        String uploadDirName = "uploads";
        if (method != null && method.isAnnotationPresent(UploadConfig.class)) {
            uploadDirName = method.getAnnotation(UploadConfig.class).directory();
        } else if (controllerClass != null && controllerClass.isAnnotationPresent(UploadConfig.class)) {
            uploadDirName = controllerClass.getAnnotation(UploadConfig.class).directory();
        }

        // Resoudre le chemin absolu du dossier d'upload
        ServletContext context = request.getServletContext();
        String realPath = context.getRealPath("/");

        File uploadDir;
        File dirPathCandidate = new File(uploadDirName);
        if (dirPathCandidate.isAbsolute()) {
            uploadDir = dirPathCandidate;
        } else if (realPath != null) {
            uploadDir = new File(realPath, uploadDirName);
        } else {
            uploadDir = new File(uploadDirName);
        }

        // Creer le dossier automatiquement s'il n'existe pas
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        try {
            Collection<Part> parts = request.getParts();
            if (parts == null) return uploadsMap;

            for (Part part : parts) {
                String submittedFileName = part.getSubmittedFileName();
                if (submittedFileName != null && !submittedFileName.trim().isEmpty()) {
                    String fieldName = part.getName();
                    String originalFileName = extractFileName(submittedFileName);
                    String extension = extractExtension(originalFileName);
                    String contentType = part.getContentType();
                    long size = part.getSize();

                    // Fichier de destination sur le disque
                    File destFile = getUniqueFile(uploadDir, originalFileName);

                    // Ecrire le fichier sur le disque
                    try (InputStream input = part.getInputStream();
                         FileOutputStream output = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = input.read(buffer)) > 0) {
                            output.write(buffer, 0, length);
                        }
                    }

                    Upload upload = new Upload(
                            originalFileName,
                            extension,
                            contentType,
                            size,
                            destFile.getAbsolutePath()
                    );

                    uploadsMap.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(upload);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uploadsMap;
    }

    private static String extractFileName(String submittedFileName) {
        // Supprimer les chemins eventuels (ex: C:\path\file.txt -> file.txt)
        int lastSlash = Math.max(submittedFileName.lastIndexOf('/'), submittedFileName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            return submittedFileName.substring(lastSlash + 1);
        }
        return submittedFileName;
    }

    private static String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }

    private static File getUniqueFile(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) {
            return file;
        }

        String baseName = fileName;
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            ext = fileName.substring(dotIndex);
        }

        int count = 1;
        while (file.exists()) {
            file = new File(dir, baseName + "_" + System.currentTimeMillis() + "_" + count + ext);
            count++;
        }
        return file;
    }
}
