using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>Creates a preset, or with a <see cref="PresetId"/> changes one and every bestia that follows it.</summary>
  public partial class SaveOrderPresetCMSG : ICMSG
  {
    /// <summary>0 for a new preset.</summary>
    public ulong PresetId { get; set; }
    public string Name { get; set; } = "";
    public OrderSheet Orders { get; set; }

    public override Envelope ToEnvelope()
    {
      var msg = new global::Bnet.SaveOrderPresetCMSG { Name = Name, Orders = Orders.ToProto() };
      if (PresetId != 0) msg.PresetId = PresetId;

      return new Envelope { SaveOrderPreset = msg };
    }
  }
}
