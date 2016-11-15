import WorldHelper from '../map/WorldHelper';

const MIN_SAFETY_TILES = 3;

/**
 * The tile renderer is responsible for controlling and managing the correct
 * rendering of tiles. To perform its duty it needs to tap into the map manager
 * and the tileset manager as well.
 */
export default class TileRenderer {
	
	constructor(game) {
	
		this._game = game;
		
		this._sprite = null;
		
		this._rendered = {x1: 0, x2: 0, y1: 0, y2: 0};
		this._newRendered = {x1: 0, x2: 0, y1: 0, y2: 0};
		
		this._map = game.add.tilemap();
		this._map.addTilesetImage('tilemap');
		this._layer = this._map.create('ground', 90, 60, 32, 32);
		this._layer.resizeWorld();
		this._layer.sendToBack();
		
		this._gameSize = {x: 0, y: 0};
	}
	
	/**
	 * Sets the current player sprite.
	 */
	set playerSprite(sprite) {
		this._sprite = sprite;
	}
	
	/**
	 * Clears the whole screen and setup a complete new rendering from the
	 * current player position.
	 */
	clearDraw() {
		// We must calculate the game size.
		this._gameSize.x = Math.ceil(this._game.width / WorldHelper.TILE_SIZE);
		this._gameSize.y = Math.ceil(this._game.height / WorldHelper.TILE_SIZE);
		
		
		let pos = WorldHelper.getTileXY(this._sprite.x, this._sprite.y);
		let startX = pos.x - WorldHelper.SIGHT_RANGE.x;
		let startY = pos.x - WorldHelper.SIGHT_RANGE.y;
		
		
		for(var x = startX; x < startX + this._gameSize.x; x++) {
			for(var y = startY; y < startY + this._gameSize.y; y++) {
				
				// Austauschen mit echten, tile informationen.
				if(x % 2 == 0) {
					this._map.putTile(30, x, y, 'ground');
				} else {
					this._map.putTile(54, x, y, 'ground');
				}
				
			}
		}
		
		this._rendered = {x1: startX, x2: startX + this._gameSize.x, y1: startY, y2: startY + this._gameSize.y};
	}
	
	/**
	 * Recalculates the current distances from the player to the border and
	 * decides if a re-render of the map is needed.
	 */
	update() {
		let tPos = WorldHelper.getTileXY(this._sprite.x, this._sprite.y);
		
		// Tile distance left.
		let tdLeft = tPos.x - this._rendered.x1;
		let tdRight = this._rendered.x2 - tPos.x;
		let tdTop = tPos.y - this._rendered.y1;
		let tdBottom = this._rendered.y2 - tPos.y;
		
		// Check if we need an extension at the right.
		if(tdRight <= MIN_SAFETY_TILES) {
			let newX2 = this._rendered.x2 + WorldHelper.CHUNK_SIZE;
			let newX1 = this._rendered.x1 + WorldHelper.CHUNK_SIZE;
			
			// Add the new tiles.
			for(let x = this._rendered.x2; x < newX2; x++) {
				for(let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.putTile(30, x, y, this._layer);				
				}
			}
			
			// Remove the old tiles.
			for(let x = this._rendered.x1; x < newX1; x++) {
				for(let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);			
				}
			}
			
			this._rendered.x1 = newX1;
			this._rendered.x2 = newX2;
		}
		
		// Check if we need an extension at the bottom.
		if(tdBottom <= MIN_SAFETY_TILES) {
			let newY2 = this._rendered.y2 + WorldHelper.CHUNK_SIZE;
			let newY1 = this._rendered.y1 + WorldHelper.CHUNK_SIZE;
			
			// Add the new tiles.
			for(let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for(let y = this._rendered.y1; y < newY2; y++) {
					this._map.putTile(30, x, y, this._layer);				
				}
			}
			
			// Remove the old tiles.
			for(let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for(let y = this._rendered.y1; y < newY1; y++) {
					this._map.removeTile(x, y, this._layer);			
				}
			}
			
			this._rendered.y1 = newY1;
			this._rendered.y2 = newY2;
		}

		// Check if we need an extension at the left.
		if(tdLeft <= MIN_SAFETY_TILES) {
			let newX2 = this._rendered.x2 - WorldHelper.CHUNK_SIZE;
			let newX1 = this._rendered.x1 - WorldHelper.CHUNK_SIZE;
			
			// Add the new tiles.
			for(let x = newX1; x < this._rendered.x1; x++) {
				for(let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.putTile(30, x, y, this._layer);				
				}
			}
			
			// Remove the old tiles at the right.
			for(let x = newX2; x < this._rendered.x2; x++) {
				for(let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);			
				}
			}
			
			this._rendered.x1 = newX1;
			this._rendered.x2 = newX2;
		}

		// Check if we need an extension at the top.
		if(tdTop <= MIN_SAFETY_TILES) {
			let newY1 = this._rendered.y1 - WorldHelper.CHUNK_SIZE;
			let newY2 = this._rendered.y2 - WorldHelper.CHUNK_SIZE;
			
			// Add the new tiles.
			for(let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for(let y = newY1; y < this._rendered.y1; y++) {
					this._map.putTile(30, x, y, this._layer);				
				}
			}
			
			// Remove the old tiles.
			for(let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for(let y = newY2; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);			
				}
			}
			
			this._rendered.y1 = newY1;
			this._rendered.y2 = newY2;
		}
	}	
}