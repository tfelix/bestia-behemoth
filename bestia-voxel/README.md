# Bestia-Voxel

This aims to be a slim and small library around efficient voxel storage and retrieval for massive game worlds voxel
storage. It uses run-length encoding for storing the voxel in a compact way.

It is heavily inspired by thoughts from the [roblox blog post](https://blog.roblox.com/2017/04/voxel-terrain-storage/)
about voxel storage algorithms. Yet its very specialized for the use in the [Bestia Game](https://bestia-game.net) so
it might not directly usable for you.

## Storage Format

<table >
<tr>
<th>1 Byte</th>
<th>1 Byte</th>
<th>1 Byte</th>
</tr>
<td>Data Header</td>
<td>Occupancy Byte (0-255)</td>
<td>Repeat Count</td>
<th>
</table>

The second bytes are set depending on the flags. The Occupancy bit is present if Flag Occupancy is 1. The Repeat Count Byte is set if the Flag RLE is 1.

<table >
<tr>
<th colspan="8">Data Header</th>
</tr>
<tr>
<th>1</th>
<th>1</th>
<th>1</th>
<th>1</th>
<th>1</th>
<th>1</th>
<th>1</th>
<th>1</th>
</tr>
<td>Flag RLE</td>
<td>Flag Occup.</td>
<td colspan="6" style="text-align: center">Material ID</td>
<th>
</table>
