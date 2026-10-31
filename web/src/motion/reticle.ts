// The reticle is your gaze through the glasses. Free, it follows the pointer with a head's lag.
// Locked, its corners close around a panel: on hover and focus, on the panel in the middle of
// the screen on touch and phones, and on the photographed panel while its answer is open.
import { gsap } from 'gsap';
import { isCanvas, isTouch, prefersReducedMotion } from '@/engine';
import { sheetWidth } from './camera';

type Why = 'hover' | 'focus' | 'gaze' | 'capture';
const TAG: Record<Why, string> = {
  hover: 'Click to take the photo',
  focus: 'Press Enter to take the photo',
  gaze: 'Tap to take the photo',
  capture: 'Photo taken',
};
const C = 34; // corner size, px

const box = { x: 0, y: 0, w: 120, h: 120, o: 0, tag: 0 };
const pointer = { x: -999, y: -999, inside: false };
let lockEl: HTMLElement | null = null;
let why: Why | null = null;
let base: HTMLElement | null = null; // the panel the open sheet belongs to
let root: HTMLElement;
let corners: HTMLElement[] = [];
let tagEl: HTMLElement;
let tagWidth = 0;
let gazeEl: HTMLElement | null = null;
let gazeName = '';
let frame = 0;

export function lock(el: HTMLElement, reason: Why) {
  lockEl = el;
  why = reason;
  if (tagEl.textContent !== TAG[reason]) {
    tagEl.textContent = TAG[reason];
    tagWidth = tagEl.offsetWidth;
  }
}

export function release() {
  if (base) return lock(base, 'capture');
  lockEl = null;
  why = null;
}

/** The panel whose answer is open, or null on pages without one. */
export function setBase(el: HTMLElement | null) {
  base = el;
  if (el) lock(el, 'capture');
  else if (why === 'capture') { lockEl = null; why = null; }
}

const panels = () => [...document.querySelectorAll<HTMLElement>('#board [data-unit]')];

function nearest(cx: number, cy: number, within: number) {
  let best: HTMLElement | null = null;
  let d = within;
  for (const p of panels()) {
    const r = p.getBoundingClientRect();
    if (r.width === 0) continue;
    const dx = Math.max(r.left - cx, 0, cx - r.right);
    const dy = Math.max(r.top - cy, 0, cy - r.bottom);
    const dist = Math.hypot(dx, dy);
    if (dist < d) { d = dist; best = p; }
  }
  return best;
}

function tick(t: number) {
  const canvas = isCanvas();
  const touch = isTouch();
  const free = innerWidth - sheetWidth();

  // Touch and phones have no hover: the panel in the middle of the screen is the one in view.
  if ((touch || !canvas) && why !== 'capture' && why !== 'focus' && ++frame % 8 === 0) {
    const p = nearest(free / 2, innerHeight / 2, innerHeight * 0.35);
    if (p) lock(p, 'gaze');
    else if (why === 'gaze') { lockEl = null; why = null; }
  }

  let tx = box.x, ty = box.y, tw = box.w, th = box.h, to = 0;
  if (lockEl && lockEl.isConnected) {
    const r = lockEl.getBoundingClientRect();
    const pad = canvas ? 18 : 12;
    tx = r.left - pad; ty = r.top - pad; tw = r.width + pad * 2; th = r.height + pad * 2;
    to = r.bottom > 0 && r.top < innerHeight && r.left < free ? 1 : 0;
  } else if (canvas && !touch && pointer.inside && pointer.x < free) {
    tw = 96; th = 96; tx = pointer.x - 48; ty = pointer.y - 48; to = 0.85;
  }

  const r = gsap.ticker.deltaRatio();
  const k = prefersReducedMotion() ? 1 : 1 - Math.pow(lockEl ? 0.72 : 0.86, r);
  box.x += (tx - box.x) * k;
  box.y += (ty - box.y) * k;
  box.w += (tw - box.w) * k;
  box.h += (th - box.h) * k;
  box.o += (to - box.o) * (prefersReducedMotion() ? 1 : 1 - Math.pow(0.8, r));
  const showTag = lockEl && why && to ? 1 : 0;
  box.tag += (showTag - box.tag) * (1 - Math.pow(0.75, r));

  const still = prefersReducedMotion();
  const sx = still ? 0 : Math.sin(t * 0.9) * 1.6 + Math.sin(t * 0.37) * 1.4;
  const sy = still ? 0 : Math.cos(t * 0.7) * 1.4;
  const x0 = box.x + sx, y0 = box.y + sy, x1 = box.x + box.w - C + sx, y1 = box.y + box.h - C + sy;
  corners[0].style.translate = `${x0}px ${y0}px`;
  corners[1].style.translate = `${x1}px ${y0}px`;
  corners[2].style.translate = `${x1}px ${y1}px`;
  corners[3].style.translate = `${x0}px ${y1}px`;
  root.style.opacity = box.o.toFixed(3);
  const below = y1 + C + 10;
  const tagX = Math.max(8, Math.min(x0, free - tagWidth - 8));
  tagEl.style.translate = `${tagX}px ${below + 28 > innerHeight ? y0 - 34 : below}px`;
  tagEl.style.opacity = box.tag.toFixed(3);

  // The corner tag says where you are looking, in the board's own words.
  if (frame % 6 === 0 && gazeEl?.isConnected) {
    const el = lockEl ?? (pointer.inside ? nearest(pointer.x, pointer.y, 220) : null);
    const name = el?.dataset.title ?? 'the board';
    if (name !== gazeName) { gazeName = name; gazeEl.textContent = name; }
  }
  frame++;
}

export function initReticle() {
  root = document.getElementById('reticle')!;
  corners = [...root.querySelectorAll<HTMLElement>('.reticle__c')];
  tagEl = root.querySelector<HTMLElement>('[data-reticle-tag]')!;
  tagWidth = tagEl.offsetWidth;
  gsap.ticker.add(tick);

  addEventListener('pointermove', (e) => {
    if (e.pointerType !== 'mouse' && e.pointerType !== 'pen') return;
    pointer.x = e.clientX; pointer.y = e.clientY; pointer.inside = true;
  }, { passive: true });
  document.documentElement.addEventListener('pointerleave', () => { pointer.inside = false; });

  document.addEventListener('pointerover', (e) => {
    if (e.pointerType !== 'mouse' && e.pointerType !== 'pen') return;
    const p = e.target instanceof Element ? e.target.closest<HTMLElement>('#board [data-unit]') : null;
    if (p && p !== base) lock(p, 'hover');
  });
  document.addEventListener('pointerout', (e) => {
    const p = e.target instanceof Element ? e.target.closest<HTMLElement>('#board [data-unit]') : null;
    const to = e.relatedTarget instanceof Element ? e.relatedTarget.closest('#board [data-unit]') : null;
    if (p && p === lockEl && why === 'hover' && to !== p) release();
  });
  document.addEventListener('focusin', (e) => {
    const p = e.target instanceof Element ? e.target.closest<HTMLElement>('#board [data-unit]') : null;
    if (p && p !== base) lock(p, 'focus');
  });
  document.addEventListener('focusout', (e) => {
    const p = e.target instanceof Element ? e.target.closest<HTMLElement>('#board [data-unit]') : null;
    if (p && p === lockEl && why === 'focus') release();
  });
}

/** Re-read the page-level UI after a swap (the corner gaze tag is page content). */
export function collectReticle() {
  gazeEl = document.querySelector<HTMLElement>('[data-gaze-name]');
  gazeName = '';
}
