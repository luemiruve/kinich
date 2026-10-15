import { Navigate, Outlet, Route, Routes } from 'react-router'

import { AdminHomePage } from '@/pages/AdminHomePage'
import { AdminInicioPage } from '@/pages/AdminInicioPage'
import { AdminSettingsPage } from '@/pages/AdminSettingsPage'
import { LoginPage } from '@/pages/LoginPage'
import { PublicHomePage } from '@/pages/PublicHomePage'
import { PortfolioProjectPage } from '@/portfolio/PortfolioProjectPage'
import { PortfolioPage } from '@/portfolio/PortfolioPage'
import { GuestOnly, LOGIN_PATH, RequireAuth } from '@/routes/guards'
import { ADMIN_HOME } from '@/routes/safeRedirect'
import { SessionProvider } from '@/session/SessionProvider'

export const ADMIN_INICIO = '/admin/inicio'
export const ADMIN_SETTINGS = '/admin/configuracion'

/** La sesión solo se consulta en /admin: la portada pública nunca llama a /api/auth/me ni recibe cookies. */
function AdminSession() {
  return (
    <SessionProvider>
      <Outlet />
    </SessionProvider>
  )
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<PublicHomePage />} />
      <Route path="/portafolio" element={<PortfolioPage />} />
      <Route path="/portafolio/:slug" element={<PortfolioProjectPage />} />
      <Route element={<AdminSession />}>
        <Route path={LOGIN_PATH} element={<GuestOnly><LoginPage /></GuestOnly>} />
        <Route path={ADMIN_HOME} element={<RequireAuth><AdminHomePage /></RequireAuth>} />
        <Route path={ADMIN_INICIO} element={<RequireAuth><AdminInicioPage /></RequireAuth>} />
        <Route path={ADMIN_SETTINGS} element={<RequireAuth><AdminSettingsPage /></RequireAuth>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
