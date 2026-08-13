import { Button, InputNumber } from 'antd'
import { BASE_CURRENCY, formatMoney } from '@/app/money'

/**
 * An amount field built for VND.
 *
 * Everyday sums here run to five and six digits, and typing them raw is both
 * slow and the easiest place in the app to make a silent mistake: 260000 and
 * 2600000 look alike at a glance and differ by a factor of ten. So the field
 * does three things a plain number box does not.
 *
 * It groups as you type (`260.000`), so the magnitude is readable rather than
 * counted. It offers a `000` button, because "26" then one tap is fewer, larger
 * targets than six digits on a phone keypad. And it echoes the value back
 * formatted underneath, which is where an extra zero actually gets caught.
 *
 * Written as a controlled `value`/`onChange` pair so it drops straight into an
 * antd `Form.Item` in place of an `InputNumber`.
 */
export function MoneyInput({
  value,
  onChange,
  min = 0,
  autoFocus,
  placeholder,
  disabled,
}: {
  value?: number | null
  onChange?: (value: number | null) => void
  min?: number
  autoFocus?: boolean
  placeholder?: string
  disabled?: boolean
}) {
  const current = value ?? null

  // Appending zeros is multiplication, but only once there is something to
  // multiply — otherwise the button would sit there doing nothing to a 0.
  const appendZeros = (count: number) => {
    if (!current) return
    onChange?.(current * 10 ** count)
  }

  const canAppend = !disabled && !!current

  return (
    <div>
      <div style={{ display: 'flex', gap: 6 }}>
        <InputNumber
          value={current}
          onChange={(next) => onChange?.(next as number | null)}
          min={min}
          autoFocus={autoFocus}
          placeholder={placeholder}
          disabled={disabled}
          style={{ flex: 1 }}
          inputMode="numeric"
          controls={false}
          // Grouped while typing; the parser strips anything that is not a digit
          // so pasted text like "260.000 ₫" is accepted rather than rejected.
          formatter={(raw) =>
            raw === undefined || raw === null
              ? ''
              : new Intl.NumberFormat('vi-VN').format(Number(raw))
          }
          parser={(display) => {
            const digits = (display ?? '').replace(/[^\d]/g, '')
            return digits ? Number(digits) : (null as unknown as number)
          }}
        />
        {/* No label or tooltip: "000" on a button next to an amount already
            says what it does, and everyday VND sums are thousands anyway. */}
        <Button
          className="tabular"
          onClick={() => appendZeros(3)}
          disabled={!canAppend}
          style={{ minWidth: 52 }}
        >
          000
        </Button>
      </div>

      {/* The check against a mistyped zero. Only shown once there is a figure,
          so an empty form stays quiet. */}
      <div
        style={{
          marginTop: 4,
          minHeight: 16,
          fontSize: 12,
          color: 'var(--on-surface-muted)',
        }}
        className="tabular"
      >
        {current ? formatMoney(current) : `Amounts are in ${BASE_CURRENCY}`}
      </div>
    </div>
  )
}
