import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App.tsx'

describe('App', () => {
  it('renders the StockLens AI application shell', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { level: 1, name: 'StockLens AI' }),
    ).toBeInTheDocument()
  })
})
