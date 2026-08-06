using Godot;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Which flavour of value a <see cref="DialogArg"/> carries. Everything except
  /// <see cref="Text"/> is a reference the client is expected to resolve to a localized name
  /// itself, so dialog text stays translatable.
  /// </summary>
  public enum DialogArgKind
  {
    Text = 0,
    Number = 1,
    Entity = 2,
    Item = 3,
    Skill = 4
  }

  /// <summary>
  /// One placeholder value for a dialog text, e.g. <c>masterName</c> filling <c>{masterName}</c>.
  ///
  /// All the numeric kinds (Number/Entity/Item/Skill) land in <see cref="Number"/> - only
  /// <see cref="Kind"/> says how to interpret it. That keeps the GDScript side to a single
  /// switch; see <c>Game/UI/Dialog/dialog_text.gd</c>.
  /// </summary>
  [GlobalClass]
  public partial class DialogArg : GodotObject
  {
    /// <summary>
    /// The placeholder name as declared in the server's dialogs.yml, without braces.
    /// </summary>
    [Export]
    public string Name { get; set; } = string.Empty;

    [Export]
    public DialogArgKind Kind { get; set; } = DialogArgKind.Text;

    /// <summary>
    /// <see cref="Kind"/> as a lowercase string, for the GDScript side. GDScript cannot see a C#
    /// enum's members, so matching on this avoids re-declaring the ordinals over there by hand -
    /// which is exactly how the operation-error codes ended up duplicated in
    /// <c>create_new_master.gd</c>.
    /// </summary>
    [Export]
    public string KindName { get; set; } = "text";

    /// <summary>
    /// Set only when <see cref="Kind"/> is <see cref="DialogArgKind.Text"/>.
    /// </summary>
    [Export]
    public string Text { get; set; } = string.Empty;

    /// <summary>
    /// The value for every non-text kind: a plain number, or an entity/item/skill id.
    /// </summary>
    [Export]
    public long Number { get; set; } = 0;

    public static DialogArg FromProto(string name, global::Bnet.DialogArg proto)
    {
      var arg = new DialogArg { Name = name };

      switch (proto.ValueCase)
      {
        case global::Bnet.DialogArg.ValueOneofCase.Text:
          arg.SetKind(DialogArgKind.Text);
          arg.Text = proto.Text ?? string.Empty;
          break;
        case global::Bnet.DialogArg.ValueOneofCase.Number:
          arg.SetKind(DialogArgKind.Number);
          arg.Number = proto.Number;
          break;
        case global::Bnet.DialogArg.ValueOneofCase.EntityId:
          arg.SetKind(DialogArgKind.Entity);
          arg.Number = (long)proto.EntityId;
          break;
        case global::Bnet.DialogArg.ValueOneofCase.ItemId:
          arg.SetKind(DialogArgKind.Item);
          arg.Number = proto.ItemId;
          break;
        case global::Bnet.DialogArg.ValueOneofCase.SkillId:
          arg.SetKind(DialogArgKind.Skill);
          arg.Number = proto.SkillId;
          break;
        default:
          // An arg with nothing set at all: keep the name so the placeholder is still replaced
          // (with an empty string) instead of leaving a raw brace on screen.
          GD.PushWarning($"DialogArg '{name}' carried no value");
          break;
      }

      return arg;
    }

    private void SetKind(DialogArgKind kind)
    {
      Kind = kind;
      KindName = kind.ToString().ToLowerInvariant();
    }
  }
}
