extends ItemUse
class_name FoundersSealUse

## Founding a guild needs a name, so using the seal asks for one rather than going to the server: the guild
## window opens its naming popup and sends the founding request itself. The seal leaves the bag when the server
## has founded the guild - a taken name keeps it.
##
## The GDScript half of net.bestia.zone.guild.FoundersSealScript, which refuses a plain use.


func on_item_used(_item: ItemResource) -> void:
	var guild := GuildPanel.get_instance()
	if guild == null:
		printerr("FoundersSealUse: no guild window to found a guild from")
		return

	guild.ask_to_found()
