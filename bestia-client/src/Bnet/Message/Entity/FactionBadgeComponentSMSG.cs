using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// The Order a master is sworn to, for the name tag. On the master and its bestias; <see cref="Removed"/> once
  /// it leaves the Order.
  /// </summary>
  [GlobalClass]
  public partial class FactionBadgeComponentSMSG : EntitySMSG
  {
    /// <summary>The wire value of <c>FactionId</c>: 1 Chaos, 2 Eternity, 3 Circle.</summary>
    [Export] public int Faction { get; set; }

    [Export] public bool Removed { get; set; }

    public static FactionBadgeComponentSMSG FromProto(ulong entityId, global::Bnet.FactionBadgeComponentSMSG proto)
    {
      return new FactionBadgeComponentSMSG
      {
        EntityId = entityId,
        Faction = (int)proto.Faction,
        Removed = proto.Removed
      };
    }

    public override string ToString()
    {
      return $"FactionBadgeComponentSMSG(EntityId={EntityId}, Faction={Faction}, Removed={Removed})";
    }
  }
}
