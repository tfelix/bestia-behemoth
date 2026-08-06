using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Server asks us to show a dialog. Carries no text at all: <see cref="DialogId"/> is resolved
  /// against <c>Localization/dialogs.csv</c> (<c>DIALOG_&lt;id&gt;_TEXT</c>, optional
  /// <c>DIALOG_&lt;id&gt;_TITLE</c>), the same scheme skill descriptions use, so translations ship
  /// with the client instead of crossing the wire.
  ///
  /// This is account-scoped, not entity-scoped - deliberately an <see cref="ISMSG"/> rather than an
  /// EntitySMSG so it never travels through entity handling. <see cref="SourceEntityId"/> is
  /// informative only (who is speaking) and may name an entity this client does not know about.
  ///
  /// Handled by the <c>DialogManager</c> autoload, which queues dialogs until the UI that shows
  /// them exists.
  /// </summary>
  [GlobalClass]
  public partial class DialogSMSG : ISMSG
  {
    /// <summary>
    /// Catalog id from the server's dialogs.yml; the translation key is derived from it.
    /// </summary>
    [Export]
    public int DialogId { get; set; } = 0;

    /// <summary>
    /// What the player is expected to do. Only Confirm exists today.
    /// </summary>
    [Export]
    public global::Bnet.DialogType DialogType { get; set; } = global::Bnet.DialogType.Confirm;

    /// <summary>
    /// Placeholder values to substitute into the translated text, keyed by
    /// <see cref="DialogArg.Name"/>. An array rather than a dictionary because Godot dictionaries
    /// of GodotObject do not survive the marshalling as cleanly.
    /// </summary>
    [Export]
    public Godot.Collections.Array<DialogArg> Args { get; set; } = new();

    /// <summary>
    /// Optional speaker, for a portrait or name. Meaningful only when
    /// <see cref="HasSourceEntity"/> is true.
    /// </summary>
    [Export]
    public ulong SourceEntityId { get; set; } = 0;

    [Export]
    public bool HasSourceEntity { get; set; } = false;

    public static DialogSMSG FromProto(global::Bnet.DialogSMSG proto)
    {
      var args = new Godot.Collections.Array<DialogArg>();
      foreach (var entry in proto.Args)
      {
        args.Add(DialogArg.FromProto(entry.Key, entry.Value));
      }

      return new DialogSMSG
      {
        DialogId = (int)proto.DialogId,
        DialogType = proto.Type,
        Args = args,
        SourceEntityId = proto.HasSourceEntityId ? proto.SourceEntityId : 0,
        HasSourceEntity = proto.HasSourceEntityId
      };
    }
  }
}
