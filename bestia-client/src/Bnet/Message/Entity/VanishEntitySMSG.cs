using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for notifying that an entity has vanished.
  /// Contains the entity ID and the kind of vanishing (gone or death).
  /// </summary>
  [GlobalClass]
  public partial class VanishEntitySMSG : EntitySMSG
  {
    private VanishKind Kind { get; set; } = VanishKind.Gone;

    public bool IsDead()
    {
      return Kind == VanishKind.Death;
    }

    public bool IsGone()
    {
      return Kind == VanishKind.Gone;
    }

    public static VanishEntitySMSG FromProto(global::Bnet.VanishEntitySMSG protoVanish)
    {
      return new VanishEntitySMSG
      {
        EntityId = protoVanish.EntityId,
        Kind = protoVanish.Kind
      };
    }
  }
}