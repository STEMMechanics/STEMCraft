package dev.stemcraft.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class BaseSourceScanTest {

    protected Path sourceRoot() {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path srcMainJava = projectDir.resolve("src/main/java");

        if (!Files.isDirectory(srcMainJava)) {
            throw new IllegalStateException("Cannot find src/main/java from " + projectDir);
        }
        return srcMainJava;
    }

    protected List<Path> listJavaFiles() throws IOException {
        Path root = sourceRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    protected List<String> readLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    protected boolean isCommentLine(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
    }
}