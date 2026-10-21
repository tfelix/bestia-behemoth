using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party owner picks how kill EXP is shared; every member is sent the new roster.</summary>
  public partial class SetPartyExpShareCMSG : ICMSG
  {
    /// <summary>True for Even share, false for Each take.</summary>
    public bool EvenShare { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetPartyExpShare = new global::Bnet.SetPartyExpShareCMSG
        {
          ExpShare = EvenShare ? PartyExpShare.EvenShare : PartyExpShare.EachTake
        }
      };
    }
  }
}
