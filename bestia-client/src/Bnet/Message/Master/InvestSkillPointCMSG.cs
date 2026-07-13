using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Message to spend one or more of the master's available skill points across one or more
  /// skill tree nodes in a single batch request. Each entry's "attack_id" key is the skill's
  /// catalog id (matches skills.yml / SkillListSMSG.skillId), "amount" is how many additional
  /// levels to invest into it.
  /// </summary>
  public partial class InvestSkillPointCMSG : ICMSG
  {
    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> InvestedPoints { get; set; } = new Godot.Collections.Array<Godot.Collections.Dictionary>();

    public InvestSkillPointCMSG()
    {
    }

    public override Envelope ToEnvelope()
    {
      var investSkillPointCmsg = new global::Bnet.InvestSkillPointCMSG();
      foreach (var entry in InvestedPoints)
      {
        investSkillPointCmsg.InvestedPoints.Add(new global::Bnet.InvestedSkillPoint
        {
          AttackId = (ulong)entry["attack_id"].AsInt64(),
          Amount = (uint)entry["amount"].AsInt32()
        });
      }

      return new Envelope
      {
        InvestSkillPoint = investSkillPointCmsg
      };
    }
  }
}
