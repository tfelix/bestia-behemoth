using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>Sends a female and a male to the farm with one Nesting Bundle.</summary>
  public partial class StartBreedingCMSG : ICMSG
  {
    public ulong StructureId { get; set; }
    public ulong MotherEntityId { get; set; }
    public ulong FatherEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { StartBreeding = new global::Bnet.StartBreedingCMSG { StructureId = StructureId, MotherEntityId = MotherEntityId, FatherEntityId = FatherEntityId } };
    }
  }
}
