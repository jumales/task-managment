import { Alert, Modal, Select, Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import type { TaskPhaseResponse } from '../../api/types';
import { resolvePhaseLabel } from '../../utils/phaseUtils';

interface Props {
  open:             boolean;
  onClose:          () => void;
  phases:           TaskPhaseResponse[];
  loadingPhases:    boolean;
  selectedPhaseId:  string | null;
  onSelectPhase:    (id: string) => void;
  saving:           boolean;
  onSave:           () => void;
  error:            string | null;
}

/** Modal for selecting a new phase for a task from the project's available phases. */
export function TaskPhaseChangeModal({
  open, onClose, phases, loadingPhases, selectedPhaseId, onSelectPhase, saving, onSave, error,
}: Props) {
  const { t } = useTranslation();

  return (
    <Modal
      title={t('tasks.changePhase')}
      open={open}
      onCancel={onClose}
      onOk={onSave}
      okText={t('common.save')}
      cancelText={t('common.cancel')}
      confirmLoading={saving}
      okButtonProps={{ disabled: !selectedPhaseId }}
    >
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      {loadingPhases ? (
        <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
      ) : (
        <Select
          style={{ width: '100%' }}
          placeholder={t('tasks.selectPhase')}
          value={selectedPhaseId ?? undefined}
          onChange={onSelectPhase}
          options={phases.map((p) => ({ value: p.id, label: resolvePhaseLabel(p) }))}
        />
      )}
    </Modal>
  );
}
