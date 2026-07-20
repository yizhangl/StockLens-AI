import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type {
  ComparisonMode,
  ComparisonPeriod,
  PricePerformance,
} from '../types/comparison.ts'
import { COMPARISON_MODES, COMPARISON_PERIODS } from '../types/comparison.ts'
import { formatChartValue, toChartData } from '../utils/chart.ts'
import { formatDate, formatPercentagePoints } from '../utils/formatters.ts'

interface PricePerformanceChartProps {
  performance: PricePerformance
  leftTicker: string
  rightTicker: string
  selectedPeriod: ComparisonPeriod
  selectedMode: ComparisonMode
  isBusy: boolean
  onPeriodChange: (period: ComparisonPeriod) => void
  onModeChange: (mode: ComparisonMode) => void
}

function shortDate(value: string): string {
  const date = new Date(`${value}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('en-US', { month: 'short', year: '2-digit' }).format(date)
}

export function PricePerformanceChart({
  performance,
  leftTicker,
  rightTicker,
  selectedPeriod,
  selectedMode,
  isBusy,
  onPeriodChange,
  onModeChange,
}: PricePerformanceChartProps) {
  const data = toChartData(performance.series)
  const returnMode = performance.mode === 'RETURN'
  const currencyMismatch =
    !returnMode &&
    performance.leftCurrency &&
    performance.rightCurrency &&
    performance.leftCurrency !== performance.rightCurrency

  return (
    <section className="panel performance-panel" aria-labelledby="price-performance-title" aria-busy={isBusy}>
      <div className="section-heading section-heading--controls">
        <div>
          <p className="section-eyebrow">Aligned daily data</p>
          <h2 id="price-performance-title">Price performance</h2>
        </div>
        <div className="chart-controls">
          <div className="segmented-control" aria-label="Performance period">
            {COMPARISON_PERIODS.map((period) => (
              <button
                type="button"
                key={period}
                className={selectedPeriod === period ? 'is-active' : ''}
                aria-pressed={selectedPeriod === period}
                onClick={() => onPeriodChange(period)}
              >
                {period}
              </button>
            ))}
          </div>
          <div className="segmented-control" aria-label="Chart mode">
            {COMPARISON_MODES.map((mode) => (
              <button
                type="button"
                key={mode}
                className={selectedMode === mode ? 'is-active' : ''}
                aria-pressed={selectedMode === mode}
                onClick={() => onModeChange(mode)}
              >
                {mode === 'PRICE' ? 'Price' : 'Return %'}
              </button>
            ))}
          </div>
        </div>
      </div>

      <p className="chart-summary">
        {data.length > 0
          ? `${data.length} common trading days from ${formatDate(performance.startDate)} to ${formatDate(performance.endDate)}.`
          : 'No common historical points are available for this period.'}
        {returnMode
          ? ` ${leftTicker} returned ${formatPercentagePoints(performance.leftReturnPercent)} and ${rightTicker} returned ${formatPercentagePoints(performance.rightReturnPercent)}.`
          : ` Values show raw closing prices in each listed currency.${currencyMismatch ? ` ${leftTicker} uses ${performance.leftCurrency} and ${rightTicker} uses ${performance.rightCurrency}; no currency conversion or raw-price winner is applied.` : ''}`}
      </p>

      {data.length > 0 ? (
        <div className="chart-area" role="img" aria-label={`${leftTicker} and ${rightTicker} ${returnMode ? 'return percentage' : 'price'} chart`}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 12, right: 18, left: 0, bottom: 0 }}>
              <CartesianGrid stroke="#e8edf5" strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="date"
                tickFormatter={shortDate}
                tick={{ fill: '#64748b', fontSize: 12 }}
                tickLine={false}
                axisLine={{ stroke: '#dce3ee' }}
                minTickGap={42}
              />
              <YAxis
                width={58}
                tickFormatter={(value) =>
                  returnMode
                    ? formatPercentagePoints(Number(value), { digits: 0 })
                    : formatChartValue(Number(value), 'PRICE', performance.leftCurrency)
                }
                tick={{ fill: '#64748b', fontSize: 12 }}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip
                labelFormatter={(label) => formatDate(String(label))}
                formatter={(value, name) => [
                  formatChartValue(
                    Number(value),
                    performance.mode,
                    name === leftTicker ? performance.leftCurrency : performance.rightCurrency,
                  ),
                  String(name),
                ]}
                contentStyle={{ borderRadius: 12, borderColor: '#dce3ee' }}
              />
              <Legend
                verticalAlign="top"
                align="right"
                iconType="circle"
                wrapperStyle={{ color: '#3e4b5f', fontSize: 12, fontWeight: 700 }}
              />
              <Line
                type="monotone"
                dataKey="left"
                name={leftTicker}
                stroke="#1769e0"
                strokeWidth={2.5}
                dot={false}
                activeDot={{ r: 4 }}
                isAnimationActive={false}
              />
              <Line
                type="monotone"
                dataKey="right"
                name={rightTicker}
                stroke="#f97316"
                strokeWidth={2.5}
                dot={false}
                activeDot={{ r: 4 }}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : <div className="chart-empty">Historical comparison unavailable</div>}

      {isBusy ? <p className="chart-updating" role="status">Updating chart…</p> : null}
    </section>
  )
}
