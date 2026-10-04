export function LoadingScreen({ label = 'Đang tải…' }) {
  return (
    <main className="screen-center" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <p>{label}</p>
    </main>
  )
}
