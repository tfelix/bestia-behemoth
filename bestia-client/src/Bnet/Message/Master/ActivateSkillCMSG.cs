using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Message to request activation of a learned skill for the account's currently active entity.
  /// </summary>
  public partial class ActivateSkillCMSG : ICMSG
  {
    [Export] public ulong AttackId { get; set; }

    [Export] public uint SkillLevel { get; set; }

    public ActivateSkillCMSG()
    {
    }

    public override Envelope ToEnvelope()
    {
      var activateSkillCmsg = new global::Bnet.ActivateSkillCMSG
      {
        AttackId = AttackId,
        SkillLevel = SkillLevel
      };

      return new Envelope
      {
        ActivateSkill = activateSkillCmsg
      };
    }
  }
}
