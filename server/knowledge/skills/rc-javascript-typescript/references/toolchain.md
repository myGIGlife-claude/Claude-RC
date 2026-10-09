# JS/TS toolchain: versions and config snippets (as of 2026-10)

Versions from registry.npmjs.org `dist-tags.latest`, accessed 2026-10-09.

| Tool | Latest | Major released | Notes |
|---|---|---|---|
| typescript | 7.0.2 | 7.0: 2026-07-08 | Native (Go). `@typescript/typescript6` 6.0.2 = JS compiler + API, binary `tsc6` |
| eslint | 10.12.0 | 10.0: 2026-02-06 | Node `^20.19 \|\| ^22.13 \|\| >=24`, flat config only |
| typescript-eslint | 8.71.1 | | peer TS `<6.1.0` |
| @biomejs/biome | 2.5.15 | 2.0: 2025-06-17 | lint + format, one binary |
| oxlint / oxfmt | 1.87.0 / 0.72.0 | oxlint 1.0: 2025-06-10; oxfmt beta 2026-02-24 | Rust; oxfmt targets Prettier output parity |
| prettier | 3.9.9 | | |
| vitest | 5.0.3 | 5.0: 2026-09-03 | Node `^22.12 \|\| ^24 \|\| >=26` |
| @playwright/test | 1.64.0 | | |
| jest | 30.5.2 | 30.0: 2025-06-10 | legacy choice for new code |
| vite | 8.3.4 | 8.0: 2026-03-12 | Rolldown bundler |
| rolldown | 1.2.13 | 1.0: 2026-05-07 | |
| tsdown | 0.23.0 | | library bundler on Rolldown, replaces tsup |
| tsup | 8.5.1 | last release 2025-11-12 | unmaintained |
| esbuild | 0.28.2 | | still pre-1.0 |
| @swc/core | 1.16.13 | | |
| tsx | 4.23.15 | | runs TS incl. non-erasable syntax |
| npm | 12.2.0 | 12.0: 2026-07-08 | Node `^22.22.2 \|\| ^24.15 \|\| >=26` |
| pnpm | 12.10.1 | 11.0: 2026-04-28, 12.0: 2026-08-26 | 11 needs Node 22+; 12 is a Rust rewrite |
| yarn (berry) | 4.18.1 | | Corepack/`packageManager` |
| bun | 1.4.2 | 1.4.0: 2026-08-20 | |
| turbo / nx | 2.11.7 / 23.3.0 | | |
| zod / valibot / arktype | 4.6.5 / 1.5.0 / 2.2.8 | zod 4.0: 2025-07-09; valibot 1.0: 2025-03-19 | Standard Schema |
| temporal-polyfill | 1.0.5 | | `@js-temporal/polyfill` 0.5.1 (2025-03) is the champions' reference |

## eslint.config.js (ESLint 10 + typescript-eslint)
```js
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import { defineConfig } from "eslint/config";

export default defineConfig(
  { ignores: ["dist/"] },
  js.configs.recommended,
  tseslint.configs.strictTypeChecked,
  { languageOptions: { parserOptions: { projectService: true } } },
);
```
- ESLint 10 looks up `eslint.config.*` from each linted file's directory (monorepo-friendly), not just the cwd.
- Type-aware rules need the TS 6 API: with TS 7 installed, alias `typescript` to `@typescript/typescript6` until typescript-eslint supports 7.1+.

## pnpm-workspace.yaml (pnpm 11+; `.npmrc` is now auth/registry only)
```yaml
packages: ["packages/*", "apps/*"]
minimumReleaseAge: 1440        # default in 11+; minutes
blockExoticSubdeps: true       # default in 11+
trustPolicy: no-downgrade
allowBuilds:
  esbuild: true
  "@swc/core": true
```
- pnpm 11 env vars are `pnpm_config_*`; `onlyBuiltDependencies` and friends are replaced by `allowBuilds`.
- pnpm 12: unknown keys in `pnpm-workspace.yaml` fail (`ERR_PNPM_UNRECOGNIZED_WORKSPACE_SETTINGS`) when the project pins a pnpm version the running pnpm satisfies, else warn; `--frozen-lockfile false` -> `--no-frozen-lockfile`.

## npm 12 (`.npmrc` + package.json)
```ini
strict-allow-scripts=true
min-release-age=1
```
```json
{ "allowScripts": { "esbuild": true } }
```
(Exact `allowScripts` value shape: check `npm help config` / docs before relying on it; unverified beyond "approvals and denials".)
- `allow-git` / `allow-remote` default `none`; `npm shrinkwrap` removed; unknown config keys now error.

## Trusted publishing (GitHub Actions)
```yaml
permissions: { id-token: write, contents: read }
steps:
  - uses: actions/checkout@v7
  - uses: actions/setup-node@v7
    with: { node-version: 24, registry-url: "https://registry.npmjs.org" }
  - run: npm ci
  - run: npm publish   # OIDC; provenance automatic; no NPM_TOKEN
```
Configure the trusted publisher (repo + workflow file) on npmjs.com first. Needs npm >= 11.5.1 and Node >= 22.14. Latest action majors on 2026-10-09: checkout v7.0.1, setup-node v7.1.0; pin to commit SHAs in practice. Provenance is automatic only for public packages from public repos.

## Library build (tsdown)
```ts
// tsdown.config.ts
import { defineConfig } from "tsdown";
export default defineConfig({ entry: ["src/index.ts"], format: ["esm"], dts: true });
```
Then verify with `npx publint` and `npx @arethetypeswrong/cli --pack`.
