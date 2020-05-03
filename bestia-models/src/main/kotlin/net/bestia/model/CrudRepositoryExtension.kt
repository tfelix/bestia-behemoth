package net.bestia.model

import org.springframework.data.repository.CrudRepository

fun <T, S> CrudRepository<T, S>.findOneOrThrow(id: S): T {
  val o = findById(id)
  return if (o.isPresent) o.get() else throw IllegalArgumentException("Data with id $id not found")
}