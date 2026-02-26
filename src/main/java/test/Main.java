package test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import servlet.annotations.Controller;

public class Main {

    public static void main(String[] args) throws Exception {
        // Scanner le classpath pour trouver les classes annotees @Controller
        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(File.pathSeparator);

        List<Class<?>> controllers = new ArrayList<>();

        for (String path : paths) {
            File file = new File(path);
            if (file.isDirectory()) {
                // Scanner le repertoire recursivement
                List<Class<?>> found = scanDirectory(file, file);
                controllers.addAll(found);
            }
        }

        // Afficher les resultats
        System.out.println("=== Classes annotees @Controller trouvees ===");
        for (Class<?> clazz : controllers) {
            System.out.println("- " + clazz.getName());
        }
        System.out.println("=============================================");
        System.out.println("Total : " + controllers.size() + " controller(s)");
    }

    // Scanner recursivement un repertoire pour trouver les .class annotes @Controller
    private static List<Class<?>> scanDirectory(File root, File current) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        File[] files = current.listFiles();
        if (files == null) return classes;

        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(scanDirectory(root, file));
            } else if (file.getName().endsWith(".class")) {
                // Construire le nom complet de la classe
                String relativePath = root.toURI().relativize(file.toURI()).getPath();
                String className = relativePath
                        .replace("/", ".")
                        .replace("\\", ".")
                        .substring(0, relativePath.length() - 6); // enlever .class

                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    classes.add(clazz);
                }
            }
        }
        return classes;
    }
}
