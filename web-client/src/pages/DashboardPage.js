import { jsx as _jsx } from "react/jsx-runtime";
import { useTranslation } from 'react-i18next';
import { Typography } from 'antd';
/** Dashboard — landing page of the application. */
export function DashboardPage() {
    const { t } = useTranslation();
    return (_jsx(Typography.Title, { level: 3, children: t('dashboard.title') }));
}
