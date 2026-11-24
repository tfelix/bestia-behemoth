package net.bestia.zone.bestiastorage

import net.bestia.bnet.proto.BestiaGeneticsProto
import net.bestia.zone.account.AvailableBestiaSlotService
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.bestia.IvReading
import net.bestia.zone.bestiastorage.BestiaStorageSMSG.Pool
import net.bestia.zone.master.skill.BloodlineStudy
import net.bestia.zone.message.SelfSMSG
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** What the storage window shows of a master's stored bestia. */
@Service
class BestiaStorageViews(
  private val masters: MasterRepository,
  private val slots: AvailableBestiaSlotService,
  private val conditions: ConditionValueCalculator,
  private val bloodlineStudy: BloodlineStudy,
) {

  @Transactional(readOnly = true)
  fun of(masterId: Long, now: Instant): BestiaStorageSMSG {
    val master = masters.findByIdOrThrow(masterId)
    val readsExactIvs = BloodlineStudy.readsExactIvs(bloodlineStudy.levelOf(masterId))
    val stored = master.bestias.storedBestias.sortedBy { it.id }.map { storedBestia(it, readsExactIvs, now) }

    return BestiaStorageSMSG(
      bestias = stored,
      storageSlotCount = slots.getStorageSlotCount(master.account.id),
      activeSlotCount = slots.getTotalSlotCount(master.account.id),
      activeBestiaCount = master.bestias.activeBestias.size,
    )
  }

  private fun storedBestia(row: PlayerBestia, readsExactIvs: Boolean, now: Instant): BestiaStorageSMSG.StoredBestia {
    val species = row.bestia
    val ivs = row.individualValues
    val maxHp = conditions.computeMaxHp(row.level, species.vitality, iv = ivs.hp)
    val maxMana = conditions.computeMaxMana(row.level, species.intelligence, iv = ivs.mana)
    val maxStamina = conditions.computeMaxStamina(row.level, species.vitality, species.strength, species.willpower, ivs.stamina)
    val vitals = row.storedVitals
    val restingSeconds = row.restsUntil?.let { Duration.between(now, it).toSeconds().coerceAtLeast(0) } ?: 0

    return BestiaStorageSMSG.StoredBestia(
      info = SelfSMSG.BestiaInfo(
        entityId = row.entityId ?: 0,
        playerBestiaId = row.id,
        mobId = species.id.toInt(),
        name = row.name,
        level = row.level,
        position = row.position,
        sex = row.sex?.toBnet() ?: BestiaGeneticsProto.BestiaSex.BESTIA_SEX_UNKNOWN,
        ivs = IvReading(ivs, readsExactIvs).toBnet(),
      ),
      restingSeconds = restingSeconds,
      health = Pool(vitals?.health?.coerceAtMost(maxHp) ?: maxHp, maxHp),
      mana = Pool(vitals?.mana?.coerceAtMost(maxMana) ?: maxMana, maxMana),
      stamina = Pool(vitals?.stamina?.coerceAtMost(maxStamina) ?: maxStamina, maxStamina),
    )
  }
}
