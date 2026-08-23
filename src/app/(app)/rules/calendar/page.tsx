import { createClient } from '@/lib/supabase/server';
import { getViewer } from '@/lib/capabilities';
import { Card, Badge } from '@/components/ui';
import { AddCalendarEntryForm } from './add-calendar-entry-form';
import { RemoveCalendarEntryButton } from './remove-calendar-entry-button';

export type CourtCalendarEntry = {
  id: string;
  date_from: string;
  date_to: string;
  label_ar: string;
  label_en: string;
  is_court_vacation: boolean;
  created_by?: string | null;
  created_at?: string | null;
};

export default async function CourtCalendarPage() {
  const [viewer, supabase] = await Promise.all([getViewer(), createClient()]);
  const canManage = viewer?.capabilities.has('manage_deadline_rules') ?? false;

  const { data, error } = await supabase
    .from('court_calendar')
    .select('*')
    .order('date_from', { ascending: true });

  const entries = (data ?? []) as CourtCalendarEntry[];

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Court calendar</h1>
        <p className="mt-1 text-sm text-ink-2">
          Holidays and court-vacation windows that feed directly into deadline computation.
        </p>
      </div>

      {error && (
        <p className="text-sm text-risk">Failed to load the court calendar: {error.message}</p>
      )}

      {canManage && (
        <Card title="Add entry">
          <AddCalendarEntryForm />
        </Card>
      )}

      <Card title="Entries">
        {entries.length === 0 && !error && <p className="text-sm text-ink-3">No entries yet.</p>}
        {entries.length > 0 && (
          <ul className="space-y-3">
            {entries.map((entry) => (
              <li
                key={entry.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-rule p-3"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={entry.is_court_vacation ? 'warn' : 'default'}>
                    {entry.is_court_vacation ? 'court vacation' : 'holiday'}
                  </Badge>
                  <span className="text-sm font-medium text-ink">{entry.label_en}</span>
                  <span className="text-sm text-ink-3">({entry.label_ar})</span>
                  <span className="text-xs text-ink-3">
                    {entry.date_from === entry.date_to
                      ? entry.date_from
                      : `${entry.date_from} – ${entry.date_to}`}
                  </span>
                </div>
                {canManage && <RemoveCalendarEntryButton id={entry.id} />}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
