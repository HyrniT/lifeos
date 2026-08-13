import { useMemo, useState } from 'react'
import {
  Button,
  DatePicker,
  Empty,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import {
  ArrowDownLeft,
  ArrowUpRight,
  Banknote,
  PiggyBank,
  Plus,
  Repeat,
  Trash2,
  TrendingUp,
  Wallet,
} from 'lucide-react'
import {
  useAccountsQuery,
  useBudgetsQuery,
  useDeleteTransactionMutation,
  useExpenseStatisticsQuery,
  useExpenseTrendQuery,
  useRecurringRulesQuery,
  useSeedFinanceDefaultsMutation,
  useToggleRecurringMutation,
  useTransactionsQuery,
} from '@/app/api'
import {
  BarSeriesChart,
  DonutBreakdown,
  StatTile,
  TrendChart,
  formatShortDate,
} from '@/components/charts'
import { BASE_CURRENCY, formatMoney } from '@/app/money'
import { DynamicIcon, EmptyState, PageHeader, PanelSkeleton, Section } from '@/components/ui'
import { TransactionModal } from './TransactionModal'
import { BudgetModal } from './BudgetModal'
import { AccountModal } from './AccountModal'
import type { Transaction } from '@/types'

const { RangePicker } = DatePicker

export function MoneyPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs()])
  const [tab, setTab] = useState('overview')
  const [txModal, setTxModal] = useState<{ open: boolean; tx?: Transaction | null }>({ open: false })
  const [budgetModal, setBudgetModal] = useState(false)
  const [accountModal, setAccountModal] = useState(false)

  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  const { data: stats, isLoading } = useExpenseStatisticsQuery({ from, to })
  const { data: trend = [] } = useExpenseTrendQuery(12)
  const { data: accounts = [] } = useAccountsQuery()
  const { data: budgets = [] } = useBudgetsQuery()
  const { data: recurring = [] } = useRecurringRulesQuery()
  const { data: transactions, isFetching: loadingTx } = useTransactionsQuery({
    from,
    to,
    size: 100,
  })
  const [deleteTx] = useDeleteTransactionMutation()
  const [toggleRecurring] = useToggleRecurringMutation()
  const [seedDefaults, { isLoading: seeding }] = useSeedFinanceDefaultsMutation()

  const money = (value: number, compact = false) => formatMoney(value, compact)

  const cashFlow = useMemo(
    () =>
      (stats?.cashFlow ?? []).map((point) => ({
        date: point.date,
        expense: point.expense,
        income: point.income,
        balance: point.runningBalance,
      })),
    [stats],
  )

  const weekdayRows = useMemo(
    () =>
      Object.entries(stats?.weekdayPattern ?? {}).map(([day, amount]) => ({
        day,
        amount,
      })),
    [stats],
  )

  const noSetup = !accounts.length

  if (noSetup) {
    return (
      <>
        <PageHeader title="Money" subtitle="Accounts, spending, budgets and forecasts." />
        <EmptyState
          icon={<Wallet size={22} />}
          title="Set up your accounts first"
          description="Create a starter set of accounts and categories, then start logging. You can rename or delete anything afterwards."
          action={
            <Button
              type="primary"
              loading={seeding}
              icon={<Plus size={15} />}
              onClick={async () => {
                await seedDefaults(BASE_CURRENCY).unwrap()
                message.success('Starter accounts and categories created')
              }}
            >
              Create starter set
            </Button>
          }
        />
      </>
    )
  }

  return (
    <>
      <PageHeader
        title="Money"
        subtitle="Where it comes from, where it goes, and what you can safely spend."
        actions={
          <>
            <RangePicker
              value={range}
              onChange={(value) => value && setRange(value as [Dayjs, Dayjs])}
              allowClear={false}
              presets={[
                { label: 'This month', value: [dayjs().startOf('month'), dayjs()] },
                {
                  label: 'Last month',
                  value: [
                    dayjs().subtract(1, 'month').startOf('month'),
                    dayjs().subtract(1, 'month').endOf('month'),
                  ],
                },
                { label: 'Last 90 days', value: [dayjs().subtract(89, 'day'), dayjs()] },
                { label: 'This year', value: [dayjs().startOf('year'), dayjs()] },
              ]}
            />
            <Button
              type="primary"
              icon={<Plus size={16} />}
              onClick={() => setTxModal({ open: true })}
            >
              Add transaction
            </Button>
          </>
        }
      />

      <div className="lo-grid lo-grid--stats">
        <StatTile
          label="Spent"
          value={money(stats?.overview.totalExpense ?? 0, true)}
          delta={stats?.overview.changeVsPreviousPeriod}
          deltaLabel="vs previous period"
          invertDelta
          icon={<ArrowUpRight size={17} />}
        />
        <StatTile
          label="Earned"
          value={money(stats?.overview.totalIncome ?? 0, true)}
          caption={`${stats?.overview.transactionCount ?? 0} transactions all time`}
          icon={<ArrowDownLeft size={17} />}
        />
        <StatTile
          label="Net worth"
          value={money(stats?.overview.netWorth ?? 0, true)}
          caption={`${money(stats?.overview.liquidBalance ?? 0, true)} liquid`}
          icon={<PiggyBank size={17} />}
        />
        <StatTile
          label="Savings rate"
          value={`${Math.round((stats?.overview.savingsRate ?? 0) * 100)}%`}
          caption={`${money(stats?.overview.averageDailySpend ?? 0)}/day average`}
          icon={<TrendingUp size={17} />}
        />
      </div>

      <Tabs
        activeKey={tab}
        onChange={setTab}
        style={{ marginTop: 28 }}
        items={[
          // ------------------------------------------------------ overview
          {
            key: 'overview',
            label: 'Overview',
            children: isLoading ? (
              <PanelSkeleton rows={8} />
            ) : (
              <>
                <div className="lo-grid lo-grid--halves">
                  <div className="lo-panel">
                    <TrendChart
                      title="Daily cash flow"
                      subtitle={`${range[0].format('D MMM')} – ${range[1].format('D MMM YYYY')}`}
                      summary={`Daily spending and income between ${from} and ${to}. Spent ${money(stats?.overview.totalExpense ?? 0)}, earned ${money(stats?.overview.totalIncome ?? 0)}.`}
                      hint="Transfers between your own accounts are excluded from both lines."
                      data={cashFlow}
                      xKey="date"
                      xFormatter={formatShortDate}
                      yFormatter={(value) => money(value, true)}
                      series={[
                        { key: 'expense', label: 'Spent', format: (v) => money(v) },
                        { key: 'income', label: 'Earned', format: (v) => money(v) },
                      ]}
                      variant="area"
                      height={300}
                      tableColumns={[
                        { key: 'date', title: 'Date' },
                        { key: 'expense', title: 'Spent', align: 'right' },
                        { key: 'income', title: 'Earned', align: 'right' },
                      ]}
                      tableRows={cashFlow.map((row) => ({
                        date: row.date,
                        expense: money(row.expense),
                        income: money(row.income),
                      }))}
                    />
                  </div>

                  <div className="lo-panel">
                    <DonutBreakdown
                      title="Where it went"
                      subtitle="Spending by category"
                      summary={`Spending split by category between ${from} and ${to}. Largest category ${stats?.overview.topCategory ?? 'none'}.`}
                      slices={(stats?.byCategory ?? []).map((row) => ({
                        label: row.name,
                        value: row.amount,
                      }))}
                      valueFormatter={(value) => money(value)}
                      centreValue={money(stats?.overview.totalExpense ?? 0, true)}
                      centreLabel="total spent"
                      height={300}
                      tableColumns={[
                        { key: 'name', title: 'Category' },
                        { key: 'amount', title: 'Amount', align: 'right' },
                        { key: 'share', title: 'Share', align: 'right' },
                      ]}
                      tableRows={(stats?.byCategory ?? []).map((row) => ({
                        name: row.name,
                        amount: money(row.amount),
                        share: `${Math.round(row.share * 100)}%`,
                      }))}
                    />
                  </div>
                </div>

                <Section>
                  <div className="lo-grid lo-grid--halves">
                    <div className="lo-panel">
                      <BarSeriesChart
                        title="Twelve-month trend"
                        subtitle="Income against spending, month by month"
                        summary="Monthly income and expense totals over the last twelve months."
                        data={trend}
                        xKey="label"
                        xFormatter={(value) => dayjs(value).format('MMM')}
                        yFormatter={(value) => money(value, true)}
                        series={[
                          { key: 'income', label: 'Earned', format: (v) => money(v) },
                          { key: 'expense', label: 'Spent', format: (v) => money(v) },
                        ]}
                        height={280}
                        tableColumns={[
                          { key: 'label', title: 'Month' },
                          { key: 'income', title: 'Earned', align: 'right' },
                          { key: 'expense', title: 'Spent', align: 'right' },
                          { key: 'net', title: 'Net', align: 'right' },
                        ]}
                        tableRows={trend.map((row) => ({
                          label: row.label,
                          income: money(row.income),
                          expense: money(row.expense),
                          net: money(row.net),
                        }))}
                      />
                    </div>

                    <div className="lo-panel">
                      <BarSeriesChart
                        title="Which days you spend"
                        subtitle="Total spending by weekday over the selected range"
                        summary="Total spending grouped by day of the week."
                        data={weekdayRows}
                        xKey="day"
                        yFormatter={(value) => money(value, true)}
                        series={[{ key: 'amount', label: 'Spent', format: (v) => money(v) }]}
                        height={280}
                        tableColumns={[
                          { key: 'day', title: 'Day' },
                          { key: 'amount', title: 'Spent', align: 'right' },
                        ]}
                        tableRows={weekdayRows.map((row) => ({
                          day: row.day,
                          amount: money(row.amount),
                        }))}
                      />
                    </div>
                  </div>
                </Section>

                {Boolean(stats?.insights.length) && (
                  <Section title="What stands out">
                    <div className="lo-grid lo-grid--cards">
                      {stats!.insights.map((insight) => (
                        <div className="lo-panel" key={insight.code}>
                          <Tag
                            color={
                              insight.severity === 'critical'
                                ? 'error'
                                : insight.severity === 'warning'
                                  ? 'warning'
                                  : insight.severity === 'positive'
                                    ? 'success'
                                    : 'default'
                            }
                            style={{ marginBottom: 8 }}
                          >
                            {insight.severity}
                          </Tag>
                          <h4 style={{ margin: '0 0 4px', fontSize: 15, fontWeight: 700 }}>
                            {insight.title}
                          </h4>
                          <p
                            style={{
                              margin: 0,
                              fontSize: 13,
                              color: 'var(--on-surface-variant)',
                            }}
                          >
                            {insight.message}
                          </p>
                        </div>
                      ))}
                    </div>
                  </Section>
                )}

                {Boolean(stats?.topMerchants.length) && (
                  <Section title="Top merchants">
                    <div className="lo-panel">
                      <Table
                        size="small"
                        pagination={false}
                        rowKey="merchant"
                        dataSource={stats!.topMerchants}
                        columns={[
                          { title: 'Merchant', dataIndex: 'merchant' },
                          { title: 'Visits', dataIndex: 'count', align: 'right', width: 100 },
                          {
                            title: 'Total',
                            dataIndex: 'total',
                            align: 'right',
                            width: 140,
                            className: 'tabular',
                            render: (value: number) => money(value),
                          },
                        ]}
                      />
                    </div>
                  </Section>
                )}
              </>
            ),
          },

          // -------------------------------------------------- transactions
          {
            key: 'transactions',
            label: `Transactions${transactions ? ` (${transactions.totalElements})` : ''}`,
            children: (
              <div className="lo-panel">
                <Table<Transaction>
                  loading={loadingTx}
                  size="middle"
                  rowKey="id"
                  dataSource={transactions?.content ?? []}
                  scroll={{ x: 720 }}
                  pagination={{ pageSize: 25, showSizeChanger: false }}
                  onRow={(record) => ({
                    onDoubleClick: () => setTxModal({ open: true, tx: record }),
                  })}
                  columns={[
                    {
                      title: 'Date',
                      dataIndex: 'occurredOn',
                      width: 110,
                      render: (value: string) => dayjs(value).format('D MMM'),
                      sorter: (a, b) => a.occurredOn.localeCompare(b.occurredOn),
                      defaultSortOrder: 'descend',
                    },
                    {
                      title: 'Description',
                      key: 'description',
                      render: (_v, row) => (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                          <span
                            style={{
                              display: 'grid',
                              placeItems: 'center',
                              width: 30,
                              height: 30,
                              borderRadius: 9,
                              background: 'var(--surface-container)',
                              flexShrink: 0,
                            }}
                          >
                            <DynamicIcon name={row.categoryIcon ?? 'circle-dashed'} size={14} />
                          </span>
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontWeight: 600, fontSize: 14 }}>
                              {row.merchant || row.note || row.categoryName || 'Transaction'}
                            </div>
                            <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                              {row.categoryName ?? 'Uncategorised'} · {row.accountName}
                              {row.recurring && ' · recurring'}
                            </div>
                          </div>
                        </div>
                      ),
                    },
                    {
                      title: 'Type',
                      dataIndex: 'type',
                      width: 110,
                      responsive: ['md'],
                      render: (value: string) => <Tag style={{ margin: 0 }}>{value.toLowerCase()}</Tag>,
                    },
                    {
                      title: 'Amount',
                      dataIndex: 'signedAmount',
                      align: 'right',
                      width: 140,
                      className: 'tabular',
                      sorter: (a, b) => a.signedAmount - b.signedAmount,
                      render: (value: number, row) => (
                        <span
                          style={{
                            fontWeight: 650,
                            color:
                              row.type === 'INCOME'
                                ? 'var(--status-good)'
                                : 'var(--on-surface)',
                          }}
                        >
                          {row.type === 'INCOME' ? '+' : row.type === 'TRANSFER' ? '' : '−'}
                          {formatMoney(Math.abs(value))}
                        </span>
                      ),
                    },
                    {
                      title: '',
                      key: 'actions',
                      width: 48,
                      render: (_v, row) => (
                        <Popconfirm
                          title="Delete this transaction?"
                          description="Account balances will be corrected."
                          onConfirm={() => deleteTx(row.id)}
                          okText="Delete"
                          okButtonProps={{ danger: true }}
                        >
                          <Button type="text" size="small" icon={<Trash2 size={14} />} />
                        </Popconfirm>
                      ),
                    },
                  ]}
                />
              </div>
            ),
          },

          // ------------------------------------------------------- budgets
          {
            key: 'budgets',
            label: 'Budgets',
            children: (
              <>
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
                  <Button icon={<Plus size={15} />} onClick={() => setBudgetModal(true)}>
                    New budget
                  </Button>
                </div>

                {!budgets.length ? (
                  <EmptyState
                    icon={<PiggyBank size={22} />}
                    title="No budgets yet"
                    description="A budget turns a number you hope for into a number you can spend today."
                    action={
                      <Button type="primary" icon={<Plus size={15} />} onClick={() => setBudgetModal(true)}>
                        Create a budget
                      </Button>
                    }
                  />
                ) : (
                  <div className="lo-grid lo-grid--cards">
                    {budgets.map((budget) => (
                      <div className="lo-panel" key={budget.id}>
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 10,
                            marginBottom: 12,
                          }}
                        >
                          <span
                            style={{
                              display: 'grid',
                              placeItems: 'center',
                              width: 36,
                              height: 36,
                              borderRadius: 11,
                              background: 'var(--surface-container)',
                            }}
                          >
                            <DynamicIcon name={budget.categoryIcon} size={17} />
                          </span>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontWeight: 680 }}>{budget.name}</div>
                            <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                              {budget.categoryName} · {budget.period.toLowerCase()}
                            </div>
                          </div>
                          <Tag
                            style={{ margin: 0 }}
                            color={
                              budget.state === 'EXCEEDED'
                                ? 'error'
                                : budget.state === 'WARNING'
                                  ? 'warning'
                                  : budget.state === 'AHEAD'
                                    ? 'success'
                                    : 'default'
                            }
                          >
                            {budget.state.replace('_', ' ').toLowerCase()}
                          </Tag>
                        </div>

                        <Progress
                          percent={Math.min(100, Math.round(budget.usedRatio * 100))}
                          strokeColor={
                            budget.state === 'EXCEEDED'
                              ? 'var(--status-critical)'
                              : budget.state === 'WARNING'
                                ? 'var(--status-warning)'
                                : 'var(--on-surface)'
                          }
                          trailColor="var(--outline-variant)"
                        />

                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            fontSize: 13,
                            marginTop: 10,
                          }}
                        >
                          <span className="tabular">
                            {money(budget.spent)} of {money(budget.amount)}
                          </span>
                          <span
                            className="tabular"
                            style={{ color: 'var(--on-surface-variant)' }}
                          >
                            {budget.daysLeft}d left
                          </span>
                        </div>

                        <div
                          style={{
                            marginTop: 10,
                            padding: '8px 10px',
                            borderRadius: 'var(--radius-sm)',
                            background: 'var(--surface-container)',
                            fontSize: 13,
                          }}
                        >
                          {budget.remaining >= 0 ? (
                            <>
                              You can spend <strong>{money(budget.safeDailySpend)}</strong> a day for
                              the rest of the period.
                            </>
                          ) : (
                            <>
                              Over budget by <strong>{money(Math.abs(budget.remaining))}</strong>.
                            </>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                <Section title="Recurring" description="Rent, salary, subscriptions — created automatically when due.">
                  {!recurring.length ? (
                    <Empty description="No recurring rules" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  ) : (
                    <div className="lo-panel">
                      <Table
                        size="small"
                        rowKey="id"
                        pagination={false}
                        dataSource={recurring}
                        scroll={{ x: 600 }}
                        columns={[
                          {
                            title: 'Name',
                            dataIndex: 'name',
                            render: (value: string) => (
                              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                <Repeat size={14} /> {value}
                              </span>
                            ),
                          },
                          {
                            title: 'Amount',
                            dataIndex: 'amount',
                            align: 'right',
                            className: 'tabular',
                            render: (value: number) => money(value),
                          },
                          { title: 'Every', dataIndex: 'cadence', render: (v: string) => v.toLowerCase() },
                          {
                            title: 'Next',
                            dataIndex: 'nextRunOn',
                            render: (value: string) => dayjs(value).format('D MMM YYYY'),
                          },
                          {
                            title: 'Active',
                            dataIndex: 'active',
                            width: 90,
                            render: (active: boolean, row) => (
                              <Button
                                size="small"
                                type={active ? 'primary' : 'default'}
                                onClick={() => toggleRecurring({ id: row.id, active: !active })}
                              >
                                {active ? 'On' : 'Off'}
                              </Button>
                            ),
                          },
                        ]}
                      />
                    </div>
                  )}
                </Section>
              </>
            ),
          },

          // ------------------------------------------------------ accounts
          {
            key: 'accounts',
            label: 'Accounts',
            children: (
              <>
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
                  <Button icon={<Plus size={15} />} onClick={() => setAccountModal(true)}>
                    New account
                  </Button>
                </div>
                <div className="lo-grid lo-grid--cards">
                  {accounts.map((account) => (
                    <div className="lo-panel" key={account.id}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <span
                          style={{
                            display: 'grid',
                            placeItems: 'center',
                            width: 40,
                            height: 40,
                            borderRadius: 12,
                            background: 'var(--surface-container)',
                          }}
                        >
                          <DynamicIcon name={account.icon} size={18} />
                        </span>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontWeight: 680 }}>{account.name}</div>
                          <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                            {account.type.replace('_', ' ').toLowerCase()} · {account.currency}
                          </div>
                        </div>
                      </div>
                      <div
                        className="tabular"
                        style={{
                          marginTop: 16,
                          fontSize: 'var(--headline-sm)',
                          fontWeight: 720,
                          letterSpacing: '-0.02em',
                        }}
                      >
                        {formatMoney(account.currentBalance)}
                      </div>
                      {account.creditLimit != null && (
                        <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                          Limit {formatMoney(account.creditLimit)}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </>
            ),
          },
        ]}
      />

      <TransactionModal
        open={txModal.open}
        transaction={txModal.tx ?? null}
        onClose={() => setTxModal({ open: false })}
      />
      <BudgetModal open={budgetModal} onClose={() => setBudgetModal(false)} />
      <AccountModal open={accountModal} onClose={() => setAccountModal(false)} />
    </>
  )
}
