package net.bestia.zone.ecs.account

import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.ecs.core.Component
import java.awt.Color

data class MasterVisual(
  val id: Int,
  val skinColor: Color,
  val hairColor: Color,
  val face: Face,
  val body: BodyType,
  val hair: Hairstyle
) : Component