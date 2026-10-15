import './index.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'

import { App } from './App'

// Router de datos con una sola ruta comodín: <App/> conserva sus <Routes>; hace falta para `useBlocker` (aviso de
// cambios sin guardar en el editor de proyectos).
const router = createBrowserRouter([{ path: '*', element: <App /> }])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
