package net.bestia.api.bugreport

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/** The files a client may send with a report, each as a multipart part named [part]. */
enum class BugReportAttachment(val part: String, private val maxBytes: Int) {
  SCREENSHOT("screenshot", 4 * MIB),
  CONTEXT("context", 256 * KIB),
  CLIENT_LOG("log", 1 * MIB),
  NET_LOG("netlog", 256 * KIB),
  CHUNKS("chunks", 4 * MIB),
  ;

  data class Prepared(val name: String, val content: ByteArray)

  /** The file as it is kept and published, or null when [bytes] are not what this part must hold. */
  fun prepare(bytes: ByteArray): Prepared? {
    if (bytes.isEmpty() || bytes.size > maxBytes) {
      return null
    }

    return when (this) {
      SCREENSHOT -> imageExtensionOf(bytes)?.let { Prepared("screenshot.$it", bytes) }
      CONTEXT -> scrubbedText(bytes).takeIf { isJsonObject(it) }?.let { Prepared("context.json", it.toByteArray()) }
      CLIENT_LOG -> Prepared("client.log", scrubbedText(bytes).toByteArray())
      NET_LOG -> Prepared("netlog.txt", scrubbedText(bytes).toByteArray())
      CHUNKS -> Prepared("chunks.bin", bytes)
    }
  }

  private fun imageExtensionOf(bytes: ByteArray): String? {
    return when {
      bytes.startsWith(JPEG_MAGIC) -> "jpg"
      bytes.startsWith(PNG_MAGIC) -> "png"
      else -> null
    }
  }

  private fun scrubbedText(bytes: ByteArray): String {
    return LogScrubber.scrub(bytes.toString(Charsets.UTF_8))
  }

  private fun isJsonObject(text: String): Boolean {
    return runCatching { JSON.readTree(text).isObject }.getOrDefault(false)
  }

  private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    return size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
  }

  private companion object {
    val JSON = jacksonObjectMapper()
    val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
  }
}

private const val KIB = 1024
private const val MIB = 1024 * KIB
