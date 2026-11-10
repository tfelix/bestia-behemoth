using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>Takes the egg out of a Breeding Farm or an Incubator into the master's bag.</summary>
  public partial class TakeEggCMSG : ICMSG
  {
    public ulong StructureId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { TakeEgg = new global::Bnet.TakeEggCMSG { StructureId = StructureId } };
    }
  }
}
