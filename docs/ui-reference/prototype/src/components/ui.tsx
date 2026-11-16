import { useEffect, useRef, useState, type ButtonHTMLAttributes, type CSSProperties, type PointerEvent as RPointerEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronLeft, ChevronRight, Minus, Plus, X } from 'lucide-react';
import { cn } from '../utils/cn';
import { useApp } from '../lib/store';
import logo from '../assets/logo.jpg';

/* ---------- Brand ---------- */
/** The RealScale-Pro eagle logo. */
export function Mark({ className, animate }: { className?: string; animate?: boolean }) {
  return <img src={logo} alt="" aria-hidden draggable={false} className={cn('rounded-[24%] object-cover', animate && 'anim-pop-in', className)} />;
}

/* ---------- Buttons ---------- */
type Variant = 'primary' | 'secondary' | 'tonal' | 'ghost' | 'dark' | 'danger';
type Size = 'lg' | 'md' | 'sm';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  full?: boolean;
  icon?: ReactNode;
}

export function Button({ variant = 'secondary', size = 'md', full, icon, children, className, ...p }: ButtonProps) {
  return (
    <button
      type="button"
      {...p}
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center gap-2 rounded-md font-medium transition-colors duration-150 select-none disabled:cursor-not-allowed',
        size === 'lg' && 'min-h-13 px-5 text-base font-semibold',
        size === 'md' && 'min-h-11 px-4 text-[0.9375rem]',
        size === 'sm' && 'min-h-9 px-3 text-sm before:absolute before:-inset-y-1 before:inset-x-0 before:content-[""]',
        variant === 'primary' && 'bg-primary text-on-primary active:bg-primary-active disabled:bg-disabled disabled:text-on-disabled',
        variant === 'secondary' && 'border border-hairline bg-canvas text-ink active:bg-soft disabled:text-muted-soft',
        variant === 'tonal' && 'bg-card text-ink active:bg-strong disabled:text-muted-soft',
        variant === 'ghost' && 'text-ink active:bg-card disabled:text-muted-soft',
        variant === 'dark' && 'bg-dark-elevated text-on-dark active:bg-dark-soft',
        variant === 'danger' && 'border border-hairline bg-canvas text-error active:bg-soft',
        full && 'w-full',
        className,
      )}
    >
      {icon}
      {children}
    </button>
  );
}

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string;
  variant?: 'outline' | 'plain' | 'dark';
}

export function IconButton({ label, variant = 'outline', className, children, ...p }: IconButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      {...p}
      className={cn(
        'relative inline-flex size-10 shrink-0 items-center justify-center rounded-full transition-colors duration-150 before:absolute before:-inset-1 before:content-[""] disabled:opacity-40',
        variant === 'outline' && 'border border-hairline bg-canvas text-ink active:bg-soft',
        variant === 'plain' && 'text-ink active:bg-card',
        variant === 'dark' && 'bg-dark-elevated/90 text-on-dark active:bg-dark-soft',
        className,
      )}
    >
      {children}
    </button>
  );
}

/* ---------- Screen scaffolding ---------- */
export function Screen({ children, className }: { children: ReactNode; className?: string }) {
  const { nav } = useApp();
  const [dir] = useState(nav.dir);
  return (
    <section className={cn('absolute inset-0 flex flex-col bg-canvas', dir === 'push' ? 'anim-push' : dir === 'pop' ? 'anim-pop' : 'anim-fade', className)}>
      {children}
    </section>
  );
}

export function TopBar({ title, onBack, close, right, scrolled }: { title: string; onBack?: () => void; close?: boolean; right?: ReactNode; scrolled?: boolean }) {
  return (
    <header className={cn('safe-t relative z-10 shrink-0 border-b bg-canvas transition-colors duration-200', scrolled ? 'border-hairline' : 'border-transparent')}>
      <div className="flex h-14 items-center gap-1.5 px-2.5">
        {onBack && (
          <IconButton label={close ? 'Close' : 'Back'} variant="plain" onClick={onBack}>
            {close ? <X className="size-5.5" /> : <ChevronLeft className="size-6" />}
          </IconButton>
        )}
        <h1 className={cn('display min-w-0 flex-1 truncate text-[1.5rem] leading-[1.35] font-semibold', !onBack && 'pl-2.5')}>{title}</h1>
        {right}
      </div>
    </header>
  );
}

export function BottomBar({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('safe-b shrink-0 border-t border-hairline bg-canvas px-5 pt-3', className)}>{children}</div>;
}

