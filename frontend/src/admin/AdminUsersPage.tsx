import { useState } from 'react'
import { Button, Input, Popconfirm, Select, Space, Switch, Table, Tag, message } from 'antd'
import dayjs from 'dayjs'
import { LogOut, Search, ShieldCheck } from 'lucide-react'
import {
  useAdminForceSignOutMutation,
  useAdminUpdateUserMutation,
  useAdminUsersQuery,
} from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { useAppSelector } from '@/app/hooks'
import { PageHeader } from '@/components/ui'
import type { UserView } from '@/types'

const ROLES = ['USER', 'SUPPORT', 'ADMIN']

export function AdminUsersPage() {
  const me = useAppSelector((state) => state.auth.user)
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)

  const { data, isFetching } = useAdminUsersQuery({ q: query || undefined, page, size: 20 })
  const [updateUser] = useAdminUpdateUserMutation()
  const [forceSignOut] = useAdminForceSignOutMutation()

  const patch = async (id: string, changes: Parameters<typeof updateUser>[0]['patch']) => {
    try {
      await updateUser({ id, patch: changes }).unwrap()
      message.success('User updated')
    } catch (err) {
      message.error(errorMessage(err, 'Could not update the user'))
    }
  }

  return (
    <>
      <PageHeader
        title="User management"
        subtitle="Enable or disable accounts, change roles, and end sessions."
        actions={
          <Input
            allowClear
            prefix={<Search size={15} style={{ color: 'var(--on-surface-muted)' }} />}
            placeholder="Search by name or e-mail"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setPage(0)
            }}
            style={{ width: 280 }}
          />
        }
      />

      <div className="lo-panel">
        <Table<UserView>
          loading={isFetching}
          rowKey="id"
          size="middle"
          scroll={{ x: 900 }}
          dataSource={data?.content ?? []}
          pagination={{
            current: page + 1,
            pageSize: data?.size ?? 20,
            total: data?.totalElements ?? 0,
            showSizeChanger: false,
            onChange: (next) => setPage(next - 1),
          }}
          columns={[
            {
              title: 'User',
              key: 'user',
              render: (_v, row) => (
                <div>
                  <div style={{ fontWeight: 650 }}>
                    {row.displayName}
                    {row.id === me?.id && (
                      <Tag style={{ marginLeft: 8, fontSize: 10 }}>you</Tag>
                    )}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>{row.email}</div>
                </div>
              ),
            },
            {
              title: 'Roles',
              dataIndex: 'roles',
              width: 220,
              render: (roles: string[], row) => (
                <Select
                  mode="multiple"
                  size="small"
                  style={{ width: '100%' }}
                  value={roles}
                  options={ROLES.map((role) => ({ value: role, label: role }))}
                  onChange={(next) => patch(row.id, { roles: next })}
                />
              ),
            },
            {
              title: 'Security',
              key: 'security',
              width: 130,
              responsive: ['lg'],
              render: (_v, row) => (
                <Space size={4}>
                  {row.twoFactorEnabled && (
                    <Tag style={{ margin: 0 }} icon={<ShieldCheck size={11} />}>
                      2FA
                    </Tag>
                  )}
                  {row.emailVerified && <Tag style={{ margin: 0 }}>verified</Tag>}
                </Space>
              ),
            },
            {
              title: 'Last seen',
              dataIndex: 'lastLoginAt',
              width: 150,
              responsive: ['md'],
              render: (value: string | null) =>
                value ? dayjs(value).format('D MMM, HH:mm') : <span style={{ opacity: 0.5 }}>never</span>,
            },
            {
              title: 'Enabled',
              dataIndex: 'enabled',
              width: 90,
              render: (enabled: boolean, row) => (
                <Switch
                  size="small"
                  checked={enabled}
                  disabled={row.id === me?.id}
                  onChange={(next) => patch(row.id, { enabled: next })}
                />
              ),
            },
            {
              title: '',
              key: 'actions',
              width: 120,
              render: (_v, row) => (
                <Popconfirm
                  title="End every session?"
                  description="They will have to sign in again on all devices."
                  onConfirm={async () => {
                    const result = await forceSignOut(row.id).unwrap()
                    message.success(result.message)
                  }}
                  okText="Sign out"
                  okButtonProps={{ danger: true }}
                >
                  <Button size="small" icon={<LogOut size={13} />}>
                    Sign out
                  </Button>
                </Popconfirm>
              ),
            },
          ]}
        />
      </div>
    </>
  )
}
