using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// What a Detect skill lets one of the player's own entities sense: masters and player bestia nearby, hidden ones
  /// too. Owner-only; the map marks them until the component is <see cref="Removed"/>.
  /// </summary>
  [GlobalClass]
  public partial class DetectingComponentSMSG : EntitySMSG
  {
    /// <summary>Where each one stands, in Godot's axes like every other client position.</summary>
    [Export] public Godot.Collections.Array<Vector3> Positions { get; set; } = [];

    /// <summary>Whether the one at the same index is a master rather than a bestia.</summary>
    [Export] public Godot.Collections.Array<bool> Masters { get; set; } = [];

    [Export] public bool Removed { get; set; }

    public static DetectingComponentSMSG FromProto(ulong entityId, global::Bnet.DetectingComponentSMSG proto)
    {
      var message = new DetectingComponentSMSG { EntityId = entityId, Removed = proto.Removed };

      foreach (var entity in proto.Entities)
      {
        // Server z-up to Godot y-up.
        message.Positions.Add(new Vector3(entity.Position.X, entity.Position.Z, entity.Position.Y));
        message.Masters.Add(entity.Master);
      }

      return message;
    }

    public override string ToString()
    {
      return $"DetectingComponentSMSG(EntityId={EntityId}, Count={Positions.Count}, Removed={Removed})";
    }
  }
}
