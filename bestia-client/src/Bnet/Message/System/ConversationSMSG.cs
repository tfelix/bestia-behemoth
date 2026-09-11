using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Somebody is talking to us, and these are the things we may say back.
  ///
  /// Account-scoped like <see cref="DialogSMSG"/> and deliberately an <see cref="ISMSG"/> rather
  /// than an EntitySMSG: the speaker is metadata, and routing this through entity handling would
  /// have the client try to build an entity for the id.
  ///
  /// <see cref="SpeakerName"/> is carried outright because entities have no display name on the
  /// wire yet. When they do, this field goes and the name comes off the entity.
  /// </summary>
  [GlobalClass]
  public partial class ConversationSMSG : ISMSG
  {
    [Export]
    public ulong SpeakerEntityId { get; set; } = 0;

    [Export]
    public string SpeakerName { get; set; } = string.Empty;

    [Export]
    public ConversationLine Speech { get; set; } = new();

    [Export]
    public Godot.Collections.Array<ConversationOption> Options { get; set; } = new();

    public static ConversationSMSG FromProto(global::Bnet.ConversationSMSG proto)
    {
      var options = new Godot.Collections.Array<ConversationOption>();
      foreach (var option in proto.Options)
      {
        options.Add(ConversationOption.FromProto(option));
      }

      return new ConversationSMSG
      {
        SpeakerEntityId = proto.SpeakerEntityId,
        SpeakerName = proto.SpeakerName ?? string.Empty,
        Speech = ConversationLine.FromProto(proto.Speech),
        Options = options
      };
    }
  }
}
