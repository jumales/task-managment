# Vite Dev Server — Common Issues and Resolutions

## 1. White screen / 504 Outdated Optimize Dep

**Symptom**: Browser shows a white screen; console logs:
```
Failed to load resource: the server responded with a status of 504 (Outdated Optimize Dep)
```

**Cause**: Vite's dependency pre-bundling cache (stored in `node_modules/.vite`) becomes stale.
This happens when:
- A new npm dependency is added
- The Vite process is killed and restarted without the cache being invalidated
- Multiple Vite instances were running simultaneously and left conflicting caches

**Resolution**:
```bash
# 1. Kill all Vite / esbuild processes
pkill -9 -f "vite"
pkill -9 -f "esbuild"
lsof -ti:3000 | xargs kill -9 2>/dev/null || true

# 2. Clear the Vite cache
rm -rf web-client/node_modules/.vite

# 3. Restart the web client
./scripts/start-dev.sh --restart web-client
```

---

## 2. Multiple Vite instances running simultaneously

**Symptom**: White screen, stale content, or 504 errors even after restarting once.

**Cause**: The `--restart` command in `start-dev.sh` kills the Terminal window by title match,
but on macOS, Terminal window titles are not always reliably updated. This can leave old
Vite processes (including esbuild workers) running in the background, each with their own
cache. The browser hits whichever instance responds first — often the stale one.

**How to check**:
```bash
ps aux | grep -E "vite|esbuild" | grep -v grep
```

**Resolution**: Kill every Vite/esbuild process by name, not by port, then restart:
```bash
pkill -9 -f "vite"
pkill -9 -f "esbuild"
rm -rf web-client/node_modules/.vite
./scripts/start-dev.sh --restart web-client
```

The `start-dev.sh` full-start (without `--restart`) already includes this cleanup step.

---

## 3. Stale compiled `.js` files shadowing `.tsx` source

**Symptom**: Code changes to `.tsx` files have no effect; the app behaves as if running
an older version.

**Cause**: If `tsc` (TypeScript compiler) was run manually or via a build step, it emits
`.js` files next to the `.tsx` source files (e.g. `TasksPage.js` alongside `TasksPage.tsx`).
Vite resolves `.js` before `.tsx` by default, so it serves the compiled — and stale — output
instead of the source.

**How to check**:
```bash
find web-client/src -name "*.js" ! -name "*.test.js"
```

**Resolution**: Delete all compiled `.js` files from `src/`:
```bash
find web-client/src -name "*.js" ! -name "*.test.js" -delete
rm -rf web-client/node_modules/.vite
./scripts/start-dev.sh --restart web-client
```

**Prevention**: Never run `tsc` directly in the `web-client` directory during development.
Use `npm run dev` (Vite) only. Add `src/**/*.js` to `.gitignore` if this keeps happening.
