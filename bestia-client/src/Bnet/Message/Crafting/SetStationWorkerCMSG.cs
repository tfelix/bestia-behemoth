using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>Pick who works a station's queue: an owned bestia's entity id, or 0 for the master.</summary>
  public partial class SetStationWorkerCMSG : ICMSG
  {
    public ulong StructureId { get; set; }
    public ulong WorkerEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetStationWorker = new global::Bnet.SetStationWorkerCMSG { StructureId = StructureId, WorkerEntityId = WorkerEntityId }
      };
    }
  }
}
