using System.Collections.Generic;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Draws the fire on burning ground: one multimesh per column, rebuilt whenever that column's cells change.
  /// </summary>
  public partial class GroundFlames : Node3D
  {
    private const float PlaceholderHeight = 0.6f;

    private readonly Dictionary<ChunkKey, MultiMeshInstance3D> _drawn = new();
    private readonly List<int> _slabsTopDown = new();
    private readonly List<Vector3> _bases = new();

    private ClientChunkStore _store;
    private ChunkWrap _wrap = ChunkWrap.None;
    private Godot.Mesh _mesh;

    public void Configure(ClientChunkStore store, ChunkWrap wrap)
    {
      _store = store;
      _wrap = wrap;
      _mesh ??= Placeholder();
      Clear();
    }

    /// <summary>Replaces what is drawn for one column. An empty list removes it.</summary>
    public void Write(ChunkKey column, IReadOnlyList<FireCell> cells)
    {
      CollectBases(column, cells);

      if (_bases.Count == 0)
      {
        Remove(column);
        return;
      }

      if (!_drawn.TryGetValue(column, out var node))
      {
        node = new MultiMeshInstance3D
        {
          Multimesh = new MultiMesh { TransformFormat = MultiMesh.TransformFormatEnum.Transform3D, Mesh = _mesh },
          CastShadow = GeometryInstance3D.ShadowCastingSetting.Off
        };
        AddChild(node);
        _drawn[column] = node;
      }

      var multimesh = node.Multimesh;
      multimesh.InstanceCount = _bases.Count;

      for (var i = 0; i < _bases.Count; i++)
      {
        // Lifted by half its height, so the box stands on the ground rather than in it.
        var centre = _bases[i] + Vector3.Up * (PlaceholderHeight * 0.5f);
        multimesh.SetInstanceTransform(i, new Transform3D(Basis.Identity, centre));
      }
    }

    public void Clear()
    {
      foreach (var node in _drawn.Values)
      {
        node.QueueFree();
      }

      _drawn.Clear();
    }

    private void Remove(ChunkKey column)
    {
      if (_drawn.Remove(column, out var node))
      {
        node.QueueFree();
      }
    }

    /// <summary>Where each burning cell's ground is, skipping cells whose chunk has not arrived yet.</summary>
    private void CollectBases(ChunkKey column, IReadOnlyList<FireCell> cells)
    {
      _bases.Clear();

      if (_store == null || cells.Count == 0)
      {
        return;
      }

      CollectSlabsTopDown(column);

      const int size = WorldLayout.ChunkSize;
      foreach (var cell in cells)
      {
        if (!cell.IsBurning)
        {
          continue;
        }

        var voxelX = (long)column.X * size + cell.Index % size;
        var voxelY = (long)column.Y * size + cell.Index / size;
        var z = SurfaceZ(column, cell.Index % size, cell.Index / size, voxelX, voxelY);

        if (!double.IsNaN(z))
        {
          var metres = (float)WorldLayout.VoxelSizeMetres;
          _bases.Add(new Vector3((voxelX + 0.5f) * metres, (float)z * metres, (voxelY + 0.5f) * metres));
        }
      }
    }

    private void CollectSlabsTopDown(ChunkKey column)
    {
      _slabsTopDown.Clear();

      foreach (var key in _store.HeldKeys())
      {
        if (key.X == column.X && key.Y == column.Y)
        {
          _slabsTopDown.Add(key.Z);
        }
      }

      _slabsTopDown.Sort((a, b) => b.CompareTo(a));
    }

    /// <summary>
    /// The drawn height of the column's top surface, which is the only ground the server burns. NaN when no
    /// slab of it is held.
    /// </summary>
    private double SurfaceZ(ChunkKey column, int localX, int localY, long voxelX, long voxelY)
    {
      foreach (var slab in _slabsTopDown)
      {
        var top = _store.Get(new ChunkKey(column.X, column.Y, slab))?.SurfaceHeightAt(localX, localY) ?? -1.0;
        if (top < 0.0)
        {
          continue;
        }

        var coarse = slab * (double)WorldLayout.ChunkHeight + top;
        var drawn = SurfaceProbe.SurfaceAt(
          _store, BlockAppearance.Current, voxelX + 0.5, voxelY + 0.5, coarse,
          WorldLayout.ChunkSize, WorldLayout.ChunkHeight, _wrap);

        return double.IsNaN(drawn) ? coarse : drawn;
      }

      return double.NaN;
    }

    /// <summary>A translucent red box on each burning square metre, until there is fire art.</summary>
    private static Godot.Mesh Placeholder() => new BoxMesh
    {
      Size = new Vector3(1.0f, PlaceholderHeight, 1.0f),
      Material = new StandardMaterial3D
      {
        Transparency = BaseMaterial3D.TransparencyEnum.Alpha,
        ShadingMode = BaseMaterial3D.ShadingModeEnum.Unshaded,
        AlbedoColor = new Color(1.0f, 0.1f, 0.05f, 0.45f)
      }
    };
  }
}
