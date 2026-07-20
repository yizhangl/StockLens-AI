import { useRef, useState, type FormEvent } from 'react'
import type { ComparisonQuery } from '../types/comparison.ts'
import { validateTickers, type TickerErrors } from '../utils/tickerValidation.ts'

interface StockSearchFormProps {
  query: ComparisonQuery
  isBusy: boolean
  onSubmit: (left: string, right: string) => void
}

export function StockSearchForm({ query, isBusy, onSubmit }: StockSearchFormProps) {
  const [left, setLeft] = useState(query.left)
  const [right, setRight] = useState(query.right)
  const [errors, setErrors] = useState<TickerErrors>({})
  const leftInput = useRef<HTMLInputElement>(null)
  const rightInput = useRef<HTMLInputElement>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const result = validateTickers(left, right)
    setLeft(result.left)
    setRight(result.right)
    setErrors(result.errors)

    if (result.errors.left) {
      leftInput.current?.focus()
      return
    }
    if (result.errors.right) {
      rightInput.current?.focus()
      return
    }
    onSubmit(result.left, result.right)
  }

  return (
    <form className="stock-search" onSubmit={handleSubmit} noValidate>
      <div className="ticker-field">
        <label htmlFor="left-ticker">First company ticker</label>
        <div className={`ticker-input${errors.left ? ' ticker-input--error' : ''}`}>
          <span aria-hidden="true">⌕</span>
          <input
            ref={leftInput}
            id="left-ticker"
            name="left"
            value={left}
            onChange={(event) => {
              setLeft(event.target.value)
              setErrors((current) => ({ ...current, left: undefined }))
            }}
            aria-describedby={errors.left ? 'left-ticker-error' : undefined}
            aria-invalid={Boolean(errors.left)}
            autoCapitalize="characters"
            autoComplete="off"
            maxLength={16}
          />
        </div>
        {errors.left ? <p id="left-ticker-error" className="field-error">{errors.left}</p> : null}
      </div>
      <span className="versus" aria-hidden="true">VS</span>
      <div className="ticker-field">
        <label htmlFor="right-ticker">Second company ticker</label>
        <div className={`ticker-input${errors.right ? ' ticker-input--error' : ''}`}>
          <span aria-hidden="true">⌕</span>
          <input
            ref={rightInput}
            id="right-ticker"
            name="right"
            value={right}
            onChange={(event) => {
              setRight(event.target.value)
              setErrors((current) => ({ ...current, right: undefined }))
            }}
            aria-describedby={errors.right ? 'right-ticker-error' : undefined}
            aria-invalid={Boolean(errors.right)}
            autoCapitalize="characters"
            autoComplete="off"
            maxLength={16}
          />
        </div>
        {errors.right ? <p id="right-ticker-error" className="field-error">{errors.right}</p> : null}
      </div>
      <button className="button button--primary compare-button" type="submit" disabled={isBusy}>
        {isBusy ? 'Comparing…' : 'Compare'}
      </button>
    </form>
  )
}
