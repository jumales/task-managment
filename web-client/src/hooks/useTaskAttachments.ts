import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { uploadFile, addAttachment, deleteAttachment, joinTask } from '../api/taskApi';
import type { TaskAttachmentResponse } from '../api/types';

/** Manages the attachment list, file upload state, and delete handler for a task. */
export function useTaskAttachments(taskId: string | undefined, initialData: TaskAttachmentResponse[]) {
  const { t } = useTranslation();

  const [attachments, setAttachments] = useState<TaskAttachmentResponse[]>(initialData);
  useEffect(() => { setAttachments(initialData); }, [initialData]);

  const [uploading, setUploading] = useState(false);
  const [error,     setError]     = useState<string | null>(null);

  /**
   * Two-step upload: first sends the raw file to file-service, then registers
   * the returned fileId on the task via the task-service attachment endpoint.
   */
  const handleUpload = (file: File) => {
    if (!taskId) return;
    setUploading(true);
    setError(null);
    uploadFile(file)
      .then(({ fileId, contentType }) =>
        addAttachment(taskId, { fileId, fileName: file.name, contentType })
      )
      .then((created) => {
        setAttachments((prev) => [...prev, created]);
        joinTask(taskId);
      })
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedUploadAttachment')))
      .finally(() => setUploading(false));
  };

  /** Hard-deletes the attachment record and its file from storage. */
  const handleDelete = (attachmentId: string) => {
    if (!taskId) return;
    deleteAttachment(taskId, attachmentId)
      .then(() => setAttachments((prev) => prev.filter((a) => a.id !== attachmentId)))
      .catch((err) => setError(err?.response?.data?.message ?? t('tasks.failedDeleteAttachment')));
  };

  return {
    attachments,
    uploading,
    error,
    handleUpload,
    handleDelete,
  };
}
