using Godot;

namespace BestiaBehemothClient.Bnet.Message.Quest
{
  /// <summary>
  /// One value for a quest's title template. <see cref="KindName"/> is <c>number</c>, <c>item</c>, <c>species</c>,
  /// <c>token</c> or <c>name</c>; the numeric kinds use <see cref="Number"/>, the others <see cref="Text"/>.
  /// </summary>
  [GlobalClass]
  public partial class QuestTextArgEntry : GodotObject
  {
    [Export] public string Name { get; set; } = "";
    [Export] public string KindName { get; set; } = "";
    [Export] public string Text { get; set; } = "";
    [Export] public long Number { get; set; }

    public static QuestTextArgEntry FromProto(string name, global::Bnet.QuestTextArg proto)
    {
      var arg = new QuestTextArgEntry { Name = name };
      switch (proto.ValueCase)
      {
        case global::Bnet.QuestTextArg.ValueOneofCase.Number:
          arg.KindName = "number";
          arg.Number = proto.Number;
          break;
        case global::Bnet.QuestTextArg.ValueOneofCase.ItemId:
          arg.KindName = "item";
          arg.Number = proto.ItemId;
          break;
        case global::Bnet.QuestTextArg.ValueOneofCase.SpeciesId:
          arg.KindName = "species";
          arg.Number = proto.SpeciesId;
          break;
        case global::Bnet.QuestTextArg.ValueOneofCase.Token:
          arg.KindName = "token";
          arg.Text = proto.Token;
          break;
        case global::Bnet.QuestTextArg.ValueOneofCase.Name:
          arg.KindName = "name";
          arg.Text = proto.Name;
          break;
      }

      return arg;
    }
  }
}
