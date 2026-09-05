import { useState, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { ChevronDown, ChevronLeft, ChevronRight, LogOut, Menu, X } from 'lucide-react'
import { BrandMark } from '../common/BrandMark'
import { useAuth } from '../../auth/AuthContext'
import { useMyEmploymentCategory } from '../../auth/useMyEmploymentCategory'
import { isNavGroup, navEntriesForRoles, primaryNavItemsForRoles, type NavEntry, type NavGroup } from './nav-config'

function isGroupActive(group: NavGroup, pathname: string): boolean {
  if (group.to && pathname === group.to) return true
  return group.children.some((child) => pathname === child.to || (child.to !== '/' && pathname.startsWith(child.to + '/')))
}

const itemLinkClass = ({ isActive }: { isActive: boolean }) =>
  `flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-brand-forest text-white' : 'text-slate-600 hover:bg-slate-100'
  }`

/**
 * Renders one role-filtered NavEntry[] tree (used for both the desktop
 * sidebar and the mobile slide-over) - a NavGroup expands/collapses,
 * defaulting open whenever the current route lives inside it, with the
 * user's own toggle (if any this session) overriding that default.
 */
function NavTree({ entries, collapsed, onNavigate }: { entries: NavEntry[]; collapsed: boolean; onNavigate?: () => void }) {
  const location = useLocation()
  const [toggled, setToggled] = useState<Record<string, boolean>>({})

  function toggleGroup(label: string, defaultOpen: boolean) {
    setToggled((prev) => ({ ...prev, [label]: !(prev[label] ?? defaultOpen) }))
  }

  return (
    <>
      {entries.map((entry) => {
        if (!isNavGroup(entry)) {
          return (
            <NavLink
              key={entry.to}
              to={entry.to}
              end={entry.to === '/'}
              onClick={onNavigate}
              className={itemLinkClass}
              title={collapsed ? entry.label : undefined}
            >
              <entry.icon size={18} className="shrink-0" />
              {!collapsed && <span className="truncate">{entry.label}</span>}
            </NavLink>
          )
        }

        const active = isGroupActive(entry, location.pathname)
        const expanded = collapsed ? false : (toggled[entry.label] ?? active)

        return (
          <div key={entry.label}>
            <div
              className={`flex items-center gap-1 rounded-md text-sm font-medium ${
                active && !expanded ? 'bg-brand-forest/10 text-brand-forest' : 'text-slate-600'
              }`}
            >
              {entry.to ? (
                <NavLink to={entry.to} end onClick={onNavigate} className={itemLinkClass} title={collapsed ? entry.label : undefined}>
                  <entry.icon size={18} className="shrink-0" />
                  {!collapsed && <span className="truncate">{entry.label}</span>}
                </NavLink>
              ) : (
                <button
                  type="button"
                  onClick={() => toggleGroup(entry.label, active)}
                  title={collapsed ? entry.label : undefined}
                  className="flex flex-1 items-center gap-3 rounded-md px-3 py-2 text-left hover:bg-slate-100"
                >
                  <entry.icon size={18} className="shrink-0" />
                  {!collapsed && <span className="flex-1 truncate">{entry.label}</span>}
                </button>
              )}
              {!collapsed && (
                <button
                  type="button"
                  onClick={() => toggleGroup(entry.label, active)}
                  aria-label={expanded ? `Collapse ${entry.label}` : `Expand ${entry.label}`}
                  className="rounded-md p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                >
                  <ChevronDown size={15} className={`transition-transform ${expanded ? 'rotate-180' : ''}`} />
                </button>
              )}
            </div>
            {!collapsed && expanded && (
              <div className="ml-4 space-y-1 border-l border-slate-100 pl-2 pt-1">
                {entry.children.map((child) => (
                  <NavLink key={child.to} to={child.to} onClick={onNavigate} className={itemLinkClass}>
                    <child.icon size={16} className="shrink-0" />
                    <span className="truncate">{child.label}</span>
                  </NavLink>
                ))}
              </div>
            )}
          </div>
        )
      })}
    </>
  )
}

export function AppShell({ children }: { children: ReactNode }) {
  const { roles, subject, logout } = useAuth()
  const employmentCategory = useMyEmploymentCategory()
  const [collapsed, setCollapsed] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const entries = navEntriesForRoles(roles, employmentCategory)
  const primaryItems = primaryNavItemsForRoles(roles, employmentCategory).slice(0, 5)

  return (
    <div className="flex h-dvh w-full overflow-hidden bg-brand-slate">
      {/* Desktop collapsible sidebar - defaults to expanded (collapsed=false), fixed 256px when open */}
      <aside
        className={`no-print hidden md:flex shrink-0 flex-col border-r border-slate-200 bg-white transition-all duration-200 ${
          collapsed ? 'w-[76px] min-w-[76px]' : 'w-64 min-w-[16rem]'
        }`}
      >
        <div className="flex items-center justify-between border-b border-slate-100 p-4">
          {!collapsed && <BrandMark size={36} />}
          <button
            type="button"
            onClick={() => setCollapsed((c) => !c)}
            className="ml-auto rounded-md p-1.5 text-slate-500 hover:bg-slate-100"
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
          </button>
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto p-2">
          <NavTree entries={entries} collapsed={collapsed} />
        </nav>
        <div className="border-t border-slate-100 p-3">
          <button
            type="button"
            onClick={logout}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-slate-600 hover:bg-red-50 hover:text-red-600"
          >
            <LogOut size={18} />
            {!collapsed && <span>Sign out</span>}
          </button>
        </div>
      </aside>

      {/* Mobile slide-over menu */}
      {mobileMenuOpen && (
        <div className="no-print fixed inset-0 z-40 md:hidden">
          <button
            type="button"
            aria-label="Close menu"
            className="absolute inset-0 bg-black/40"
            onClick={() => setMobileMenuOpen(false)}
          />
          <div className="absolute inset-y-0 left-0 flex w-72 flex-col bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <BrandMark size={32} showLegalName={false} />
              <button type="button" onClick={() => setMobileMenuOpen(false)} className="p-1.5 text-slate-500">
                <X size={20} />
              </button>
            </div>
            <nav className="flex-1 space-y-1 overflow-y-auto p-2">
              <NavTree entries={entries} collapsed={false} onNavigate={() => setMobileMenuOpen(false)} />
              <button
                type="button"
                onClick={logout}
                className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
              >
                <LogOut size={18} />
                <span>Sign out</span>
              </button>
            </nav>
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        {/* Mobile header banner */}
        <header className="no-print flex items-center justify-between border-b border-slate-200 bg-white px-3 py-2 md:hidden">
          <button
            type="button"
            onClick={() => setMobileMenuOpen(true)}
            className="rounded-md p-2 text-slate-600 hover:bg-slate-100"
            aria-label="Open menu"
          >
            <Menu size={22} />
          </button>
          <BrandMark size={30} />
          <span className="w-9" />
        </header>

        {/* Desktop top bar */}
        <header className="no-print hidden items-center justify-between border-b border-slate-200 bg-white px-6 py-3 md:flex">
          <p className="text-sm text-slate-500">Welcome back, <span className="font-medium text-slate-700">{subject ?? 'user'}</span></p>
          <div className="flex items-center gap-2">
            {roles.map((role) => (
              <span key={role} className="rounded-full bg-brand-jute/20 px-2.5 py-1 text-xs font-medium text-brand-forest">
                {role.replace('_', ' ')}
              </span>
            ))}
          </div>
        </header>

        <main className="flex-1 overflow-y-auto pb-20 md:pb-0">
          <div className="mx-auto w-full max-w-6xl p-4 sm:p-6">{children}</div>
        </main>

        {/* Mobile bottom navigation */}
        <nav className="no-print fixed inset-x-0 bottom-0 z-30 flex border-t border-slate-200 bg-white md:hidden">
          {primaryItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${
                  isActive ? 'text-brand-forest' : 'text-slate-500'
                }`
              }
            >
              <item.icon size={20} />
              <span className="truncate">{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </div>
    </div>
  )
}
