package net.bestia.messages

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.fasterxml.jackson.databind.type.TypeFactory
import mu.KotlinLogging
import org.reflections.Reflections
import java.lang.IllegalArgumentException
import java.lang.reflect.Modifier
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * Custom TypeId Resolver for message objects. Upon start it looks for all
 * messages which inherit from Message and determine their id. It stores it and
 * uses this serialize the messages.
 *
 * @author Thomas Felix
 */
class MessageTypeIdResolver : TypeIdResolverBase() {

  private val typeFactory = TypeFactory.defaultInstance()
  private var baseType: JavaType? = null

  /**
   * Finds all IDs of the messages and registers them for later
   * identification.
   */
  override fun init(bt: JavaType?) {
    super.init(bt)
    baseType = bt
  }

  override fun getMechanism(): Id {
    return Id.CUSTOM
  }

  override fun idFromValue(value: Any): String {
    return idFromValueAndType(value, value.javaClass)
  }

  override fun idFromValueAndType(value: Any, suggestedType: Class<*>): String {
    return classToId[suggestedType]
        ?: throw IllegalArgumentException("Could not get ID from class: $suggestedType")
  }

  override fun typeFromId(context: DatabindContext?, id: String?): JavaType {
    val clazz = idToClass[id]
    return typeFactory.constructSpecializedType(baseType!!, clazz)
  }

  companion object {
    private val idToClass = HashMap<String, Class<out MessageId>>()
    private val classToId = HashMap<Class<out MessageId>, String>()

    init {
      val reflections = Reflections("net.bestia.messages")
      val messages = reflections.getSubTypesOf(MessageId::class.java)

      // Instantiate the message classes to get their message id from the
      // method and store it for later serialization and deserialization.
      for (msg in messages) {

        // Avoid abstract classes.
        if (Modifier.isAbstract(msg.modifiers)) {
          continue
        }

        try {
          val fields = msg.fields
          LOG.debug(fields.toString())
          val messageId = msg.getDeclaredField("MESSAGE_ID")
          val key = messageId.get(null) as String

          idToClass[key] = msg
          classToId[msg] = key

          LOG.trace("Found Message.class: {} - {}", key, msg.toString())

        } catch (e: Exception) {
          LOG.error(e) {
            "Could not get static MESSAGE_ID field from Message class: ${msg.name}. " +
                "Serialization and deserialization will fail."
          }
          System.exit(1)
        }
      }
    }
  }
}
