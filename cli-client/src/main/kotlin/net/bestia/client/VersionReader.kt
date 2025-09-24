package net.bestia.client

import java.io.IOException
import java.util.*

object VersionReader {
  val version: String
    get() {
      try {
        VersionReader::class.java.getResourceAsStream("/version.properties").use {
          val props = Properties()
          props.load(it)
          return props.getProperty("version", "unknown")
        }
      } catch (e: IOException) {
        return "unknown"
      }
    }
}