class_name EmblemPicture

## Turns whatever picture the leader picks into the emblem the server accepts: square, 32x32, a PNG of at most
## 8 KiB. Drawn only at whole-number scales with nearest filtering, so a pixel stays a crisp square.

const SIZE := 32
const MAX_BYTES := 8 * 1024
const GUILD_EMBLEM_SKILL_ID := 9001


## Cropped to its middle square, then scaled. Empty when the file is no image Godot can read.
static func png_from_file(path: String) -> PackedByteArray:
	var image := Image.load_from_file(path)
	if image == null or image.is_empty():
		return PackedByteArray()

	var side := mini(image.get_width(), image.get_height())
	var corner := Vector2i((image.get_width() - side) / 2, (image.get_height() - side) / 2)
	image = image.get_region(Rect2i(corner, Vector2i(side, side)))
	image.convert(Image.FORMAT_RGBA8)
	if side != SIZE:
		image.resize(SIZE, SIZE, Image.INTERPOLATE_LANCZOS)

	var png := image.save_png_to_buffer()
	return png if png.size() <= MAX_BYTES else PackedByteArray()


static func texture_of(png: PackedByteArray) -> ImageTexture:
	var image := Image.new()
	if image.load_png_from_buffer(png) != OK:
		return null
	return ImageTexture.create_from_image(image)


## A square showing an emblem at [param scale] times its size.
static func frame(scale: int) -> TextureRect:
	var picture := TextureRect.new()
	picture.custom_minimum_size = Vector2(SIZE, SIZE) * scale
	picture.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	picture.stretch_mode = TextureRect.STRETCH_SCALE
	picture.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	return picture
