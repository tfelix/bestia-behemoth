using System;
using System.Collections.Generic;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Draws the coarse ring: the ground past the range real chunks reach.
  /// </summary>
  /// <remarks>
  /// A patch is a heightfield, so this is a regular grid mesh - two triangles per cell, normals from a central
  /// difference - and not <see cref="SurfaceNets"/>. That is most of why the ring is affordable: no band scan,
  /// no apron gather, no isosurface, and 8 192 triangles for ground that would otherwise be sixty-four chunks.
  ///
  /// <para>
  /// <b>It borrows the chunk renderer's material rather than loading its own.</b> The terrain shader needs no
  /// UVs and no tangents - it is triplanar off world position - so a grid mesh satisfies it as well as an
  /// isosurface does, and sharing means one texture array in VRAM instead of two.
  /// </para>
  ///
  /// <para>
  /// <b>Nothing here collides and nothing casts a shadow.</b> Colliders are bounded to a couple of chunks
  /// around the player already, and the sun's shadow cascade stops at the fog. A ring that cast shadows would
  /// pay for a much larger cascade to light ground that is about to be swallowed by fog anyway.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class FarTerrainRenderer : Node3D
  {
    /// <summary>
    /// Patch meshes built per frame. Building one is a straight loop over 4 225 samples with no neighbour
    /// lookups, so this can be higher than the chunk mesher's budget, but it still allocates array meshes.
    /// </summary>
    [Export] public int BuildsPerFrame { get; set; } = 2;

    /// <summary>
    /// How far the full-detail chunks reach, in metres, so this can stop drawing where they start.
    /// </summary>
    /// <remarks>
    /// The hole is punched here rather than by the server withholding patches, because this is the side that
    /// knows exactly where its own chunks reach. A patch is eight chunks wide, so a server-side hole could only
    /// ever be a ragged approximation of a square view volume.
    ///
    /// <para>
    /// Set from <c>WorldInfoSMSG.ViewRadiusChunks</c> and shrunk by one chunk of overlap. Overlapping is the
    /// safe direction: the coarse surface hidden under a real chunk costs a few thousand overdrawn pixels,
    /// where a gap between the two is a hole a player can see the sky through.
    /// </remarks>
    public float ChunkReachMetres { get; private set; }

    private readonly Dictionary<PatchKey, MeshInstance3D> _tiles = new();

    private readonly Queue<SurfacePatch> _toBuild = new();

    /// <summary>Where the player's own chunks are centred, in Godot metres. The hole follows it.</summary>
    private Vector3 _anchor;

    private Material _material;

    private float _voxelSize = 1.0f;

    public int TileCount => _tiles.Count;

    public override void _Process(double delta)
    {
      var budget = Math.Max(1, BuildsPerFrame);

      for (var done = 0; done < budget && _toBuild.Count > 0; done++)
      {
        Install(_toBuild.Dequeue());
      }
    }

    /// <summary>
    /// Points the renderer at a world and at the material the chunk renderer is already using.
    /// </summary>
    public void Configure(WorldInfoSMSG worldInfo, Material terrainMaterial)
    {
      _material = terrainMaterial;

      if (worldInfo != null)
      {
        _voxelSize = (float)worldInfo.VoxelSizeMetres;

        // One chunk of overlap, deliberately. See ChunkReachMetres.
        var chunks = Math.Max(0, worldInfo.ViewRadiusChunks - 1);
        ChunkReachMetres = chunks * worldInfo.ChunkSize * _voxelSize;
      }

      Clear();
    }

    /// <summary>Moves the hole to follow the player, in the same Godot metres the meshes are built in.</summary>
    public void SetAnchor(Vector3 anchor) => _anchor = anchor;

    public void Queue(SurfacePatch patch) => _toBuild.Enqueue(patch);

    public void Remove(PatchKey key)
    {
      if (!_tiles.Remove(key, out var instance))
      {
        return;
      }

      instance.QueueFree();
    }

    public void Clear()
    {
      foreach (var instance in _tiles.Values)
      {
        instance.QueueFree();
      }

      _tiles.Clear();
      _toBuild.Clear();
    }

    private void Install(SurfacePatch patch)
    {
      var mesh = Build(patch);

      Remove(patch.Key);

      if (mesh == null)
      {
        // Every cell fell inside the chunk hole. Normal for the patches under the player, and drawing an empty
        // mesh would cost a node and a draw call to show nothing.
        return;
      }

      var instance = new MeshInstance3D
      {
        Name = $"FarPatch {patch.Key}",
        Mesh = mesh,
        CastShadow = GeometryInstance3D.ShadowCastingSetting.Off
      };

      if (_material != null)
      {
        instance.MaterialOverride = _material;
      }

      AddChild(instance);
      _tiles[patch.Key] = instance;
    }

    /// <summary>
    /// Turns a patch into one grid mesh, or null when every cell of it is hidden by real chunks.
    /// </summary>
    /// <remarks>
    /// Vertices are absolute Godot metres and the instance sits at the origin, exactly as the chunk mesher
    /// emits them - so the two coordinate systems cannot drift apart. Server <c>(x, y, z)</c> is Godot
    /// <c>(x, z, y)</c>: world y runs north and elevation is up.
    /// </remarks>
    private ArrayMesh Build(SurfacePatch patch)
    {
      var key = patch.Key;
      var step = key.MetresPerSample;
      var originX = key.OriginX;
      var originY = key.OriginY;

      var appearance = BlockAppearance.Current;

      var vertices = new List<Vector3>(PatchGrid.SampleCount);
      var normals = new List<Vector3>(PatchGrid.SampleCount);
      var colours = new List<Color>(PatchGrid.SampleCount);
      var weights = new List<byte>(PatchGrid.SampleCount * BlockAppearance.Slots);
      var indices = new List<int>(PatchGrid.Cells * PatchGrid.Cells * 6);

      for (var j = 0; j < PatchGrid.Samples; j++)
      {
        for (var i = 0; i < PatchGrid.Samples; i++)
        {
          var height = patch.HeightAt(i, j);
          vertices.Add(new Vector3(originX + i * step, height, originY + j * step));
          normals.Add(NormalAt(patch, i, j, step));

          var block = patch.BlockAt(i, j);
          colours.Add(appearance.ColourOf(block));
          AppendSlotWeights(weights, appearance.SlotOf(block));
        }
      }

      for (var j = 0; j < PatchGrid.Cells; j++)
      {
        for (var i = 0; i < PatchGrid.Cells; i++)
        {
          if (IsHiddenByChunks(originX + i * step, originY + j * step, step))
          {
            continue;
          }

          var a = PatchGrid.Index(i, j);
          var b = PatchGrid.Index(i + 1, j);
          var c = PatchGrid.Index(i, j + 1);
          var d = PatchGrid.Index(i + 1, j + 1);

          // Godot treats clockwise as front-facing, so for ground whose normal is +Y the right-hand cross
          // product of the first two edges must come out -Y. Both triangles below satisfy that; reversing
          // either one renders the ring as backfaces, which looks like it never arrived. SurfaceNets carries
          // the same note at its own winding for the same reason.
          indices.Add(a); indices.Add(b); indices.Add(c);
          indices.Add(b); indices.Add(d); indices.Add(c);
        }
      }

      if (indices.Count == 0)
      {
        return null;
      }

      var arrays = new Godot.Collections.Array();
      arrays.Resize((int)Godot.Mesh.ArrayType.Max);
      arrays[(int)Godot.Mesh.ArrayType.Vertex] = vertices.ToArray();
      arrays[(int)Godot.Mesh.ArrayType.Normal] = normals.ToArray();
      arrays[(int)Godot.Mesh.ArrayType.Color] = colours.ToArray();
      arrays[(int)Godot.Mesh.ArrayType.Index] = indices.ToArray();

      var format = SlotWeightFormat(weights.ToArray(), arrays);

      var mesh = new ArrayMesh();
      mesh.AddSurfaceFromArrays(Godot.Mesh.PrimitiveType.Triangles, arrays, null, null, format);

      return mesh;
    }

    /// <summary>
    /// Whether a cell is inside the square the full-detail chunks already draw.
    /// </summary>
    /// <remarks>
    /// A square rather than a disc, because the chunk view volume is a square. Tested per cell rather than per
    /// patch: a patch is eight chunks wide, so the boundary runs through patches rather than between them.
    /// </remarks>
    private bool IsHiddenByChunks(float cellX, float cellY, float step)
    {
      if (ChunkReachMetres <= 0.0f)
      {
        return false;
      }

      // The far corner of the cell, so a cell that only partly overlaps the chunks is still drawn. Erring
      // towards drawing is the safe direction: overdraw against a hole in the world.
      var dx = Math.Abs(cellX + step - _anchor.X);
      var dy = Math.Abs(cellY + step - _anchor.Z);

      return dx < ChunkReachMetres && dy < ChunkReachMetres;
    }

    /// <summary>
    /// The surface normal from a central difference over the height grid.
    /// </summary>
    /// <remarks>
    /// One-sided at the patch edge, which is where the shading of two neighbouring patches can disagree - the
    /// samples themselves are shared exactly, but a one-sided difference is not the same slope as a central
    /// one. It shows as a faint seam under raking light at a range where fog has already taken most of the
    /// contrast; the alternative is a halo of samples on every patch, which is a sixth more of everything.
    /// </remarks>
    private static Vector3 NormalAt(SurfacePatch patch, int i, int j, float step)
    {
      var west = patch.HeightAt(Math.Max(0, i - 1), j);
      var east = patch.HeightAt(Math.Min(PatchGrid.Cells, i + 1), j);
      var south = patch.HeightAt(i, Math.Max(0, j - 1));
      var north = patch.HeightAt(i, Math.Min(PatchGrid.Cells, j + 1));

      var spanX = (Math.Min(PatchGrid.Cells, i + 1) - Math.Max(0, i - 1)) * step;
      var spanY = (Math.Min(PatchGrid.Cells, j + 1) - Math.Max(0, j - 1)) * step;

      var normal = new Vector3(-(east - west) / spanX, 1.0f, -(north - south) / spanY);

      return normal.Normalized();
    }

    /// <summary>
    /// One weight per slot for this vertex: all of it on the material's own slot.
    /// </summary>
    /// <remarks>
    /// No blending between slots, unlike the chunk mesher, which accumulates the materials meeting at a cell.
    /// A patch has one material per sample and nothing finer to blend from - and at this range the shader's
    /// own triplanar detail is well past the point where a per-vertex mix would be visible.
    /// </remarks>
    private static void AppendSlotWeights(List<byte> weights, BlockAppearance.SurfaceSlot slot)
    {
      var chosen = (int)slot;

      for (var index = 0; index < BlockAppearance.Slots; index++)
      {
        weights.Add(index == chosen ? (byte)0xFF : (byte)0);
      }
    }

    /// <summary>
    /// Splits the slot weights across the four custom channels and declares their format.
    /// </summary>
    /// <remarks>
    /// All four or none, and each exactly four bytes per vertex: a partial or wrongly-sized set makes
    /// <c>AddSurfaceFromArrays</c> silently add no surface at all, which renders as invisible terrain rather
    /// than as an error. <see cref="TerrainRenderer"/> guards the same way for the same reason.
    /// </remarks>
    private static Godot.Mesh.ArrayFormat SlotWeightFormat(byte[] weights, Godot.Collections.Array arrays)
    {
      const int Channels = 4;
      var vertices = weights.Length / BlockAppearance.Slots;

      for (var channel = 0; channel < Channels; channel++)
      {
        var lane = new byte[vertices * Channels];

        for (var vertex = 0; vertex < vertices; vertex++)
        {
          Array.Copy(weights, vertex * BlockAppearance.Slots + channel * Channels, lane, vertex * Channels, Channels);
        }

        arrays[(int)Godot.Mesh.ArrayType.Custom0 + channel] = lane;
      }

      const uint Rgba8 = (uint)Godot.Mesh.ArrayCustomFormat.Rgba8Unorm;

      return (Godot.Mesh.ArrayFormat)(
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom0Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom1Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom2Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom3Shift));
    }
  }
}
