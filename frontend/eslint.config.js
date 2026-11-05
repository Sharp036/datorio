import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

// npm run lint: the recommended JavaScript and TypeScript rules and the rules of React hooks
export default tseslint.config(
  { ignores: ['dist'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      // The two classic hook rules; the React Compiler rules of the recommended set are not enabled
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // A name starting with _ is dropped on purpose: const { key: _, ...rest } = obj
      '@typescript-eslint/no-unused-vars': ['error', {
        argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_', ignoreRestSiblings: true,
      }],
      // set.has(x) ? set.delete(x) : set.add(x) toggles a value
      '@typescript-eslint/no-unused-expressions': ['error', { allowTernary: true }],
      // Chart options and API payloads are typed loosely in places; shown, but not blocking
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
)
