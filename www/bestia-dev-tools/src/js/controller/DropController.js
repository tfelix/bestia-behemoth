
export default class DropController {
	constructor() {

	}

	onDrop(data, evt) {
		let that = this;
		evt.stopPropagation();
		evt.preventDefault();

		var files = evt.dataTransfer.files; // FileList object.

		// files is a FileList of File objects. List some properties.
		var output = [];
		for (var i = 0, f; f = files[i]; i++) {

			if (!f.name.match(/\.json$/)) {
				console.error('Drop only JSON files.');
				continue;
			}

			var reader = new FileReader();

			reader.onload = function (content) {

				console.debug('File loaded.');
				var data = JSON.parse(content.target.result);
				that._handleParsedData(data);
			};


			// Read in the image file as a data URL.
			reader.readAsText(f);
		}

	}

	onDragOver(data, evt) {
		evt.stopPropagation();
		evt.preventDefault();
		evt.dataTransfer.dropEffect = 'copy'; // Explicitly show this is a copy.
	}

	_handleParsedData(data) {

	}
};
