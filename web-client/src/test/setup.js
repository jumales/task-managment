import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import '../i18n';
// Ensure React Testing Library cleans up the DOM after each test
afterEach(() => {
    cleanup();
});
// ---------------------------------------------------------------------------
// Ant Design / jsdom compatibility shims
// ---------------------------------------------------------------------------
// Ant Design's responsive Table/Pagination uses window.matchMedia, which is
// not implemented in jsdom. Provide a minimal stub so it doesn't throw.
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => { },
        removeListener: () => { },
        addEventListener: () => { },
        removeEventListener: () => { },
        dispatchEvent: () => false,
    }),
});
