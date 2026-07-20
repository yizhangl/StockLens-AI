import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { StockSearchForm } from './StockSearchForm.tsx'

const query = { left: 'AAPL', right: 'MSFT', period: '1Y', mode: 'RETURN' } as const

describe('StockSearchForm', () => {
  it('normalizes and submits two valid tickers with Enter', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<StockSearchForm query={query} isBusy={false} onSubmit={onSubmit} />)

    const left = screen.getByLabelText(/first company ticker/i)
    await user.clear(left)
    await user.type(left, ' googl ')
    const right = screen.getByLabelText(/second company ticker/i)
    await user.clear(right)
    await user.type(right, 'amzn{Enter}')

    expect(onSubmit).toHaveBeenCalledWith('GOOGL', 'AMZN')
  })

  it('shows inline errors and focuses the invalid field', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<StockSearchForm query={query} isBusy={false} onSubmit={onSubmit} />)

    const left = screen.getByLabelText(/first company ticker/i)
    await user.clear(left)
    await user.click(screen.getByRole('button', { name: 'Compare' }))

    expect(screen.getByText(/enter the first ticker/i)).toBeInTheDocument()
    expect(left).toHaveFocus()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects duplicates and keeps inputs editable while the request is busy', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<StockSearchForm query={query} isBusy={true} onSubmit={onSubmit} />)
    expect(screen.getByRole('button', { name: /comparing/i })).toBeDisabled()
    expect(screen.getByLabelText(/first company ticker/i)).not.toBeDisabled()

    render(<StockSearchForm query={{ ...query, right: 'AAPL' }} isBusy={false} onSubmit={onSubmit} />)
    const buttons = screen.getAllByRole('button', { name: 'Compare' })
    await user.click(buttons.at(-1)!)
    expect(screen.getByText(/different tickers/i)).toBeInTheDocument()
  })
})
