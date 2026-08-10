/** Mirrors of the backend DTOs. Kept in one file so a contract change is one diff. */

// ================================================================== identity
export interface UserView {
  id: string
  email: string
  displayName: string
  avatarUrl?: string | null
  locale: string
  timezone: string
  baseCurrency: string
  roles: string[]
  enabled: boolean
  emailVerified: boolean
  twoFactorEnabled: boolean
  lastLoginAt?: string | null
  createdAt: string
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: UserView
}

export interface TwoFactorChallenge {
  twoFactorRequired: true
  challengeToken: string
  message: string
}

export type LoginResult = TokenPair | TwoFactorChallenge

export function isTwoFactorChallenge(result: LoginResult): result is TwoFactorChallenge {
  return (result as TwoFactorChallenge).twoFactorRequired === true
}

export interface SessionView {
  id: string
  userAgent?: string
  ipAddress?: string
  issuedAt: string
  expiresAt: string
  current: boolean
}

export interface TotpSetup {
  secret: string
  otpauthUri: string
  recoveryCodes: string[]
}

// ==================================================================== habits
export type HabitType = 'BUILD' | 'QUIT'
export type Frequency =
  | 'DAILY'
  | 'WEEKLY_TARGET'
  | 'SPECIFIC_DAYS'
  | 'INTERVAL'
  | 'MONTHLY_TARGET'
export type Unit =
  | 'TIMES'
  | 'MINUTES'
  | 'HOURS'
  | 'PAGES'
  | 'STEPS'
  | 'KILOMETRES'
  | 'MILLILITRES'
  | 'GRAMS'
  | 'CUSTOM'
export type Difficulty = 'TRIVIAL' | 'EASY' | 'MEDIUM' | 'HARD' | 'EPIC'

export interface Habit {
  id: string
  name: string
  icon: string
  color: string
  description?: string | null
  type: HabitType
  frequency: Frequency
  daysOfWeek: number[]
  intervalDays?: number | null
  targetPerPeriod: number
  unit: Unit
  unitLabel?: string | null
  targetValue?: number | null
  reminderTime?: string | null
  difficulty: Difficulty
  category: string
  sortOrder: number
  archived: boolean
  currentStreak: number
  longestStreak: number
  totalCheckIns: number
  lastCheckInDate?: string | null
  completionRate30d?: number | null
  doneToday: boolean
  version: number
  createdAt: string
}

export interface GameStats {
  xp: number
  level: number
  coins: number
  hp: number
  xpIntoLevel: number
  xpForNextLevel: number
  levelProgress: number
  totalCheckIns: number
  currentDayStreak: number
  longestDayStreak: number
  streakFreezes: number
  lastActiveDate?: string | null
}

export interface CheckInResult {
  habitId: string
  date: string
  value: number
  xpAwarded: number
  currentStreak: number
  milestoneReached: boolean
  stats: GameStats
  newAchievements: string[]
}

export interface TodaySummary {
  date: string
  totalDue: number
  completed: number
  completionRate: number
  xpEarnedToday: number
  due: Habit[]
  stats: GameStats
}

export interface HeatmapCell {
  date: string
  count: number
  intensity: number
}

export interface HabitInsights {
  habitId: string
  habitName: string
  currentStreak: number
  longestStreak: number
  totalCheckIns: number
  completionRate7d: number
  completionRate30d: number
  completionRate90d: number
  weekdayCompletion: Record<string, number>
  heatmap: HeatmapCell[]
  bestDay: string
  worstDay: string
  averageMood?: number | null
  trend: 'up' | 'down' | 'steady'
}

export interface Achievement {
  code: string
  title: string
  description: string
  icon: string
  tier: 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM'
  unlocked: boolean
  unlockedAt?: string | null
  progress: number
}

export interface HabitLogEntry {
  id: string
  habitId: string
  date: string
  value: number
  note?: string | null
  mood?: number | null
  xpAwarded: number
}

// ================================================================== expenses
export type AccountType =
  | 'CASH'
  | 'BANK'
  | 'CREDIT_CARD'
  | 'E_WALLET'
  | 'SAVINGS'
  | 'INVESTMENT'
  | 'LOAN'
export type TxType = 'EXPENSE' | 'INCOME' | 'TRANSFER'
export type CategoryKind = 'EXPENSE' | 'INCOME'
export type BudgetPeriod = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY'
export type Cadence = 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY'

export interface Account {
  id: string
  name: string
  type: AccountType
  currency: string
  openingBalance: number
  currentBalance: number
  creditLimit?: number | null
  icon: string
  color: string
  excludeFromTotals: boolean
  sortOrder: number
  archived: boolean
  createdAt: string
}

