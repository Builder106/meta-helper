// Every visible string on the site lives here.
// Assumptions (placeholder: true below, and listed in DESIGN.md):
//   - site.url is a guessed production domain.
//   - The code unit follows the backend prompt's two-part format. Other units use those same
//     headings for the site. All wording was written for this site, not recorded from the app.
//   - The glasses in the Blender renders are a generic pair of camera glasses, not Meta's product.

export type UnitSlug = 'sum' | 'pipeline' | 'glasses' | 'who' | 'notes' | 'run' | 'source';

export type Unit = {
  slug: UnitSlug;
  title: string; // written on the board and used as the sheet heading
  kind: 'code' | 'diagram' | 'text';
  summary: string; // meta description, 120 to 160 characters
  lede: string; // one line under the sheet heading
  readout: string[]; // VERBATIM READ-OUT, one spoken line per entry
  explanation: string[]; // EXPLANATION paragraphs
  links?: { label: string; href: string }[];
  // Board placement in board units (1 unit = 1 px of the 2800 x 1600 render).
  at: { x: number; y: number; w: number; r: number };
};

export const site = {
  name: 'MetaHelper',
  url: 'https://metahelper.yinkavaughan.me', // placeholder: true
  repo: 'https://github.com/Builder106/meta-helper',
  title: 'MetaHelper | Hear the code in front of you',
  description:
    'MetaHelper reads code aloud through Meta Ray-Ban glasses: take a photo of a screen, whiteboard or page and hear it word for word, then explained.',
  hero: {
    title: ['Hear the code', 'in front of you.'],
    body: 'MetaHelper reads code aloud through Meta Ray-Ban glasses. Photograph a screen, a whiteboard or a page, and it reads the code word for word, then tells you what it does.',
    start: 'start here',
  },
  note:
    'This example was written for the site. It is not a recording or a response from the app.',
  voiceNote: 'Listen uses your browser’s voice. On the glasses, Azure Speech reads it.',
  hint: {
    pointer: 'Drag or scroll to look around. Click a panel to take its photo.',
    touch: 'Tap a panel to take its photo.',
  },
  glasses: {
    photo: '/img/glasses.webp',
    alt: 'A pair of black camera glasses lying open on a desk in front of a whiteboard. A small camera sits in one corner of the frame and a light in the other.',
  },
} as const;

