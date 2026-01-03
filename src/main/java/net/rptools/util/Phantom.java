package net.rptools.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * File walker looking for ".hbs" files
 */
public class Phantom {
    private static final Logger log = LoggerFactory.getLogger(Phantom.class);
    private static final List<Path> PATHS = new ArrayList<>();
    private static final Predicate<File> FILE_PREDICATE = file ->
            file != null && file.exists() && file.canRead() && !file.isDirectory()
                    && file.getName().toLowerCase().endsWith(".hbs");
    private static final AtomicBoolean success = new AtomicBoolean();
    private static final AtomicReference<Throwable> throwable = new AtomicReference<>(null);

    public Phantom(Path rootPath) {
        PATHS.clear();
        throwable.set(null);
        success.set(true);
        FileVisitor<Path> ghost = new Ghost();
        try {
            Files.walkFileTree(rootPath, ghost);
        } catch (Exception e) {
            success.set(false);
            log.error(e.getLocalizedMessage(), e);
        }
    }

    public Throwable getThrowable(){ return throwable.get();}

    private static class Ghost extends SimpleFileVisitor<Path> {
        private void onError(Throwable t){
            success.set(false);
            throwable.set(t);
            List<String> args = new ArrayList<>();
            args.add(t.getLocalizedMessage());
            args.addAll(
                    Arrays.stream(t.getStackTrace())
                            .limit(24)
                            .map(stackTraceElement ->
                                    String.format("Line: %d, Class: %s, Method: %s",
                                            stackTraceElement.getLineNumber(),
                                            stackTraceElement.getClassName(),
                                            stackTraceElement.getMethodName()
                                    )).toList());
            Utils.alert("Error Walking File-Tree: " + t.getClass().getSimpleName(), args.toArray(String[]::new));
        }

        @Override
        @Nonnull
        public FileVisitResult visitFile(@Nonnull Path path, @Nonnull BasicFileAttributes attrs) {
            if (FILE_PREDICATE.test(path.toAbsolutePath().toFile())) {
                PATHS.add(path.toAbsolutePath());
                return FileVisitResult.CONTINUE;
            } else {
                try {
                    return super.visitFile(path, attrs);
                } catch (IOException e) {
                    onError(e);
                    return FileVisitResult.TERMINATE;
                }
            }
        }

        @Override @Nonnull
        public FileVisitResult preVisitDirectory(@Nonnull Path dir, @Nonnull BasicFileAttributes attrs) {
            try {
                return super.preVisitDirectory(dir, attrs);
            } catch (Throwable t){
                onError(t);
            }
            return FileVisitResult.TERMINATE;
        }

        @Override
        @Nonnull
        public FileVisitResult visitFileFailed(@Nullable Path file, @Nonnull IOException e) {
            onError(e);
            return FileVisitResult.TERMINATE;
        }
    }

    public List<Path> getPaths() {
        if (success.get()) {
            return PATHS;
        } else {
            return new ArrayList<>();
        }
    }
}
