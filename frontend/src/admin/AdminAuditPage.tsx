import { useState } from 'react'
import { Select, Table, Tag } from 'antd'
import dayjs from 'dayjs'
import { useAdminAuditQuery } from '@/app/api'
import { PageHeader } from '@/components/ui'
import type { AuditEntry } from '@/types'

const ACTIONS = [
  'LOGIN',
  'LOGIN_FAILED',
  'LOGIN_LOCKED',
  'LOGOUT',
  'REGISTER',
  'TOKEN_REFRESH',
  'TOKEN_REUSE_DETECTED',
  'OAUTH_LOGIN',
  'OAUTH_LINKED',
  '2FA_ENABLED',
  '2FA_DISABLED',
  '2FA_FAILED',
  'PASSWORD_CHANGED',
  'ADMIN_USER_UPDATED',
  'ADMIN_USER_DISABLED',
]

/** Events that mean something is wrong, not merely noteworthy. */
const CRITICAL = new Set(['TOKEN_REUSE_DETECTED', 'LOGIN_LOCKED', '2FA_FAILED'])

export function AdminAuditPage() {
  const [action, setAction] = useState<string | undefined>()
  const [outcome, setOutcome] = useState<string | undefined>()
  const [page, setPage] = useState(0)

  const { data, isFetching } = useAdminAuditQuery({ action, outcome, page, size: 50 })

  return (
    <>
      <PageHeader
        title="Security audit log"
        subtitle="Append-only record of every authentication and administration event."
        actions={
          <>
            <Select
              allowClear
              placeholder="All actions"
              style={{ width: 220 }}
              value={action}
              onChange={(value) => {
                setAction(value)
                setPage(0)
              }}
              options={ACTIONS.map((value) => ({
                value,
                label: value.replace(/_/g, ' ').toLowerCase(),
              }))}
            />
            <Select
              allowClear
              placeholder="Any outcome"
              style={{ width: 160 }}
              value={outcome}
              onChange={(value) => {
                setOutcome(value)
                setPage(0)
              }}
              options={[
                { value: 'SUCCESS', label: 'Success' },
                { value: 'FAILURE', label: 'Failure' },
              ]}
            />
          </>
        }
      />

      <div className="lo-panel">
        <Table<AuditEntry>
          loading={isFetching}
          rowKey="id"
          size="small"
          scroll={{ x: 820 }}
          dataSource={data?.content ?? []}
          pagination={{
            current: page + 1,
            pageSize: data?.size ?? 50,
            total: data?.totalElements ?? 0,
            showSizeChanger: false,
            onChange: (next) => setPage(next - 1),
          }}
          rowClassName={(row) => (CRITICAL.has(row.action) ? 'lo-audit-critical' : '')}
          columns={[
            {
              title: 'When',
              dataIndex: 'occurredAt',
              width: 160,
              render: (value: string) => (
                <span className="tabular" style={{ fontSize: 12 }}>
                  {dayjs(value).format('D MMM YYYY HH:mm:ss')}
                </span>
              ),
            },
            {
              title: 'Action',
              dataIndex: 'action',
              width: 190,
              render: (value: string) => (
                <Tag
                  style={{ margin: 0 }}
                  color={CRITICAL.has(value) ? 'error' : undefined}
                >
                  {value.replace(/_/g, ' ').toLowerCase()}
                </Tag>
              ),
            },
            {
              title: 'Outcome',
              dataIndex: 'outcome',
              width: 100,
              render: (value: string) => (
                <Tag style={{ margin: 0 }} color={value === 'FAILURE' ? 'error' : 'success'}>
                  {value.toLowerCase()}
                </Tag>
              ),
            },
            {
              title: 'User',
              dataIndex: 'userEmail',
              render: (value: string | null) =>
                value ?? <span style={{ opacity: 0.5 }}>anonymous</span>,
            },
            { title: 'IP', dataIndex: 'ipAddress', width: 140, responsive: ['lg'] },
            { title: 'Detail', dataIndex: 'detail', ellipsis: true, responsive: ['xl'] },
          ]}
        />
      </div>

      <style>{`
        .lo-audit-critical td { background: var(--status-critical-bg) !important; }
      `}</style>
    </>
  )
}
