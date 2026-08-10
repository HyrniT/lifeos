import { useEffect } from 'react'
import { DatePicker, Form, Input, InputNumber, Modal, Segmented, Select, message } from 'antd'
import dayjs from 'dayjs'
import {
  useAccountsQuery,
  useCategoriesQuery,
  useCreateTransactionMutation,
  useUpdateTransactionMutation,
} from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { DynamicIcon } from '@/components/ui'
import type { Transaction, TxType } from '@/types'

export function TransactionModal({
  open,
  transaction,
  onClose,
}: {
  open: boolean
  transaction: Transaction | null
  onClose: () => void
}) {
  const [form] = Form.useForm()
  const type: TxType = Form.useWatch('type', form) ?? 'EXPENSE'

  const { data: accounts = [] } = useAccountsQuery()
  const { data: categories = [] } = useCategoriesQuery()
  const [create, { isLoading: creating }] = useCreateTransactionMutation()
  const [update, { isLoading: updating }] = useUpdateTransactionMutation()

  useEffect(() => {
    if (!open) return
    if (transaction) {
      form.setFieldsValue({ ...transaction, occurredOn: dayjs(transaction.occurredOn) })
    } else {
      form.resetFields()
      form.setFieldsValue({
        type: 'EXPENSE',
        occurredOn: dayjs(),
        accountId: accounts[0]?.id,
      })
    }
  }, [open, transaction, accounts, form])

  // Income and expense draw from different category sets; showing all of them
  // is how "Salary" ends up filed as an expense.
  const relevantCategories = categories.filter((category) =>
    type === 'INCOME' ? category.kind === 'INCOME' : category.kind === 'EXPENSE',
  )

  const onSubmit = async (values: Record<string, unknown>) => {
    const payload = {
      ...values,
      occurredOn: (values.occurredOn as dayjs.Dayjs).format('YYYY-MM-DD'),
      categoryId: values.type === 'TRANSFER' ? null : values.categoryId,
      toAccountId: values.type === 'TRANSFER' ? values.toAccountId : null,
    }
    try {
      if (transaction) {
        await update({ id: transaction.id, patch: payload as Partial<Transaction> }).unwrap()
        message.success('Transaction updated')
      } else {
        await create(payload as Partial<Transaction>).unwrap()
        message.success('Transaction added')
      }
      onClose()
    } catch (err) {
      message.error(errorMessage(err, 'Could not save the transaction'))
    }
  }

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={transaction ? 'Edit transaction' : 'Add transaction'}
      okText={transaction ? 'Save' : 'Add'}
      confirmLoading={creating || updating}
      onOk={() => form.submit()}
      width={520}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" onFinish={onSubmit} requiredMark={false}>
        <Form.Item name="type" label="Type">
          <Segmented
            block
            options={[
              { label: 'Expense', value: 'EXPENSE' },
              { label: 'Income', value: 'INCOME' },
              { label: 'Transfer', value: 'TRANSFER' },
            ]}
          />
        </Form.Item>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item
            name="amount"
            label="Amount"
            rules={[
              { required: true, message: 'How much?' },
              {
                type: 'number',
                min: 0.0001,
                message: 'Amount must be greater than zero',
              },
            ]}
          >
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              step={0.01}
              autoFocus
              placeholder="0.00"
            />
          </Form.Item>

          <Form.Item
            name="occurredOn"
            label="Date"
            rules={[{ required: true, message: 'Pick a date' }]}
          >
            <DatePicker style={{ width: '100%' }} format="D MMM YYYY" />
          </Form.Item>
        </div>

        <Form.Item
          name="accountId"
          label={type === 'TRANSFER' ? 'From account' : 'Account'}
          rules={[{ required: true, message: 'Choose an account' }]}
        >
          <Select
            options={accounts.map((account) => ({
              value: account.id,
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                  <DynamicIcon name={account.icon} size={14} />
                  {account.name}
                </span>
              ),
            }))}
          />
        </Form.Item>

        {type === 'TRANSFER' ? (
          <Form.Item
            name="toAccountId"
            label="To account"
            dependencies={['accountId']}
            rules={[
              { required: true, message: 'Choose a destination' },
              ({ getFieldValue }) => ({
                validator(_rule, value) {
                  if (!value || value !== getFieldValue('accountId')) return Promise.resolve()
                  return Promise.reject(new Error('Pick a different account'))
                },
              }),
            ]}
          >
            <Select options={accounts.map((a) => ({ value: a.id, label: a.name }))} />
          </Form.Item>
        ) : (
          <Form.Item name="categoryId" label="Category">
            <Select
              allowClear
              showSearch
              optionFilterProp="title"
              options={relevantCategories.map((category) => ({
                value: category.id,
                title: category.name,
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <DynamicIcon name={category.icon} size={14} />
                    {category.name}
                  </span>
                ),
              }))}
            />
          </Form.Item>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item name="merchant" label="Merchant">
            <Input placeholder="Where?" maxLength={120} />
          </Form.Item>
          <Form.Item name="note" label="Note">
            <Input placeholder="Optional" maxLength={500} />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  )
}
