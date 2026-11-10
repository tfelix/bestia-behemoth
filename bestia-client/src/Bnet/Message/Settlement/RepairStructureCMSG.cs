using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Settlement
{
  /// <summary>Starts repairing a player structure in a town, paying in from the bag, or stops it.</summary>
  public partial class RepairStructureCMSG : ICMSG
  {
    public ulong EntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { RepairStructure = new global::Bnet.RepairStructureCMSG { EntityId = EntityId } };
    }
  }
}
