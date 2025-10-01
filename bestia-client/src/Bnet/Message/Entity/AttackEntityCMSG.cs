using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{

  /// <summary>
  /// Message to request attacking a target entity.
  /// </summary>
  public partial class AttackEntityCMSG : ICMSG
  {
    [Export] public ulong EntityId { get; set; }

    [Export] public ulong UsedAttackId { get; set; }

    [Export] public uint SkillLevel { get; set; }

    public AttackEntityCMSG()
    {
    }

    public override Envelope ToEnvelope()
    {
      var attackEntityCmsg = new global::Bnet.AttackEntityCMSG
      {
        EntityId = EntityId,
        UsedAttackId = UsedAttackId,
        SkillLevel = SkillLevel
      };

      return new Envelope
      {
        AttackEntity = attackEntityCmsg
      };
    }
  }
}