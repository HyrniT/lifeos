import { useMemo, useState } from 'react'
import { Progress, Segmented, Tag } from 'antd'
import dayjs from 'dayjs'
import { motion } from 'framer-motion'
import { Coins, Flame, Heart, Lock, Sparkles, Trophy } from 'lucide-react'
import { useAchievementsQuery, useGameStatsQuery } from '@/app/api'
import { ProgressRing, StatTile } from '@/components/charts'
import { DynamicIcon, PageHeader, PanelSkeleton, Section, StaggerItem, StaggerList } from '@/components/ui'

const TIER_ORDER = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM'] as const

export function AchievementsPage() {
  const [filter, setFilter] = useState<'all' | 'unlocked' | 'locked'>('all')
  const { data: achievements = [], isLoading } = useAchievementsQuery()
  const { data: stats } = useGameStatsQuery()

  const visible = useMemo(() => {
    const sorted = [...achievements].sort((a, b) => {
      if (a.unlocked !== b.unlocked) return a.unlocked ? -1 : 1
      return TIER_ORDER.indexOf(a.tier) - TIER_ORDER.indexOf(b.tier)
    })
    if (filter === 'unlocked') return sorted.filter((item) => item.unlocked)
    if (filter === 'locked') return sorted.filter((item) => !item.unlocked)
    return sorted
  }, [achievements, filter])

  const unlocked = achievements.filter((item) => item.unlocked).length

  return (
    <>
      <PageHeader
        title="Achievements"
        subtitle="Milestones you have actually reached, and how close the rest are."
        actions={
          <Segmented
            value={filter}
            onChange={(value) => setFilter(value as typeof filter)}
            options={[
              { label: 'All', value: 'all' },
              { label: `Unlocked (${unlocked})`, value: 'unlocked' },
              { label: 'Locked', value: 'locked' },
            ]}
          />
        }
      />

      <div
        className="lo-panel lo-grain"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 32,
          flexWrap: 'wrap',
          marginBottom: 28,
        }}
      >
        <ProgressRing
          value={stats?.levelProgress ?? 0}
          size={140}
          thickness={12}
          label={String(stats?.level ?? 1)}
          caption="level"
        />
        <div style={{ flex: 1, minWidth: 220 }}>
          <h3 style={{ margin: '0 0 6px', fontSize: 'var(--title-lg)', fontWeight: 720 }}>
            {stats?.xp ?? 0} XP total
          </h3>
          <p style={{ margin: '0 0 14px', color: 'var(--on-surface-variant)', fontSize: 14 }}>
            {stats ? `${stats.xpForNextLevel - stats.xpIntoLevel} XP to level ${stats.level + 1}` : '—'}
          </p>
          <Progress
            percent={Math.round((stats?.levelProgress ?? 0) * 100)}
            strokeColor="var(--on-surface)"
            trailColor="var(--outline-variant)"
          />
        </div>
      </div>

      <div className="lo-grid lo-grid--stats">
        <StatTile
          label="Unlocked"
          value={`${unlocked}/${achievements.length}`}
          icon={<Trophy size={17} />}
        />
        <StatTile
          label="Current streak"
          value={`${stats?.currentDayStreak ?? 0}d`}
          caption={`Longest ${stats?.longestDayStreak ?? 0}d`}
          icon={<Flame size={17} />}
        />
        <StatTile label="Coins" value={stats?.coins ?? 0} icon={<Coins size={17} />} />
        <StatTile
          label="Vitality"
          value={`${stats?.hp ?? 100}/100`}
          caption={`${stats?.streakFreezes ?? 0} streak freeze${stats?.streakFreezes === 1 ? '' : 's'} banked`}
          icon={<Heart size={17} />}
        />
      </div>

      <Section>
        {isLoading ? (
          <div className="lo-grid lo-grid--cards">
            {[0, 1, 2, 3, 4, 5].map((index) => (
              <PanelSkeleton key={index} rows={2} />
            ))}
          </div>
        ) : (
          <StaggerList>
            <div className="lo-grid lo-grid--cards">
              {visible.map((achievement) => (
                <StaggerItem key={achievement.code}>
                  <motion.div
                    className="lo-panel"
                    whileHover={{ y: -3 }}
                    transition={{ duration: 0.18 }}
                    style={{
                      height: '100%',
                      opacity: achievement.unlocked ? 1 : 0.72,
                      // Unlocked entries also get a solid rule, so the state is not
                      // carried by opacity alone.
                      borderLeft: achievement.unlocked
                        ? '3px solid var(--on-surface)'
                        : '1px solid var(--outline-variant)',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                      <span
                        style={{
                          display: 'grid',
                          placeItems: 'center',
                          width: 44,
                          height: 44,
                          borderRadius: 14,
                          flexShrink: 0,
                          background: achievement.unlocked
                            ? 'var(--on-surface)'
                            : 'var(--surface-container)',
                          color: achievement.unlocked ? 'var(--surface)' : 'var(--on-surface-muted)',
                        }}
                      >
                        {achievement.unlocked ? (
                          <DynamicIcon name={achievement.icon} size={20} />
                        ) : (
                          <Lock size={18} />
                        )}
                      </span>

                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            flexWrap: 'wrap',
                          }}
                        >
                          <strong style={{ fontSize: 15 }}>{achievement.title}</strong>
                          <Tag style={{ margin: 0, fontSize: 10, letterSpacing: '0.06em' }}>
                            {achievement.tier}
                          </Tag>
                        </div>
                        <p
                          style={{
                            margin: '4px 0 0',
                            fontSize: 13,
                            color: 'var(--on-surface-variant)',
                          }}
                        >
                          {achievement.description}
                        </p>
                      </div>
                    </div>

                    <div style={{ marginTop: 14 }}>
                      {achievement.unlocked ? (
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 6,
                            fontSize: 12,
                            color: 'var(--on-surface-muted)',
                          }}
                        >
                          <Sparkles size={13} />
                          Unlocked{' '}
                          {achievement.unlockedAt
                            ? dayjs(achievement.unlockedAt).format('D MMM YYYY')
                            : ''}
                        </div>
                      ) : (
                        <>
                          <Progress
                            percent={Math.round(achievement.progress * 100)}
                            showInfo={false}
                            size="small"
                            strokeColor="var(--on-surface)"
                            trailColor="var(--outline-variant)"
                          />
                          <span style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                            {Math.round(achievement.progress * 100)}% there
                          </span>
                        </>
                      )}
                    </div>
                  </motion.div>
                </StaggerItem>
              ))}
            </div>
          </StaggerList>
        )}
      </Section>
    </>
  )
}
