var sampleEquipItemInfo = {
	ulv : 0,
	f : null,
	bb : 0
};

describe("Bestia.Inventory.EquipItemInfoViewModel", function() {
	
	it("shows upgrade level like +5 and nothing otherwise.", function() {
		var eqii = new Bestia.Inventory.EquipItemInfoViewModel();
		eqii.update(sampleEquipItemInfo);
		expect(eqii.upgradeLevel()).toBe('');
		
		sampleEquipItemInfo.ulv = 5;
		eqii.update(sampleEquipItemInfo);
		expect(eqii.upgradeLevel()).toBe('+5');
	});
	
	it("It is not broken.", function() {
		var eqii = new Bestia.Inventory.EquipItemInfoViewModel();
		eqii.update(sampleEquipItemInfo);
		expect(eqii.bBroken()).toBe(false);
	});
	
	
});