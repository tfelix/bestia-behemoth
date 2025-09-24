package net.bestia.client

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.client.command.Session
import java.io.Closeable
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class BestiaSocketClient(
  ipAddress: String,
  port: Int,
  private val session: Session
) : Closeable {

  private val socket = Socket(ipAddress, port)

  // Flag to control the receiving loop
  @Volatile
  private var keepReceiving = true

  // Get the output stream
  private val outputStream = socket.getOutputStream()
  private val inputStream = socket.getInputStream()

  private var receivingThread = thread(start = true, isDaemon = true) {
    try {
      while (keepReceiving) {
        val envelope = receiveMessage()

        session.receiveEnvelope(envelope)

        if(envelope.hasDisconnected()) {
          session.client = null
          close()
        }
      }
    } catch (e: Exception) {
      keepReceiving = false
    }
  }

  fun sendEnvelope(envelope: EnvelopeProto.Envelope) {
    // Serialize the protobuf message to a byte array
    val messageBytes = envelope.toByteArray()

    // Create a byte buffer with big-endian order
    val buffer = ByteBuffer.allocate(4 + messageBytes.size).order(ByteOrder.BIG_ENDIAN)

    // Put the size prefix (4 bytes)
    buffer.putInt(messageBytes.size)

    // Put the message bytes
    buffer.put(messageBytes)

    // Write the buffer to the output stream
    outputStream.write(buffer.array())

    // Flush the output stream
    outputStream.flush()
  }

  private fun receiveMessage(): EnvelopeProto.Envelope {
    // Read the length prefix (4 bytes, big-endian)
    val lengthBuffer = ByteArray(4)
    var bytesRead = 0
    while (bytesRead < 4) {
      bytesRead += inputStream.read(lengthBuffer, bytesRead, 4 - bytesRead)
    }
    val messageLength = ByteBuffer.wrap(lengthBuffer).order(ByteOrder.BIG_ENDIAN).int

    // Read the message bytes
    val messageBuffer = ByteArray(messageLength)
    bytesRead = 0
    while (bytesRead < messageLength) {
      bytesRead += inputStream.read(messageBuffer, bytesRead, messageLength - bytesRead)
    }

    // Decode the protobuf message
    return EnvelopeProto.Envelope.parseFrom(messageBuffer)
  }

  override fun close() {
    keepReceiving = false
    receivingThread.interrupt()
    receivingThread.join(5000)
    socket.close()
  }
}