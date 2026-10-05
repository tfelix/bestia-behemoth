using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>Founds a party with this player as its owner.</summary>
  public partial class CreatePartyCMSG : ICMSG
  {
    public string PartyName { get; set; } = string.Empty;

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CreateParty = new global::Bnet.CreatePartyCMSG
        {
          PartyName = PartyName
        }
      };
    }
  }
}
