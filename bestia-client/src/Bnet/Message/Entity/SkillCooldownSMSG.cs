using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>One of the player's entities cast a skill that now recovers; counted down by the client.</summary>
  [GlobalClass]
  public partial class SkillCooldownSMSG : EntitySMSG
  {
    [Export] public ulong SkillId { get; set; }

    [Export] public ulong RemainingMs { get; set; }

    public static SkillCooldownSMSG FromProto(global::Bnet.SkillCooldownSMSG proto)
    {
      return new SkillCooldownSMSG
      {
        EntityId = proto.EntityId,
        SkillId = proto.SkillId,
        RemainingMs = proto.RemainingMs
      };
    }
  }
}
