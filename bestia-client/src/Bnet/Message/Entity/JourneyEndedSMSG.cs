using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// One of the player's own entities ended a journey, arrived or not.
  /// </summary>
  [GlobalClass]
  public partial class JourneyEndedSMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }

    /// <summary>Why it ended short, as the lowercase snake_case enum name; "none" when it arrived.</summary>
    [Export] public string FailureName { get; set; } = "none";

    [Export] public Vector3 Position { get; set; } = Vector3.Zero;

    [Export] public string Name { get; set; } = "";

    public static JourneyEndedSMSG FromProto(global::Bnet.JourneyEndedSMSG proto)
    {
      var position = proto.Position;
      return new JourneyEndedSMSG
      {
        EntityId = proto.EntityId,
        FailureName = EnumName.Of(proto.Failure),
        Position = position == null ? Vector3.Zero : new Vector3(position.X, position.Z, position.Y),
        Name = proto.Name
      };
    }
  }
}
