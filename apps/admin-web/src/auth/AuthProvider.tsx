import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useLocation, useNavigate } from "react-router";
import type { AdminRole } from "./roles";
import {
  SESSION_EXPIRED_EVENT,
  clearSession,
  readSession,
  writeSession,
  type AdminSession,
} from "./session";

/**
 * 状态管理取舍：只引入 React Context。
 * 当前唯一的跨页客户端状态是登录会话；服务端数据后续由各页面按需请求，
 * 未出现需要全局缓存的共享状态，因此不引入 Redux/Zustand 等库。
 */
interface AuthContextValue {
  session: AdminSession | null;
  role: AdminRole | null;
  isAuthenticated: boolean;
  can: (role: AdminRole) => boolean;
  signIn: (session: AdminSession) => void;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [session, setSession] = useState<AdminSession | null>(() =>
    readSession(),
  );
  const navigate = useNavigate();
  const location = useLocation();

  const signIn = useCallback((next: AdminSession) => {
    writeSession(next);
    setSession(next);
  }, []);

  const signOut = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  const can = useCallback(
    (required: AdminRole) => session?.role === required,
    [session],
  );

  // 401 统一出口：清空会话并带着回跳地址去登录页。
  useEffect(() => {
    const onExpired = () => {
      setSession(null);
      const from = `${location.pathname}${location.search}`;
      navigate(`/login?redirect=${encodeURIComponent(from)}`, {
        replace: true,
      });
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
    };
  }, [location.pathname, location.search, navigate]);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      role: session?.role ?? null,
      isAuthenticated: session !== null,
      can,
      signIn,
      signOut,
    }),
    [session, can, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth 必须在 AuthProvider 内使用");
  }
  return context;
}
