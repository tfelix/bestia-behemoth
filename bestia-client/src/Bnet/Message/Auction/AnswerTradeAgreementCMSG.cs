using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Accepts or declines a Trade Agreement offer.</summary>
  public partial class AnswerTradeAgreementCMSG : ICMSG
  {
    public ulong OfferId { get; set; }

    public bool Accept { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        AnswerTradeAgreement = new global::Bnet.AnswerTradeAgreementCMSG
        {
          OfferId = OfferId,
          Accept = Accept
        }
      };
    }
  }
}
