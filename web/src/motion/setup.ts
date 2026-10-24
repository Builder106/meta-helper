// Global motion: the capture transition, where the camera looks on each page, and the first-load
// intro (the hero sentence is written in marker). Runs once on the client before the first page.
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { ease, isCanvas, onPage, prefersReducedMotion, setTransition } from '../engine';
import { drawOn } from '../engine/glax';
import { collect, frameTarget, glideTo, jumpTo } from './camera';
import { collectReticle, initReticle, lock, release, setBase } from './reticle';
import { initCamera } from './camera';
import { clean, nav, trackNav } from './nav';
import { startSheet } from './sheet';

gsap.registerPlugin(ScrollTrigger);

const panelFor = (path: string) =>
  path === '/' ? null : document.querySelector<HTMLElement>(`#board [data-unit="${path.slice(1)}"]`);
const erased = () => document.querySelector<HTMLElement>('#board [data-place="erased"]');
const hero = () => document.querySelector<HTMLElement>('[data-place="hero"]');
const isUnitPath = (p: string) => !!panelFor(p);

/** Wait a beat, then hand over to the page intro; the rest of the timeline keeps playing. */
const handOff = (tl: gsap.core.Timeline, after: number) =>
  new Promise<void>((resolve) => { gsap.delayedCall(Math.min(after, tl.duration()), resolve); });

function capture() {
  setTransition(
    {
      leave: (curtain) => {
        const to = nav.to;
        const panel = panelFor(to);
        const sheet = document.querySelector<HTMLElement>('[data-sheet]');
        const body = sheet?.querySelector<HTMLElement>('[data-sheet-body]');
        const flash = curtain.querySelector<HTMLElement>('.curtain__panel');
        const tl = gsap.timeline();
        const wide = isCanvas();

        if (panel) {
          // Look at the panel, let the viewfinder close on it, then the shutter.
          if (body) tl.to(body, { autoAlpha: 0, duration: 0.25, ease: ease.soft }, 0);
          if (wide) tl.add(glideTo(frameTarget(panel, true)), 0);
          tl.add(() => lock(panel, 'capture'), wide ? '>-0.2' : 0);
          tl.to({}, { duration: 0.28 });
          tl.set(curtain, { autoAlpha: 1 });
          tl.fromTo(flash, { opacity: 0 }, { opacity: 0.92, duration: 0.07, ease: 'power2.out' });
          return tl;
        }
        if (to === '/') {
          setBase(null);
          release();
          if (sheet) tl.to(sheet, wide ? { xPercent: 100, duration: 0.7, ease: ease.inOut } : { yPercent: 100, duration: 0.6, ease: ease.inOut });
          return tl;
        }
        // The transcript, the 404 and anything else: the answer text clears, the board stays.
        setBase(null);
        if (body) tl.to(body, { autoAlpha: 0, duration: 0.25 });
        const e = erased();
        if (wide && e && !isUnitPath(to) && to !== '/transcript') tl.add(glideTo(frameTarget(e, true)), 0);
        return tl;
      },
      enter: (curtain) => {
        const flash = curtain.querySelector<HTMLElement>('.curtain__panel');
        const sheet = document.querySelector<HTMLElement>('[data-sheet]');
        const photo = sheet?.querySelector<HTMLElement>('[data-photo]');
        const tl = gsap.timeline();
        if (getComputedStyle(curtain).visibility === 'visible') {
          tl.to(flash, { opacity: 0, duration: 0.45, ease: 'power2.out' }, 0).set(curtain, { autoAlpha: 0 });
        }
        if (sheet && !nav.hadSheet) {
          tl.from(sheet, isCanvas() ? { xPercent: 100, duration: 0.85, ease: ease.out } : { yPercent: 100, duration: 0.8, ease: ease.out }, 0);
        }
        if (photo) tl.from(photo, { y: -40, rotate: 7, scale: 1.14, autoAlpha: 0, duration: 0.85, ease: ease.out }, 0.02);
        return handOff(tl, 0.15);
      },
    },
    {
      // Reduced motion: no camera glide or shutter, a short fade.
      leave: (curtain) => {
        const panel = panelFor(nav.to);
        if (panel && isCanvas()) jumpTo(frameTarget(panel, true));
        if (nav.to === '/') setBase(null);
        return gsap.timeline().set(curtain, { autoAlpha: 1 }).fromTo(curtain.querySelector('.curtain__panel'), { opacity: 0 }, { opacity: 0.4, duration: 0.12 });
      },
      enter: (curtain) => gsap.to(curtain, { autoAlpha: 0, duration: 0.15 }),
    },
  );
}

