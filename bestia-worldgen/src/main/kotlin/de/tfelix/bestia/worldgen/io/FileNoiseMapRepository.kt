package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import mu.KotlinLogging
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

private val LOG = KotlinLogging.logger { }

class FileNoiseMapRepository(
    private val saveDir: Path
) : NoiseMapRepository {

  override fun delete(identifier: String) {
    val filename = "$identifier$FILE_EXT"
    val file = Paths.get(saveDir.toAbsolutePath().toString(), filename).toFile()

    file.delete()
  }

  override fun save(identifier: String, map: NoiseMap2D) {
    val filename = "$identifier$FILE_EXT"
    val filePath = Paths.get(saveDir.toAbsolutePath().toString(), filename)

    LOG.info { "Writing $identifier to file: $filePath." }

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
        oos.writeObject(map)
      }
    } catch (e: IOException) {
      LOG.error("Error while serializing map part.", e)
    }
  }

  override fun load(identifier: String): NoiseMap2D? {
    /*
    val file = Paths
        .get(saveDir.toAbsolutePath().toString(), identifier + FILE_EXT)
        .toFile()

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
    }*/

    return null
  }

  companion object {
    private const val FILE_EXT = ".dat"
  }
}
