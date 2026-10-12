import { Icon } from './Icon'

const STEPS = [
  { key: 'PENDING_PAYMENT', label: 'Reserved', icon: 'clock' },
  { key: 'PAID', label: 'Paid', icon: 'card' },
  { key: 'COLLECTED', label: 'Collected', icon: 'truck' },
]

const POSITION = { PENDING_PAYMENT: 0, PAID: 1, COLLECTED: 2 }

/** Reserved -> Paid -> Collected. Cancelled/expired orders stop after "Reserved". */
export function OrderTimeline({ status }) {
  const stopped = status === 'CANCELLED' || status === 'EXPIRED'
  const current = stopped ? 0 : POSITION[status]

  return (
    <ol className="bb-timeline">
      {STEPS.map((step, index) => {
        let state = ''
        if (stopped) state = index === 0 ? 'is-stopped' : ''
        else if (index < current || (index === current && status === 'COLLECTED')) state = 'is-done'
        else if (index === current) state = index === 0 ? 'is-current' : 'is-done'
        const label = stopped && index === 0 ? (status === 'CANCELLED' ? 'Cancelled' : 'Expired') : step.label
        return (
          <li key={step.key} className={state} aria-current={index === current && !stopped ? 'step' : undefined}>
            <span className="bb-timeline-dot">
              <Icon name={state === 'is-done' ? 'check' : stopped && index === 0 ? 'close' : step.icon} size={18} />
            </span>
            {label}
          </li>
        )
      })}
    </ol>
  )
}