export interface Category {
  id: string
  name: string
  kind: CategoryKind
  icon: string
  color: string
  parentId?: string | null
  monthlyBudget?: number | null
  sortOrder: number
  archived: boolean
  system: boolean
}

export interface Transaction {
  id: string
  accountId: string
  accountName?: string | null
  toAccountId?: string | null
  toAccountName?: string | null
  categoryId?: string | null
  categoryName?: string | null
  categoryIcon?: string | null
  categoryColor?: string | null
  amount: number
  signedAmount: number
  currency: string
  type: TxType
  occurredOn: string
  note?: string | null
  merchant?: string | null
  tags: string[]
  recurring: boolean
  createdAt: string
}

export interface BudgetStatus {
  id: string
  name: string
  categoryId?: string | null
  categoryName: string
  categoryIcon: string
  amount: number
  spent: number
  remaining: number
  usedRatio: number
  period: BudgetPeriod
  periodStart: string
  periodEnd: string
  daysLeft: number
  safeDailySpend: number
  state: 'ON_TRACK' | 'AHEAD' | 'WARNING' | 'EXCEEDED'
  alertThreshold: number
}

export interface RecurringRule {
  id: string
  name: string
  accountId: string
  categoryId?: string | null
  amount: number
  currency: string
  type: TxType
  cadence: Cadence
  nextRunOn: string
  endOn?: string | null
  lastRunOn?: string | null
  note?: string | null
  active: boolean
}

export interface MoneyOverview {
  from: string
  to: string
  currency: string
  totalIncome: number
  totalExpense: number
  net: number
  netWorth: number
  liquidBalance: number
  savingsRate: number
  averageDailySpend: number
  projectedMonthEnd: number
  transactionCount: number
  biggestExpense: number
  topCategory?: string | null
  changeVsPreviousPeriod: number
}

export interface CategoryBreakdown {
  categoryId?: string | null
  name: string
  icon: string
  color: string
  amount: number
  share: number
  transactionCount: number
  averageAmount: number
}

export interface CashFlowPoint {
  date: string
  income: number
  expense: number
  net: number
  runningBalance: number
}

export interface TrendPoint {
  label: string
  income: number
  expense: number
  net: number
}

export interface MerchantSpend {
  merchant: string
  count: number
  total: number
}

export interface SpendingInsight {
  code: string
  severity: 'positive' | 'info' | 'warning' | 'critical'
  title: string
  message: string
  data: Record<string, unknown>
}

export interface ExpenseStatistics {
  overview: MoneyOverview
  byCategory: CategoryBreakdown[]
  cashFlow: CashFlowPoint[]
  monthlyTrend: TrendPoint[]
  topMerchants: MerchantSpend[]
  budgets: BudgetStatus[]
  insights: SpendingInsight[]
  weekdayPattern: Record<string, number>
}

// ================================================================== planning
export type Priority = 'P1' | 'P2' | 'P3' | 'P4'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
export type GoalStatus = 'ACTIVE' | 'ACHIEVED' | 'PAUSED' | 'ABANDONED'
export type ProjectStatus = 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED'
export type SessionType = 'POMODORO' | 'DEEP_WORK' | 'SHORT_BREAK' | 'LONG_BREAK'
export type Recurrence =
  | 'NONE'
  | 'DAILY'
  | 'WEEKDAYS'
  | 'WEEKLY'
  | 'BIWEEKLY'
  | 'MONTHLY'
  | 'YEARLY'

export interface Task {
  id: string
  title: string
  notes?: string | null
  projectId?: string | null
  projectName?: string | null
  goalId?: string | null
  parentTaskId?: string | null
  priority: Priority
  status: TaskStatus
  dueDate?: string | null
  dueTime?: string | null
  scheduledFor?: string | null
  estimateMinutes?: number | null
  actualMinutes: number
  recurrence: Recurrence
  tags: string[]
  sortOrder: number
  eisenhowerQuadrant: 1 | 2 | 3 | 4
  overdue: boolean
  completedAt?: string | null
  createdAt: string
  subtasks: Task[]
  subtaskCount: number
  subtasksDone: number
}

export interface Goal {
  id: string
  title: string
  description?: string | null
  category: string
  icon: string
  color: string
  projectId?: string | null
  targetValue: number
  currentValue: number
  unit: string
  startDate?: string | null
  targetDate?: string | null
  status: GoalStatus
  progress: number
  timeElapsed?: number | null
  pace: 'ahead' | 'on-track' | 'behind' | 'unknown'
  daysRemaining?: number | null
  linkedTasks: number
  linkedTasksDone: number
  createdAt: string
}

