import { CheckCircle2, Info, TriangleAlert, X } from 'lucide-react'
import { useToastStore } from '../../features/ui/toastStore.js'

const ICONS = { success: CheckCircle2, error: TriangleAlert, info: Info }

export function Toaster() {
  const toasts = useToastStore((state) => state.toasts)
  const dismiss = useToastStore((state) => state.dismiss)
  return (
    <div className="toaster" aria-live="polite">
      {toasts.map(({ id, message, tone }) => {
        const Icon = ICONS[tone] || Info
        return (
          <div key={id} className={`toast toast--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
            <Icon size={17} />
            <span>{message}</span>
            <button type="button" className="icon-button" onClick={() => dismiss(id)} aria-label="Đóng thông báo"><X size={14} /></button>
          </div>
        )
      })}
    </div>
  )
}
