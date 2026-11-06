package net.bestia.zone.item.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ItemInstanceEnchantmentRepository : JpaRepository<ItemInstanceEnchantment, Long> {

  @Modifying
  @Query("delete from ItemInstanceEnchantment e where e.endsAt <= :now")
  fun deleteEnded(@Param("now") now: Instant): Int
}
