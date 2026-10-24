// Glaxnimate artwork on the engine's single clock: GSAP draws the marker strokes.
import { gsap } from 'gsap';
import { prefersReducedMotion } from './env';

/** Draw strokes that carry pathLength="1". Under reduced motion they are drawn at once. */
export function drawOn(targets: gsap.TweenTarget, vars: gsap.TweenVars = {}) {
  const els = gsap.utils.toArray<SVGGeometryElement>(targets);
  if (!els.length) return null;
  if (prefersReducedMotion()) {
    gsap.set(els, { strokeDasharray: 1, strokeDashoffset: 0 });
    return null;
  }
  gsap.set(els, { strokeDasharray: 1, strokeDashoffset: 1 });
  return gsap.to(els, { strokeDashoffset: 0, duration: 0.9, ease: 'power2.inOut', ...vars });
}
