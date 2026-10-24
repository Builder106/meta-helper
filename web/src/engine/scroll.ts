// Native scroll only: the desktop board is a camera, not a scrolling page, and phones scroll
// natively. The position is read on the GSAP ticker (the one clock) and fanned out to onScroll.
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

export type ScrollState = { y: number; velocity: number; progress: number };
const state: ScrollState = { y: 0, velocity: 0, progress: 0 };
const listeners = new Set<(s: ScrollState) => void>();
let started = false;

export function initScroll() {
  if (started) return;
  started = true;
  gsap.ticker.add(() => {
    const y = scrollY;
    if (y === state.y && state.velocity === 0) return;
    const max = document.documentElement.scrollHeight - innerHeight;
    state.velocity = y - state.y;
    state.y = y;
    state.progress = max > 0 ? y / max : 0;
    listeners.forEach((fn) => fn(state));
  });
}

export const onScroll = (fn: (s: ScrollState) => void) => {
  listeners.add(fn);
  return () => listeners.delete(fn);
};
export const scrollState = () => state;
export function resetScroll() {
  scrollTo(0, 0);
  state.y = 0;
  state.velocity = 0;
}
