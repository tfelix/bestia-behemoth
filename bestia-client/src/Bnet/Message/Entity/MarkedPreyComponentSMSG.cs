using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Where the prey of the player's Hunter's Mark stands, wherever it goes. Owner-only; the map marks it while
  /// <see cref="Marked"/> holds.
  /// </summary>
  [GlobalClass]
  public partial class MarkedPreyComponentSMSG : EntitySMSG
  {
    [Export] public bool Marked { get; set; }

    [Export] public ulong PreyEntityId { get; set; }

    /// <summary>In Godot's axes like every other client position.</summary>
    [Export] public Vector3 Position { get; set; }

    public static MarkedPreyComponentSMSG FromProto(ulong entityId, global::Bnet.MarkedPreyComponentSMSG proto)
    {
      var message = new MarkedPreyComponentSMSG { EntityId = entityId, Marked = proto.Marked, PreyEntityId = proto.PreyEntityId };
      if (proto.Position != null)
      {
        // Server z-up to Godot y-up.
        message.Position = new Vector3(proto.Position.X, proto.Position.Z, proto.Position.Y);
      }

      return message;
    }

    public override string ToString()
    {
      return $"MarkedPreyComponentSMSG(EntityId={EntityId}, Marked={Marked}, PreyEntityId={PreyEntityId})";
    }
  }
}
