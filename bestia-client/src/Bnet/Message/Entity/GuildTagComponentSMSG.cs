using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// The guild a master is in and its rank's title, for the name tag. On the master and its bestias;
  /// <see cref="Removed"/> once it leaves the guild.
  /// </summary>
  [GlobalClass]
  public partial class GuildTagComponentSMSG : EntitySMSG
  {
    [Export] public ulong GuildId { get; set; }

    [Export] public string GuildName { get; set; } = "";

    [Export] public string RankTitle { get; set; } = "";

    [Export] public bool Removed { get; set; }

    public static GuildTagComponentSMSG FromProto(ulong entityId, global::Bnet.GuildTagComponentSMSG proto)
    {
      return new GuildTagComponentSMSG
      {
        EntityId = entityId,
        GuildId = proto.GuildId,
        GuildName = proto.GuildName,
        RankTitle = proto.RankTitle,
        Removed = proto.Removed
      };
    }

    public override string ToString()
    {
      return $"GuildTagComponentSMSG(EntityId={EntityId}, GuildName={GuildName}, RankTitle={RankTitle}, Removed={Removed})";
    }
  }
}
