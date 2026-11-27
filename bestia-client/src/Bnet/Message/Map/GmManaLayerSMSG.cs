using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// The mana field and the world event director around a GM, for the GM's map. See
  /// <c>gm_mana_layer_smsg.proto</c>. A <see cref="Width"/> of 0 clears the layer.
  /// </summary>
  [GlobalClass]
  public partial class GmManaLayerSMSG : ISMSG
  {
    /// <summary>The grid's south-west corner in map metres.</summary>
    [Export] public Vector2 SouthWest { get; set; }

    [Export] public float CellMetres { get; set; }

    /// <summary>Cells per row; rows run west to east, then south to north.</summary>
    [Export] public int Width { get; set; }

    [Export] public float[] Density { get; set; } = [];

    /// <summary>The proto's ManaTier per cell, 0 calm to 7 burn.</summary>
    [Export] public int[] Tier { get; set; } = [];

    /// <summary>The province of each cell, -1 for none.</summary>
    [Export] public int[] Province { get; set; } = [];

    /// <summary>How corrupted each cell is by the Withering, 0 to 1.</summary>
    [Export] public float[] Corruption { get; set; } = [];

    /// <summary>Each with <c>index</c>, <c>name</c>, <c>budget</c>, <c>quiet_seconds</c> and <c>sanctuary</c>.</summary>
    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> Provinces { get; set; } = [];

    /// <summary>Each with <c>instance_id</c>, <c>event_id</c>, <c>x</c>, <c>y</c> and <c>province</c>.</summary>
    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> Events { get; set; } = [];

    public static GmManaLayerSMSG FromProto(global::Bnet.GmManaLayerSMSG proto)
    {
      var layer = new GmManaLayerSMSG
      {
        SouthWest = new Vector2((float)proto.WestMetres, (float)proto.SouthMetres),
        CellMetres = proto.CellMetres,
        Width = (int)proto.Width,
        Density = [.. proto.Density],
        Province = [.. proto.Province],
        Corruption = [.. proto.Corruption],
      };
      layer.Tier = new int[proto.Tier.Count];
      for (var i = 0; i < proto.Tier.Count; i++)
      {
        layer.Tier[i] = (int)proto.Tier[i];
      }

      foreach (var province in proto.Provinces)
      {
        layer.Provinces.Add(new Godot.Collections.Dictionary
        {
          ["index"] = province.Index,
          ["name"] = province.Name,
          ["budget"] = province.Budget,
          ["quiet_seconds"] = province.QuietSeconds,
          ["sanctuary"] = province.Sanctuary
        });
      }

      foreach (var worldEvent in proto.Events)
      {
        layer.Events.Add(new Godot.Collections.Dictionary
        {
          ["instance_id"] = worldEvent.InstanceId,
          ["event_id"] = worldEvent.EventId,
          ["x"] = worldEvent.EastMetres,
          ["y"] = worldEvent.NorthMetres,
          ["province"] = worldEvent.Province
        });
      }

      return layer;
    }
  }
}