/** Tracks whether a scroll container has moved off the top, for the collapsing header line. */
export function useScrolled(threshold = 4) {
  const [scrolled, setScrolled] = useState(false);
  const onScroll = (e: { currentTarget: HTMLElement }) => {
    const v = e.currentTarget.scrollTop > threshold;
    setScrolled((cur) => (cur === v ? cur : v));
  };
  return { scrolled, onScroll };
}

/* ---------- Bottom sheet ---------- */
export function Sheet({ open, onClose, title, children, footer, tall }: { open: boolean; onClose: () => void; title?: string; children: ReactNode; footer?: ReactNode; tall?: boolean }) {
  const { device, addGuard } = useApp();
  const [mounted, setMounted] = useState(open);
  const [closing, setClosing] = useState(false);
  const panel = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (open) {
      setMounted(true);
      setClosing(false);
      return;
    }
    setClosing(true);
    const t = setTimeout(() => {
      setMounted(false);
      setClosing(false);
    }, 220);
    return () => clearTimeout(t);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const removeGuard = addGuard(() => closeRef.current());
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && closeRef.current();
    window.addEventListener('keydown', onKey);
    const previous = document.activeElement as HTMLElement | null;
    const focusTimer = setTimeout(() => panel.current?.focus(), 30);
    return () => {
      removeGuard();
      window.removeEventListener('keydown', onKey);
      clearTimeout(focusTimer);
      previous?.focus?.();
    };
  }, [open, addGuard]);

  const drag = useRef<{ y: number; dy: number } | null>(null);
  const onDown = (e: RPointerEvent<HTMLDivElement>) => {
    drag.current = { y: e.clientY, dy: 0 };
    e.currentTarget.setPointerCapture(e.pointerId);
  };
  const onMove = (e: RPointerEvent<HTMLDivElement>) => {
    const d = drag.current;
    if (!d || !panel.current) return;
    d.dy = Math.max(0, e.clientY - d.y);
    panel.current.style.transition = 'none';
    panel.current.style.transform = `translateY(${d.dy}px)`;
  };
  const onUp = () => {
    const d = drag.current;
    drag.current = null;
    if (!d || !panel.current) return;
    if (d.dy > 90) onClose();
    else {
      panel.current.style.transition = 'transform 0.25s var(--spring)';
      panel.current.style.transform = '';
    }
  };

  if (!mounted || !device) return null;
  return createPortal(
    <div className="absolute inset-0 z-50 flex flex-col justify-end">
      <div className={cn('absolute inset-0 bg-scrim', closing ? 'anim-fade-out' : 'anim-fade')} onClick={onClose} aria-hidden />
      <div
        ref={panel}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title ?? 'Dialog'}
        className={cn('relative flex max-h-[90%] flex-col rounded-t-2xl bg-canvas text-ink outline-none', tall && 'h-[90%]', closing ? 'anim-sheet-out' : 'anim-sheet-in')}
      >
        <div className="flex h-6 shrink-0 touch-none items-center justify-center" onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp}>
          <span className="h-1 w-9 rounded-full bg-strong" />
        </div>
        {title && (
          <div className="flex shrink-0 items-start gap-3 px-5 pt-1 pb-3">
            <h2 className="display min-w-0 flex-1 pt-1 text-[1.75rem]">{title}</h2>
            <IconButton label="Close" onClick={onClose}>
              <X className="size-5" />
            </IconButton>
          </div>
        )}
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-1">{children}</div>
        {footer ? <div className="safe-b shrink-0 border-t border-hairline-soft px-5 pt-3">{footer}</div> : <div className="safe-b shrink-0" />}
      </div>
    </div>,
    device,
  );
}

export function ConfirmSheet({ open, onClose, title, body, confirmLabel, cancelLabel = 'Cancel', onConfirm, danger }: { open: boolean; onClose: () => void; title: string; body: ReactNode; confirmLabel: string; cancelLabel?: string; onConfirm: () => void; danger?: boolean }) {
  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={title}
      footer={
        <div className="flex flex-col gap-2">
          <Button variant={danger ? 'danger' : 'primary'} size="lg" full onClick={onConfirm}>
            {confirmLabel}
          </Button>
          <Button variant="ghost" full onClick={onClose}>
            {cancelLabel}
          </Button>
        </div>
      }
    >
      <p className="pb-4 text-[0.9375rem] leading-relaxed text-body">{body}</p>
    </Sheet>
  );
}

