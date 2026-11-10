using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>A master's fishing line in the water. Its bobber goes when the line is <see cref="Removed"/>.</summary>
  [GlobalClass]
  public partial class FishingComponentSMSG : EntitySMSG
  {
    /// <summary>The bobber's tile in Godot's axes, its height the water's top.</summary>
    [Export] public Vector3 Bobber { get; set; }

    /// <summary>A fish has the hook: a strike now lands it.</summary>
    [Export] public bool Biting { get; set; }

    [Export] public bool Removed { get; set; }

    public static FishingComponentSMSG FromProto(ulong entityId, global::Bnet.FishingComponentSMSG proto)
    {
      return new FishingComponentSMSG
      {
        EntityId = entityId,
        // Server z-up to Godot y-up.
        Bobber = new Vector3(proto.Bobber.X, proto.Bobber.Z, proto.Bobber.Y),
        Biting = proto.Biting,
        Removed = proto.Removed
      };
    }

    public override string ToString()
    {
      return $"FishingComponentSMSG(EntityId={EntityId}, Bobber={Bobber}, Biting={Biting}, Removed={Removed})";
    }
  }
}
