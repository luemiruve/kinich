/**
 * Cliente HTTP de la API. La sesión viaja en una cookie HttpOnly que el navegador envía solo;
 * aquí únicamente se reenvía el token CSRF (cookie XSRF-TOKEN → cabecera X-XSRF-TOKEN).
 */

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** Error de la API con el formato ProblemDetail (RFC 9457) que devuelve el backend. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | null
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, code: string | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

export function readCookie(name: string): string | null {
  const prefix = `${name}=`
  const match = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return match ? decodeURIComponent(match.slice(prefix.length)) : null
}

/** Petición del token en curso: las escrituras simultáneas la comparten en vez de pedir cada una el suyo. */
let pendingCsrfToken: Promise<string | null> | null = null

/**
 * El backend emite la cookie CSRF en cualquier respuesta; si aún no existe, se pide una vez. Dos peticiones
 * paralelas sin cookie recibirían tokens distintos y la última cookie invalidaría la cabecera de la otra.
 */
async function ensureCsrfToken(): Promise<string | null> {
  const token = readCookie(CSRF_COOKIE)
  if (token) {
    return token
  }
  pendingCsrfToken ??= fetch('/api/auth/me', { credentials: 'same-origin' })
    .then(() => readCookie(CSRF_COOKIE))
    .finally(() => {
      pendingCsrfToken = null
    })
  return pendingCsrfToken
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  // Con FormData el navegador pone multipart/form-data con su boundary.
  if (init.body !== undefined && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken()
    if (token) {
      headers.set(CSRF_HEADER, token)
    }
  }

  let response: Response
  try {
    response = await fetch(path, { ...init, method, headers, credentials: 'same-origin' })
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor. Revisa tu conexión.')
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = await response.json()
    return new ApiError(
      response.status,
      typeof problem.detail === 'string' ? problem.detail : defaultMessage(response.status),
      problem.errors ?? {},
      typeof problem.code === 'string' ? problem.code : null,
    )
  } catch {
    return new ApiError(response.status, defaultMessage(response.status))
  }
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Debes iniciar sesión para continuar.'
  if (status === 403) return 'No tienes permiso para realizar esta acción.'
  if (status === 429) return 'Demasiados intentos. Espera unos minutos.'
  return 'Ocurrió un error inesperado.'
}
