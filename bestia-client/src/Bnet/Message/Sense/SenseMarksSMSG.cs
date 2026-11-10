using Godot;

namespace BestiaBehemothClient.Bnet.Message.Sense
{
  /// <summary>
  /// What a sensing skill found. Each mark is a dictionary with <c>kind</c> (the proto's SenseKind as int),
  /// <c>x</c> and <c>y</c> in map metres, <c>label</c>, <c>ref_id</c>, <c>depth</c> and <c>size</c>. Shown for
  /// <see cref="VisibleSeconds"/>, then forgotten.
  /// </summary>
  [GlobalClass]
  public partial class SenseMarksSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> Marks { get; set; } = [];

    [Export] public int VisibleSeconds { get; set; }

    public static SenseMarksSMSG FromProto(global::Bnet.SenseMarksSMSG proto)
    {
      var marks = new Godot.Collections.Array<Godot.Collections.Dictionary>();
      foreach (var mark in proto.Marks)
      {
        marks.Add(new Godot.Collections.Dictionary
        {
          ["kind"] = (int)mark.Kind,
          ["x"] = mark.EastMetres,
          ["y"] = mark.NorthMetres,
          ["label"] = mark.Label,
          ["ref_id"] = mark.RefId,
          ["depth"] = mark.DepthMetres,
          ["size"] = mark.SizeMetres
        });
      }

      return new SenseMarksSMSG { Marks = marks, VisibleSeconds = (int)proto.VisibleSeconds };
    }
  }
}
