/**
 * This message is send by the server if some data regarding a bestia are
 * changing. We will take the data and update the view model with all the
 * information.
 * 
 * @module Server.Config
 */
(function(app, $, ko) {
	'use strict';
	
	
	function StatusPointViewModel(msg) {
		this.curMana = ko.observable(0);
		this.maxMana = ko.observable(0);
		this.curHp = ko.observable(0);
		this.maxHp = ko.observable(0);
		this.manaWidth = ko.computed(function() {
			return (this.maxMana() === 0) ? '0%' : (Math.floor(this.curMana() * 100 / this.maxMana())) + '%';
	    }, this);
		this.hpWidth = ko.computed(function() {
			return (this.maxHp() === 0) ? '0%' : (Math.floor(this.curHp() * 100 / this.maxHp())) + '%';
	    }, this);
		
		
		if(msg !== undefined) {
			this.update(msg);
		}
	}
	StatusPointViewModel.prototype.update = function(msg) {
		this.curMana(msg.cMana);
		this.maxMana(msg.mMana);
		this.curHp(msg.cHp);
		this.maxHp(msg.mHp);
	};
	
	function BestiaViewModel(msg) {
		this.playerBestiaId = ko.observable();
		this.sprite = ko.observable();
		this.equip = [];
		this.location = ko.observable();
		this.customName = ko.observable('');
		this.name = ko.observable();
		this.statusEffects = [];
		this.image = ko.observable();
		this.slot = ko.observable();
		this.statusPoints = new StatusPointViewModel();

		if (msg !== undefined) {
			this.update(msg);
			this.statusPoints.update(msg.sp);
		}
	}
	BestiaViewModel.prototype.update = function(msg) {
		var self = this;
		this.playerBestiaId(msg.pbid);
		this.sprite(app.server.Config.makeUrl('sprite', msg.s));
		//this.equip;
		this.location('');
		this.customName(msg.cn);
		this.name(msg.bdbn);
		this.statusEffects = [];
		this.image(app.server.Config.makeUrl('image', msg.img));
		this.slot(msg.sl);
		this.statusPoints.update(msg.sp);
		
		self.selectBestia = function(bestia) {
            console.log(bestia);
        };
	};
	
	
	
	function BestiaInfoViewModel() {
		this.masterBestia = new BestiaViewModel();
		this.selectedBestia = new BestiaViewModel();
		this.bestias = ko.observableArray([]);
		this.slots = ko.observable();
	}
	BestiaInfoViewModel.prototype.update = function(msg) {
		this.selectedBestia.update(msg.bm);
		
		// Add as many
		//this.bestias(msg.b);
		/*for(var i = msg.s - msg.b.length; i > 0; i--) {
			//this.bestias.push({});
		}*/
		var self = this;
		ko.utils.arrayForEach(msg.b, function(bestia) {
			var model = new BestiaViewModel();
			model.update(bestia);
            self.bestias.push(model); 
        });
		
		this.slots(msg.s);
	};
	

	var bestiaInfo = new BestiaInfoViewModel();

	// Apply bindings AFTER the DOM has loaded.
	$(document).ready(function() {
		ko.applyBindings(bestiaInfo, $('#navi').get(0));
	});


	/**
	 * Handler to fill out the data. We get this data from the bestia.update
	 * messages.
	 */
	var onMessageHandler = function(_, msg) {

		bestiaInfo.update(msg);
		
	};

	// Register for messages.
	$.subscribe('bestia.info', onMessageHandler);
})(Bestia, jQuery, ko);