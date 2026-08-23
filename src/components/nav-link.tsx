'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

export function NavLink({ href, label }: { href: string; label: string }) {
  const pathname = usePathname();
  const active = pathname === href || pathname.startsWith(`${href}/`);

  return (
    <Link
      href={href}
      className={`block rounded-md px-3 py-1.5 text-sm transition-colors ${
        active ? 'bg-seal-soft font-medium text-seal' : 'text-ink-2 hover:bg-surface-2 hover:text-ink'
      }`}
    >
      {label}
    </Link>
  );
}
