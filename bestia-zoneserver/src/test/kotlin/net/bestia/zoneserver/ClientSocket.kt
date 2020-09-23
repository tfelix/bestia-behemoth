package net.bestia.zoneserver

import mu.KotlinLogging
import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.messages.proto.SystemProtos
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

private val LOG = KotlinLogging.logger { }

class ClientSocket(
    private val ip: String,
    private val port: Int
) : Closeable {
  private var socket: Socket = Socket()
  private var dOut: DataOutputStream? = null
  private var dIn: DataInputStream? = null

  private fun connect() {
    socket = Socket(ip, port)
    dOut = DataOutputStream(socket.getOutputStream())
    dIn = DataInputStream(socket.getInputStream())
  }

  fun receivePacket(timeout: Duration = Duration.ofSeconds(1)): ByteArray? {
    val start = Instant.now()

    while (Duration.between(start, Instant.now()) < timeout) {
      if (dIn!!.available() <= 4) {
        continue
      }

      val packetSize = dIn!!.readInt()
      val buffer = ByteArray(packetSize)

      if (dIn!!.available() < packetSize) {
        continue
      }

      dIn!!.read(buffer, 0, packetSize)

      return buffer
    }

    return null
  }

  fun send(data: ByteArray) {
    val sendBuffer = ByteBuffer.allocate(data.size + Int.SIZE_BYTES)
    sendBuffer.putInt(data.size)
    sendBuffer.put(data)

    dOut?.write(sendBuffer.array())
    dOut?.flush()
  }

  override fun close() {
    dOut?.close()
    dIn?.close()
    socket.close()
  }

  fun connectAndAuth() {
    val msg = AccountProtos.AuthRequest.newBuilder()
        .setAccountId(1)
        .setToken("50cb5740-c390-4d48-932f-eef7cbc113c1")
        .build()
        .toByteArray()

    var isConnected = false
    while (!isConnected) {
      connect()
      Thread.sleep(50)
      val connectionErrorMsg = receivePacket()

      if (connectionErrorMsg != null) {
        val response = MessageProtos.Wrapper.parseFrom(connectionErrorMsg)

        if (response.payloadCase == MessageProtos.Wrapper.PayloadCase.SYS_LOGIN_STATUS) {
          if (response.sysLoginStatus.serverState == SystemProtos.ServerState.NO_LOGINS) {
            LOG.debug { "Server accepts no logins" }
            continue
          }
        }
      }

      try {
        send(msg)
      } catch (e: IOException) {
        // no op
      }
      isConnected = true
    }
  }
}

/*
inline fun <T> ClientSocket.receive(): AccountProtos.AccountVarResponse? {
  val packageBytes = receivePacket()
  val wrapper = MessageProtos.Wrapper.parseFrom(packageBytes)

  return when (wrapper.payloadCase) {
    MessageProtos.Wrapper.PayloadCase.ACCOUNT_VAR_RESPONSE -> wrapper.accountVarResponse
    MessageProtos.Wrapper.PayloadCase.COMP_POSITION -> wrapper.compPosition
  }
}*/