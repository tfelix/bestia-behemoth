using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// When each burning square metre near the player caught fire and went out, as one texture the shaders
  /// sample by world position.
  /// </summary>
  /// <remarks>
  /// R is <see cref="FireCell.IgnitedAt"/>, G is <see cref="FireCell.OutAt"/>, both on
  /// <see cref="GroundFireClock"/>. Laid out and wrapped exactly like <see cref="GroundMarkTexture"/>, so one
  /// UV reads both. Times rather than a burn level, so the shaders animate on their own between uploads.
  /// </remarks>
  public sealed class GroundFireTexture
  {
    private const int Channels = 2;

    private readonly byte[] _bytes = new byte[GroundMarkTexture.Size * GroundMarkTexture.Size * Channels * sizeof(float)];

    private readonly ImageTexture _texture;
    private bool _dirty;

    public GroundFireTexture()
    {
      _texture = ImageTexture.CreateFromImage(Pixels());
    }

    public Texture2D Texture => _texture;

    private Span<float> Texels => MemoryMarshal.Cast<byte, float>(_bytes.AsSpan());

    /// <summary>Replaces one column's cells. Cells not in <paramref name="cells"/> are cleared.</summary>
    public void WriteColumn(int chunkX, int chunkY, int chunkSize, IReadOnlyList<FireCell> cells)
    {
      ClearColumn(chunkX, chunkY, chunkSize);

      var texels = Texels;
      foreach (var cell in cells)
      {
        var at = TexelAt(chunkX, chunkY, chunkSize, cell.Index % chunkSize, cell.Index / chunkSize);
        texels[at] = cell.IgnitedAt;
        texels[at + 1] = cell.OutAt;
      }
    }

    public void ClearColumn(int chunkX, int chunkY, int chunkSize)
    {
      var texels = Texels;
      for (var localY = 0; localY < chunkSize; localY++)
      {
        for (var localX = 0; localX < chunkSize; localX++)
        {
          var at = TexelAt(chunkX, chunkY, chunkSize, localX, localY);
          texels[at] = GroundFireClock.Never;
          texels[at + 1] = GroundFireClock.Never;
        }
      }

      _dirty = true;
    }

    /// <summary>Uploads if anything changed. Cheap when nothing did, which is nearly always.</summary>
    public void Flush()
    {
      if (!_dirty)
      {
        return;
      }

      _texture.Update(Pixels());
      _dirty = false;
    }

    public void Clear()
    {
      Array.Clear(_bytes);
      _dirty = true;
    }

    private Image Pixels() =>
      Image.CreateFromData(GroundMarkTexture.Size, GroundMarkTexture.Size, false, Image.Format.Rgf, _bytes);

    private static int TexelAt(int chunkX, int chunkY, int chunkSize, int localX, int localY) =>
      GroundMarkTexture.TexelIndexAt((long)chunkX * chunkSize + localX, (long)chunkY * chunkSize + localY) * Channels;
  }
}
