import type { ThemeConfig } from 'antd'
import { theme as antdTheme } from 'antd'

export type ThemeMode = 'light' | 'dark'

/**
 * Ant Design mapped onto the Material Design 3 monochrome tokens.
 *
 * Ant ships a blue-forward palette and 6px corners; Material wants a neutral
 * accent, a 12/16/28px corner scale, 48dp touch targets and its own easing. All
 * of that is expressed here as tokens rather than as CSS overrides, so third-party
 * Ant components inherit it instead of fighting it.
 */

const shared = {
  fontFamily:
    "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  fontSize: 14,
  fontSizeHeading1: 32,
  fontSizeHeading2: 28,
  fontSizeHeading3: 22,
  fontSizeHeading4: 18,
  fontSizeHeading5: 16,
  fontSizeLG: 16,
  fontSizeSM: 13,
  lineHeight: 1.55,

  // MD3 corner scale.
  borderRadius: 12,
  borderRadiusLG: 16,
  borderRadiusSM: 8,
  borderRadiusXS: 4,

  // MD3 minimum touch target is 48dp; Ant's default `controlHeight` is 32.
  controlHeight: 40,
  controlHeightLG: 48,
  controlHeightSM: 32,

  wireframe: false,
  motionEaseInOut: 'cubic-bezier(0.2, 0, 0, 1)',
  motionEaseOut: 'cubic-bezier(0.05, 0.7, 0.1, 1)',
  motionDurationMid: '0.25s',
  motionDurationSlow: '0.4s',
} as const

const lightTokens = {
  ...shared,
  colorPrimary: '#101010',
  colorInfo: '#101010',
  colorLink: '#101010',
  colorSuccess: '#1f7a3d',
  colorWarning: '#8a6100',
  colorError: '#a41d1d',

  colorBgBase: '#ffffff',
  colorBgLayout: '#f6f6f6',
  colorBgContainer: '#ffffff',
  colorBgElevated: '#ffffff',
  colorBgSpotlight: '#171717',

  colorText: '#101010',
  colorTextSecondary: '#565656',
  colorTextTertiary: '#8a8a8a',
  colorTextQuaternary: '#b4b4b4',

  colorBorder: '#c6c6c6',
  colorBorderSecondary: '#e4e4e4',
  colorSplit: '#ececec',

  colorFillSecondary: 'rgba(16,16,16,0.06)',
  colorFillTertiary: 'rgba(16,16,16,0.04)',
  colorFillQuaternary: 'rgba(16,16,16,0.02)',

  boxShadow: '0 1px 2px rgba(0,0,0,0.06), 0 2px 6px rgba(0,0,0,0.06)',
  boxShadowSecondary: '0 6px 12px rgba(0,0,0,0.08), 0 2px 4px rgba(0,0,0,0.06)',
}

const darkTokens = {
  ...shared,
  colorPrimary: '#f4f4f4',
  colorInfo: '#f4f4f4',
  colorLink: '#f4f4f4',
  colorSuccess: '#5cc98a',
  colorWarning: '#e0b355',
  colorError: '#f0736e',

  colorBgBase: '#0a0a0a',
  colorBgLayout: '#0a0a0a',
  colorBgContainer: '#121212',
  colorBgElevated: '#1f1f1f',
  colorBgSpotlight: '#ececec',

  colorText: '#f4f4f4',
  colorTextSecondary: '#a9a9a9',
  colorTextTertiary: '#7c7c7c',
  colorTextQuaternary: '#575757',

  colorBorder: '#3a3a3a',
  colorBorderSecondary: '#2c2c2c',
  colorSplit: '#232323',

  colorFillSecondary: 'rgba(255,255,255,0.08)',
  colorFillTertiary: 'rgba(255,255,255,0.05)',
  colorFillQuaternary: 'rgba(255,255,255,0.03)',

  boxShadow: '0 2px 6px rgba(0,0,0,0.55)',
  boxShadowSecondary: '0 6px 16px rgba(0,0,0,0.65)',
}

const componentOverrides = (mode: ThemeMode): ThemeConfig['components'] => ({
  Button: {
    // A monochrome button has no hue to signal emphasis, so weight and shape do
    // the work: filled = primary, tonal = secondary, text = tertiary.
    borderRadius: 999,
    borderRadiusLG: 999,
    borderRadiusSM: 999,
    fontWeight: 600,
    primaryShadow: 'none',
    defaultShadow: 'none',
    dangerShadow: 'none',
    paddingInline: 20,
    paddingInlineLG: 26,
  },
  Card: {
    borderRadiusLG: 16,
    paddingLG: 20,
    headerFontSize: 16,
    headerHeight: 56,
  },
  Modal: { borderRadiusLG: 28, titleFontSize: 20 },
  Drawer: { footerPaddingBlock: 16 },
  Input: { borderRadius: 12, paddingBlock: 8 },
  InputNumber: { borderRadius: 12 },
  Select: { borderRadius: 12, optionSelectedFontWeight: 600 },
  DatePicker: { borderRadius: 12 },
  Segmented: {
    borderRadius: 999,
    itemSelectedBg: mode === 'dark' ? '#f4f4f4' : '#101010',
    itemSelectedColor: mode === 'dark' ? '#101010' : '#ffffff',
    trackPadding: 4,
  },
  Tabs: { horizontalItemPadding: '12px 0', titleFontSize: 15 },
  Table: {
    borderRadiusLG: 16,
    headerBg: mode === 'dark' ? '#171717' : '#f6f6f6',
    headerSplitColor: 'transparent',
    rowHoverBg: mode === 'dark' ? 'rgba(255,255,255,0.05)' : 'rgba(16,16,16,0.035)',
    cellPaddingBlock: 14,
  },
  Tag: { borderRadiusSM: 999, defaultBg: mode === 'dark' ? '#1f1f1f' : '#f0f0f0' },
  Progress: { defaultColor: mode === 'dark' ? '#f4f4f4' : '#101010' },
  Menu: {
    itemBorderRadius: 12,
    itemMarginInline: 8,
    itemHeight: 44,
    itemSelectedBg: mode === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(16,16,16,0.07)',
    itemSelectedColor: mode === 'dark' ? '#ffffff' : '#101010',
    activeBarWidth: 0,
    itemHoverBg: mode === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(16,16,16,0.04)',
  },
  Tooltip: {
    borderRadius: 8,
    colorBgSpotlight: mode === 'dark' ? '#ececec' : '#171717',
    colorTextLightSolid: mode === 'dark' ? '#151515' : '#f5f5f5',
  },
  Statistic: { contentFontSize: 30, titleFontSize: 13 },
  Layout: {
    headerBg: mode === 'dark' ? '#0a0a0a' : '#ffffff',
    bodyBg: mode === 'dark' ? '#0a0a0a' : '#f6f6f6',
    siderBg: mode === 'dark' ? '#0a0a0a' : '#ffffff',
    headerHeight: 64,
    headerPadding: '0 20px',
  },
  Empty: { colorTextDescription: mode === 'dark' ? '#7c7c7c' : '#8a8a8a' },
  Divider: { colorSplit: mode === 'dark' ? '#232323' : '#ececec' },
})

export function buildTheme(mode: ThemeMode): ThemeConfig {
  return {
    algorithm: mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: mode === 'dark' ? darkTokens : lightTokens,
    components: componentOverrides(mode),
    cssVar: true,
    hashed: false,
  }
}