export interface Project {
  id: string
  name: string
  description?: string | null
  icon: string
  color: string
  status: ProjectStatus
  dueDate?: string | null
  sortOrder: number
  taskCount: number
  taskDone: number
  progress: number
  createdAt: string
}

export interface FocusSession {
  id: string
  taskId?: string | null
  type: SessionType
  sessionDate: string
  startedAt: string
  endedAt?: string | null
  plannedMinutes: number
  actualMinutes: number
  completed: boolean
  focusScore?: number | null
  note?: string | null
}

export interface JournalEntry {
  id: string
  entryDate: string
  mood?: number | null
  energy?: number | null
  highlights?: string | null
  gratitude?: string | null
  notes?: string | null
  updatedAt: string
}

export interface ProductivityPoint {
  date: string
  completed: number
  created: number
  focusMinutes: number
}

export interface PlanningStatistics {
  tasksOpen: number
  tasksDone: number
  tasksOverdue: number
  tasksCompletedLast7d: number
  tasksCompletedLast30d: number
  completionRate30d: number
  focusMinutesLast7d: number
  focusMinutesLast30d: number
  focusSessionsTotal: number
  averageSessionMinutes: number
  activeGoals: number
  achievedGoals: number
  averageGoalProgress: number
  byQuadrant: Record<string, number>
  byPriority: Record<string, number>
  timeline: ProductivityPoint[]
  focusByHour: Record<string, number>
  mostProductiveDay: string
  averageMood?: number | null
  averageEnergy?: number | null
}

export interface Agenda {
  date: string
  overdue: Task[]
  dueToday: Task[]
  scheduled: Task[]
  focusMinutesToday: number
  journal?: JournalEntry | null
}

// ================================================================= analytics
export interface TimelinePoint {
  date: string
  habitCheckIns: number
  xpEarned: number
  expense: number
  income: number
  tasksCompleted: number
  focusMinutes: number
  mood?: number | null
}

export interface Correlation {
  code: string
  title: string
  message: string
  strength: number
  sampleDays: number
  direction: 'positive' | 'negative'
  data: Record<string, unknown>
}

export interface LifeOverview {
  from: string
  to: string
  activeDays: number
  totalCheckIns: number
  totalXp: number
  totalSpent: number
  totalEarned: number
  totalTasksCompleted: number
  totalFocusMinutes: number
  habitConsistency: number
  averageDailySpend: number
  strongestDay: string
  weakestDay: string
  timeline: TimelinePoint[]
  correlations: Correlation[]
  balanceScore: Record<string, number>
}

// =============================================================== notifications
export interface AppNotification {
  id: string
  userId: string
  kind: string
  title: string
  body: string
  icon: string
  severity: 'info' | 'success' | 'warning' | 'critical'
  /** Where tapping the notification should take the user. */
  deepLink?: string | null
  data: Record<string, unknown>
  read: boolean
  createdAt: string
}

export interface NotificationKind {
  code: string
  label: string
  description: string
}

export interface NotificationPreferences {
  inAppEnabled: boolean
  pushEnabled: boolean
  emailEnabled: boolean
  /** Kinds switched off; anything absent is on. */
  mutedKinds: string[]
  /** Minutes before a deadline to warn. Several entries mean several warnings. */
  leadTimeMinutes: number[]
  remindAtDeadline: boolean
  remindWhenOverdue: boolean
  dailySummaryEnabled: boolean
  dailySummaryTime: string
  quietHoursEnabled: boolean
  quietFrom: string
  quietTo: string
  timezone: string
  pushAvailable: boolean
  pushDevices: number
}

// ===================================================================== admin
export interface AdminOverview {
  totalUsers: number
  activeUsers: number
  disabledUsers: number
  newUsersLast7d: number
  activeSessions: number
  loginsLast24h: number
  failedLoginsLast24h: number
  googleLinkedAccounts: number
  twoFactorEnabledUsers: number
  auditBreakdown: { action: string; count: number }[]
}

export interface AuditEntry {
  id: string
  userId?: string | null
  userEmail?: string | null
  action: string
  outcome: 'SUCCESS' | 'FAILURE'
  detail?: string | null
  ipAddress?: string | null
  occurredAt: string
}

export interface ServiceInstance {
  serviceId: string
  instanceId: string
  host: string
  port: number
  uri: string
  secure: boolean
  metadata: Record<string, string>
}

export interface InfraProbe {
  status: 'UP' | 'DOWN'
  latencyMs: number
  detail: string
}

export interface SystemHealth {
  checkedAt: string
  postgres: InfraProbe
  redis: InfraProbe
  discovery: InfraProbe
}

// =================================================================== shared
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  path?: string
  fieldErrors?: Record<string, string>
}
