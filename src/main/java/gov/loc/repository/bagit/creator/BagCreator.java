package gov.loc.repository.bagit.creator;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.loc.repository.bagit.annotation.Incubating;
import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.domain.Version;
import gov.loc.repository.bagit.hash.Hasher;
import gov.loc.repository.bagit.hash.MD5Hasher;
import gov.loc.repository.bagit.hash.SHA1Hasher;
import gov.loc.repository.bagit.hash.SHA256Hasher;
import gov.loc.repository.bagit.hash.SHA512Hasher;
import gov.loc.repository.bagit.writer.BagitFileWriter;
import gov.loc.repository.bagit.writer.ManifestWriter;

/**
 * Responsible for creating a bag in place.
 */
public final class BagCreator {
  private static final Logger logger = LoggerFactory.getLogger(BagCreator.class);
  private final Map<String,Hasher> bagitNameToHasherMap;
  
  /**
   * Defaults to the top 4 supported checksum algorithms, namely MD5, SHA1, SHA256, SHA512.
   * If you wish to include a different set of checksum algorithms see the other constructors
   * 
   * @throws NoSuchAlgorithmException in the event that MD5, SHA1, SHA256, or SHA512 doesn't exist 
   */
  public BagCreator() throws NoSuchAlgorithmException{
    this(Arrays.asList(new MD5Hasher(), new SHA1Hasher(), new SHA256Hasher(), new SHA512Hasher()));
  }
  
  /**
   * Allows you to customize which checksum algorithms are used when creating manifest(s)
   * @param hashers the collection of {@link Hasher} that will be used to create the manifest(s)
   */
  public BagCreator(final Collection<Hasher> hashers){
    bagitNameToHasherMap = new HashMap<>();
    for (final Hasher hasher : hashers){
      bagitNameToHasherMap.put(hasher.getBagitName(), hasher);
    }
  }
  
  /**
   * Creates a basic(only required elements) bag plus tag manifest(s).
   * This method moves and creates files, thus if an error is thrown during operation it may leave the filesystem 
   * in an unknown state of transition. Thus this is <b>not thread safe</b>
   * 
   * @param root the directory that will become the base of the bag and where to start searching for content
   * @param includeHidden to include hidden files when generating the bagit files, like the manifests
   * @throws NoSuchAlgorithmException if {@link MessageDigest} can't find the algorithm
   * @throws IOException if there is a problem writing or moving file(s)
   * @return a {@link Bag} object representing the newly created bagit bag
   */
  public Bag bagInPlace(final Path root, final boolean includeHidden) throws NoSuchAlgorithmException, IOException{
    final Bag bag = new Bag(new Version(0, 97));
    bag.setRootDir(root);
    logger.info("Creating a bag with version: [{}] in directory: [{}]", bag.getVersion(), root);
    
    final Path dataDir = root.resolve("data");
    Files.createDirectory(dataDir);
    final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(root);
    for(final Path path : directoryStream){
      if(!path.equals(dataDir) && !Files.isHidden(path) || includeHidden){
        Files.move(path, dataDir.resolve(path.getFileName()));
      }
    }
    
    logger.info("Creating payload manifest(s)");
    final CreatePayloadManifestsVistor payloadVisitor = new CreatePayloadManifestsVistor(bagitNameToHasherMap, includeHidden);
    Files.walkFileTree(dataDir, payloadVisitor);
    
    bag.getPayLoadManifests().addAll(payloadVisitor.getManifests());
    BagitFileWriter.writeBagitFile(bag.getVersion(), bag.getFileEncoding(), root);
    ManifestWriter.writePayloadManifests(bag.getPayLoadManifests(), root, root, bag.getFileEncoding());
    
    //TODO write tag manifests
    logger.info("Creating tag manifest(s)");
    final CreateTagManifestsVistor tagVistor = new CreateTagManifestsVistor(bagitNameToHasherMap, includeHidden);
    Files.walkFileTree(root, tagVistor);
    
    bag.getTagManifests().addAll(tagVistor.getManifests());
    ManifestWriter.writeTagManifests(bag.getTagManifests(), root, root, bag.getFileEncoding());
    
    return bag;
  }
  
  /**
   * Creates a basic(only required elements) .bagit bag in place.
   * This creates files and directories, thus if an error is thrown during operation it may leave the filesystem 
   * in an unknown state of transition. Thus this is <b>not thread safe</b>
   * 
   * @param root the directory that will become the base of the bag and where to start searching for content
   * @param includeHidden to include hidden files when generating the bagit files, like the manifests
   * @return a {@link Bag} object representing the newly created bagit bag
   * @throws NoSuchAlgorithmException if {@link MessageDigest} can't find the algorithm
   * @throws IOException if there is a problem writing files or .bagit directory
   */
  @Incubating
  public Bag createDotBagit(final Path root, final boolean includeHidden) throws NoSuchAlgorithmException, IOException{
    final Bag bag = new Bag(new Version(2, 0));
    bag.setRootDir(root);
    logger.info("Creating a bag with version: [{}] in directory: [{}]", bag.getVersion(), root);
    
    final Path dotbagitDir = root.resolve(".bagit");
    Files.createDirectories(dotbagitDir);
    
    logger.info("Creating payload manifest");
    final CreatePayloadManifestsVistor visitor = new CreatePayloadManifestsVistor(bagitNameToHasherMap, includeHidden);
    Files.walkFileTree(root, visitor);
    
    bag.getPayLoadManifests().addAll(visitor.getManifests());
    BagitFileWriter.writeBagitFile(bag.getVersion(), bag.getFileEncoding(), dotbagitDir);
    ManifestWriter.writePayloadManifests(bag.getPayLoadManifests(), dotbagitDir, root, bag.getFileEncoding());
    
    //TODO write tag manifest
    
    return bag;
  }

  public Map<String, Hasher> getBagitNameToHasherMap() {
    return bagitNameToHasherMap;
  }
}