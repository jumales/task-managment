/**
 * Extracts the backend error message from an Axios error response.
 * Falls back to the provided fallback string when no message is present.
 */
export function getApiErrorMessage(err: unknown, fallback: string): string {
  return (
    (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
  );
}
