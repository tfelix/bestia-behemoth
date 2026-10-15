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
    private const string ShaderPath = "res://Game/VFX/GroundFire/ground_flame.gdshader";

    /// <summary>Two overlapping flames per square metre, so a burning field reads as one sheet of fire.</summary>
    private const int FlamesPerCell = 2;

    /// <summary>How far a flame may stand from its cell's centre, in metres.</summary>
    private const float Jitter = 0.3f;

    private const float MinHeight = 0.8f;
    private const float MaxHeight = 1.9f;

    private readonly Dictionary<ChunkKey, MultiMeshInstance3D> _drawn = new();
    private readonly List<int> _slabsTopDown = new();
    private readonly List<(Vector3 Base, FireCell Cell)> _bases = new();

    private ClientChunkStore _store;
    private ChunkWrap _wrap = ChunkWrap.None;
    private Godot.Mesh _mesh;

    public void Configure(ClientChunkStore store, ChunkWrap wrap)
    {
      _store = store;
      _wrap = wrap;
      _mesh ??= new QuadMesh { Material = new ShaderMaterial { Shader = GD.Load<Shader>(ShaderPath) } };
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
          Multimesh = new MultiMesh
          {
            TransformFormat = MultiMesh.TransformFormatEnum.Transform3D,
            UseCustomData = true,
            Mesh = _mesh
          },
          CastShadow = GeometryInstance3D.ShadowCastingSetting.Off
        };
        AddChild(node);
        _drawn[column] = node;
      }

      var multimesh = node.Multimesh;
      multimesh.InstanceCount = _bases.Count * FlamesPerCell;

      var low = float.PositiveInfinity;
      var high = float.NegativeInfinity;
      var instance = 0;

      foreach (var (ground, cell) in _bases)
      {
        for (var flame = 0; flame < FlamesPerCell; flame++)
        {
          var seed = Hash(ground, flame);
          var offset = new Vector3((Unit(seed, 1) - 0.5f) * 2f * Jitter, 0f, (Unit(seed, 2) - 0.5f) * 2f * Jitter);
          var height = Mathf.Lerp(MinHeight, MaxHeight, Unit(seed, 3));

          multimesh.SetInstanceTransform(instance, new Transform3D(Basis.Identity, ground + offset));
          multimesh.SetInstanceCustomData(instance, new Color(cell.IgnitedAt, cell.OutAt, Unit(seed, 4), height));
          instance++;
        }

        low = Mathf.Min(low, ground.Y);
        high = Mathf.Max(high, ground.Y);
      }

      // The shader builds each billboard itself, so the mesh's own bounds say nothing about where it draws.
      var corner = new Vector3(
        column.X * WorldLayout.ChunkSize - 2f, low - 1f, column.Y * WorldLayout.ChunkSize - 2f);
      node.CustomAabb = new Aabb(
        corner, new Vector3(WorldLayout.ChunkSize + 4f, high - low + MaxHeight + 3f, WorldLayout.ChunkSize + 4f));
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

    /// <summary>Where each recorded cell's ground is, skipping cells whose chunk has not arrived yet.</summary>
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
        var voxelX = (long)column.X * size + cell.Index % size;
        var voxelY = (long)column.Y * size + cell.Index / size;
        var z = SurfaceZ(column, cell.Index % size, cell.Index / size, voxelX, voxelY);

        if (!double.IsNaN(z))
        {
          var metres = (float)WorldLayout.VoxelSizeMetres;
          _bases.Add((new Vector3((voxelX + 0.5f) * metres, (float)z * metres, (voxelY + 0.5f) * metres), cell));
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

    /// <summary>The same flame on every client and after every rebuild, so a step does not reshuffle the fire.</summary>
    private static uint Hash(Vector3 ground, int flame)
    {
      var x = (uint)Mathf.FloorToInt(ground.X) * 73856093u ^ (uint)Mathf.FloorToInt(ground.Z) * 19349663u ^
              (uint)flame * 83492791u;
      x ^= x >> 16;
      x *= 0x7FEB352Du;
      x ^= x >> 15;
      x *= 0x846CA68Bu;
      x ^= x >> 16;

      return x;
    }

    /// <summary>One of several independent values in [0, 1) drawn from a seed.</summary>
    private static float Unit(uint seed, uint which)
    {
      var x = seed ^ (which * 0x9E3779B9u);
      x ^= x >> 15;
      x *= 0x2C1B3C6Du;
      x ^= x >> 12;

      return (x >> 8) * (1.0f / 16777216.0f);
    }
  }
}
