import { useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Divider, List, Popconfirm, Space, Typography, Tooltip } from 'antd';
import { DeleteOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { useTaskAttachments } from '../../hooks/useTaskAttachments';
import apiClient from '../../api/client';

const PAGE_SIZE = 5;

type Props = ReturnType<typeof useTaskAttachments> & {
  /** When true (supervisor role), upload and delete controls are hidden. */
  readOnly?: boolean;
};

/** Lists task attachments with upload control on top and paginated list below. Upload and delete are hidden for read-only (supervisor) users. */
export function TaskAttachmentsTab({ attachments, uploading, error, handleUpload, handleDelete, readOnly }: Props) {
  const { t }     = useTranslation();
  const fileInput = useRef<HTMLInputElement>(null);

  const sorted = useMemo(
    () => [...attachments].sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt)),
    [attachments],
  );

  const handleDownload = (fileId: string, fileName: string) => {
    apiClient.get<Blob>(`/api/v1/files/${fileId}/download`, { responseType: 'blob' })
      .then((response) => {
        const url = URL.createObjectURL(response.data);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.click();
        URL.revokeObjectURL(url);
      });
  };

  const onFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      handleUpload(file);
      // Reset so the same file can be re-uploaded if needed
      e.target.value = '';
    }
  };

  return (
    <>
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}

      <input
        ref={fileInput}
        type="file"
        style={{ display: 'none' }}
        onChange={onFileChange}
      />
      {!readOnly && (
        <Button
          icon={<UploadOutlined />}
          loading={uploading}
          onClick={() => fileInput.current?.click()}
          style={{ marginBottom: 12 }}
        >
          {t('tasks.uploadAttachment')}
        </Button>
      )}

      <Divider style={{ marginTop: 0, marginBottom: 8 }} />

      <List
        dataSource={sorted}
        locale={{ emptyText: t('tasks.noAttachments') }}
        pagination={sorted.length > PAGE_SIZE ? { pageSize: PAGE_SIZE, size: 'small', hideOnSinglePage: true } : false}
        renderItem={(a) => (
          <List.Item
            key={a.id}
            actions={[
              <Tooltip title={t('tasks.download')}>
                <Button
                  size="small"
                  type="text"
                  icon={<DownloadOutlined />}
                  onClick={() => handleDownload(a.fileId, a.fileName)}
                />
              </Tooltip>,
              ...(!readOnly ? [
                <Popconfirm
                  key="delete"
                  title={t('tasks.confirmDeleteAttachment')}
                  onConfirm={() => handleDelete(a.id)}
                  okText={t('common.delete')}
                  cancelText={t('common.cancel')}
                >
                  <Tooltip title={t('tasks.deleteAttachment')}>
                    <Button danger size="small" type="text" icon={<DeleteOutlined />} />
                  </Tooltip>
                </Popconfirm>,
              ] : []),
            ]}
          >
            <List.Item.Meta
              title={a.fileName}
              description={
                <Space>
                  <Typography.Text type="secondary">{a.contentType}</Typography.Text>
                  <Typography.Text type="secondary">·</Typography.Text>
                  <Typography.Text type="secondary">{a.uploadedByUserName}</Typography.Text>
                  <Typography.Text type="secondary">·</Typography.Text>
                  <Typography.Text type="secondary">{new Date(a.uploadedAt).toLocaleString()}</Typography.Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </>
  );
}
