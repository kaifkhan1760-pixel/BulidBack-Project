import { useEffect, useRef, useState } from 'react'

/**
 * Loads data for a page and reloads whenever `key` changes (e.g. the URL filters).
 * - An older request is cancelled when a newer one starts (AbortController),
 *   so a slow old answer can never replace newer results.
 * - 401 is ignored here because the app already sends the user to login.
 */
export function useApiQuery(key, fetchFn) {
  const latestFetch = useRef(fetchFn)
  useEffect(() => {
    latestFetch.current = fetchFn
  })

  const [reloadCount, setReloadCount] = useState(0)
  const requestKey = `${key}#${reloadCount}`
  const [result, setResult] = useState({ key: null, data: null, error: null })

  useEffect(() => {
    const controller = new AbortController()
    latestFetch.current(controller.signal)
      .then((data) => setResult({ key: requestKey, data, error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError' && error.status !== 401) setResult({ key: requestKey, data: null, error })
      })
    return () => controller.abort()
  }, [requestKey])

  const loading = result.key !== requestKey
  return {
    loading,
    data: loading ? null : result.data,
    error: loading ? null : result.error,
    reload: () => setReloadCount((n) => n + 1),
  }
}
