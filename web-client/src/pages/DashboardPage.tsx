import { useTranslation } from 'react-i18next';
import { Typography } from 'antd';

/** Dashboard — landing page of the application. */
export function DashboardPage() {
  const { t } = useTranslation();
  return (
    <Typography.Title level={3}>{t('dashboard.title')}</Typography.Title>
  );
}
