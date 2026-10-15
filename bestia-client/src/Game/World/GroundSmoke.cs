using System;
using System.Collections.Generic;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>Smoke and sparks rising from a column's burning cells: one pair of particle systems per column.</summary>
  internal sealed class GroundSmoke
  {
    private const string SmokeShaderPath = "res://Game/VFX/GroundFire/ground_smoke.gdshader";
    private const string SparksShaderPath = "res://Game/VFX/GroundFire/ground_sparks.gdshader";

    private const int SmokeAmount = 64;
    private const int SparkAmount = 48;

    /// <summary>Under <see cref="ClientGroundFire.KeptAfterOutSeconds"/>, so the last smoke is gone before its column is.</summary>
    private const float SmokeLifetime = 4.5f;

    private const float SparkLifetime = 1.0f;

    /// <summary>How many burning cells it takes to emit the full amount. A smaller fire emits less.</summary>
    private const float CellsForFullAmount = 48f;

    private const float MinAmountRatio = 0.15f;

    private static readonly StringName EmissionPoints = "emission_points";
    private static readonly StringName EmissionPointCount = "emission_point_count";

    private sealed class Plume
    {
      internal GpuParticles3D Smoke;
      internal GpuParticles3D Sparks;
      internal ImageTexture Points;
    }

    private readonly Node3D _parent;
    private readonly Dictionary<ChunkKey, Plume> _plumes = new();
    private readonly Shader _smokeShader = GD.Load<Shader>(SmokeShaderPath);
    private readonly Shader _sparksShader = GD.Load<Shader>(SparksShaderPath);
    private readonly Godot.Mesh _smokeQuad;
    private readonly Godot.Mesh _sparkQuad;

    internal GroundSmoke(Node3D parent)
    {
      _parent = parent;

      var soft = SoftDot();
      _smokeQuad = new QuadMesh
      {
        Material = new StandardMaterial3D
        {
          BillboardMode = BaseMaterial3D.BillboardModeEnum.Particles,
          BillboardKeepScale = true,
          Transparency = BaseMaterial3D.TransparencyEnum.Alpha,
          VertexColorUseAsAlbedo = true,
          AlbedoTexture = soft,
          SpecularMode = BaseMaterial3D.SpecularModeEnum.Disabled,
          ProximityFadeEnabled = true,
          ProximityFadeDistance = 0.6f
        }
      };
      _sparkQuad = new QuadMesh
      {
        Material = new StandardMaterial3D
        {
          BillboardMode = BaseMaterial3D.BillboardModeEnum.Particles,
          BillboardKeepScale = true,
          Transparency = BaseMaterial3D.TransparencyEnum.Alpha,
          BlendMode = BaseMaterial3D.BlendModeEnum.Add,
          ShadingMode = BaseMaterial3D.ShadingModeEnum.Unshaded,
          VertexColorUseAsAlbedo = true,
          AlbedoTexture = soft
        }
      };
    }

    /// <summary>
    /// Points a column's plume at the cells burning now. With none, it stops emitting and what is already in
    /// the air lives out.
    /// </summary>
    internal void Write(ChunkKey column, IReadOnlyList<Vector3> burning)
    {
      if (burning.Count == 0)
      {
        if (_plumes.TryGetValue(column, out var dying))
        {
          dying.Smoke.Emitting = false;
          dying.Sparks.Emitting = false;
        }

        return;
      }

      var origin = new Vector3(column.X * WorldLayout.ChunkSize, 0f, column.Y * WorldLayout.ChunkSize);
      var image = PointsImage(burning, origin, out var low, out var high);

      if (!_plumes.TryGetValue(column, out var plume))
      {
        plume = new Plume { Points = ImageTexture.CreateFromImage(image) };
        plume.Smoke = Emitter(_smokeShader, _smokeQuad, SmokeAmount, SmokeLifetime, origin, plume.Points);
        plume.Sparks = Emitter(_sparksShader, _sparkQuad, SparkAmount, SparkLifetime, origin, plume.Points);
        _plumes[column] = plume;
      }
      else
      {
        plume.Points.SetImage(image);
      }

      var ratio = Mathf.Clamp(burning.Count / CellsForFullAmount, MinAmountRatio, 1f);
      var bounds = new Aabb(
        new Vector3(-6f, low - 1f, -6f), new Vector3(WorldLayout.ChunkSize + 12f, high - low + 14f, WorldLayout.ChunkSize + 12f));

      foreach (var emitter in new[] { plume.Smoke, plume.Sparks })
      {
        ((ShaderMaterial)emitter.ProcessMaterial).SetShaderParameter(EmissionPointCount, burning.Count);
        emitter.AmountRatio = ratio;
        emitter.VisibilityAabb = bounds;
        emitter.Emitting = true;
      }
    }

    internal void Remove(ChunkKey column)
    {
      if (_plumes.Remove(column, out var plume))
      {
        plume.Smoke.QueueFree();
        plume.Sparks.QueueFree();
      }
    }

    internal void Clear()
    {
      foreach (var plume in _plumes.Values)
      {
        plume.Smoke.QueueFree();
        plume.Sparks.QueueFree();
      }

      _plumes.Clear();
    }

    private GpuParticles3D Emitter(
      Shader shader, Godot.Mesh quad, int amount, float lifetime, Vector3 origin, Texture2D points)
    {
      var process = new ShaderMaterial { Shader = shader };
      process.SetShaderParameter(EmissionPoints, points);

      var emitter = new GpuParticles3D
      {
        Amount = amount,
        Lifetime = lifetime,
        ProcessMaterial = process,
        DrawPass1 = quad,
        CastShadow = GeometryInstance3D.ShadowCastingSetting.Off,
        Position = origin
      };
      _parent.AddChild(emitter);

      return emitter;
    }

    /// <summary>One RGB float texel per burning cell, relative to the column's corner.</summary>
    private static Image PointsImage(IReadOnlyList<Vector3> burning, Vector3 origin, out float low, out float high)
    {
      var floats = new float[burning.Count * 3];
      low = float.PositiveInfinity;
      high = float.NegativeInfinity;

      for (var i = 0; i < burning.Count; i++)
      {
        var local = burning[i] - origin;
        floats[i * 3] = local.X;
        floats[i * 3 + 1] = local.Y;
        floats[i * 3 + 2] = local.Z;
        low = Mathf.Min(low, local.Y);
        high = Mathf.Max(high, local.Y);
      }

      var bytes = new byte[floats.Length * sizeof(float)];
      Buffer.BlockCopy(floats, 0, bytes, 0, bytes.Length);

      return Image.CreateFromData(burning.Count, 1, false, Image.Format.Rgbf, bytes);
    }

    /// <summary>A white dot fading to nothing at its rim, shared by smoke and sparks.</summary>
    private static Texture2D SoftDot() => new GradientTexture2D
    {
      Width = 64,
      Height = 64,
      Fill = GradientTexture2D.FillEnum.Radial,
      FillFrom = new Vector2(0.5f, 0.5f),
      FillTo = new Vector2(0.5f, 0f),
      Gradient = new Gradient
      {
        Offsets = new[] { 0f, 1f },
        Colors = new[] { new Color(1f, 1f, 1f, 1f), new Color(1f, 1f, 1f, 0f) }
      }
    };
  }
}
