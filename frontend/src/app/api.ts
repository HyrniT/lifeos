import { createApi } from '@reduxjs/toolkit/query/react'
import { baseQueryWithReauth } from './baseQuery'
import type {
  Account,
  Achievement,
  AdminOverview,
  Agenda,
  AppNotification,
  NotificationKind,
  NotificationPreferences,
  AuditEntry,
  BudgetStatus,
  Category,
  CheckInResult,
  ExpenseStatistics,
  FocusSession,
  GameStats,
  Goal,
  Habit,
  HabitInsights,
  HabitLogEntry,
  HeatmapCell,
  JournalEntry,
  LifeOverview,
  LoginResult,
  PageResponse,
  PlanningStatistics,
  Project,
  RecurringRule,
  ServiceInstance,
  SessionView,
  SystemHealth,
  Task,
  TaskStatus,
  TodaySummary,
  TokenPair,
  TotpSetup,
  Transaction,
  TrendPoint,
  UserView,
} from '@/types'

/**
 * One RTK Query API for the whole product.
 *
 * Tag invalidation is what keeps the UI honest: a check-in touches habits, the
 * dashboard, gamification and analytics, and listing all four here means no screen
 * has to remember to refetch.
 */
export const api = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithReauth,
  tagTypes: [
    'Habit',
    'HabitToday',
    'Stats',
    'Achievement',
    'Account',
    'Category',
    'Transaction',
    'Budget',
    'Recurring',
    'ExpenseStats',
    'Task',
    'Goal',
    'Project',
    'Focus',
    'Journal',
    'PlanningStats',
    'Analytics',
    'Notification',
    'NotificationPrefs',
    'Me',
    'Session',
    'AdminUser',
    'AdminOverview',
    'Audit',
  ],
  // Data is personal and low-volume; a short window avoids a spinner on every
  // tab switch without ever showing genuinely stale numbers.
  keepUnusedDataFor: 60,
  refetchOnReconnect: true,
  endpoints: (builder) => ({
    // ============================================================ identity
    login: builder.mutation<LoginResult, { email: string; password: string; totpCode?: string }>({
      query: (body) => ({ url: '/auth/login', method: 'POST', body }),
    }),
    verifyTwoFactor: builder.mutation<TokenPair, { challengeToken: string; code: string }>({
      query: (body) => ({ url: '/auth/verify-2fa', method: 'POST', body }),
    }),
    register: builder.mutation<
      TokenPair,
      { email: string; password: string; displayName: string; timezone?: string; baseCurrency?: string }
    >({
      query: (body) => ({ url: '/auth/register', method: 'POST', body }),
    }),
    logout: builder.mutation<{ message: string }, { refreshToken: string }>({
      query: (body) => ({ url: '/auth/logout', method: 'POST', body }),
    }),
    authProviders: builder.query<{ password: boolean; google: boolean }, void>({
      query: () => '/auth/providers',
    }),
    googleAuthUrl: builder.query<{ authorizationUrl: string; state: string }, string | void>({
      query: (redirectUri) =>
        `/auth/oauth2/google/url${redirectUri ? `?redirectUri=${encodeURIComponent(redirectUri)}` : ''}`,
    }),
    googleCallback: builder.mutation<
      TokenPair,
      { code: string; state: string; redirectUri?: string }
    >({
      query: (body) => ({ url: '/auth/oauth2/google/callback', method: 'POST', body }),
    }),

    me: builder.query<UserView, void>({ query: () => '/users/me', providesTags: ['Me'] }),
    updateProfile: builder.mutation<UserView, Partial<UserView>>({
      query: (body) => ({ url: '/users/me', method: 'PATCH', body }),
      invalidatesTags: ['Me'],
    }),
    changePassword: builder.mutation<
      { message: string },
      { currentPassword: string; newPassword: string }
    >({
      query: (body) => ({ url: '/users/me/password', method: 'POST', body }),
      invalidatesTags: ['Session'],
    }),
    sessions: builder.query<SessionView[], void>({
      query: () => '/users/me/sessions',
      providesTags: ['Session'],
    }),
    revokeSessions: builder.mutation<{ message: string }, void>({
      query: () => ({ url: '/users/me/sessions', method: 'DELETE' }),
      invalidatesTags: ['Session'],
    }),

    setupTotp: builder.mutation<TotpSetup, void>({
      query: () => ({ url: '/auth/2fa/setup', method: 'POST' }),
    }),
    confirmTotp: builder.mutation<{ enabled: boolean; recoveryCodes: string[] }, { code: string }>({
      query: (body) => ({ url: '/auth/2fa/confirm', method: 'POST', body }),
      invalidatesTags: ['Me'],
    }),
    disableTotp: builder.mutation<{ message: string }, { currentPassword: string }>({
      query: (body) => ({ url: '/auth/2fa/disable', method: 'POST', body }),
      invalidatesTags: ['Me'],
    }),

    // ============================================================== habits
    habits: builder.query<Habit[], { includeArchived?: boolean } | void>({
      query: (args) => `/habits?includeArchived=${args?.includeArchived ? 'true' : 'false'}`,
      providesTags: ['Habit'],
    }),
    habit: builder.query<Habit, string>({
      query: (id) => `/habits/${id}`,
      providesTags: (_r, _e, id) => [{ type: 'Habit', id }],
    }),
    habitsToday: builder.query<TodaySummary, void>({
      query: () => '/habits/today',
      providesTags: ['HabitToday', 'Stats'],
    }),
    habitHeatmap: builder.query<HeatmapCell[], { from?: string; to?: string } | void>({
      query: (args) => {
        const params = new URLSearchParams()
        if (args?.from) params.set('from', args.from)
        if (args?.to) params.set('to', args.to)
        return `/habits/heatmap?${params.toString()}`
      },
      providesTags: ['Habit'],
    }),
    habitLogs: builder.query<HabitLogEntry[], { id: string; from?: string; to?: string }>({
      query: ({ id, from, to }) => {
        const params = new URLSearchParams()
        if (from) params.set('from', from)
        if (to) params.set('to', to)
        return `/habits/${id}/logs?${params.toString()}`
      },
      providesTags: (_r, _e, { id }) => [{ type: 'Habit', id }],
    }),
    habitInsights: builder.query<HabitInsights, string>({
      query: (id) => `/habits/${id}/insights`,
      providesTags: (_r, _e, id) => [{ type: 'Habit', id }],
    }),
    createHabit: builder.mutation<Habit, Partial<Habit>>({
      query: (body) => ({ url: '/habits', method: 'POST', body }),
      invalidatesTags: ['Habit', 'HabitToday', 'Achievement'],
    }),
    updateHabit: builder.mutation<Habit, { id: string; patch: Partial<Habit> }>({
      query: ({ id, patch }) => ({ url: `/habits/${id}`, method: 'PATCH', body: patch }),
      invalidatesTags: ['Habit', 'HabitToday'],
    }),
    archiveHabit: builder.mutation<Habit, { id: string; archived: boolean }>({
      query: ({ id, archived }) => ({
        url: `/habits/${id}/${archived ? 'archive' : 'unarchive'}`,
        method: 'POST',
      }),
      invalidatesTags: ['Habit', 'HabitToday'],
    }),
    deleteHabit: builder.mutation<void, string>({
      query: (id) => ({ url: `/habits/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Habit', 'HabitToday', 'Stats'],
    }),
    reorderHabits: builder.mutation<Habit[], string[]>({
      query: (orderedIds) => ({ url: '/habits/reorder', method: 'POST', body: { orderedIds } }),
      invalidatesTags: ['Habit'],
    }),
    checkIn: builder.mutation<
      CheckInResult,
      { id: string; date?: string; value?: number; note?: string; mood?: number }
    >({
      query: ({ id, ...body }) => ({ url: `/habits/${id}/check-in`, method: 'POST', body }),
      invalidatesTags: ['Habit', 'HabitToday', 'Stats', 'Achievement', 'Analytics'],
    }),
    undoCheckIn: builder.mutation<Habit, { id: string; date?: string }>({
      query: ({ id, date }) => ({
        url: `/habits/${id}/check-in${date ? `?date=${date}` : ''}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Habit', 'HabitToday', 'Stats', 'Analytics'],
    }),

    gameStats: builder.query<GameStats, void>({
      query: () => '/gamification/stats',
      providesTags: ['Stats'],
    }),
    achievements: builder.query<Achievement[], void>({
      query: () => '/gamification/achievements',
      providesTags: ['Achievement'],
    }),

    // ============================================================ expenses
    accounts: builder.query<Account[], { includeArchived?: boolean } | void>({
      query: (args) => `/accounts?includeArchived=${args?.includeArchived ? 'true' : 'false'}`,
      providesTags: ['Account'],
    }),
    createAccount: builder.mutation<Account, Partial<Account>>({
      query: (body) => ({ url: '/accounts', method: 'POST', body }),
      invalidatesTags: ['Account', 'ExpenseStats'],
    }),
    updateAccount: builder.mutation<Account, { id: string; patch: Partial<Account> }>({
      query: ({ id, patch }) => ({ url: `/accounts/${id}`, method: 'PUT', body: patch }),
      invalidatesTags: ['Account', 'ExpenseStats'],
    }),
    deleteAccount: builder.mutation<void, string>({
      query: (id) => ({ url: `/accounts/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Account', 'ExpenseStats'],
    }),
    seedFinanceDefaults: builder.mutation<Account[], string>({
      query: (currency) => ({ url: `/accounts/seed-defaults?currency=${currency}`, method: 'POST' }),
      invalidatesTags: ['Account', 'Category'],
    }),

    categories: builder.query<Category[], { kind?: 'EXPENSE' | 'INCOME' } | void>({
      query: (args) => `/categories${args?.kind ? `?kind=${args.kind}` : ''}`,
      providesTags: ['Category'],
    }),
    createCategory: builder.mutation<Category, Partial<Category>>({
      query: (body) => ({ url: '/categories', method: 'POST', body }),
      invalidatesTags: ['Category'],
    }),
    updateCategory: builder.mutation<Category, { id: string; patch: Partial<Category> }>({
      query: ({ id, patch }) => ({ url: `/categories/${id}`, method: 'PUT', body: patch }),
      invalidatesTags: ['Category', 'ExpenseStats'],
    }),
    deleteCategory: builder.mutation<void, string>({
      query: (id) => ({ url: `/categories/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Category'],
    }),

    transactions: builder.query<
      PageResponse<Transaction>,
      {
        from?: string
        to?: string
        accountId?: string
        categoryId?: string
        type?: string
        search?: string
        page?: number
        size?: number
      } | void
    >({
      query: (args) => {
        const params = new URLSearchParams()
        Object.entries(args ?? {}).forEach(([key, value]) => {
          if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
        })
        return `/expenses?${params.toString()}`
      },
      providesTags: ['Transaction'],
    }),
    createTransaction: builder.mutation<Transaction, Partial<Transaction>>({
      query: (body) => ({ url: '/expenses', method: 'POST', body }),
      invalidatesTags: ['Transaction', 'Account', 'Budget', 'ExpenseStats', 'Analytics'],
    }),
    updateTransaction: builder.mutation<Transaction, { id: string; patch: Partial<Transaction> }>({
      query: ({ id, patch }) => ({ url: `/expenses/${id}`, method: 'PUT', body: patch }),
      invalidatesTags: ['Transaction', 'Account', 'Budget', 'ExpenseStats', 'Analytics'],
    }),
    deleteTransaction: builder.mutation<void, string>({
      query: (id) => ({ url: `/expenses/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Transaction', 'Account', 'Budget', 'ExpenseStats', 'Analytics'],
    }),

    expenseStatistics: builder.query<ExpenseStatistics, { from?: string; to?: string } | void>({
      query: (args) => {
        const params = new URLSearchParams()
        if (args?.from) params.set('from', args.from)
        if (args?.to) params.set('to', args.to)
        return `/expenses/statistics?${params.toString()}`
      },
      providesTags: ['ExpenseStats'],
    }),
    expenseTrend: builder.query<TrendPoint[], number | void>({
      query: (months) => `/expenses/statistics/trend?months=${months ?? 12}`,
      providesTags: ['ExpenseStats'],
    }),

    budgets: builder.query<BudgetStatus[], void>({
      query: () => '/budgets',
      providesTags: ['Budget'],
    }),
    createBudget: builder.mutation<unknown, Record<string, unknown>>({
      query: (body) => ({ url: '/budgets', method: 'POST', body }),
      invalidatesTags: ['Budget', 'ExpenseStats'],
    }),
    updateBudget: builder.mutation<unknown, { id: string; patch: Record<string, unknown> }>({
      query: ({ id, patch }) => ({ url: `/budgets/${id}`, method: 'PUT', body: patch }),
      invalidatesTags: ['Budget', 'ExpenseStats'],
    }),
    deleteBudget: builder.mutation<void, string>({
      query: (id) => ({ url: `/budgets/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Budget', 'ExpenseStats'],
    }),

    recurringRules: builder.query<RecurringRule[], void>({
      query: () => '/budgets/recurring',
      providesTags: ['Recurring'],
    }),
    createRecurring: builder.mutation<RecurringRule, Record<string, unknown>>({
      query: (body) => ({ url: '/budgets/recurring', method: 'POST', body }),
      invalidatesTags: ['Recurring'],
    }),
    toggleRecurring: builder.mutation<unknown, { id: string; active: boolean }>({
      query: ({ id, active }) => ({
        url: `/budgets/recurring/${id}/toggle?active=${active}`,
        method: 'POST',
      }),
      invalidatesTags: ['Recurring'],
    }),
    deleteRecurring: builder.mutation<void, string>({
      query: (id) => ({ url: `/budgets/recurring/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Recurring'],
    }),

    // ============================================================ planning
    tasks: builder.query<
      Task[],
      { status?: TaskStatus; projectId?: string; goalId?: string; search?: string } | void
    >({
      query: (args) => {
        const params = new URLSearchParams()
        Object.entries(args ?? {}).forEach(([key, value]) => {
          if (value) params.set(key, String(value))
        })
        return `/tasks?${params.toString()}`
      },
      providesTags: ['Task'],
    }),
    agenda: builder.query<Agenda, string | void>({
      query: (date) => `/tasks/agenda${date ? `?date=${date}` : ''}`,
      providesTags: ['Task', 'Journal', 'Focus'],
    }),
    planningStatistics: builder.query<PlanningStatistics, number | void>({
      query: (days) => `/tasks/statistics?days=${days ?? 30}`,
      providesTags: ['PlanningStats', 'Task', 'Focus'],
    }),
    createTask: builder.mutation<Task, Partial<Task>>({
      query: (body) => ({ url: '/tasks', method: 'POST', body }),
      invalidatesTags: ['Task', 'PlanningStats', 'Analytics'],
    }),
    updateTask: builder.mutation<Task, { id: string; patch: Partial<Task> }>({
      query: ({ id, patch }) => ({ url: `/tasks/${id}`, method: 'PUT', body: patch }),
      invalidatesTags: ['Task', 'PlanningStats', 'Goal'],
    }),
    setTaskStatus: builder.mutation<Task, { id: string; status: TaskStatus }>({
      query: ({ id, status }) => ({ url: `/tasks/${id}/status?status=${status}`, method: 'POST' }),
      invalidatesTags: ['Task', 'PlanningStats', 'Goal', 'Analytics'],
    }),
    reorderTasks: builder.mutation<Task[], string[]>({
      query: (orderedIds) => ({ url: '/tasks/reorder', method: 'POST', body: { orderedIds } }),
      invalidatesTags: ['Task'],
    }),
    deleteTask: builder.mutation<void, string>({
      query: (id) => ({ url: `/tasks/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Task', 'PlanningStats'],
    }),

    goals: builder.query<Goal[], { status?: string } | void>({
      query: (args) => `/goals${args?.status ? `?status=${args.status}` : ''}`,
      providesTags: ['Goal'],
    }),
    createGoal: builder.mutation<Goal, Partial<Goal>>({
      query: (body) => ({ url: '/goals', method: 'POST', body }),
      invalidatesTags: ['Goal', 'PlanningStats'],
    }),
    updateGoal: builder.mutation<Goal, { id: string; patch: Partial<Goal> }>({
      query: ({ id, patch }) => ({ url: `/goals/${id}`, method: 'PUT', body: patch }),
      invalidatesTags: ['Goal', 'PlanningStats'],
    }),
    deleteGoal: builder.mutation<void, string>({
      query: (id) => ({ url: `/goals/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Goal', 'PlanningStats'],
    }),

    projects: builder.query<Project[], void>({ query: () => '/projects', providesTags: ['Project'] }),
    createProject: builder.mutation<Project, Partial<Project>>({
      query: (body) => ({ url: '/projects', method: 'POST', body }),
      invalidatesTags: ['Project'],
    }),
    updateProject: builder.mutation<Project, { id: string; patch: Partial<Project> }>({
      query: ({ id, patch }) => ({ url: `/projects/${id}`, method: 'PUT', body: patch }),
      // 'Task' as well: a task row shows its project's name, so a rename that only
      // invalidated 'Project' would leave the old name on screen until a refetch.
      invalidatesTags: ['Project', 'Task'],
    }),
    deleteProject: builder.mutation<void, string>({
      query: (id) => ({ url: `/projects/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Project', 'Task'],
    }),

    currentFocus: builder.query<FocusSession | null, void>({
      query: () => '/focus/current',
      providesTags: ['Focus'],
    }),
    focusHistory: builder.query<FocusSession[], { from?: string; to?: string } | void>({
      query: (args) => {
        const params = new URLSearchParams()
        if (args?.from) params.set('from', args.from)
        if (args?.to) params.set('to', args.to)
        return `/focus?${params.toString()}`
      },
      providesTags: ['Focus'],
    }),
    startFocus: builder.mutation<
      FocusSession,
      { taskId?: string; type?: string; plannedMinutes?: number }
    >({
      query: (body) => ({ url: '/focus/start', method: 'POST', body }),
      invalidatesTags: ['Focus'],
    }),
    endFocus: builder.mutation<
      FocusSession,
      { id: string; focusScore?: number; note?: string; completed?: boolean }
    >({
      query: ({ id, ...body }) => ({ url: `/focus/${id}/end`, method: 'POST', body }),
      invalidatesTags: ['Focus', 'PlanningStats', 'Task', 'Analytics'],
    }),

    journal: builder.query<JournalEntry[], { from?: string; to?: string } | void>({
      query: (args) => {
        const params = new URLSearchParams()
        if (args?.from) params.set('from', args.from)
        if (args?.to) params.set('to', args.to)
        return `/journal?${params.toString()}`
      },
      providesTags: ['Journal'],
    }),
    saveJournal: builder.mutation<JournalEntry, Partial<JournalEntry>>({
      query: (body) => ({ url: '/journal', method: 'PUT', body }),
      invalidatesTags: ['Journal', 'PlanningStats', 'Analytics'],
    }),

    // =========================================================== analytics
    lifeOverview: builder.query<LifeOverview, { from?: string; to?: string } | void>({
      query: (args) => {
        const params = new URLSearchParams()
        if (args?.from) params.set('from', args.from)
        if (args?.to) params.set('to', args.to)
        return `/analytics/overview?${params.toString()}`
      },
      providesTags: ['Analytics'],
    }),

    // ======================================================== notifications
    notifications: builder.query<AppNotification[], { unreadOnly?: boolean } | void>({
      query: (args) => `/notifications?unreadOnly=${args?.unreadOnly ? 'true' : 'false'}`,
      providesTags: ['Notification'],
    }),
    unreadCount: builder.query<{ unread: number }, void>({
      query: () => '/notifications/unread-count',
      providesTags: ['Notification'],
    }),
    markNotificationRead: builder.mutation<unknown, string>({
      query: (id) => ({ url: `/notifications/${id}/read`, method: 'POST' }),
      invalidatesTags: ['Notification'],
    }),
    markAllNotificationsRead: builder.mutation<unknown, void>({
      query: () => ({ url: '/notifications/read-all', method: 'POST' }),
      invalidatesTags: ['Notification'],
    }),
    clearNotifications: builder.mutation<void, void>({
      query: () => ({ url: '/notifications', method: 'DELETE' }),
      invalidatesTags: ['Notification'],
    }),
    notificationPreferences: builder.query<NotificationPreferences, void>({
      query: () => '/notifications/preferences',
      providesTags: ['NotificationPrefs'],
    }),
    updateNotificationPreferences: builder.mutation<
      NotificationPreferences,
      Partial<NotificationPreferences>
    >({
      query: (body) => ({ url: '/notifications/preferences', method: 'PUT', body }),
      invalidatesTags: ['NotificationPrefs'],
    }),
    notificationKinds: builder.query<NotificationKind[], void>({
      query: () => '/notifications/kinds',
    }),
    sendTestNotification: builder.mutation<
      { sent: boolean; pushDevices: number; openTabs: number },
      void
    >({
      query: () => ({ url: '/notifications/test', method: 'POST' }),
      invalidatesTags: ['Notification'],
    }),

    // =============================================================== admin
    adminOverview: builder.query<AdminOverview, void>({
      query: () => '/admin/overview',
      providesTags: ['AdminOverview'],
    }),
    adminUsers: builder.query<
      PageResponse<UserView>,
      { q?: string; enabled?: boolean; page?: number; size?: number } | void
    >({
      query: (args) => {
        const params = new URLSearchParams()
        Object.entries(args ?? {}).forEach(([key, value]) => {
          if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
        })
        return `/admin/users?${params.toString()}`
      },
      providesTags: ['AdminUser'],
    }),
    adminUpdateUser: builder.mutation<
      UserView,
      { id: string; patch: { enabled?: boolean; roles?: string[]; displayName?: string } }
    >({
      query: ({ id, patch }) => ({ url: `/admin/users/${id}`, method: 'PATCH', body: patch }),
      invalidatesTags: ['AdminUser', 'AdminOverview', 'Audit'],
    }),
    adminForceSignOut: builder.mutation<{ message: string }, string>({
      query: (id) => ({ url: `/admin/users/${id}/sign-out`, method: 'POST' }),
      invalidatesTags: ['AdminUser', 'AdminOverview'],
    }),
    adminAudit: builder.query<
      PageResponse<AuditEntry>,
      { userId?: string; action?: string; outcome?: string; page?: number; size?: number } | void
    >({
      query: (args) => {
        const params = new URLSearchParams()
        Object.entries(args ?? {}).forEach(([key, value]) => {
          if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
        })
        return `/admin/audit?${params.toString()}`
      },
      providesTags: ['Audit'],
    }),
    adminServices: builder.query<ServiceInstance[], void>({ query: () => '/admin/system/services' }),
    adminSystemHealth: builder.query<SystemHealth, void>({ query: () => '/admin/system/health' }),
  }),
})

export const {
  useLoginMutation,
  useVerifyTwoFactorMutation,
  useRegisterMutation,
  useLogoutMutation,
  useAuthProvidersQuery,
  useLazyGoogleAuthUrlQuery,
  useGoogleCallbackMutation,
  useMeQuery,
  useUpdateProfileMutation,
  useChangePasswordMutation,
  useSessionsQuery,
  useRevokeSessionsMutation,
  useSetupTotpMutation,
  useConfirmTotpMutation,
  useDisableTotpMutation,

  useHabitsQuery,
  useHabitQuery,
  useHabitsTodayQuery,
  useHabitHeatmapQuery,
  useHabitLogsQuery,
  useHabitInsightsQuery,
  useCreateHabitMutation,
  useUpdateHabitMutation,
  useArchiveHabitMutation,
  useDeleteHabitMutation,
  useReorderHabitsMutation,
  useCheckInMutation,
  useUndoCheckInMutation,
  useGameStatsQuery,
  useAchievementsQuery,

  useAccountsQuery,
  useCreateAccountMutation,
  useUpdateAccountMutation,
  useDeleteAccountMutation,
  useSeedFinanceDefaultsMutation,
  useCategoriesQuery,
  useCreateCategoryMutation,
  useUpdateCategoryMutation,
  useDeleteCategoryMutation,
  useTransactionsQuery,
  useCreateTransactionMutation,
  useUpdateTransactionMutation,
  useDeleteTransactionMutation,
  useExpenseStatisticsQuery,
  useExpenseTrendQuery,
  useBudgetsQuery,
  useCreateBudgetMutation,
  useUpdateBudgetMutation,
  useDeleteBudgetMutation,
  useRecurringRulesQuery,
  useCreateRecurringMutation,
  useToggleRecurringMutation,
  useDeleteRecurringMutation,

  useTasksQuery,
  useAgendaQuery,
  usePlanningStatisticsQuery,
  useCreateTaskMutation,
  useUpdateTaskMutation,
  useSetTaskStatusMutation,
  useReorderTasksMutation,
  useDeleteTaskMutation,
  useGoalsQuery,
  useCreateGoalMutation,
  useUpdateGoalMutation,
  useDeleteGoalMutation,
  useProjectsQuery,
  useCreateProjectMutation,
  useUpdateProjectMutation,
  useDeleteProjectMutation,
  useCurrentFocusQuery,
  useFocusHistoryQuery,
  useStartFocusMutation,
  useEndFocusMutation,
  useJournalQuery,
  useSaveJournalMutation,

  useLifeOverviewQuery,

  useNotificationsQuery,
  useUnreadCountQuery,
  useMarkNotificationReadMutation,
  useMarkAllNotificationsReadMutation,
  useClearNotificationsMutation,
  useNotificationPreferencesQuery,
  useUpdateNotificationPreferencesMutation,
  useNotificationKindsQuery,
  useSendTestNotificationMutation,

  useAdminOverviewQuery,
  useAdminUsersQuery,
  useAdminUpdateUserMutation,
  useAdminForceSignOutMutation,
  useAdminAuditQuery,
  useAdminServicesQuery,
  useAdminSystemHealthQuery,
} = api
