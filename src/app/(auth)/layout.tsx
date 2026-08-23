export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-1 items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <p className="font-mono text-xs uppercase tracking-[0.2em] text-ink-3">Hoyaam AI</p>
          <h1 className="font-display text-2xl font-semibold text-ink">Back-Office</h1>
        </div>
        <div className="rounded-lg border border-rule bg-surface p-8 shadow-sm">{children}</div>
      </div>
    </div>
  );
}
