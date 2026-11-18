using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// What a thing in the world says when we read it: a signpost, a headstone, a place we examine.
  /// </summary>
  [GlobalClass]
  public partial class ReadableSMSG : ISMSG
  {
    [Export]
    public ulong SourceEntityId { get; set; } = 0;

    [Export]
    public TextRef Text { get; set; } = new();

    public static ReadableSMSG FromProto(global::Bnet.ReadableSMSG proto)
    {
      return new ReadableSMSG
      {
        SourceEntityId = proto.SourceEntityId,
        Text = TextRef.FromProto(proto.Text)
      };
    }
  }
}
