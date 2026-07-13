import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useEffect } from 'react'

export const Route = createFileRoute('/auth/callback')({
  component: AuthCallbackComponent,
})

function AuthCallbackComponent() {
  const navigate = useNavigate()
  
  useEffect(() => {
    // Extract token from URL
    const urlParams = new URLSearchParams(window.location.search)
    const token = urlParams.get('token')
    
    if (token) {
      // Save token to localStorage
      localStorage.setItem('token', token)
      
      // Redirect to home or intended page
      navigate({ to: '/' })
    } else {
      console.error('No token found in callback URL')
      // Maybe redirect to login page with error
      navigate({ to: '/' })
    }
  }, [navigate])

  return (
    <div className="flex h-screen w-full items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
        <p className="text-lg font-medium">Authenticating...</p>
      </div>
    </div>
  )
}
