import { type ButtonHTMLAttributes, type InputHTMLAttributes, type LabelHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';

export function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-ink-2">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-ink-3">{hint}</span>}
    </label>
  );
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`w-full rounded-md border border-rule bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-seal focus:ring-1 focus:ring-seal ${props.className ?? ''}`}
    />
  );
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...props}
      className={`w-full rounded-md border border-rule bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-seal focus:ring-1 focus:ring-seal ${props.className ?? ''}`}
    />
  );
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...props}
      className={`w-full rounded-md border border-rule bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-seal focus:ring-1 focus:ring-seal ${props.className ?? ''}`}
    />
  );
}

export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' }) {
  const styles = {
    primary: 'bg-seal text-white hover:opacity-90 disabled:opacity-50',
    secondary: 'border border-rule text-ink hover:bg-surface-2 disabled:opacity-50',
    danger: 'bg-risk text-white hover:opacity-90 disabled:opacity-50',
  };
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed ${styles[variant]} ${className}`}
    />
  );
}

export function Alert({ tone = 'risk', children }: { tone?: 'risk' | 'warn' | 'seal'; children: React.ReactNode }) {
  const styles = {
    risk: 'bg-risk-soft text-risk border-risk/30',
    warn: 'bg-warn-soft text-warn border-warn/30',
    seal: 'bg-seal-soft text-seal border-seal/30',
  };
  return (
    <div className={`rounded-md border px-3 py-2 text-sm ${styles[tone]}`} role={tone === 'risk' ? 'alert' : undefined}>
      {children}
    </div>
  );
}

export function Card({ title, action, children }: { title?: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-rule bg-surface">
      {(title || action) && (
        <div className="flex items-center justify-between border-b border-rule px-5 py-3">
          {title && <h2 className="font-display text-lg font-medium text-ink">{title}</h2>}
          {action}
        </div>
      )}
      <div className="p-5">{children}</div>
    </div>
  );
}

export function Badge({ tone = 'default', children }: { tone?: 'default' | 'seal' | 'warn' | 'risk'; children: React.ReactNode }) {
  const styles = {
    default: 'bg-surface-2 text-ink-2',
    seal: 'bg-seal-soft text-seal',
    warn: 'bg-warn-soft text-warn',
    risk: 'bg-risk-soft text-risk',
  };
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 font-mono text-xs ${styles[tone]}`}>
      {children}
    </span>
  );
}

export function Label(props: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label {...props} className={`text-sm font-medium text-ink-2 ${props.className ?? ''}`} />;
}