export const units: Unit[] = [
  {
    slug: 'sum',
    title: 'sum.c',
    kind: 'code',
    summary: 'A C function with an off-by-one bug, read aloud word for word and then explained, the way MetaHelper answers a photo of code.',
    lede: 'Six lines of C on the board, with one comparison circled in red.',
    readout: [
      'int sum, open paren, int a, open bracket, close bracket, comma, int n, close paren, open brace.',
      'Inside the function: int total equals zero, semicolon.',
      'for, open paren, int i equals zero, semicolon, i less than or equal to n, semicolon, i plus plus, close paren.',
      'Inside the loop: total plus equals a, open bracket, i, close bracket, semicolon.',
      'Back in the function: return total, semicolon.',
      'Close brace.',
      'Circled in red: less than or equal to. Written next to it: one step too far.',
    ],
    explanation: [
      'This is a C function called sum. It takes an array of integers called a, and its length, n, and adds the numbers up.',
      'First it sets total to zero. Then the for loop counts i up from zero while i is less than or equal to n, adding a of i to total each time. Finally it returns total.',
      'The circled comparison is the bug. An array of n numbers has positions zero to n minus one, so on the last pass, when i equals n, the loop reads one element past the end. In C that is undefined behavior: you might get a garbage number, or a crash. Change less than or equal to into less than.',
    ],
    at: { x: 2170, y: 330, w: 540, r: 1.2 },
  },
  {
    slug: 'pipeline',
    title: 'How it gets to your ears',
    kind: 'diagram',
    summary: 'How MetaHelper works: glasses photo, phone gallery, server, Gemini, Azure Speech, ffmpeg, and back to the open-ear speakers.',
    lede: 'A diagram in blue marker: seven boxes in a loop that starts and ends at the glasses.',
    readout: [
      'A hand-drawn diagram of seven boxes joined by arrows.',
      'Top row, left to right: glasses, takes the photo. Phone, spots it in the gallery. Server, POST slash process dash image. Gemini, reads it, explains it.',
      'An arrow runs down to the bottom row, which goes right to left: Azure Speech, says it as an MP3. ffmpeg, turns it down. Your ears, open-ear speakers.',
    ],
    explanation: [
      'You take the photo with the glasses. The Meta AI app saves it to your phone, and the MetaHelper app, which watches the gallery for new pictures, picks it up.',
      'The app uploads the photo to the MetaHelper server at /process-image. The server sends it to Gemini with one instruction: read the code word for word, then explain it.',
      'Azure Speech turns that text into an MP3, and ffmpeg lowers the volume, because the speakers sit right beside your ears. The phone plays the answer through the glasses.',
      'In testing, a warm server answered in about 10 to 25 seconds. The first answer from a real pair of glasses took 28.',
    ],
    at: { x: 110, y: 120, w: 1040, r: -0.6 },
  },
  {
    slug: 'glasses',
    title: 'The glasses',
    kind: 'diagram',
    summary: 'What MetaHelper uses on Meta Ray-Ban glasses: the camera takes the photo, the open-ear speakers play the answer, and a double-tap replays it.',
    lede: 'Two arrows in blue marker point down at the pair lying in the tray.',
    readout: [
      'Two arrows point at the glasses lying in the marker tray.',
      'One is labelled camera. The other is labelled capture light.',
      'Underneath: tap the arm twice to hear it again.',
    ],
    explanation: [
      'MetaHelper uses Meta Ray-Ban glasses for two jobs. The camera in the frame takes the photo, and the speakers in the arms play the answer without covering your ears, so you can still hear the room.',
      'The light beside the camera comes on while it takes a photo, so the people around you can see it.',
      'Double-tap the arm to hear the last answer again.',
      'Today the photo travels through the Meta AI app and your phone’s gallery. Direct capture through Meta’s Wearables SDK is a future option. It needs a registered app ID before that path can be developed and tested.',
    ],
    at: { x: 1150, y: 1150, w: 560, r: 0 },
  },
  {
    slug: 'who',
    title: 'Who it’s for',
    kind: 'text',
    summary: 'MetaHelper is for developers and students who are blind or have low vision, and anyone who needs code read to them hands-free.',
    lede: 'A short note in black marker on the left of the board.',
    readout: [
      'Who it’s for.',
      'For anyone who has to read code they can’t see well: the slide from the back row, the whiteboard in a lecture, a printout, someone else’s screen.',
    ],
    explanation: [
      'MetaHelper is for developers and students who are blind or have low vision, and for anyone whose hands and eyes are busy while the code they need sits on a screen, a whiteboard, a slide or a printed page.',
      'It reads the code exactly first, so you can follow along or type it out, and only then explains it, block by block.',
      'If the photo is blurred or cut off, it says so and asks you to take it again. It never guesses at what it can’t see.',
    ],
    at: { x: 150, y: 700, w: 640, r: -1 },
  },
  {
    slug: 'notes',
    title: 'Lab notes',
    kind: 'text',
    summary: 'How MetaHelper came together: server first, then gallery watching, then the first spoken answer from real glasses in June 2026.',
    lede: 'Four dated lines, each with a tick.',
    readout: [
      'Lab notes.',
      '17 December 2025: photo in, MP3 out.',
      '18 December 2025: watch the gallery instead.',
      '16 June 2026: first answer from real glasses.',
      '17 June 2026: retry when Gemini is busy.',
    ],
    explanation: [
      'The server came first. On the first day it could already take an image and return spoken audio, before any glasses were involved.',
      'The next day, capturing straight from the glasses through Meta’s SDK gave way to something that works today: the phone app watches the gallery and picks up each photo the glasses save there.',
      'On 16 June 2026 the whole loop ran on real hardware for the first time. A photo of a LeetCode problem, taken through the glasses, came back as a spoken answer in 28 seconds.',
      'A day later, busy errors from Gemini were making it say “I had trouble analyzing that image.” Now it waits and tries again, then falls back to a lighter model.',
    ],
    at: { x: 1260, y: 770, w: 660, r: 0.6 },
  },
  {
    slug: 'run',
    title: 'Run your own',
    kind: 'code',
    summary: 'Self-host the MetaHelper server: build the Docker image, add a Gemini key and Azure Speech credentials, and POST photos to /process-image.',
    lede: 'Two Docker commands and three settings, in black marker.',
    readout: [
      'docker build, dash t, metahelper dash backend, dot slash backend.',
      'docker run, dash p, 8080 colon 8080, backslash.',
      'Indented: dash dash env dash file, backend slash dot env, backslash.',
      'Indented: metahelper dash backend.',
      'Underneath: needs GOOGLE underscore API underscore KEY, AZURE underscore SPEECH underscore KEY, AZURE underscore SPEECH underscore REGION.',
    ],
    explanation: [
      'The server is a Spring Boot app on Java 26, with ffmpeg built into its Docker image. These two commands build the image and start it on port 8080.',
      'It needs three settings in backend/.env: a Google Gemini API key, an Azure Speech key, and the Azure region. The voice and the volume are optional settings.',
      'Send it a photo as a POST to /process-image, in a form field called file, and it answers with MP3 audio. Point the Android app at your server with the metahelper.backend.url Gradle property, or set MetaHelperBackendURL in the iOS Info.plist.',
    ],
    links: [
      { label: 'Self-hosting guide', href: 'https://github.com/Builder106/meta-helper/blob/main/docs/deployment.md' },
      { label: 'README', href: 'https://github.com/Builder106/meta-helper#readme' },
    ],
    at: { x: 2010, y: 1100, w: 700, r: -0.8 },
  },
  {
    slug: 'source',
    title: 'Source',
    kind: 'text',
    summary: 'MetaHelper is open source under the MIT License: a Kotlin Multiplatform phone app and a Spring Boot server, on GitHub.',
    lede: 'An address and a licence, in the bottom corner.',
    readout: [
      'Source.',
      'github dot com, slash Builder106, slash meta dash helper.',
      'MIT License. Kotlin and Spring Boot.',
    ],
    explanation: [
      'MetaHelper is open source under the MIT License.',
      'The phone app is Kotlin Multiplatform, with Jetpack Compose on Android and Compose Multiplatform on iOS, sharing the code that watches for photos, uploads them and plays the answer. The server is Spring Boot.',
      'Bug reports and pull requests are welcome. CONTRIBUTING.md explains how to build each part.',
    ],
    links: [
      { label: 'MetaHelper on GitHub', href: 'https://github.com/Builder106/meta-helper' },
      { label: 'CONTRIBUTING.md', href: 'https://github.com/Builder106/meta-helper/blob/main/CONTRIBUTING.md' },
      { label: 'MIT License', href: 'https://github.com/Builder106/meta-helper/blob/main/LICENSE' },
    ],
    at: { x: 480, y: 1130, w: 460, r: 1.4 },
  },
];

export const order: UnitSlug[] = ['sum', 'pipeline', 'glasses', 'who', 'notes', 'run', 'source'];
export const unitBySlug = (slug: string) => units.find((u) => u.slug === slug);
export const nextUnit = (slug: UnitSlug) => unitBySlug(order[(order.indexOf(slug) + 1) % order.length])!;

// Board places that are not units.
export const places = {
  hero: { x: 1240, y: 150, w: 900 },
  erased: { x: 2070, y: 1130, w: 620 },
};

export const notFound = {
  title: 'Nothing to read here',
  lede: 'A corner of the board that someone wiped.',
  readout: ['Nothing readable. This part of the board has been wiped clean.'],
  explanation: [
    'I can’t read anything at this address, and I won’t guess at what used to be here.',
    'Reframe and retake the photo: go back to the board, or open the transcript.',
  ],
};
