import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

import { setSessionExpiredHandler } from '@/session/sessionEvents';

/**
 * Sends the user to the login route when the session is gone.
 *
 * The API layer clears the session and announces it; this bridge is the part
 * of the app that owns the router, so it is the one that navigates.
 */
export function SessionRedirectBridge() {
  const navigate = useNavigate();

  useEffect(() => {
    setSessionExpiredHandler((path) => {
      navigate(path);
    });

    return () => setSessionExpiredHandler(null);
  }, [navigate]);

  return null;
}
