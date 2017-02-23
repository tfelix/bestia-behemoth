import json from 'rollup-plugin-json';
import babel from 'rollup-plugin-babel';

export default {
  entry: 'src/js/main.js',
  format: 'es',
  sourceMap: true,
  plugins: [json(), babel()],
  dest: 'build/js/app.bundle.js'
};