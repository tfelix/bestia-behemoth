using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Notifies nearby players that an entity activated a skill. Target selection and
  /// visual/effect resolution are not modeled yet - this is currently just the
  /// acknowledgement hook for that future work.
  /// </summary>
  [GlobalClass]
  public partial class SkillActivatedSMSG : EntitySMSG
  {
    [Export] public ulong AttackId { get; set; }
    [Export] public uint SkillLevel { get; set; }

    public static SkillActivatedSMSG FromProto(global::Bnet.SkillActivatedSMSG protoSkillActivated)
    {
      return new SkillActivatedSMSG
      {
        EntityId = protoSkillActivated.EntityId,
        AttackId = protoSkillActivated.AttackId,
        SkillLevel = protoSkillActivated.SkillLevel
      };
    }
  }
}
