import { ORDER_STATUS } from '../orders/orderRules'

export function OrderStatusBadge({ status }) {
  const info = ORDER_STATUS[status] ?? { label: status, tone: 'muted' }
  return <span className={`bb-status bb-status-${info.tone}`}>{info.label}</span>
}
