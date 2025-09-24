package net.bestia.zone.status

data class DerivedStatusValues(
  val matk: Int,
  val hitrate: Int,
  val flee: Int
) {

  companion object {
    fun fromStatusValues(
      lv: Int,
      sv: StatusValues
    ): DerivedStatusValues {
      val matk = lv / 4 + sv.intelligence + sv.intelligence / 2 + sv.willpower / 3 + sv.dexterity / 5

      return DerivedStatusValues(
        matk = matk,
        hitrate = 10,
        flee = 10
      )
    }
  }
}