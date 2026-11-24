extends ItemUse
class_name BestiaLedgerUse

## Opening the ledger asks the server for the storage; its answer opens the storage window. The ledger stays in
## the bag: the GDScript half of net.bestia.zone.bestiastorage.BestiaLedgerScript, which refuses a plain use.


func on_item_used(_item: ItemResource) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.open_bestia_storage()
