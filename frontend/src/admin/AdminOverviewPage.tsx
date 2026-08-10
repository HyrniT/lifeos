import { Alert, Tag } from 'antd'
import { KeyRound, ShieldCheck, UserPlus, Users, XCircle } from 'lucide-react'
import { useAdminOverviewQuery, useAdminSystemHealthQuery } from '@/app/api'
import { BarSeriesChart, StatTile } from '@/components/charts'
import { PageHeader, PanelSkeleton, Section } from '@/components/ui'

export function AdminOverviewPage() {
  const { data, isLoading } = useAdminOverviewQuery(undefined, { pollingInterval: 30_000 })
  const { data: health } = useAdminSystemHealthQuery(undefined, { pollingInterval: 30_000 })

  const auditRows = (data?.auditBreakdown ?? []).slice(0, 10).map((row) => ({
    action: row.action.replace(/_/g, ' ').toLowerCase(),
    count: row.count,
  }))

  const failureRate =
    data && data.loginsLast24h + data.failedLoginsLast24h > 0
      ? data.failedLoginsLast24h / (data.loginsLast24h + data.failedLoginsLast24h)
      : 0

  const down = health
    ? Object.entries(health)
        .filter(([key]) => key !== 'checkedAt')
        .filter(([, probe]) => (probe as { status: string }).status !== 'UP')
        .map(([key]) => key)
    : []

  return (
    <>
      <PageHeader
        title="Platform overview"
        subtitle="Accounts, sessions and authentication activity across the deployment."
      />

      {down.length > 0 && (
        <Alert
          type="error"
          showIcon
          message={`${down.length} dependency down`}
          description={`Not reachable: ${down.join(', ')}. Check the System tab for details.`}
          style={{ marginBottom: 20 }}
        />
      )}

      {failureRate > 0.4 && (
        <Alert
          type="warning"
          showIcon
          message="High share of failed sign-ins"
          description={`${Math.round(failureRate * 100)}% of sign-in attempts in the last 24 hours failed. That is consistent with a credential-stuffing attempt; the audit log will show whether it is one IP or many.`}
          style={{ marginBottom: 20 }}
        />
      )}

      {isLoading ? (
        <PanelSkeleton rows={6} />
      ) : (
        <>
          <div className="lo-grid lo-grid--stats">
            <StatTile
              label="Total users"
              value={data?.totalUsers ?? 0}
              caption={`${data?.activeUsers ?? 0} enabled · ${data?.disabledUsers ?? 0} disabled`}
              icon={<Users size={17} />}
            />
            <StatTile
              label="New this week"
              value={data?.newUsersLast7d ?? 0}
              icon={<UserPlus size={17} />}
            />
            <StatTile
              label="Active sessions"
              value={data?.activeSessions ?? 0}
              caption={`${data?.loginsLast24h ?? 0} sign-ins in 24h`}
              icon={<KeyRound size={17} />}
            />
            <StatTile
              label="Failed sign-ins 24h"
              value={data?.failedLoginsLast24h ?? 0}
              caption={`${Math.round(failureRate * 100)}% of attempts`}
              icon={<XCircle size={17} />}
            />
          </div>

          <Section>
            <div className="lo-grid lo-grid--halves">
              <div className="lo-panel">
                <BarSeriesChart
                  title="Audit activity"
                  subtitle="Events recorded in the last seven days"
                  summary="Count of audit-log events by action type over the last seven days."
                  data={auditRows}
                  xKey="action"
                  series={[{ key: 'count', label: 'Events' }]}
                  layout="horizontal"
                  height={320}
                  tableColumns={[
                    { key: 'action', title: 'Action' },
                    { key: 'count', title: 'Events', align: 'right' },
                  ]}
                  tableRows={auditRows}
                />
              </div>

              <div className="lo-panel">
                <h3 style={{ margin: '0 0 16px', fontSize: 'var(--title-md)', fontWeight: 700 }}>
                  Account security posture
                </h3>

                {[
                  {
                    label: 'Two-factor enabled',
                    value: data?.twoFactorEnabledUsers ?? 0,
                    total: data?.totalUsers ?? 0,
                    icon: <ShieldCheck size={16} />,
                  },
                  {
                    label: 'Google-linked accounts',
                    value: data?.googleLinkedAccounts ?? 0,
                    total: data?.totalUsers ?? 0,
                    icon: <KeyRound size={16} />,
                  },
                ].map((row) => {
                  const share = row.total ? row.value / row.total : 0
                  return (
                    <div key={row.label} style={{ marginBottom: 20 }}>
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          marginBottom: 6,
                          fontSize: 14,
                        }}
                      >
                        {row.icon}
                        <span style={{ flex: 1 }}>{row.label}</span>
                        <span className="tabular" style={{ fontWeight: 650 }}>
                          {row.value}/{row.total}
                        </span>
                      </div>
                      <div
                        style={{
                          height: 6,
                          borderRadius: 999,
                          background: 'var(--outline-variant)',
                          overflow: 'hidden',
                        }}
                      >
                        <div
                          style={{
                            width: `${Math.round(share * 100)}%`,
                            height: '100%',
                            background: 'var(--on-surface)',
                            transition: 'width 400ms cubic-bezier(0.2,0,0,1)',
                          }}
                        />
                      </div>
                    </div>
                  )
                })}

                <Alert
                  type="info"
                  showIcon
                  style={{ marginTop: 8 }}
                  message="Defence in depth"
                  description="Sign-in is rate limited per account and per IP in Redis, refresh tokens rotate with reuse detection, and every attempt lands in the audit log — the numbers above are the part users control."
                />
              </div>
            </div>
          </Section>
        </>
      )}
    </>
  )
}
