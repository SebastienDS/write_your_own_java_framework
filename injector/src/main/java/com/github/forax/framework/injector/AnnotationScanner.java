package com.github.forax.framework.injector;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class AnnotationScanner {
  private static final int CLASS_EXTENSION_LENGTH = ".class".length();

  private final HashMap<Class<?>, Consumer<? super Class<?>>> map = new HashMap<>();

  static Stream<String> findAllJavaFilesInFolder(Path path) throws IOException {
    Objects.requireNonNull(path);
    return Files.list(path)
        .map(Path::getFileName)
        .map(Object::toString)
        .filter(filename -> filename.endsWith(".class"))
        .map(filename -> filename.substring(0, filename.length() - CLASS_EXTENSION_LENGTH));
  }

  static List<Class<?>> findAllClasses(String packageName, ClassLoader classLoader) {
    Objects.requireNonNull(packageName);
    Objects.requireNonNull(classLoader);
    var urls = Collections.list(Utils2.getResources(packageName.replace('.', '/'), classLoader));

    if (urls.isEmpty()) {
      throw new IllegalStateException("No folder found");
    }

    return urls.stream()
        .map(AnnotationScanner::toPath)
        .flatMap(path -> {
          try {
            return findAllJavaFilesInFolder(path)
                .<Class<?>>map(className -> Utils2.loadClass(packageName + '.' + className, classLoader));
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }).toList();
  }

  private static Path toPath(URL url) {
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  public void addAction(Class<? extends Annotation> annotationClass, Consumer<? super Class<?>> action) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(action);
    var previous = map.putIfAbsent(annotationClass, action);
    if (previous != null) {
      throw new IllegalStateException("Annotation already registered");
    }
  }

  public void scanClassPathPackageForAnnotations(Class<?> type) {
    Objects.requireNonNull(type);
    var correspondingPackage = type.getPackageName();
    var classLoader = type.getClassLoader();
    findAllClasses(correspondingPackage, classLoader)
        .forEach(scannedClass -> {
          Stream.of(scannedClass.getAnnotations())
              .map(Annotation::annotationType)
              .map(map::get)
              .filter(Objects::nonNull)
              .forEach(action -> action.accept(scannedClass));
        });
  }
}
