using Godot;
using Godot.Collections;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>The selected master's order presets and named places, to pick from in the orders window.</summary>
  [GlobalClass]
  public partial class OrderBookSMSG : ISMSG
  {
    /// <summary>Each with <c>id</c>, <c>name</c>, <c>followers</c> and <c>orders</c> (an <see cref="OrderSheet"/>).</summary>
    [Export] public Array<Dictionary> Presets { get; set; } = new();
    /// <summary>Each with <c>id</c>, <c>name</c>, <c>kind</c> (the <c>Place.Kind</c> ordinal), <c>x</c>, <c>y</c> and <c>radius</c>.</summary>
    [Export] public Array<Dictionary> Places { get; set; } = new();

    public static OrderBookSMSG FromProto(global::Bnet.OrderBookSMSG proto)
    {
      var book = new OrderBookSMSG();
      foreach (var preset in proto.Presets)
      {
        book.Presets.Add(new Dictionary
        {
          ["id"] = preset.Id,
          ["name"] = preset.Name,
          ["followers"] = preset.Followers,
          ["orders"] = OrderSheet.FromProto(preset.Orders),
        });
      }
      foreach (var place in proto.Places)
      {
        book.Places.Add(new Dictionary
        {
          ["id"] = place.Id,
          ["name"] = place.Name,
          ["kind"] = (int)place.Kind,
          ["x"] = place.X,
          ["y"] = place.Y,
          ["radius"] = place.Radius,
        });
      }

      return book;
    }
  }
}
