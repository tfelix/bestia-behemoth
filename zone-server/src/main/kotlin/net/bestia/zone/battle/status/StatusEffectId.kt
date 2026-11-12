package net.bestia.zone.battle.status

/**
 * Every status effect the server can apply, by name. This is what call sites use -
 * `statusEffectService.applyEffect(world, targetId, StatusEffectId.BLESSING, level)` - so picking an
 * effect is a symbol lookup with autocompletion rather than a magic number scattered over script
 * companion objects.
 *
 * Must stay in lockstep with `status_effects.yml`, which is the source of truth for the id's
 * metadata (polarity, icon visibility, client sync, script name).
 * [StatusEffectCatalogBootValidator] fails the boot if an entry exists on only one side, so the
 * duplication cannot silently rot. Same arrangement as
 * [net.bestia.zone.dialog.DialogId] / `dialogs.yml`.
 *
 * Ids are not contiguous on purpose: 3 belonged to the removed THORNS effect and is not reused, so
 * an old client or a stale log line can never be mistaken for a live effect.
 */
enum class StatusEffectId(val id: Long) {
  SWIFTNESS(1),
  CRIPPLE(2),
  RESISTED_ONCE_MARKER(4),
  BLESSING(5),
  MASTER_INTRO_MARKER(6),
  PLAY_DEAD(7),
  FIRST_AID_COOLDOWN(8),
  INVULNERABLE(9),
  WARDED(10),
  WARD_AURA(11),
  GUILD_AURA(12),
  BATTLE_ORDERS(13),
  GUILD_REGENERATION(14),
  EMERGENCY_MOVE(15),
  TELEPORT_LOCK(16),
  EMPOWERED(17),
  WEAKENED(18),
  WARMED(19),
  COOLED(20),
  AGGRESSOR(21),
  DISHONORED(22),
  THIEF(23),
  STUN(24),
  FROZEN(25),
  STONING(26),
  STONE(27),
  IMMOBILIZED(28),
  SLOWED(29),
  CONFUSED(30),
  FEARED(31),
  BLIND(32),
  POISON(34),
  BLEEDING(35),
  BURNING(36),
  SICK(37),
  WITHERED(38),
  REGENERATING(39),
  LIFESTEAL(40),
  MANA_SEALED(41),
  ENRAGED(42),
  FATIGUE(43),
  BERSERK(44),
  TAILWIND(45),
  ENDOWED(46),
  ARMOR_ENDOWED(47),
  HIT_DOWN(48),
  ASPD_DOWN(49),
  QUAGMIRED(50),
  SHELTERED(51),
  REFLECT(52),
  BARRIER(53),
  CORROSIVE(54),
  CONTROLLED(55),
  HIDDEN(56),
  CLOAKED(57),
  CHANNELING(58),
  ARMOR_STRIPPED(59),
  DISARMED(60),
  STRUCK_OFF(61),
  SILENCED(62),
  HEADGEAR_STRIPPED(63),
  GARMENT_STRIPPED(64),
  FOOTGEAR_STRIPPED(65),
  SHIELD_STRIPPED(66),

  // The master skill tree's own effects, from id 200.
  RITUAL_CHARGE(200),
  RITUAL_FATIGUE(201),
  SPRINTING(202),
  CAMPED(203),
  RUWACH(204),
  MASTER_ENDOWED(205),
  MAGIC_ROD(206),
  MANA_DRAINING(207),
  TELEPORT_SICKNESS(208),
  SIGNUM_CRUCIS(209),
  LEX_AETERNA(210),
  INCREASE_AGI(211),
  KYRIE_ELEISON(212),
  SUFFRAGIUM(213),
  MAGNIFICAT(214),
  GLORIA(215),
  ASPERSIO(216),
  MAGIC_ARMOR(217),
  MINDBREAK(218),
  SAFETY_WALL(219),
  QUAGMIRE(220),
  HIDING(221),
  CLOAKING(222),
  EQUIPMENT_COATING(223),
  ENCHANT_POISON(224);

  companion object {
    private val byId = entries.associateBy { it.id }

    /** What keeps its bearer out of sight: the bestias' Burrow and Shadow Cloak, the Assassin's Hide and Cloak. */
    val CONCEALMENTS: Set<StatusEffectId> = setOf(HIDDEN, CLOAKED, HIDING, CLOAKING)

    fun findById(id: Long): StatusEffectId? = byId[id]
  }
}
