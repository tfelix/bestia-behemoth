using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party owner picks how the party holds its drops; every member is sent the new roster.</summary>
  public partial class SetPartyLootShareCMSG : ICMSG
  {
    /// <summary>True for Take turns, false for Party share.</summary>
    public bool TakeTurns { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetPartyLootShare = new global::Bnet.SetPartyLootShareCMSG
        {
          LootShare = TakeTurns ? PartyLootShare.TakeTurns : PartyLootShare.PartyShare
        }
      };
    }
  }
}