/** The hero sentence is written left to right, then underlined, then the arrow to sum.c. */
function writeHero(intro: gsap.core.Timeline) {
  const title = document.querySelector<HTMLElement>('[data-hero-title]');
  if (!title || prefersReducedMotion() || !isCanvas()) return;
  const lines = title.querySelectorAll<HTMLElement>('.line');
  const tl = gsap.timeline({ paused: true });
  tl.fromTo(lines, { clipPath: 'inset(-12% 100% -24% -2%)' }, { clipPath: 'inset(-12% -3% -24% -2%)', duration: 1.05, ease: ease.write, stagger: 0.62 });
  const under = drawOn('.place--hero .mark--underline [data-draw]', { paused: true, duration: 0.7 });
  if (under) tl.add(under.play(), '-=0.15');
  tl.from('[data-hero-body]', { autoAlpha: 0, y: 14, duration: 0.9, ease: ease.out }, '-=0.35');
  const arrow = drawOn('.place--hero .mark--start [data-draw]', { paused: true, duration: 0.6, stagger: 0.25 });
  if (arrow) tl.add(arrow.play(), '-=0.5');
  tl.from('[data-hero-start]', { autoAlpha: 0, duration: 0.5 }, '<');
  intro.add(() => { tl.play(0); }, 0.1);
}

/** Marks on the board draw themselves the first time they come into view. */
function drawBoard(first: boolean) {
  if (prefersReducedMotion()) return;
  const marks = [...document.querySelectorAll<SVGSVGElement>('#board .panel .mark')];
  if (isCanvas()) {
    if (!first) return;
    const seen = marks.filter((m) => {
      const r = m.getBoundingClientRect();
      return r.right > 0 && r.left < innerWidth && r.bottom > 0 && r.top < innerHeight;
    });
    seen.forEach((m, i) => drawOn(m.querySelectorAll('[data-draw]'), { delay: 1.1 + i * 0.25, stagger: 0.03, duration: 0.6 }));
    return;
  }
  marks.forEach((m) => {
    const paths = m.querySelectorAll('[data-draw]');
    const t = drawOn(paths, { paused: true, stagger: 0.025, duration: 0.6 });
    if (t) ScrollTrigger.create({ trigger: m, start: 'top 85%', once: true, onEnter: () => t.play() });
  });
}

export function setupMotion() {
  trackNav();
  initCamera();
  initReticle();
  capture();
  startSheet();

  onPage(({ intro, first }) => {
    collect();
    collectReticle();
    const root = document.documentElement;
    const page = root.dataset.page;
    const path = clean(location.pathname);
    const panel = page === 'unit' ? panelFor(path) : null;

    document.querySelectorAll('#board [data-unit]').forEach((p) => p.removeAttribute('aria-current'));
    panel?.setAttribute('aria-current', 'page');
    setBase(panel);

    if (isCanvas()) {
      const spot = panel ?? (page === 'missing' ? erased() : null);
      if (first) {
        const h = hero();
        if (spot) jumpTo(frameTarget(spot, true));
        else if (h) jumpTo(frameTarget(h));
      } else if (page === 'missing' && spot) {
        glideTo(frameTarget(spot, true));
      }
    }

    if (first && page === 'home') writeHero(intro);
    drawBoard(first);
  });
}
