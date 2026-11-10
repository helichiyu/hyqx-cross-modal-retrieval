import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

/**
 * ESLint 9 flat config。
 *
 * 按文件类型分别配置解析器：`.vue` 用 vue-eslint-parser + TS 子解析器，
 * `.ts` 只用 typescript-eslint；否则 TS 解析器会抢走 `.vue` 导致解析报错。
 */
export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**'] },
  {
    files: ['**/*.vue'],
    // 顺序不能反：后应用的配置覆盖 parser，vue-eslint-parser 必须在最后生效
    extends: [...tseslint.configs.recommended, ...pluginVue.configs['flat/recommended']],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/require-default-prop': 'off',
    },
  },
  {
    files: ['**/*.ts'],
    extends: [...tseslint.configs.recommended],
  },
)
