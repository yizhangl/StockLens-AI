import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { comparisonFixture } from '../../../test/comparisonFixture.ts'
import { PricePerformanceChart } from './PricePerformanceChart.tsx'

describe('PricePerformanceChart', () => {
  it('renders accessible return summaries and emits control changes', async () => {
    const user = userEvent.setup()
    const onPeriodChange = vi.fn()
    const onModeChange = vi.fn()
    const fixture = comparisonFixture()
    render(
      <PricePerformanceChart
        performance={fixture.pricePerformance}
        leftTicker="AAPL"
        rightTicker="MSFT"
        selectedPeriod="1Y"
        selectedMode="RETURN"
        isBusy={false}
        onPeriodChange={onPeriodChange}
        onModeChange={onModeChange}
      />,
    )

    expect(screen.getByText(/AAPL returned 18\.4% and MSFT returned -7\.2%/)).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /AAPL and MSFT return percentage chart/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '6M' }))
    await user.click(screen.getByRole('button', { name: 'Price' }))
    expect(onPeriodChange).toHaveBeenCalledWith('6M')
    expect(onModeChange).toHaveBeenCalledWith('PRICE')
  })

  it('handles an empty aligned series', () => {
    const fixture = comparisonFixture()
    render(
      <PricePerformanceChart
        performance={{ ...fixture.pricePerformance, series: [], pointCount: 0 }}
        leftTicker="AAPL"
        rightTicker="MSFT"
        selectedPeriod="1Y"
        selectedMode="RETURN"
        isBusy={false}
        onPeriodChange={vi.fn()}
        onModeChange={vi.fn()}
      />,
    )
    expect(screen.getByText(/no common historical points/i)).toBeInTheDocument()
    expect(screen.getByText(/historical comparison unavailable/i)).toBeInTheDocument()
  })

  it('discloses mismatched currencies without declaring a raw-price winner', () => {
    const fixture = comparisonFixture()
    render(
      <PricePerformanceChart
        performance={{
          ...fixture.pricePerformance,
          mode: 'PRICE',
          leftCurrency: 'USD',
          rightCurrency: 'CAD',
        }}
        leftTicker="AAPL"
        rightTicker="SHOP"
        selectedPeriod="1Y"
        selectedMode="PRICE"
        isBusy={false}
        onPeriodChange={vi.fn()}
        onModeChange={vi.fn()}
      />,
    )
    expect(screen.getByText(/AAPL uses USD and SHOP uses CAD/i)).toBeInTheDocument()
    expect(screen.getByText(/no currency conversion or raw-price winner/i)).toBeInTheDocument()
  })
})
