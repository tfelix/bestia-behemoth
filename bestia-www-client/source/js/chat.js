define(['knockout', 'connection'], function (ko, con) {
	
	// Add startsWith functionality to string prototype.
	if (typeof String.prototype.startsWith != 'function') {
		String.prototype.startsWith = function (str){
			return this.slice(0, str.length) == str;
		};
	}
	
	var Chat = function(){
		this.mode = ko.observable(1);
		this.modeText = ko.computed(function() {
				if(this.mode() == 1) {
					return "Öffentlich";
				}
				if(this.mode() == 2) {
					return "Party";
				}
				if(this.mode() == 3) {
					return "Gilde";
				}
			}, this);
			
		this.messages = ko.observableArray([
    { nickname: "Bungle", message: "Bear du bist ja doof! trolololol.", type: "test" },
    { nickname: "George", message: "ja lass mal looten gehen.", type: "test" },
    { nickname: "Zippy", message: "Das scheint ja echt zu gehe...", type: "test" }
]);
	
		this.identifyLocalCommand = function(str) {
			if(str.startsWith('/s')) {
				// Public chat.
				this.mode(1);
			} else if(str.startsWith('/p')) {
				// Party chat.
				this.mode(2);
			} else if(str.startsWith('/g')) {
				// Guild chat.
				this.mode(3);
			}
		};
		
		this.submitChat = function(formEl) {			
			$(formEl).find('input').val('');
			console.log("Formular abgeschickt.");
		}
	};
	
	return Chat;
});