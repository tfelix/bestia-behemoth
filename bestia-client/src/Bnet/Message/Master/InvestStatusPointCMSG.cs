using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Message to spend one or more of the master's available status points across one or more
  /// base status attributes in a single batch request. Each entry's "attribute" key is the
  /// StatusAttribute enum ordinal (STRENGTH=0, AGILITY=1, VITALITY=2, INTELLIGENCE=3,
  /// DEXTERITY=4, WILLPOWER=5), "amount" is how many points to invest into it.
  /// </summary>
  [GlobalClass]
  public partial class InvestStatusPointCMSG : ICMSG
  {
    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> InvestedPoints { get; set; } = new Godot.Collections.Array<Godot.Collections.Dictionary>();

    public InvestStatusPointCMSG()
    {
    }

    public override Envelope ToEnvelope()
    {
      var investStatusPointCmsg = new global::Bnet.InvestStatusPointCMSG();
      foreach (var entry in InvestedPoints)
      {
        investStatusPointCmsg.InvestedPoints.Add(new global::Bnet.InvestedStatusPoint
        {
          Attribute = (global::Bnet.StatusAttribute)entry["attribute"].AsInt32(),
          Amount = (uint)entry["amount"].AsInt32()
        });
      }

      return new Envelope
      {
        InvestStatusPoint = investStatusPointCmsg
      };
    }
  }
}
