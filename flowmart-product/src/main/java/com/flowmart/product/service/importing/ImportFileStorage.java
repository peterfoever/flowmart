package com.flowmart.product.service.importing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;

/** 文件只放私有目录，不以原始文件名拼路径，不通过静态资源暴露。 */
@Component
public class ImportFileStorage {
    private final Path root;
    public ImportFileStorage(@Value("${flowmart.imports.storage-dir:./data/imports}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    public String save(Long batchId, byte[] data) throws IOException {
        Files.createDirectories(root);
        Path target = root.resolve(batchId + ".xlsx");
        Files.write(target, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return target.toString();
    }
}