/* ---------- Form controls ---------- */
export function Switch({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={(e) => {
        e.stopPropagation();
        onChange(!checked);
      }}
      className={cn(
        'relative inline-flex h-8 w-13 shrink-0 items-center rounded-full border-2 transition-colors duration-200 before:absolute before:-inset-2 before:content-[""]',
        checked ? 'border-inverse bg-inverse' : 'border-muted-soft bg-transparent',
        disabled && 'opacity-50',
      )}
    >
      <span className={cn('block size-5 rounded-full transition-transform duration-300 ease-spring', checked ? 'translate-x-6 bg-on-inverse' : 'translate-x-1 bg-muted-soft')} />
    </button>
  );
}

export function Segmented<T extends string>({ value, onChange, options, label }: { value: T; onChange: (v: T) => void; options: { value: T; label: string }[]; label: string }) {
  const n = options.length;
  const i = Math.max(0, options.findIndex((o) => o.value === value));
  return (
    <div role="radiogroup" aria-label={label} className="relative grid rounded-md bg-card p-1" style={{ gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))` }}>
      <span
        aria-hidden
        className="absolute top-1 bottom-1 left-1 rounded-sm bg-canvas shadow-soft transition-transform duration-300 ease-spring"
        style={{ width: `calc((100% - 0.5rem) / ${n})`, transform: `translateX(${i * 100}%)` }}
      />
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={o.value === value}
          onClick={() => onChange(o.value)}
          className={cn('relative z-[1] min-h-10 rounded-sm px-1.5 text-sm leading-tight font-medium transition-colors duration-200', o.value === value ? 'text-ink' : 'text-body')}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Chip({ selected, onClick, children, sub, disabled, className }: { selected?: boolean; onClick?: () => void; children: ReactNode; sub?: ReactNode; disabled?: boolean; className?: string }) {
  return (
    <button
      type="button"
      aria-pressed={!!selected}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        'inline-flex min-h-11 flex-col items-center justify-center rounded-md border px-3.5 py-1.5 text-sm leading-tight font-medium transition-colors duration-150',
        selected ? 'border-inverse bg-inverse text-on-inverse' : 'border-hairline bg-canvas text-ink active:bg-soft',
        disabled && 'cursor-not-allowed border-hairline-soft text-muted-soft',
        className,
      )}
    >
      <span>{children}</span>
      {sub && <span className={cn('mt-0.5 text-xs font-normal', selected ? 'text-on-inverse-soft' : disabled ? 'text-muted-soft' : 'text-muted')}>{sub}</span>}
    </button>
  );
}

export function Range({ value, min, max, step = 1, onChange, label, suffix = '' }: { value: number; min: number; max: number; step?: number; onChange: (v: number) => void; label: string; suffix?: string }) {
  const p = ((value - min) / (max - min)) * 100;
  return (
    <div className="flex items-center gap-3">
      <input
        type="range"
        aria-label={label}
        className="range min-w-0 flex-1"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        style={{ '--p': `${p}%` } as CSSProperties}
      />
      <span className="tnum w-12 shrink-0 text-right font-mono text-[0.8125rem] text-ink">
        {value}
        {suffix}
      </span>
    </div>
  );
}

export function Stepper({ value, min, max, step, onChange, label, format }: { value: number; min: number; max: number; step: number; onChange: (v: number) => void; label: string; format?: (v: number) => string }) {
  return (
    <div className="inline-flex items-center rounded-md border border-hairline" role="group" aria-label={label}>
      <button type="button" aria-label={`Decrease ${label}`} disabled={value <= min} onClick={() => onChange(Math.max(min, value - step))} className="flex size-11 items-center justify-center text-ink disabled:text-muted-soft">
        <Minus className="size-4" />
      </button>
      <span className="tnum min-w-16 text-center font-mono text-sm text-ink" aria-live="polite">
        {format ? format(value) : value}
      </span>
      <button type="button" aria-label={`Increase ${label}`} disabled={value >= max} onClick={() => onChange(Math.min(max, value + step))} className="flex size-11 items-center justify-center text-ink disabled:text-muted-soft">
        <Plus className="size-4" />
      </button>
    </div>
  );
}

export function Field({ label, hint, right, children }: { label: string; hint?: ReactNode; right?: ReactNode; children: ReactNode }) {
  return (
    <div>
      <div className="mb-2 flex items-baseline justify-between gap-3">
        <h3 className="text-[0.9375rem] font-medium text-ink">{label}</h3>
        {right}
      </div>
      {children}
      {hint && <p className="mt-2 text-[0.8125rem] leading-snug text-muted">{hint}</p>}
    </div>
  );
}

/* ---------- Display ---------- */
type Tone = 'neutral' | 'strong' | 'amber' | 'outline' | 'dark';
export function Badge({ tone = 'neutral', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs leading-5 font-medium whitespace-nowrap',
        tone === 'neutral' && 'bg-card text-ink',
        tone === 'strong' && 'bg-strong text-ink',
        tone === 'amber' && 'bg-amber text-on-accent',
        tone === 'outline' && 'border border-hairline text-muted',
        tone === 'dark' && 'bg-dark-elevated text-on-dark',
        className,
      )}
    >
      {children}
    </span>
  );
}

export function Dot({ tone }: { tone: 'good' | 'ok' | 'no' | 'on' }) {
  return <span aria-hidden className={cn('inline-block size-1.5 shrink-0 rounded-full', tone === 'good' && 'bg-teal', tone === 'on' && 'bg-success', tone === 'ok' && 'bg-amber', tone === 'no' && 'bg-muted-soft')} />;
}

export function RadioMark({ checked, className }: { checked: boolean; className?: string }) {
  return (
    <span aria-hidden className={cn('flex size-6 shrink-0 items-center justify-center rounded-full border-2 transition-colors', checked ? 'border-inverse bg-inverse text-on-inverse' : 'border-muted-soft', className)}>
      {checked && <Check className="size-3.5" strokeWidth={3} />}
    </span>
  );
}

export function Group({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('divide-y divide-hairline-soft overflow-hidden rounded-lg border border-hairline bg-canvas', className)}>{children}</div>;
}

export function Row({ icon, title, sub, value, trailing, onClick, chevron, disabled }: { icon?: ReactNode; title: ReactNode; sub?: ReactNode; value?: ReactNode; trailing?: ReactNode; onClick?: () => void; chevron?: boolean; disabled?: boolean }) {
  const body = (
    <>
      {icon && <span className="shrink-0 text-ink [&>svg]:size-5">{icon}</span>}
      <span className="min-w-0 flex-1">
        <span className="block text-[0.9375rem] leading-snug font-medium text-ink">{title}</span>
        {sub && <span className="mt-0.5 block text-[0.8125rem] leading-snug text-muted">{sub}</span>}
      </span>
      {value && <span className="max-w-[45%] shrink-0 truncate text-sm text-muted">{value}</span>}
      {trailing}
      {chevron && <ChevronRight className="size-4 shrink-0 text-muted-soft" aria-hidden />}
    </>
  );
  const cls = 'flex min-h-14 w-full items-center gap-3 px-4 py-2.5 text-left';
  if (onClick)
    return (
      <button type="button" onClick={onClick} disabled={disabled} className={cn(cls, 'transition-colors active:bg-soft disabled:opacity-60')}>
        {body}
      </button>
    );
  return <div className={cls}>{body}</div>;
}

export function SwitchRow({ icon, title, sub, checked, onChange, disabled }: { icon?: ReactNode; title: string; sub?: ReactNode; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <div className="flex min-h-14 items-center gap-3 px-4 py-2.5" onClick={() => !disabled && onChange(!checked)}>
      {icon && <span className="shrink-0 text-ink [&>svg]:size-5">{icon}</span>}
      <span className="min-w-0 flex-1">
        <span className="block text-[0.9375rem] leading-snug font-medium text-ink">{title}</span>
        {sub && <span className="mt-0.5 block text-[0.8125rem] leading-snug text-muted">{sub}</span>}
      </span>
      <Switch checked={checked} onChange={onChange} label={title} disabled={disabled} />
    </div>
  );
}

export function ProgressBar({ value, label, className }: { value: number; label: string; className?: string }) {
  return (
    <div role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(value * 100)} className={cn('h-1.5 shrink-0 overflow-hidden rounded-full bg-strong', className)}>
      <div className="h-full rounded-full bg-primary" style={{ width: `${Math.min(100, value * 100)}%` }} />
    </div>
  );
}

export function Ring({ value, size = 28, label }: { value: number; size?: number; label: string }) {
  const r = (size - 4) / 2;
  const c = 2 * Math.PI * r;
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(value * 100)}>
      <circle cx={size / 2} cy={size / 2} r={r} strokeWidth="3" fill="none" className="stroke-strong" />
      <circle cx={size / 2} cy={size / 2} r={r} strokeWidth="3" fill="none" strokeLinecap="round" className="stroke-primary" strokeDasharray={c} strokeDashoffset={c * (1 - value)} />
    </svg>
  );
}

export function Toast() {
  const { toastMsg } = useApp();
  if (!toastMsg) return null;
  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-28 z-[60] flex justify-center px-6" role="status" aria-live="polite">
      <div key={toastMsg.id} className="anim-rise max-w-full rounded-md bg-inverse px-4 py-2.5 text-center text-sm leading-snug text-on-inverse shadow-soft">
        {toastMsg.msg}
      </div>
    </div>
  );
}
