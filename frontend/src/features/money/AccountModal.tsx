import { Form, Input, InputNumber, Modal, Select, message } from 'antd'
import { useCreateAccountMutation } from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { useAppSelector } from '@/app/hooks'
import { DynamicIcon } from '@/components/ui'

const ICONS = ['wallet', 'banknote', 'landmark', 'credit-card', 'piggy-bank', 'smartphone', 'trending-up', 'coins']
const CURRENCIES = ['USD', 'EUR', 'GBP', 'CHF', 'VND', 'JPY', 'SGD', 'AUD', 'CAD', 'INR']

export function AccountModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm()
  const baseCurrency = useAppSelector((state) => state.auth.user?.baseCurrency ?? 'USD')
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
          currency: baseCurrency,
          icon: 'wallet',
          openingBalance: 0,
        }}
      >
        <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name the account' }]}>
          <Input placeholder="Main current account" autoFocus maxLength={80} />
        </Form.Item>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
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

          <Form.Item name="currency" label="Currency">
            <Select showSearch options={CURRENCIES.map((code) => ({ value: code, label: code }))} />
          </Form.Item>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item
            name="openingBalance"
            label="Starting balance"
            tooltip="What is in the account right now."
          >
            <InputNumber style={{ width: '100%' }} step={10} />
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
            <InputNumber style={{ width: '100%' }} min={0} step={100} />
          </Form.Item>
        )}
      </Form>
    </Modal>
  )
}
