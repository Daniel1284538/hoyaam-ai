'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { createClient } from '@/lib/supabase/client';
import { Button, Field, Input, Alert } from '@/components/ui';

export default function MfaEnrollPage() {
  const router = useRouter();
  const [factorId, setFactorId] = useState<string | null>(null);
  const [qrCode, setQrCode] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const supabase = createClient();
    supabase.auth.mfa.enroll({ factorType: 'totp' }).then(({ data, error: enrollError }) => {
      if (enrollError) {
        setError(enrollError.message);
        return;
      }
      setFactorId(data.id);
      setQrCode(data.totp.qr_code);
      setSecret(data.totp.secret);
    });
  }, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!factorId) return;
    setError(null);
    setLoading(true);

    const supabase = createClient();
    const { data: challenge, error: challengeError } = await supabase.auth.mfa.challenge({ factorId });
    if (challengeError) {
      setError(challengeError.message);
      setLoading(false);
      return;
    }

    const { error: verifyError } = await supabase.auth.mfa.verify({
      factorId,
      challengeId: challenge.id,
      code,
    });
    if (verifyError) {
      setError(verifyError.message);
      setLoading(false);
      return;
    }

    router.push('/');
    router.refresh();
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-2">
        Multi-factor authentication is required for every account. Scan this
        code with an authenticator app (1Password, Authy, Google
        Authenticator, …) before you can continue.
      </p>
      {error && <Alert>{error}</Alert>}
      {qrCode && (
        // eslint-disable-next-line @next/next/no-img-element -- data: SVG from Supabase, not an optimizable asset
        <img src={qrCode} alt="TOTP QR code" className="mx-auto h-40 w-40 rounded-md border border-rule bg-white p-2" />
      )}
      {secret && (
        <p className="text-center font-mono text-xs text-ink-3">
          Can&apos;t scan? Enter manually: <span className="text-ink">{secret}</span>
        </p>
      )}
      <form onSubmit={onSubmit} className="space-y-4">
        <Field label="Code from your authenticator app">
          <Input
            inputMode="numeric"
            pattern="[0-9]*"
            maxLength={6}
            required
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
        </Field>
        <Button type="submit" disabled={loading || !factorId} className="w-full">
          {loading ? 'Confirming…' : 'Confirm and continue'}
        </Button>
      </form>
    </div>
  );
}
