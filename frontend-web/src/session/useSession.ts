import { useContext } from 'react'

import { SessionContext, type SessionValue } from './sessionContext'

export function useSession(): SessionValue {
  const session = useContext(SessionContext)
  if (!session) {
    throw new Error('useSession debe usarse dentro de <SessionProvider>')
  }
  return session
}
