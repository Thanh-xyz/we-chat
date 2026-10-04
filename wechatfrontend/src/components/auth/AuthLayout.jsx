import { MessageCircleMore } from 'lucide-react'
import { Link } from 'react-router-dom'

export function AuthLayout({ eyebrow, title, description, children, footer }) {
  return (
    <main className="auth-page">
      <section className="auth-brand" aria-label="Giới thiệu WeChat Clone">
        <div className="brand-mark brand-mark--large" aria-hidden="true">
          <MessageCircleMore size={34} strokeWidth={2.2} />
        </div>
        <p className="eyebrow">CHATSPACE</p>
        <h1>Những cuộc trò chuyện gần gũi hơn.</h1>
        <p>
          Một không gian nhắn tin nhanh, gọn và riêng tư để bạn luôn bắt kịp những người quan trọng.
        </p>
        <div className="auth-orbits" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <Link className="mobile-brand" to="/" aria-label="ChatSpace trang chủ">
            <span className="brand-mark"><MessageCircleMore size={22} /></span>
            ChatSpace
          </Link>
          {eyebrow && <p className="eyebrow">{eyebrow}</p>}
          <h2>{title}</h2>
          {description && <p className="auth-description">{description}</p>}
          {children}
          {footer && <div className="auth-footer">{footer}</div>}
        </div>
      </section>
    </main>
  )
}

export function FormAlert({ children, tone = 'error' }) {
  if (!children) return null
  return (
    <div className={`form-alert form-alert--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}
