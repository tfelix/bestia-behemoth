package net.bestia.zone.architecture

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A `@Lob` without a length becomes a 255-byte `tinyblob` or `tinytext` on MariaDB, and a larger value then
 * fails to insert. The tests run on H2, which does not care, so the length is checked here instead.
 */
class LobColumnRulesTest {

  @Test
  fun `every lob column declares a length beyond 255 bytes`() {
    val lobs = ZoneClasses.main
      .filter { it.isAnnotatedWith(Entity::class.java) }
      .flatMap { it.fields }
      .filter { it.isAnnotatedWith(Lob::class.java) }

    val tooShort = lobs.filter { field ->
      !field.isAnnotatedWith(Column::class.java) || field.getAnnotationOfType(Column::class.java).length <= TINY
    }

    assertEquals(emptyList<String>(), tooShort.map { it.fullName })
  }

  private companion object {
    const val TINY = 255
  }
}
