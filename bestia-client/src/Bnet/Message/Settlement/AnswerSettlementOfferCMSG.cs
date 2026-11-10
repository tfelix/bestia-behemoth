using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  public partial class AnswerSettlementOfferCMSG : ICMSG
  {
    public ulong SettlementId { get; set; }

    public bool Accept { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        AnswerSettlementOffer = new global::Bnet.AnswerSettlementOfferCMSG { SettlementId = SettlementId, Accept = Accept }
      };
    }
  }
}
