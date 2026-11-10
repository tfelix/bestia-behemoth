using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>Puts an egg from the master's bag into their Incubator.</summary>
  public partial class PlaceEggCMSG : ICMSG
  {
    public ulong StructureId { get; set; }
    public ulong UniqueId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { PlaceEgg = new global::Bnet.PlaceEggCMSG { StructureId = StructureId, UniqueId = UniqueId } };
    }
  }
}
