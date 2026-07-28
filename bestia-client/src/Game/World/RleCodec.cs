using System;
using System.IO;
using System.IO.Compression;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Decodes the chunk payload the server sends. The C# half of the server's <c>RleCodec</c>.
  /// </summary>
  /// <remarks>
  /// Format, which must not be changed here alone:
  /// <code>
  /// u8   version
  /// uvar size
  /// uvar height
  /// repeated to volume: uvar blockId,   uvar runLength
  /// repeated to volume: uvar occupancy, uvar runLength
  /// </code>
  ///
  /// <para>
  /// Runs stream across the whole chunk in array order rather than restarting per column, so a run carries
  /// from the top of one column into the bottom of the next - which is why a chunk of open sea or one deep
  /// underground is a handful of bytes rather than one run per column.
  /// </para>
  ///
  /// <para>
  /// Materials and occupancy are two sequential streams, not interleaved pairs. Occupancy changes precisely
  /// where material does not, so as its own stream it costs a few runs per column while interleaving would
  /// break every run in the material stream.
  /// </para>
  ///
  /// <para><b>It refuses rather than guesses.</b> A version it does not know, a truncated stream, trailing
  /// bytes, a zero-length run - each throws. This is the one place data from outside the process becomes a
  /// chunk, and the server's own decoder validates for the same reason: an invariant that only holds for
  /// payloads you produced yourself is not an invariant. Silently reinterpreting a payload under the current
  /// chunk size is not one of the two acceptable outcomes.</para>
  /// </remarks>
  public static class RleCodec
  {
    /// <summary>Must match the server's <c>RleCodec.VERSION</c>, which the world info also reports.</summary>
    public const int Version = 2;

    public static VoxelChunk Decode(int chunkX, int chunkY, int chunkZ, byte[] bytes)
    {
      var cursor = new Cursor(bytes);

      var version = cursor.ReadByte();
      if (version != Version)
      {
        throw new InvalidDataException(
          $"Chunk ({chunkX},{chunkY},{chunkZ}) was encoded with RLE version {version}, this build reads {Version}");
      }

      var size = cursor.ReadVarInt();
      var height = cursor.ReadVarInt();

      if (size <= 0 || height <= 0 || (long)size * size * height > MaxVolume)
      {
        // A length read off the wire must not be allowed to size an allocation unchecked.
        throw new InvalidDataException(
          $"Chunk ({chunkX},{chunkY},{chunkZ}) claims implausible dimensions {size}x{size}x{height}");
      }

      var volume = size * size * height;

      var blocks = ReadRuns(cursor, volume, "blocks");
      var occupancy = ReadRuns(cursor, volume, "occupancy");

      if (cursor.HasMore)
      {
        throw new InvalidDataException(
          $"Chunk ({chunkX},{chunkY},{chunkZ}) has trailing bytes after both streams decoded; " +
          "the payload is not what it claims");
      }

      var chunk = new VoxelChunk(chunkX, chunkY, chunkZ, size, height, blocks, occupancy);
      chunk.Validate();

      return chunk;
    }

    /// <summary>Raw inflate, matching the JDK <c>Deflater</c> the server uses - zlib framed, not gzip.</summary>
    public static byte[] Inflate(byte[] compressed)
    {
      using var input = new MemoryStream(compressed);
      using var zlib = new ZLibStream(input, CompressionMode.Decompress);
      using var output = new MemoryStream(compressed.Length * 4);

      zlib.CopyTo(output);

      return output.ToArray();
    }

    private static byte[] ReadRuns(Cursor cursor, int volume, string what)
    {
      var values = new byte[volume];
      var written = 0;

      while (written < volume)
      {
        if (!cursor.HasMore)
        {
          throw new InvalidDataException(
            $"The {what} stream ends after {written} of {volume} entries; the payload is truncated");
        }

        var value = cursor.ReadVarInt();
        var run = cursor.ReadVarInt();

        if (value is < 0 or > 255)
        {
          throw new InvalidDataException($"The {what} stream contains {value}, which cannot be a byte");
        }

        if (run <= 0)
        {
          throw new InvalidDataException($"The {what} stream contains a run of length {run}");
        }

        if (written + run > volume)
        {
          throw new InvalidDataException($"The {what} stream decodes to more than {volume} entries");
        }

        values.AsSpan(written, run).Fill((byte)value);
        written += run;
      }

      return values;
    }

    /// <summary>A 64-cubed chunk of 2-byte voxels. Far above anything shipped; this is a sanity bound.</summary>
    private const long MaxVolume = 64L * 64L * 1024L;

    private sealed class Cursor
    {
      private readonly byte[] _bytes;
      private int _at;

      internal Cursor(byte[] bytes) => _bytes = bytes;

      internal bool HasMore => _at < _bytes.Length;

      internal int ReadByte()
      {
        if (_at >= _bytes.Length)
        {
          throw new InvalidDataException("Truncated chunk payload");
        }

        return _bytes[_at++];
      }

      internal int ReadVarInt()
      {
        var result = 0;
        var shift = 0;

        while (true)
        {
          var b = ReadByte();
          result |= (b & 0x7F) << shift;

          if ((b & 0x80) == 0)
          {
            return result;
          }

          shift += 7;

          if (shift >= 35)
          {
            throw new InvalidDataException("Varint in chunk payload is longer than five bytes");
          }
        }
      }
    }
  }
}
