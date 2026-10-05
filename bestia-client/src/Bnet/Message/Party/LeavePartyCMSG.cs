using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>Leaves the party; when the owner leaves, the party is disbanded.</summary>
  public partial class LeavePartyCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        LeaveParty = new global::Bnet.LeavePartyCMSG()
      };
    }
  }
}
