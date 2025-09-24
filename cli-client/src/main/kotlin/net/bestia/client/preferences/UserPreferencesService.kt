package net.bestia.client.preferences

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * User preference service that provides key-value storage in the user's home directory.
 * Supports both simple string-based preferences and typed JSON preferences.
 */
class UserPreferencesService {

  private val configDir = Paths.get(System.getProperty("user.home"), ".bestia-cli")
  private val preferencesFile = configDir.resolve("preferences.properties")

  private val cache = ConcurrentHashMap<String, String>()

  init {
    // Create config directory if it doesn't exist
    if (!Files.exists(configDir)) {
      Files.createDirectories(configDir)
    }
  }

  /**
   * Store a simple string preference
   */
  fun setPreference(key: String, value: String) {
    cache[key] = value
  }

  /**
   * Get a string preference with optional default value
   */
  fun getPreference(key: String, defaultValue: String? = null): String? {
    return cache[key] ?: defaultValue
  }

  /**
   * Remove a preference
   */
  fun removePreference(key: String) {
    cache.remove(key)
    savePreferences()
  }

  /**
   * Check if a preference exists
   */
  fun hasPreference(key: String): Boolean {
    return cache.containsKey(key)
  }

  /**
   * Clear all preferences
   */
  fun clearAll() {
    cache.clear()
    savePreferences()
  }

  /**
   * Get preferences as a map (string preferences only)
   */
  fun getAllPreferences(): Map<String, String> {
    return cache.toMap()
  }

  /**
   * Batch set preferences
   */
  fun setPreferences(preferences: Map<String, String>) {
    cache.putAll(preferences)
    savePreferences()
  }

  private fun loadPreferences() {
    // Load simple string preferences
    if (Files.exists(preferencesFile)) {
      try {
        val properties = Properties()
        Files.newInputStream(preferencesFile).use { properties.load(it) }
        properties.forEach { (key, value) ->
          cache[key.toString()] = value.toString()
        }
      } catch (e: Exception) {
        // If preferences file is corrupted, start fresh
        println("Warning: Could not load preferences file, starting with empty preferences")
      }
    }
  }

  private fun savePreferences() {
    try {
      val properties = Properties()
      cache.forEach { (key, value) ->
        properties.setProperty(key, value)
      }

      Files.newOutputStream(preferencesFile).use {
        properties.store(it, "Bestia CLI User Preferences")
      }
    } catch (e: Exception) {
      println("Warning: Could not save preferences: ${e.message}")
    }
  }

  companion object {
    // Singleton instance for easy access
    @JvmStatic
    val instance: UserPreferencesService by lazy { UserPreferencesService() }
  }
}
