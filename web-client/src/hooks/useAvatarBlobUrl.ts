import { useEffect, useState } from 'react';
import { downloadFile } from '../api/userApi';

/**
 * Asynchronously downloads the file identified by fileId through the API gateway
 * and returns a local blob URL for use as an <img> src.
 *
 * Returns null while loading or if fileId is null.
 * Revokes the blob URL automatically when the component unmounts or fileId changes.
 */
export function useAvatarBlobUrl(fileId: string | null): string | null {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!fileId) {
      setBlobUrl(null);
      return;
    }

    let currentUrl: string | null = null;

    downloadFile(fileId)
      .then((url) => {
        currentUrl = url;
        setBlobUrl(url);
      })
      .catch(() => setBlobUrl(null));

    return () => {
      if (currentUrl) URL.revokeObjectURL(currentUrl);
    };
  }, [fileId]);

  return blobUrl;
}
