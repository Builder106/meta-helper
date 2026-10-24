// What the router is doing right now, shared by the transition, the camera and the sheet.
export const nav = {
  from: '/',
  to: '/',
  type: 'push' as string,
  hadSheet: false,
  cameFromBoard: false,
};

export const clean = (p: string) => p.replace(/\/$/, '') || '/';

export function trackNav() {
  document.addEventListener('astro:before-preparation', (e) => {
    nav.from = clean(e.from.pathname);
    nav.to = clean(e.to.pathname);
    nav.type = e.navigationType;
    nav.hadSheet = !!document.querySelector('[data-sheet]');
    // Closing a unit goes back in history only when the previous entry is the board itself.
    nav.cameFromBoard = nav.to !== '/' && nav.from === '/';
  });
}
