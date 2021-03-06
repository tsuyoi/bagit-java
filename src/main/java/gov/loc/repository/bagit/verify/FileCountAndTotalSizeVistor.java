package gov.loc.repository.bagit.verify;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.loc.repository.bagit.util.PathUtils;

/**
 * Implements {@link SimpleFileVisitor} to ensure that the encountered file is in one of the manifests.
 */
public class FileCountAndTotalSizeVistor extends SimpleFileVisitor<Path> {
  private static final Logger logger = LoggerFactory.getLogger(FileCountAndTotalSizeVistor.class);
  
  private transient long totalSize;
  private transient long count;

  @Override
  public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
    if(PathUtils.isHidden(dir) || dir.endsWith(Paths.get(".bagit"))){
      logger.debug("Skipping {} cause we ignore hidden directories", dir);
      return FileVisitResult.SKIP_SUBTREE;
    }
    
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) throws IOException{
    if(PathUtils.isHidden(path) && !path.endsWith(".keep")){
      logger.debug("Skipping [{}] since we are ignoring hidden files", path);
    }
    else{
      count++;
      final long size = Files.size(path);
      logger.debug("File [{}] hash a size of [{}] bytes", path, size);
      totalSize += size;
    }
    
    return FileVisitResult.CONTINUE;
  }

  public long getCount() {
    return count;
  }

  public long getTotalSize() {
    return totalSize;
  }
}
