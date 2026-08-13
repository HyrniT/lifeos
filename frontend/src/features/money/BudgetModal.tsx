import { Form, Input, Modal, Select, Slider, message } from 'antd'
import { useCategoriesQuery, useCreateBudgetMutation } from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { DynamicIcon, MoneyInput } from '@/components/ui'

export function BudgetModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm()
  const { data: categories = [] } = useCategoriesQuery({ kind: 'EXPENSE' })
  const [create, { isLoading }] = useCreateBudgetMutation()

  const onSubmit = async (values: Record<string, unknown>) => {
    try {
      await create(values).unwrap()
      message.success('Budget created')
      form.resetFields()
      onClose()
    } catch (err) {
      message.error(errorMessage(err, 'Could not create the budget'))
    }
  }

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title="New budget"
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
        initialValues={{ period: 'MONTHLY', alertThreshold: 80 }}
      >
        <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name the budget' }]}>
          <Input placeholder="Eating out" autoFocus maxLength={80} />
        </Form.Item>

        <Form.Item
          name="categoryId"
          label="Category"
          tooltip="Leave empty to cap all spending rather than one category."
        >
          <Select
            allowClear
            placeholder="All spending"
            options={categories.map((category) => ({
              value: category.id,
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                  <DynamicIcon name={category.icon} size={14} />
                  {category.name}
                </span>
              ),
            }))}
          />
        </Form.Item>

        <div className="lo-form-grid">
          <Form.Item
            name="amount"
            label="Limit"
            rules={[{ required: true, message: 'Set a limit' }]}
          >
            <MoneyInput min={0} placeholder="500.000" />
          </Form.Item>

          <Form.Item name="period" label="Period">
            <Select
              options={[
                { value: 'WEEKLY', label: 'Weekly' },
                { value: 'MONTHLY', label: 'Monthly' },
                { value: 'QUARTERLY', label: 'Quarterly' },
                { value: 'YEARLY', label: 'Yearly' },
              ]}
            />
          </Form.Item>
        </div>

        <Form.Item
          name="alertThreshold"
          label="Warn me at"
          tooltip="The point where the budget flips from on-track to warning."
        >
          <Slider min={50} max={100} step={5} marks={{ 50: '50%', 80: '80%', 100: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
