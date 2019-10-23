package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.map.MapDataPart
import mu.KotlinLogging
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * This is a local implementation of the [MapGenDAO] API. It is assumed
 * that all nodes share the same instance of this class in order for the
 * communication to work properly.
 *
 * @author Thomas Felix
 */
class LocalFileMapGenDAO(
    private val nodeName: String,
    private val saveDir: Path
) : MapGenDAO {

  private val datastore = HashMap<String, Any>()
  private val nodes = HashSet<String>()

  private inner class LocalFileIterator(
      fileDir: Path
  ) : Iterator<MapDataPart> {

    private var currentFile = 0
    private val files: Array<File> = fileDir.toFile().listFiles()

    override fun hasNext(): Boolean {
      return currentFile < files.size
    }

    override fun next(): MapDataPart {
      var nextFile: File? = null
      while (nextFile == null && currentFile < files.size) {
        if (!files[currentFile].isDirectory && files[currentFile].name.endsWith(FILE_EXT)) {
          nextFile = files[currentFile]
        }

        currentFile++
      }

      if (nextFile == null) {
        throw NoSuchElementException()
      }

      try {
        ObjectInputStream(FileInputStream(nextFile)).use { oos ->
          return oos.readObject() as MapDataPart
        }
      } catch (e: IOException) {
        LOG.error("Error while serializing map part.", e)
        throw NoSuchElementException()
      } catch (e: ClassNotFoundException) {
        LOG.error("Error while serializing map part.", e)
        throw NoSuchElementException()
      }
    }

  }

  override fun saveMapDataPart(part: MapDataPart) {
    val filename = "${part.ident}$FILE_EXT"
    val filePath = Paths.get(saveDir.toString(), filename)

    LOG.info { "Writing $part to file: $filePath." }

    if (Files.notExists(filePath)) {
      try {
        Files.createDirectories(filePath.parent)
        Files.createFile(filePath)
      } catch (e: IOException) {
        LOG.error("Could not create file: {}.", filePath, e)
        return
      }
    }

    try {
      ObjectOutputStream(FileOutputStream(filePath.toFile())).use { oos ->
        oos.writeObject(part)
      }
    } catch (e: IOException) {
      LOG.error("Error while serializing map part.", e)
    }

  }

  override val mapDataPartIterator: Iterator<MapDataPart> = LocalFileIterator(saveDir)

  override fun getNodeData(key: String): Any {
    return datastore[nodeName + "_" + key]!!
  }

  override fun getAllData(key: String): List<Any> {
    val data = ArrayList<Any>()

    nodes.forEach { _ ->
      val d = getNodeData(key)
      data.add(d)
    }

    return data
  }

  override fun getMasterData(key: String): Any {
    return datastore["master_$key"]!!
  }

  override fun saveMasterData(key: String, data: Any) {
    datastore["master_$key"] = data
  }

  override fun saveNodeData(key: String, data: Any) {
    nodes.add(nodeName)
    datastore[nodeName + "_" + key] = data
  }

  companion object {
    private const val FILE_EXT = ".dat"
  }
}
