// The answer sheet: the read-out and the explanation arrive word by word, and Listen speaks them
// with the browser's voice, lighting each word as it is said.
import { gsap } from 'gsap';
import { SplitText } from 'gsap/SplitText';
import { ease, onPage, prefersReducedMotion } from '../engine';
import { nav } from './nav';

gsap.registerPlugin(SplitText);

const PREF = 'metahelper:voice';
const readPref = () => { try { return localStorage.getItem(PREF) === 'on'; } catch { return false; } };
const writePref = (on: boolean) => { try { localStorage.setItem(PREF, on ? 'on' : 'off'); } catch { /* private mode */ } };

export function startSheet() {
  onPage(({ intro, first }) => {
    const sheet = document.querySelector<HTMLElement>('[data-sheet]');
    if (!sheet) return;
    const synth = 'speechSynthesis' in window ? window.speechSynthesis : null;
    const listen = sheet.querySelector<HTMLButtonElement>('[data-listen]');
    const replay = sheet.querySelector<HTMLButtonElement>('[data-replay]');
    const close = sheet.querySelector<HTMLAnchorElement>('[data-close]');
    const photo = sheet.querySelector<HTMLElement>('[data-photo]');
    const head = [...sheet.querySelectorAll<HTMLElement>('.sheet__top, .sheet__title, .sheet__lede, .controls')];

    // Words, in reading order, with their character offsets in the spoken text.
    const blocks = [...sheet.querySelectorAll<HTMLElement>('[data-speak] > *')];
    const splits = blocks.map((b) => SplitText.create(b, { type: 'words', wordsClass: 'word', aria: 'none' }));
    const words: HTMLElement[] = [];
    const starts: number[] = [];
    let text = '';
    splits.forEach((s) => {
      (s.words as HTMLElement[]).forEach((w) => {
        starts.push(text.length);
        words.push(w);
        text += (w.textContent ?? '') + ' ';
      });
      text += ' ';
    });

    const still = prefersReducedMotion();
    const reveal = still ? null : gsap.timeline({ paused: true })
      .from(head, { y: 18, autoAlpha: 0, duration: 0.7, stagger: 0.06, ease: ease.out })
      .from(words, { y: 6, autoAlpha: 0, duration: 0.45, stagger: 0.007, ease: ease.soft }, 0.25);
    if (reveal) intro.add(() => { reveal.play(0); }, first ? 0.55 : 0.05);
    if (first && !still) {
      const wide = matchMedia('(min-width: 900px)').matches;
      intro.from(sheet, wide ? { xPercent: 100, duration: 1, ease: ease.inOut } : { yPercent: 100, duration: 0.9, ease: ease.inOut }, 0);
    }

    let said = -1;
    const mark = (i: number) => {
      if (i === said) return;
      for (let j = Math.max(said, 0); j <= i; j++) words[j]?.classList.add('is-said');
      said = i;
    };
    const stop = () => {
      synth?.cancel();
      sheet.classList.remove('is-speaking');
      words.forEach((w) => w.classList.remove('is-said'));
      said = -1;
    };
    const speak = () => {
      if (!synth) return;
      stop();
      const u = new SpeechSynthesisUtterance(text);
      u.lang = 'en-US';
      u.rate = 1;
      u.onboundary = (e) => {
        if (e.name && e.name !== 'word') return;
        let lo = 0, hi = starts.length - 1;
        while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (starts[mid] <= e.charIndex) lo = mid; else hi = mid - 1; }
        mark(lo);
      };
      u.onend = () => { sheet.classList.remove('is-speaking'); };
      sheet.classList.add('is-speaking');
      synth.speak(u);
    };

    let on = readPref();
    if (synth && listen) {
      listen.hidden = false;
      sheet.querySelector<HTMLElement>('[data-voice-note]')?.removeAttribute('hidden');
      listen.setAttribute('aria-pressed', String(on));
      if (on && !first) gsap.delayedCall(0.6, speak);
    }
    const onListen = () => {
      on = !on;
      writePref(on);
      listen?.setAttribute('aria-pressed', String(on));
      if (on) speak(); else stop();
    };
    const onReplay = () => {
      stop();
      reveal?.play(0.25);
      if (on) speak();
    };
    const onClose = (e: MouseEvent) => {
      if (!nav.cameFromBoard || e.metaKey || e.ctrlKey || e.shiftKey) return;
      e.preventDefault();
      history.back();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && close) close.click();
    };
    listen?.addEventListener('click', onListen);
    replay?.addEventListener('click', onReplay);
    close?.addEventListener('click', onClose);
    photo?.addEventListener('dblclick', onReplay);
    document.addEventListener('keydown', onKey);

    return () => {
      stop();
      splits.forEach((s) => s.revert());
      listen?.removeEventListener('click', onListen);
      replay?.removeEventListener('click', onReplay);
      close?.removeEventListener('click', onClose);
      photo?.removeEventListener('dblclick', onReplay);
      document.removeEventListener('keydown', onKey);
    };
  });
}
