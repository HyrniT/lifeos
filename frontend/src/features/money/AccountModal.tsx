import { Form, Input, Modal, Select, message } from 'antd'
import { useCreateAccountMutation } from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { DynamicIcon, MoneyInput } from '@/components/ui'

const ICONS = ['wallet', 'banknote', 'landmark', 'credit-card', 'piggy-bank', 'smartphone', 'trending-up', 'coins']

export function AccountModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm()
  const [create, { isLoading }] = useCreateAccountMutation()
  const type = Form.useWatch('type', form)

  const onSubmit = async (values: Record<string, unknown>) => {
    try {
      await create(values).unwrap()
      message.success('Account created')
      form.resetFields()
      onClose()
    } catch (err) {
      message.error(errorMessage(err, 'Could not create the account'))
    }
  }

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title="New account"
      okText="Create"
      confirmLoading={isLoading}
      onOk={() => form.submit()}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={onSubmit}
        requiredMark={false}
        initialValues={{
          type: 'BANK',
          icon: 'wallet',
        }}
      >
        <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name the account' }]}>
          <Input placeholder="Main current account" autoFocus maxLength={80} />
        </Form.Item>

        <Form.Item name="type" label="Type">
          <Select
            options={[
              { value: 'CASH', label: 'Cash' },
              { value: 'BANK', label: 'Bank' },
              { value: 'CREDIT_CARD', label: 'Credit card' },
              { value: 'E_WALLET', label: 'E-wallet' },
              { value: 'SAVINGS', label: 'Savings' },
              { value: 'INVESTMENT', label: 'Investment' },
              { value: 'LOAN', label: 'Loan' },
            ]}
          />
        </Form.Item>

        <div className="lo-form-grid">
          <Form.Item
            name="openingBalance"
            label="Starting balance"
            tooltip="What is in the account right now."
          >
            <MoneyInput />
          </Form.Item>

          <Form.Item name="icon" label="Icon">
            <Select
              options={ICONS.map((icon) => ({
                value: icon,
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <DynamicIcon name={icon} size={14} />
                    {icon.replace(/-/g, ' ')}
                  </span>
                ),
              }))}
            />
          </Form.Item>
        </div>

        {type === 'CREDIT_CARD' && (
          <Form.Item name="creditLimit" label="Credit limit">
            <MoneyInput min={0} />
          </Form.Item>
        )}
      </Form>
    </Modal>
  )
}
