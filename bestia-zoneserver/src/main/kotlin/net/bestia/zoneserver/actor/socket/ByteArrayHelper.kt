package net.bestia.zoneserver.actor.socket

import akka.io.Tcp
import akka.io.TcpMessage
import akka.util.ByteString

fun ByteArray.toByteString(): ByteString {
  return ByteString.fromArray(this)
}

fun ByteArray.toTcpMessage(): Tcp.Command? {
  return TcpMessage.write(this.toByteString())
}