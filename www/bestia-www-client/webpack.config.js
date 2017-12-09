'use strict';

const path = require('path');
const webpack = require('webpack');
const CleanWebpackPlugin = require('clean-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

const OUT_DIR = './dist';

module.exports = {
    context: path.resolve(__dirname),
    entry: './src/js/main.js',
    devtool: 'source-map',
    output: {
        filename: 'behemoth.bundle.js',
        path: path.resolve(__dirname, OUT_DIR)
    },
    devServer: {
        contentBase: OUT_DIR
    },
    module: {
        rules: [
            {test: /\.css$/, use: ['style-loader', 'css-loader']},
            {test: /\.scss$/, use: ['style-loader', 'css-loader', 'sass-loader']}
    ]
    },
    plugins: [
        new webpack.DefinePlugin({
            'CANVAS_RENDERER': JSON.stringify(false),
            'WEBGL_RENDERER': JSON.stringify(true)
        }),
        new CleanWebpackPlugin([OUT_DIR]),
        new CopyWebpackPlugin([
            { from: '../game-data', to: 'assets' },
            { from: './src', ignore: ['js/**/*.js', 'js/**/*.json']}
        ])
    ]
};