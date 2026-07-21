import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { comparisonFixture } from '../../../test/comparisonFixture.ts'
import { CompanySummaryCard } from './CompanySummaryCard.tsx'
import { DataProvenanceFooter } from './DataProvenanceFooter.tsx'
import { MetricCategoryCard } from './MetricCategoryCard.tsx'
import { RecentDevelopments } from './RecentDevelopments.tsx'

describe('dashboard sections', () => {
  it('renders company values using percentage-point conventions and safe links', () => {
    const fixture = comparisonFixture()
    render(<CompanySummaryCard company={fixture.left} side="left" />)
    expect(screen.getByRole('heading', { name: 'AAPL' })).toBeInTheDocument()
    expect(screen.getByText(/\+0\.71%/)).toBeInTheDocument()
    expect(screen.getByText(/\$2\.96T/)).toBeInTheDocument()
    expect(screen.getByText(/latest available/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /company website/i })).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('lets keyboard and pointer users expand a long company description', async () => {
    const fixture = comparisonFixture()
    fixture.left.description = 'Apple builds and supports consumer technology products. '.repeat(8)
    render(<CompanySummaryCard company={fixture.left} side="left" />)

    const description = screen.getByText(/Apple builds and supports/i)
    const toggle = screen.getByRole('button', { name: /read full description/i })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(description).not.toHaveClass('company-description--expanded')

    await userEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(description).toHaveClass('company-description--expanded')
  })

  it('formats metric fractions and exposes backend outcome text', () => {
    const fixture = comparisonFixture()
    render(
      <MetricCategoryCard
        group={fixture.metricGroups[1]!}
        leftTicker="AAPL"
        rightTicker="MSFT"
      />,
    )
    expect(screen.getByText('45.9%')).toBeInTheDocument()
    expect(screen.getByText('69.2%')).toBeInTheDocument()
    expect(screen.getByText('Supported advantage')).toBeInTheDocument()
  })

  it('renders news as text-only safe links and an empty-side state', () => {
    const fixture = comparisonFixture()
    render(
      <RecentDevelopments
        leftTicker="AAPL"
        rightTicker="MSFT"
        leftArticles={fixture.news.left}
        rightArticles={[]}
      />,
    )
    expect(screen.getByRole('link', { name: /apple announces/i })).toHaveAttribute('target', '_blank')
    expect(screen.getByText(/no recent developments/i)).toBeInTheDocument()
  })

  it('renders honest provenance and disclaimer', () => {
    const fixture = comparisonFixture()
    render(<DataProvenanceFooter provenance={fixture.provenance} />)
    expect(screen.getByText('Financial Modeling Prep')).toBeInTheDocument()
    expect(screen.getByText('Yahoo Finance')).toBeInTheDocument()
    expect(screen.getByText(/Jul 17, 2026.*Jul 19, 2026/)).toBeInTheDocument()
    expect(screen.getByText(/does not constitute financial advice/i)).toBeInTheDocument()
    expect(screen.queryByText(/buy|sell|winner/i)).not.toBeInTheDocument()
  })
})
