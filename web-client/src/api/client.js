import axios from 'axios';
import keycloak from '../auth/keycloak';
/**
 * Axios instance that routes all requests through the API Gateway.
 * Automatically attaches the Bearer token and refreshes it when expired.
 */
const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_URL,
});
// Attach a fresh Bearer token to every outgoing request
apiClient.interceptors.request.use(async (config) => {
    // Refresh token if it expires within the next 30 seconds
    await keycloak.updateToken(30).catch(() => keycloak.login());
    config.headers.Authorization = `Bearer ${keycloak.token}`;
    return config;
});
// Redirect to login on 401 (e.g. token rejected by gateway)
apiClient.interceptors.response.use((response) => response, (error) => {
    if (error.response?.status === 401)
        keycloak.login();
    return Promise.reject(error);
});
export default apiClient;
