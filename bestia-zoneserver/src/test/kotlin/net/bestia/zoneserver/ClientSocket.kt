package net.bestia.zoneserver

import com.google.protobuf.Message
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
import java.time.Instant

private val LOG = KotlinLogging.logger { }

class ClientSocket(
    private val ip: String,
    private val port: Int
) : Closeable {
  inner class ReceiverThread(
      private var dIn: DataInputStream
  ) : Thread("clientSocket") {
    var isRunning = true

    val packets = mutableListOf<Any>()

    override fun run() {
      while (isRunning) {
        receivePacket()?.let { packet ->
          translatePacket(packet)?.let {

            synchronized(packets) {
              packets.add(it)
            }
          }
        }
      }
    }

    private fun translatePacket(packet: ByteArray): Any? {
      val wrapper = MessageProtos.Wrapper.parseFrom(packet)
      bytesReceived += packet.size
      packetsReceived += 1

      return when (wrapper.payloadCase) {
        MessageProtos.Wrapper.PayloadCase.AUTH_RESPONSE -> wrapper.authResponse
        MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE -> wrapper.clientVarResponse
        MessageProtos.Wrapper.PayloadCase.COMP_POSITION -> wrapper.compPosition
        MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_RESPONSE -> wrapper.clientInfoResponse
        MessageProtos.Wrapper.PayloadCase.CHAT_RESPONSE -> wrapper.chatResponse
        MessageProtos.Wrapper.PayloadCase.PING_RESPONSE -> wrapper.pingResponse
        MessageProtos.Wrapper.PayloadCase.ATTACK_LIST_RESPONSE -> wrapper.attackListResponse
        else -> {
          LOG.warn { "No translation for packet type: ${wrapper.payloadCase}" }
          null
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

  fun receive(
      msgClass: Class<out Message>,
      timeout: Duration = Duration.ofSeconds(5)
  ): Any? {
    var foundMsg: Any? = null
    val end = Instant.now() + timeout

    while (foundMsg == null && Instant.now() < end) {
      synchronized(receiverThread.packets) {
        val idx = receiverThread.packets.indexOfFirst { it.javaClass == msgClass }

        foundMsg = if (idx == -1) {

          null
        } else {
          val msg = receiverThread.packets[idx]
          receiverThread.packets.removeAt(idx)

          msg
        }
      }
      Thread.sleep(10)
    }

    return foundMsg
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

      val authResponse = receive<AccountProtos.AuthResponse>()

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

inline fun <reified T : Message> ClientSocket.receive(timeout: Duration = Duration.ofSeconds(5)): T? {
  return receive(T::class.java, timeout) as T?
}