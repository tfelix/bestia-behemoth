package net.bestia.model

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.ObjectMapper
import net.bestia.model.bestia.PlayerBestia
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.reflections.Reflections
import java.io.Serializable
import javax.persistence.Entity

class GeneralModelTest {
  private val mapper = ObjectMapper().apply {
    // Ignore null fields for the sake of the tests.
    setSerializationInclusion(Include.NON_NULL)
  }

  private val reflections = Reflections("net.bestia.model")
  private val allEntities = reflections.getTypesAnnotatedWith(Entity::class.java)


  /**
   * All entities must implement serializable.
   */
  @Test
  fun all_serializable() {
    for (clazz in allEntities) {

      // Whitelist classes dont need to be serializable.
      if (WHITELIST.contains(clazz)) {
        continue
      }

      assertTrue(
          Serializable::class.java.isAssignableFrom(clazz),
          clazz.toGenericString() + " does not implement Serializable."
      )
    }
  }

  /**
   * All entities must implement serializable
   */
  @Test
  @Throws(InstantiationException::class, IllegalAccessException::class)
  fun all_std_ctor() {
    for (clazz in allEntities) {
      clazz.getDeclaredConstructor().newInstance()
    }
  }

  /**
   * All models should be serializable to JSON.
   */
  @Test
  @Throws(Exception::class)
  fun all_serializable_json() {
    for (clazz in allEntities) {

      // Whitelist classes dont need to be serializable.
      if (WHITELIST.contains(clazz)) {
        continue
      }

      val obj = clazz.getDeclaredConstructor().newInstance()
      mapper.writeValueAsString(obj)
    }
  }

  companion object {
    private val WHITELIST = setOf(
        PlayerBestia::class.java
    )
  }
}
