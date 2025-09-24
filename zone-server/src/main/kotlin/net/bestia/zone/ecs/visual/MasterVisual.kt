package net.bestia.zone.ecs.visual

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.WorldAcessor
import java.awt.Color

data class MasterVisual(
  val id: Int,
  val skinColor: Color,
  val hairColor: Color,
  val face: Face,
  val body: BodyType,
  val hair: Hairstyle
) : Component<MasterVisual> {

  override fun type(): ComponentType<MasterVisual> = MasterVisual

  class MasterVisualAcessor(
    private val entity: Entity
  ) : WorldAcessor {

    var id: Int = 0
      private set
    var skinColor: Color = Color.WHITE
      private set
    var hairColor: Color = Color.BLACK
      private set
    var face: Face = Face.FACE_1
      private set
    var body: BodyType = BodyType.BODY_M_1
      private set
    var hair: Hairstyle = Hairstyle.HAIR_1
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(MasterVisual)
          ?: throw ComponentNotFoundException(MasterVisual)
      }

      id = comp.id
      skinColor = comp.skinColor
      hairColor = comp.hairColor
      face = comp.face
      body = comp.body
      hair = comp.hair
    }
  }

  companion object : ComponentType<MasterVisual>()
}