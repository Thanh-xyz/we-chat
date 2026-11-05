import { useEffect } from 'react'

/** Calls onDismiss on outside pointer-down or Escape while `active`. */
export function useDismiss(ref, onDismiss, active = true) {
  useEffect(() => {
    if (!active) return undefined
    function handlePointer(event) {
      if (ref.current && !ref.current.contains(event.target)) onDismiss()
    }
    function handleKey(event) {
      if (event.key === 'Escape') onDismiss()
    }
    document.addEventListener('pointerdown', handlePointer)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('pointerdown', handlePointer)
      document.removeEventListener('keydown', handleKey)
    }
  }, [active, onDismiss, ref])
}
