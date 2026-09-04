using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// An entity the client should stop drawing, and why.
  /// <para>
  /// The kind is exposed as predicates rather than as a property because GDScript cannot read the proto
  /// enum. <see cref="IsOutOfSight"/> is the one that must not play a send-off: it means the entity is
  /// alive and simply outside this client's view, and it can come back within the same second.
  /// </para>
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

    /// <summary>Left the client's view while still alive - the chunk it stands in is no longer held.</summary>
    public bool IsOutOfSight()
    {
      return Kind == VanishKind.OutOfSight;
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