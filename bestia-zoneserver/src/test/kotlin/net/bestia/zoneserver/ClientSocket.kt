package net.bestia.zoneserver

import mu.KotlinLogging
import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.MessageProtos
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val LOG = KotlinLogging.logger { }

class ClientSocket(
    private val ip: String,
    private val port: Int
) : Closeable {
  class ReceiverThread(
      private var dIn: DataInputStream
  ) : Thread() {
    var isRunning = true

    val packets = LinkedBlockingQueue<ByteArray>()

    override fun run() {
      while (isRunning) {
        receivePacket()?.let {
          packets.add(it)
        }
      }
    }

    private fun receivePacket(): ByteArray? {
      try {
        if (dIn.available() <= 4) {
          return null
        }

        val packetSize = dIn.readInt()
        val buffer = ByteArray(packetSize)
        dIn.readFully(buffer)

        return buffer
      } catch (e: SocketException) {
        // socket was closed. abort.
        isRunning = false
        return null
      }
    }
  }

  private var isConnected = false
  private lateinit var socket: Socket
  private lateinit var dOut: DataOutputStream
  private lateinit var receiverThread: ReceiverThread

  private var bytesSend = 0
  private var bytesReceived = 0
  private var packetsReceived = 0

  private fun connectSocket() {
    socket = Socket(ip, port)
    dOut = DataOutputStream(socket.getOutputStream())
    receiverThread = ReceiverThread(DataInputStream(socket.getInputStream()))
    receiverThread.start()
  }

  fun send(data: ByteArray) {
    val sendBuffer = ByteBuffer.allocate(data.size + Int.SIZE_BYTES)
    sendBuffer.putInt(data.size)
    sendBuffer.put(data)

    dOut.write(sendBuffer.array())
    bytesSend += sendBuffer.position()
    dOut.flush()
  }

  fun <T> receive(
      messageType: MessageProtos.Wrapper.PayloadCase,
      timeout: Duration = Duration.ofSeconds(5)
  ): T? {
    val packageBytes = receiverThread.packets.poll(timeout.seconds, TimeUnit.SECONDS)
        ?: return null
    val wrapper = MessageProtos.Wrapper.parseFrom(packageBytes)
    bytesReceived += packageBytes.size
    packetsReceived += 1

    @Suppress("UNCHECKED_CAST")
    return when (messageType) {
      MessageProtos.Wrapper.PayloadCase.AUTH_RESPONSE -> wrapper.authResponse
      MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE -> wrapper.clientVarResponse
      MessageProtos.Wrapper.PayloadCase.COMP_POSITION -> wrapper.compPosition
      MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_RESPONSE -> wrapper.clientInfoResponse
      MessageProtos.Wrapper.PayloadCase.CHAT_RESPONSE -> wrapper.chatResponse
      else -> error("No matching packet found for $messageType")
    } as T
  }

  override fun close() {
    if (!isConnected) {
      return
    }
    receiverThread.isRunning = false
    dOut.close()
    socket.close()
  }

  fun printStatistics() {
    println()
    println("Client Socket Statistics:\n")
    println("Send: $bytesSend bytes, Received: $bytesReceived bytes")
    println("Packets received: $packetsReceived")
    println()
  }

  fun connect() {
    val authMessage = AccountProtos.AuthRequest.newBuilder()
        .setAccountId(1)
        .setToken("50cb5740-c390-4d48-932f-eef7cbc113c1")
        .build()
    val payload = MessageProtos.Wrapper.newBuilder()
        .setAuthRequest(authMessage)
        .build()
        .toByteArray()

    while (!isConnected) {
      connectSocket()
      send(payload)

      val authResponse = receive<AccountProtos.AuthResponse>(MessageProtos.Wrapper.PayloadCase.AUTH_RESPONSE)

      if (authResponse != null) {
        if (authResponse.loginStatus == AccountProtos.LoginStatus.NO_LOGINS_ALLOWED) {
          LOG.debug { "Server accepts no logins" }
          Thread.sleep(100)
          continue
        }

        if (authResponse.loginStatus == AccountProtos.LoginStatus.UNAUTHORIZED) {
          error("Server does not accept the credentials. Abort.")
        }

        isConnected = true
      }
    }
  }
}