var path = require('path');

module.exports = {
  entry: './js/game.ts',
  devtool: 'source-map',
  mode: 'development',
  output: {
    filename: 'bundle.js',
    path: path.resolve(__dirname, 'build')
  },
  devServer: {
    contentBase: path.resolve(__dirname, './'),
    publicPath: '/build/',
    host: '127.0.0.1',
    port: 8080,
    open: true
  },
  resolve: {
    extensions: ['.ts', '.js'],
    modules: [
      path.resolve('./node_modules'),
      path.resolve('./js')
    ],
    alias: {
      phaser: path.resolve(__dirname, 'node_modules/phaser/dist/phaser.js'),
      Utilities: path.resolve(__dirname, 'node_modules/loglevel/dist/loglevel.js')
    }
  },
  module: {
    rules: [
      { test: /\.ts$/, loader: 'ts-loader', exclude: '/node_modules/' },
      { test: /phaser\.js$/, loader: 'expose-loader?Phaser' }
    ]
  }
};
