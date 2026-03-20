import '@testing-library/jest-dom';

// ---------------------------------------------------------------------------
// Ant Design / jsdom compatibility shims
// ---------------------------------------------------------------------------

// Ant Design's responsive Table/Pagination uses window.matchMedia, which is
// not implemented in jsdom. Provide a minimal stub so it doesn't throw.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});
