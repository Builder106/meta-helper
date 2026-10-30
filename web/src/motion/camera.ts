// The camera over the board (wide screens). One transform on every [data-camera] layer, applied
// on the GSAP ticker. Input moves an aim point; the camera eases toward it.
import { gsap } from 'gsap';
import { ease, isCanvas, prefersReducedMotion } from '../engine';

export const BOARD = { w: 2800, h: 1600 };
export const cam = { x: 0, y: 0, s: 1 };
const aim = { x: 0, y: 0 };
let layers: HTMLElement[] = [];
let glide: gsap.core.Tween | null = null;
let qx: gsap.QuickToFunc;
let qy: gsap.QuickToFunc;
let wasCanvas = false;

const fitScale = () => gsap.utils.clamp(0.72, 1.15, Math.min(innerWidth / 1480, innerHeight / 880));

function limits() {
  const vw = innerWidth / cam.s;
  const vh = innerHeight / cam.s;
  const m = 60;
  const lx = BOARD.w - vw;
  const ly = BOARD.h - vh;
  return {
    x: lx < 0 ? [lx / 2, lx / 2] : [-m, lx + m],
    y: ly < 0 ? [ly / 2, ly / 2] : [-m, ly + m],
  };
}

function clampTo(x: number, y: number) {
  const l = limits();
  return { x: gsap.utils.clamp(l.x[0], l.x[1], x), y: gsap.utils.clamp(l.y[0], l.y[1], y) };
}

/** The sheet's width on a wide screen, measured or (before it exists) estimated from the token. */
export function sheetWidth(expectSheet?: boolean) {
  if (!isCanvas()) return 0;
  const sheet = document.querySelector<HTMLElement>('[data-sheet]');
  if (sheet) return sheet.classList.contains('sheet--wide') ? 0 : sheet.offsetWidth;
  return expectSheet ? gsap.utils.clamp(416, 704, innerWidth * 0.44) : 0;
}

export function collect() {
  layers = [...document.querySelectorAll<HTMLElement>('[data-camera]')];
  render(gsap.ticker.time);
}

function render(t: number) {
  const canvas = isCanvas();
  if (!canvas) {
    if (wasCanvas) layers.forEach((l) => (l.style.transform = ''));
    wasCanvas = false;
    return;
  }
  wasCanvas = true;
  const still = prefersReducedMotion();
  const dx = still ? 0 : Math.sin(t * 0.33) * 2.4;
  const dy = still ? 0 : Math.cos(t * 0.27) * 1.6;
  const tx = -(cam.x + dx) * cam.s;
  const ty = -(cam.y + dy) * cam.s;
  const value = `translate3d(${tx.toFixed(2)}px, ${ty.toFixed(2)}px, 0) scale(${cam.s})`;
  for (const l of layers) l.style.transform = value;
}

function resetNudgers() {
  qx = gsap.quickTo(cam, 'x', { duration: 0.7, ease: ease.soft });
  qy = gsap.quickTo(cam, 'y', { duration: 0.7, ease: ease.soft });
}

function stopCameraMotion() {
  glide?.kill();
  gsap.killTweensOf(cam, 'x,y');
  resetNudgers();
}

/** Where the camera must be for a board point to sit at a screen point. */
function camFor(ux: number, uy: number, sx: number, sy: number) {
  return clampTo(ux - sx / cam.s, uy - sy / cam.s);
}

function center(el: HTMLElement) {
  return { x: el.offsetLeft + el.offsetWidth / 2, y: el.offsetTop + el.offsetHeight / 2 };
}

/** Camera target that puts el in the middle of the free part of the screen. */
export function frameTarget(el: HTMLElement, expectSheet = false) {
  const c = center(el);
  const free = innerWidth - sheetWidth(expectSheet);
  return camFor(c.x, c.y, free / 2, innerHeight / 2);
}

export function jumpTo(p: { x: number; y: number }) {
  stopCameraMotion();
  Object.assign(aim, p);
  cam.x = p.x;
  cam.y = p.y;
  render(gsap.ticker.time);
}

