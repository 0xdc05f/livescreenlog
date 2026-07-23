import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import typescript from '@rollup/plugin-typescript';
import dts from 'rollup-plugin-dts';
import fs from 'fs';

const packageJson = JSON.parse(fs.readFileSync('./package.json', 'utf8'));

const injectVersion = {
  name: 'inject-version',
  renderChunk(code) {
    return {
      code: code.replace(/__SDK_VERSION__/g, JSON.stringify(packageJson.version)),
      map: null,
    };
  },
};

export default [
  {
    input: 'src/index.ts',
    output: [
      {
        file: packageJson.main,
        format: 'cjs',
        sourcemap: true,
      },
      {
        file: packageJson.module,
        format: 'esm',
        sourcemap: true,
      },
      {
        file: packageJson.browser,
        format: 'umd',
        name: 'LiveScreenLogUMD',
        sourcemap: true,
        exports: 'named',
        footer: 'if (typeof window !== "undefined" && window.LiveScreenLogUMD && window.LiveScreenLogUMD.LiveScreenLog) { window.LiveScreenLog = window.LiveScreenLogUMD.LiveScreenLog; }',
      },
    ],
    plugins: [
      resolve(),
      commonjs(),
      typescript({ tsconfig: './tsconfig.json' }),
      injectVersion,
    ],
  },
  {
    input: 'src/index.ts',
    output: [{ file: packageJson.types, format: 'esm' }],
    plugins: [dts()],
  },
];
