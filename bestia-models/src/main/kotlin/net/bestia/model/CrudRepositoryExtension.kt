package net.bestia.model

import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.repository.CrudRepository

fun <T, S> CrudRepository<T, S>.findOne(id: S): T? {
  val o = findById(id)
  return if (o.isPresent) o.get() else null
}

fun <T, S> CrudRepository<T, S>.findOneOrThrow(id: S): T {
  val o = findById(id)
  return if (o.isPresent) o.get() else throw EmptyResultDataAccessException(1)
}