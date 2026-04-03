# React Best Practices Analysis

Analyze the React frontend in `web-client/src/` against the Vercel React Best Practices guide at `.claude/react-best-practices.md`. Do not guess — read the actual files before reporting.

## Scope

All files under `web-client/src/`:
- Components (`components/`, `pages/`)
- Hooks (`hooks/`)
- API layer (`api/`)
- Auth (`auth/`)
- App entry (`App.js`, `main.js`)

## Rule Categories (in priority order)

### 1 — Eliminating Waterfalls (CRITICAL)
- Are independent API calls parallelized with `Promise.all`?
- Are `await` calls deferred until their result is actually needed?
- Are there sequential fetches that could run in parallel?

### 2 — Bundle Size Optimization (CRITICAL)
- Are barrel file imports used? (e.g., `import { X } from '../components'`)
- Are heavy components loaded with dynamic imports / lazy loading?
- Are third-party analytics/logging libraries deferred after hydration?

### 3 — Client-Side Data Fetching (MEDIUM-HIGH)
- Is there any deduplication of identical API calls (SWR / React Query / manual)?
- Are global event listeners deduplicated?
- Is localStorage data versioned and minimized?

### 4 — Re-render Optimization (MEDIUM)
- Are non-primitive values (objects, arrays, functions) passed as props without memoization?
- Are components defined inside other components (causes remount on every render)?
- Is `useEffect` used for state that could be derived during render?
- Are effect dependencies using primitive values or object references?
- Is `functional setState` used where the new state depends on the previous state?
- Is `useState` initialized with a function for expensive computations?

### 5 — Rendering Performance (MEDIUM)
- Is static JSX hoisted outside components?
- Are `&&` conditionals used where the left side can be `0` (use ternary instead)?
- Are `content-visibility` or virtualization used for long lists?

### 6 — JavaScript Performance (LOW-MEDIUM)
- Are `Map`/`Set` used for repeated O(1) lookups instead of `.find()`/`.filter()`?
- Are multiple `.filter().map()` chains combined into a single `.reduce()` or loop?
- Are `RegExp` objects created inside loops?

## Instructions

1. Read the relevant source files before evaluating each category.
2. For each category report:
   - **Status**: PASS / FAIL / WARN
   - **Finding**: what was found (or confirmed safe)
   - **File**: exact file path and line number(s)
   - **Recommendation**: specific fix with code example if FAIL or WARN
3. End with a **priority action list** ordered: FAIL → WARN → PASS.
4. For every FAIL, show the before/after code change.

Reference guide: `.claude/react-best-practices.md`
