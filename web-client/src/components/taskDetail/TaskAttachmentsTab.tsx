import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, List, Popconfirm, Space, Typography } from 'antd';
import { DeleteOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { useTaskAttachments } from '../../hooks/useTaskAttachments';

type Props = ReturnType<typeof useTaskAttachments>;

/** Lists task attachments and provides upload/delete controls. */
export function TaskAttachmentsTab({ attachments, uploading, error, handleUpload, handleDelete }: Props) {
  const { t }     = useTranslation();
  const fileInput = useRef<HTMLInputElement>(null);

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

      <List
        dataSource={attachments}
        locale={{ emptyText: t('tasks.noAttachments') }}
        renderItem={(a) => (
          <List.Item
            key={a.id}
            actions={[
              <a
                href={`/api/v1/files/${a.fileId}/download`}
                target="_blank"
                rel="noreferrer"
              >
                <DownloadOutlined /> {t('tasks.download')}
              </a>,
              <Popconfirm
                title={t('tasks.confirmDeleteAttachment')}
                onConfirm={() => handleDelete(a.id)}
                okText={t('common.delete')}
                cancelText={t('common.cancel')}
              >
                <Button danger size="small" icon={<DeleteOutlined />}>
                  {t('tasks.deleteAttachment')}
                </Button>
              </Popconfirm>,
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

      <input
        ref={fileInput}
        type="file"
        style={{ display: 'none' }}
        onChange={onFileChange}
      />
      <Button
        icon={<UploadOutlined />}
        loading={uploading}
        onClick={() => fileInput.current?.click()}
        style={{ marginTop: 12 }}
      >
        {t('tasks.uploadAttachment')}
      </Button>
    </>
  );
}
