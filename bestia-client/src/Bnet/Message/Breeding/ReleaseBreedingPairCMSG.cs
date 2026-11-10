using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>Lets the pair go. A clutch in progress is lost with its bundle.</summary>
  public partial class ReleaseBreedingPairCMSG : ICMSG
  {
    public ulong StructureId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { ReleaseBreedingPair = new global::Bnet.ReleaseBreedingPairCMSG { StructureId = StructureId } };
    }
  }
}
