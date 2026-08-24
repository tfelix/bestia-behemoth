using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{

  /// <summary>
  /// Swings the active entity's basic attack at a target entity. A basic attack has no entry in the
  /// Attack DB, so nothing here names one - casting a skill is ActivateSkillCMSG.
  /// </summary>
  public partial class AttackEntityCMSG : ICMSG
  {
    [Export] public ulong EntityId { get; set; }

    public AttackEntityCMSG()
    {
    }

    public override Envelope ToEnvelope()
    {
      var attackEntityCmsg = new global::Bnet.AttackEntityCMSG
      {
        EntityId = EntityId
      };

      return new Envelope
      {
        AttackEntity = attackEntityCmsg
      };
    }
  }
}