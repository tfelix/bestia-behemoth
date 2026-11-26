package net.bestia.api.bugreport

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BugReportAttachmentTest {

  @Test
  fun `a screenshot is named by what its bytes are`() {
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01)
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D)

    assertEquals("screenshot.jpg", BugReportAttachment.SCREENSHOT.prepare(jpeg)?.name)
    assertEquals("screenshot.png", BugReportAttachment.SCREENSHOT.prepare(png)?.name)
    assertNull(BugReportAttachment.SCREENSHOT.prepare("<svg/>".toByteArray()))
  }

  @Test
  fun `the context must be a JSON object and is scrubbed`() {
    val prepared = BugReportAttachment.CONTEXT.prepare("""{"server":"10.0.0.7"}""".toByteArray())

    assertEquals("context.json", prepared?.name)
    assertEquals("""{"server":"<ip>"}""", prepared?.content?.toString(Charsets.UTF_8))
    assertNull(BugReportAttachment.CONTEXT.prepare("[1, 2]".toByteArray()))
    assertNull(BugReportAttachment.CONTEXT.prepare("not json".toByteArray()))
  }

  @Test
  fun `empty and oversized parts are refused, chunks are kept as sent`() {
    val chunks = byteArrayOf(1, 2, 3)

    assertNull(BugReportAttachment.CLIENT_LOG.prepare(ByteArray(0)))
    assertNull(BugReportAttachment.NET_LOG.prepare(ByteArray(256 * 1024 + 1) { 'a'.code.toByte() }))
    assertContentEquals(chunks, BugReportAttachment.CHUNKS.prepare(chunks)?.content)
  }
}
