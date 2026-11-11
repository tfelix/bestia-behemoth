using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>Deletes a preset. Its bestias keep the orders they had, as their own.</summary>
  public partial class DeleteOrderPresetCMSG : ICMSG
  {
    public ulong PresetId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { DeleteOrderPreset = new global::Bnet.DeleteOrderPresetCMSG { PresetId = PresetId } };
    }
  }
}
