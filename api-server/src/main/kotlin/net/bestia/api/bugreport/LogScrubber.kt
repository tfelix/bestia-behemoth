package net.bestia.api.bugreport

/**
 * Masks what could name the human behind a report, or let a reader act as them, in the text files it carries. The
 * client scrubs too; this is the copy that does not depend on the client's version.
 */
object LogScrubber {

  private val AUTHORIZATION = Regex("""(?i)\b(Bearer|Ticket)\s+[A-Za-z0-9._~+/=-]+""")
  private val JWT = Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
  private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

  /** Octets above 255 do not match, so most four-part driver versions survive. */
  private val IPV4 = Regex("""\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b""")

  fun scrub(text: String): String {
    return text
      .replace(AUTHORIZATION, "$1 <secret>")
      .replace(JWT, "<jwt>")
      .replace(EMAIL, "<email>")
      .replace(IPV4, "<ip>")
  }
}
