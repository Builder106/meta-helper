export const prefersReducedMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
export const isTouch = () => matchMedia('(hover: none), (pointer: coarse)').matches;
export const isMobile = () => matchMedia('(max-width: 767px)').matches;
/** The board is a camera you look across at this width; below it, a column you scroll. */
export const isCanvas = () => matchMedia('(min-width: 900px)').matches;

// Shared easing vocabulary. Use these names everywhere; never invent per-component curves.
export const ease = {
  out: 'expo.out', // default entrance
  inOut: 'expo.inOut', // camera moves, sheet, transitions
  soft: 'power3.out', // small UI
  write: 'power2.inOut', // marker strokes
} as const;
