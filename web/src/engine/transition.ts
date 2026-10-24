// Page transitions. The adapter runs leave() while the next page loads. The site registers its
// own capture transition with setTransition(); these defaults only cover a missing setup.
import { gsap } from 'gsap';
import { prefersReducedMotion } from './env';

export type Transition = {
  leave: (curtain: HTMLElement) => Promise<unknown> | gsap.core.Animation;
  enter: (curtain: HTMLElement) => Promise<unknown> | gsap.core.Animation;
};

let current: Transition = {
  leave: (c) => gsap.timeline().set(c, { autoAlpha: 1 }).fromTo(c, { opacity: 0 }, { opacity: 1, duration: 0.3 }),
  enter: (c) => gsap.to(c, { autoAlpha: 0, duration: 0.3 }),
};
let reduced: Transition | null = null;

export const setTransition = (t: Transition, calm?: Transition) => { current = t; reduced = calm ?? null; };
const pick = () => (prefersReducedMotion() && reduced ? reduced : current);
const curtain = () => document.getElementById('curtain') as HTMLElement;

export const playLeave = () => Promise.resolve(pick().leave(curtain()));
export const playEnter = () => Promise.resolve(pick().enter(curtain()));
