import { Alert, Table, Tag } from 'antd'
import dayjs from 'dayjs'
import { CheckCircle2, Server, XCircle } from 'lucide-react'
import { useAdminServicesQuery, useAdminSystemHealthQuery } from '@/app/api'
import { PageHeader, PanelSkeleton, Section } from '@/components/ui'
import type { InfraProbe, ServiceInstance } from '@/types'

export function AdminSystemPage() {
  const { data: services = [], isLoading: loadingServices } = useAdminServicesQuery(undefined, {
    pollingInterval: 15_000,
  })
  const { data: health, isLoading: loadingHealth } = useAdminSystemHealthQuery(undefined, {
    pollingInterval: 15_000,
  })

  const probes = health
    ? (Object.entries(health).filter(([key]) => key !== 'checkedAt') as [string, InfraProbe][])
    : []

  // Instances of the same service are grouped so replica counts are obvious —
  // that is the whole point of running more than one.
  const grouped = services.reduce<Record<string, ServiceInstance[]>>((acc, instance) => {
    acc[instance.serviceId] = acc[instance.serviceId] ?? []
    acc[instance.serviceId].push(instance)
    return acc
  }, {})

  return (
    <>
      <PageHeader
        title="System health"
        subtitle="Registered service instances and the reachability of every dependency."
      />

      {loadingHealth ? (
        <PanelSkeleton rows={3} />
      ) : (
        <div className="lo-grid lo-grid--stats">
          {probes.map(([name, probe]) => (
            <div className="lo-stat" key={name}>
              <div className="lo-stat__head">
                <span className="lo-stat__label">{name}</span>
                <span className="lo-stat__icon">
                  {probe.status === 'UP' ? (
                    <CheckCircle2 size={17} color="var(--status-good)" />
                  ) : (
                    <XCircle size={17} color="var(--status-critical)" />
                  )}
                </span>
              </div>
              <div className="lo-stat__value" style={{ fontSize: 'var(--title-lg)' }}>
                {probe.status}
              </div>
              <div className="lo-stat__foot">
                <span className="tabular">{probe.latencyMs} ms</span>
                <span style={{ opacity: 0.7, fontSize: 12 }}>{probe.detail}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {health && (
        <p style={{ marginTop: 12, fontSize: 12, color: 'var(--on-surface-muted)' }}>
          Checked {dayjs(health.checkedAt).format('D MMM YYYY HH:mm:ss')} · refreshes every 15s
        </p>
      )}

      <Section title="Modules" description="The bounded contexts in this deployment, and the paths each answers on.">
        {!loadingServices && services.length === 0 ? (
          <Alert
            type="warning"
            showIcon
            message="No instances registered"
            description="Either service discovery is disabled on this deployment, or the registry is unreachable. In single-node mode this is expected."
          />
        ) : (
          <div className="lo-panel">
            <Table<ServiceInstance>
              loading={loadingServices}
              rowKey="instanceId"
              size="small"
              scroll={{ x: 720 }}
              pagination={false}
              dataSource={services}
              columns={[
                {
                  title: 'Service',
                  dataIndex: 'serviceId',
                  render: (value: string) => (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      <Server size={14} />
                      <strong>{value}</strong>
                      {grouped[value]?.length > 1 && (
                        <Tag style={{ margin: 0 }}>×{grouped[value].length}</Tag>
                      )}
                    </span>
                  ),
                },
                { title: 'Host', dataIndex: 'host', width: 180 },
                { title: 'Port', dataIndex: 'port', width: 90, className: 'tabular' },
                {
                  title: 'URI',
                  dataIndex: 'uri',
                  ellipsis: true,
                  responsive: ['lg'],
                  render: (value: string) => <code style={{ fontSize: 12 }}>{value}</code>,
                },
                {
                  title: 'TLS',
                  dataIndex: 'secure',
                  width: 80,
                  render: (secure: boolean) => (
                    <Tag style={{ margin: 0 }} color={secure ? 'success' : undefined}>
                      {secure ? 'on' : 'off'}
                    </Tag>
                  ),
                },
              ]}
            />
          </div>
        )}
      </Section>
    </>
  )
}
