/**
 * Loading and standby state.
 */
export default {
    create: function() {
        let img = this.add.image(10, 200, 'logo');
        img.setOrigin(0, 0);
    },

    files: [
        { type: 'image', key: 'logo', url: 'assets/img/bestia_logo_big.png' }
    ]
};