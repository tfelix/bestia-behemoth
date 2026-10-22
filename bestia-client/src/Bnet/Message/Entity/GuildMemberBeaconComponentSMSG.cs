using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Where a guild member is and how its master fares, on the master's entity. Heard by every guild member being
  /// played, wherever they stand, so it arrives for entities that have no node here; see
  /// <c>EntityManager.get_guild_beacons</c>.
  /// </summary>
  [GlobalClass]
  public partial class GuildMemberBeaconComponentSMSG : EntitySMSG
  {
    /// <summary>Of the entity the member plays, in Godot's axes like every other client position.</summary>
    [Export] public Vector3 Position { get; set; }

    [Export] public string AreaName { get; set; } = "";

    [Export] public uint HpCurrent { get; set; }

    [Export] public uint HpMax { get; set; }

    [Export] public uint ManaCurrent { get; set; }

    [Export] public uint ManaMax { get; set; }

    public static GuildMemberBeaconComponentSMSG FromProto(ulong entityId, global::Bnet.GuildMemberBeaconSMSG proto)
    {
      var message = new GuildMemberBeaconComponentSMSG
      {
        EntityId = entityId,
        AreaName = proto.AreaName,
        HpCurrent = proto.HpCurrent,
        HpMax = proto.HpMax,
        ManaCurrent = proto.ManaCurrent,
        ManaMax = proto.ManaMax
      };

      // Server z-up to Godot y-up.
      if (proto.Position != null)
      {
        message.Position = new Vector3(proto.Position.X, proto.Position.Z, proto.Position.Y);
      }

      return message;
    }

    public override string ToString()
    {
      return $"GuildMemberBeaconComponentSMSG(EntityId={EntityId}, Position={Position}, AreaName={AreaName})";
    }
  }
}
