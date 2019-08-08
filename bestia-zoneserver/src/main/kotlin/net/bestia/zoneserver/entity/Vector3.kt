package net.bestia.zoneserver.entity

data class Vector3(
    var x: Long,
    var y: Long,
    var z: Long
) {

  fun add(rhs: Vector3) {
    x += rhs.x
    y += rhs.y
    z += rhs.z
  }
}