export function glideTo(p: { x: number; y: number }, duration?: number) {
  stopCameraMotion();
  Object.assign(aim, p);
  const d = duration ?? gsap.utils.clamp(0.45, 1.1, Math.hypot(p.x - cam.x, p.y - cam.y) / 1500);
  if (prefersReducedMotion() || d < 0.05) {
    cam.x = p.x;
    cam.y = p.y;
    render(gsap.ticker.time);
    return gsap.timeline();
  }
  glide = gsap.to(cam, { x: p.x, y: p.y, duration: d, ease: ease.inOut });
  return glide;
}

function nudge(dx: number, dy: number) {
  glide?.kill();
  const p = clampTo(aim.x + dx, aim.y + dy);
  Object.assign(aim, p);
  if (prefersReducedMotion()) return jumpTo(p);
  qx(p.x);
  qy(p.y);
}

/** Bring el into view if any of it is off the free part of the screen. */
export function reveal(el: HTMLElement) {
  if (!isCanvas()) return;
  const r = el.getBoundingClientRect();
  const free = innerWidth - sheetWidth();
  const m = 48;
  if (r.left >= m && r.top >= m && r.right <= free - m && r.bottom <= innerHeight - m) return;
  jumpTo(frameTarget(el));
}

const insideUi = (t: EventTarget | null) =>
  t instanceof Element && !!t.closest('[data-sheet], .corner, .skip-link, button, input, textarea, select');

export function initCamera() {
  cam.s = fitScale();
  resetNudgers();
  gsap.ticker.add((t) => render(t));
  document.addEventListener('astro:after-swap', collect);

  addEventListener('resize', () => {
    cam.s = fitScale();
    jumpTo(clampTo(cam.x, cam.y));
  });

  // Drag to look around. A drag that starts on a panel must not also open it.
  let start: { x: number; y: number; cx: number; cy: number; id: number } | null = null;
  let dragged = false;
  document.addEventListener('pointerdown', (e) => {
    if (!isCanvas() || e.button !== 0 || insideUi(e.target)) return;
    start = { x: e.clientX, y: e.clientY, cx: aim.x, cy: aim.y, id: e.pointerId };
    dragged = false;
  });
  addEventListener('pointermove', (e) => {
    if (!start || e.pointerId !== start.id) return;
    const dx = e.clientX - start.x;
    const dy = e.clientY - start.y;
    if (!dragged && Math.hypot(dx, dy) < 6) return;
    dragged = true;
    document.documentElement.classList.add('is-dragging');
    nudge(start.cx - dx / cam.s - aim.x, start.cy - dy / cam.s - aim.y);
  });
  const end = () => {
    start = null;
    document.documentElement.classList.remove('is-dragging');
  };
  addEventListener('pointerup', end);
  addEventListener('pointercancel', end);
  document.addEventListener('click', (e) => {
    if (!dragged) return;
    dragged = false;
    e.preventDefault();
    e.stopPropagation();
  }, true);
  document.addEventListener('dragstart', (e) => {
    if (e.target instanceof Element && e.target.closest('#board, .window')) e.preventDefault();
  });

  // Wheel and trackpad pan the board; over the sheet they scroll the sheet.
  addEventListener('wheel', (e) => {
    if (!isCanvas() || insideUi(e.target)) return;
    e.preventDefault();
    const k = e.deltaMode === 1 ? 16 : 1;
    const dx = (e.shiftKey && !e.deltaX ? e.deltaY : e.deltaX) * k;
    const dy = (e.shiftKey && !e.deltaX ? 0 : e.deltaY) * k;
    nudge(dx / cam.s, dy / cam.s);
  }, { passive: false });

  // Arrow keys look around when focus is on the board, not in the sheet.
  document.addEventListener('keydown', (e) => {
    if (!isCanvas() || insideUi(e.target) || e.altKey || e.metaKey || e.ctrlKey) return;
    const step = 160;
    const map: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step],
    };
    const d = map[e.key];
    if (!d) return;
    e.preventDefault();
    nudge(d[0], d[1]);
  });

  // Tabbing to a panel looks at it.
  document.addEventListener('focusin', (e) => {
    const panel = e.target instanceof Element ? e.target.closest<HTMLElement>('#board [data-unit]') : null;
    if (panel) reveal(panel);
  });
}
