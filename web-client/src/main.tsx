import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Spin } from 'antd';
import keycloak from './auth/keycloak';
import { AuthProvider } from './auth/AuthProvider';
import App from './App';
import './i18n';

// Initialize Keycloak BEFORE React renders to avoid double-init from StrictMode.
// onLoad: 'login-required' redirects to Keycloak login if not authenticated.
// Reuse the same root — React 18.3 throws if createRoot is called twice on the same element.
const root = createRoot(document.getElementById('root')!);
root.render(<Spin fullscreen tip="Authenticating..." />);

keycloak
  .init({ onLoad: 'login-required', pkceMethod: 'S256' })
  .then(() => {
    root.render(
      <StrictMode>
        <AuthProvider>
          <App />
        </AuthProvider>
      </StrictMode>,
    );
  })
  .catch(() => keycloak.login());
