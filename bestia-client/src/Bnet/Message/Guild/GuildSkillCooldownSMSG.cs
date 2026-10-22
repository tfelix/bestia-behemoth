using Godot;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>A guild skill was cast and now recovers for the whole guild; counted down by the client.</summary>
  [GlobalClass]
  public partial class GuildSkillCooldownSMSG : GuildSMSG
  {
    [Export] public ulong SkillId { get; set; }

    [Export] public ulong RemainingMs { get; set; }

    public static GuildSkillCooldownSMSG FromProto(global::Bnet.GuildSkillCooldownSMSG proto)
    {
      return new GuildSkillCooldownSMSG
      {
        SkillId = proto.SkillId,
        RemainingMs = proto.RemainingMs
      };
    }
  }
}
