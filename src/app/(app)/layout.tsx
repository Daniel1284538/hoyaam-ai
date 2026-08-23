import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getViewer } from '@/lib/capabilities';
import { NAV_SECTIONS } from '@/lib/nav';
import { NavLink } from '@/components/nav-link';
import { SignOutButton } from '@/components/sign-out-button';

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const viewer = await getViewer();
  // proxy.ts already guarantees an authenticated, aal2 session reaches
  // here — this is a defensive fallback, not the enforcement boundary.
  if (!viewer) redirect('/login');

  const visibleSections = NAV_SECTIONS
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => item.anyOf.some((cap) => viewer.capabilities.has(cap))),
    }))
    .filter((section) => section.items.length > 0);

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-60 flex-col border-r border-rule bg-surface">
        <div className="border-b border-rule px-4 py-4">
          <p className="font-mono text-[10px] uppercase tracking-[0.2em] text-ink-3">Hoyaam AI</p>
          <Link href="/" className="font-display text-lg font-semibold text-ink">
            Back-Office
          </Link>
        </div>
        <nav className="flex-1 space-y-5 overflow-y-auto px-3 py-4">
          {visibleSections.map((section) => (
            <div key={section.title}>
              <p className="mb-1 px-3 font-mono text-[10px] uppercase tracking-[0.15em] text-ink-3">
                {section.title}
              </p>
              <div className="space-y-0.5">
                {section.items.map((item) => (
                  <NavLink key={item.href} href={item.href} label={item.label} />
                ))}
              </div>
            </div>
          ))}
          {visibleSections.length === 0 && (
            <p className="px-3 text-sm text-ink-3">No modules available for your role yet.</p>
          )}
        </nav>
        <div className="border-t border-rule px-4 py-3">
          <p className="truncate text-xs text-ink-2">{viewer.email}</p>
          <p className="mb-2 truncate text-xs text-ink-3">{viewer.roles.join(', ') || 'no role'}</p>
          <SignOutButton />
        </div>
      </aside>
      <main className="flex-1 overflow-y-auto p-8">{children}</main>
    </div>
  );
}
