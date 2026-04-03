import { jsx as _jsx } from "react/jsx-runtime";
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Spin } from 'antd';
import keycloak from './auth/keycloak';
import { AuthProvider } from './auth/AuthProvider';
import App from './App';
import './i18n';
// Initialize Keycloak BEFORE React renders to avoid double-init from StrictMode.
// onLoad: 'login-required' redirects to Keycloak login if not authenticated.
createRoot(document.getElementById('root')).render(_jsx(Spin, { fullscreen: true, tip: "Authenticating..." }));
keycloak
    .init({ onLoad: 'login-required', pkceMethod: 'S256' })
    .then(() => {
    createRoot(document.getElementById('root')).render(_jsx(StrictMode, { children: _jsx(AuthProvider, { children: _jsx(App, {}) }) }));
})
    .catch(() => keycloak.login());
