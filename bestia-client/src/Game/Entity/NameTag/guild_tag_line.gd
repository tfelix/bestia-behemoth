class_name GuildTagLine

## The name tag's second line for a member of a guild, as Ragnarok Online writes it: the guild, then the rank.


static func of(tag: GuildTagComponentSMSG) -> String:
	if tag == null:
		return ""
	return "%s [%s]" % [tag.GuildName, tag.RankTitle]


## The emblem beside [param label]'s guild line; made on first use. @return what to keep as [param emblem]
static func show_emblem(label: Label3D, emblem: NameTagEmblem, hash: String) -> NameTagEmblem:
	if emblem == null:
		if hash.is_empty():
			return null
		emblem = NameTagEmblem.new()
		label.add_child(emblem)
	emblem.show_beside(label, hash)
	return emblem


static func under(entity_name: String, guild_line: String) -> String:
	if guild_line.is_empty():
		return entity_name
	return "%s\n%s" % [entity_name, guild_line]
