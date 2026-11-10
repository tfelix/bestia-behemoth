using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>Asks what an egg in the master's bag holds. Answered by a BestiaEggSMSG.</summary>
  public partial class InspectEggCMSG : ICMSG
  {
    public ulong UniqueId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { InspectEgg = new global::Bnet.InspectEggCMSG { UniqueId = UniqueId } };
    }
  }
}
