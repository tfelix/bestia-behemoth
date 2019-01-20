package net.bestia.model.util

import mu.KotlinLogging
import java.io.*

@PublishedApi
internal val LOG = KotlinLogging.logger { }

/**
 * Helper object to serialize objects into byte arrays and vice versa. Some data
 * in the database is persisted as binary stream data. This class helps to
 * transform this data into binary streams.
 *
 * @author Thomas Felix
 */
object ObjectSerializer {

  /**
   * Deserializes the entity from raw byte array.
   *
   * @param data
   * Raw byte array.
   * @return The entity.
   */
  inline fun <reified T> deserialize(data: ByteArray): T? {
    try {
      ByteArrayInputStream(data).use { bis ->
        ObjectInputStream(bis).use { ois ->

          return ois.readObject() as T
        }
      }
    } catch (e: Exception) {
      LOG.warn("Could not deserialize entity.", e)
      return null
    }
  }

  /**
   * Serializes the object to a raw byte array.
   *
   * @param obj
   * The object
   * @return Raw byte array.
   */
  inline fun <reified T> serialize(obj: T): ByteArray? {
    try {
      ByteArrayOutputStream().use { bos ->
        ObjectOutputStream(bos).use { ois ->
          ois.writeObject(obj)

          return bos.toByteArray()
        }
      }
    } catch (e: IOException) {
      LOG.warn("Could not serialize entity.", e)
      return null
    }
  }
}
