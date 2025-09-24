using Godot;

namespace Bnet.Message.Entity;

/// <summary>
/// Message to request attacking a target entity.
/// </summary>
public partial class AttackEntity : BestiaBehemothClient.Bnet.Message.ICMSG
{
  [Export] public ulong EntityId { get; set; }

  [Export] public ulong UsedAttackId { get; set; }

  [Export] public uint SkillLevel { get; set; }

  public AttackEntity()
  {
  }

  public override Envelope ToEnvelope()
  {
    var attackEntityCmsg = new AttackEntityCMSG
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
