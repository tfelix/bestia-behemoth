using System.Collections.Generic;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// The block palette: which material each id in a chunk payload means.
  /// </summary>
  /// <remarks>
  /// Sent by the server rather than hardcoded here, so a renamed or added material is not a client release.
  /// Ids are sparse and permanent - grouped by material family with gaps inside each family - so this is a
  /// lookup by id, never an index into the list.
  /// </remarks>
  [GlobalClass]
  public partial class BlockPaletteSMSG : ISMSG
  {
    public sealed class Entry
    {
      public int Id { get; init; }
      public string Name { get; init; } = "";
      public bool Solid { get; init; }
      public bool Opaque { get; init; }
    }

    public IReadOnlyDictionary<int, Entry> ById => _byId;

    private readonly Dictionary<int, Entry> _byId = new();

    public int Count => _byId.Count;

    public string NameOf(int blockId) => _byId.TryGetValue(blockId, out var entry) ? entry.Name : $"#{blockId}";

    public static BlockPaletteSMSG FromProto(global::Bnet.BlockPaletteSMSG proto)
    {
      var palette = new BlockPaletteSMSG();

      foreach (var block in proto.Blocks)
      {
        palette._byId[(int)block.Id] = new Entry
        {
          Id = (int)block.Id,
          Name = block.Name,
          Solid = block.Solid,
          Opaque = block.Opaque
        };
      }

      return palette;
    }

    public override string ToString() => $"BlockPalette[{Count} materials]";
  }
}
