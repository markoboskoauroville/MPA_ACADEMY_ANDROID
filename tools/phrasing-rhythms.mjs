// Request 23 (12.11.2026): Basic Phrasing over every traditional rhythm. The mechanism of PH01 (lessons/phrasing-malfuf.mjs in
// the mpa-academy repo) applied to the 17 rhythms of the site (lessons/traditional-rhythms.mjs, TR01-TR17, copied below):
// bar 1 = the rhythm (N cells), bar 2 = the same N beats split in two groups a + b = N:
//   FORWARD  (N-1)+1, (N-2)+2 ... 1+(N-1), then N   : DUM + ghost notes (a), then strong high notes (b)
//   REVERSE  N, (N-1)+1 ... 1+(N-1)                 : strong high notes (a), then DUM + ghost notes (b)
// Strong high notes follow the fingering of their number (the fingerings lessons): even = hand to hand S i S r ...,
// uneven = hand to hand and the last two i r. S = T (strong hand), i/r = K (weaker hand).
// Run: node tools/phrasing-rhythms.mjs  (rewrites android/app/src/main/assets/exercises.json, checks Malfuf against PH01)
import fs from 'fs';
const RHYTHMS = [
  ['Maqsum', '8/8', 'D T - T D - T -'], ['Baladi', '8/8', 'D D - T D - T -'], ['Sayyidi', '8/8', 'D T - D D - T -'],
  ['Malfuf', '8/16', 'D - - T - - T -'], ['Shiftatelli', '16/8', 'D - - T - - T - D - D - T - - -'],
  ['Ayoub', '8/16', 'D - - T D - T -'], ['Karachi', '8/16', 'T - - T T - D -'], ['Samai', '10/4', 'D - - T - D D T - -'],
  ['Karshlama', '9/8', 'D - T - D - T T -'], ['Laz', '7/8', 'D - D - T - -'], ['Kalamantiano', '7/8', 'D - - T - T -'],
  ['Tik', '5/8', 'D - - T -'], ['Baiduska', '5/16', 'D - T - -'], ['Igrig', '10/8', 'D - - T - D - T - -'],
  ['Kopanitza', '11/8', 'D - T - T - - D - T -'], ['Daasa', '7/8', 'D T - D - T -'], ['Chaabi', '6/8', 'T - T T D -'],
];
const fing = (n) => (n === 1 ? ['S'] : Array.from({ length: n }, (_, i) => (n % 2 && i >= n - 2 ? (i === n - 2 ? 'i' : 'r') : i % 2 === 0 ? 'S' : Math.floor(i / 2) % 2 === 0 ? 'i' : 'r')));
const strong = (n) => fing(n).map((f) => (f === 'S' ? 'T' : 'K'));
const dumGhost = (n) => ['D', ...Array(n - 1).fill('-')];
const down = (N) => Array.from({ length: N - 1 }, (_, i) => N - 1 - i);   // N-1 ... 1
const exercise = ([name, meter, cells], k) => {
  const bar1 = cells.split(' '); const N = bar1.length;
  const fwd = [...down(N), 0].map((a) => (a ? [...dumGhost(a), ...strong(N - a)] : strong(N)));
  const rev = [0, ...down(N)].map((a) => (a ? [...strong(a), ...dumGhost(N - a)] : strong(N)));
  const lab = (a) => (a ? `${a}+${N - a}` : `${N}`);
  const fl = (g) => fing(g).map((f) => ({ S: 'R', i: 'Li', r: 'Lr' })[f]).join(' ');
  const fings = [...new Set([...down(N), N])].sort((x, y) => x - y).map((g) => `${g}: ${fl(g)}`).join('\n');
  return {
    code: name === 'Malfuf' ? 'PH01' : `PT${String(k + 1).padStart(2, '0')}`,   // PH01 keeps its code (saved phrase); never shown
    name: name === 'Malfuf' ? 'Basic Phrasing over Malfuf 1' : `Basic Phrasing over ${name}`,
    rhythm: name, meter, beats: N, base: 20, loops: 2,
    notes: `Basic Phrasing over ${name} (${meter}, ${N} beats). Bar 1 is always ${name}: ${bar1.join(' ')}. Bar 2 splits its ${N} beats into two groups that always add up to ${N}.\n` +
      `FORWARD: DUM and ghost notes first, then strong high notes: ${down(N).map(lab).join(', ')}, then ${N}.\n` +
      `REVERSE DYNAMICS: strong high notes first, then DUM and ghost notes: ${N}, ${down(N).map(lab).join(', ')}.\n` +
      `Fingering of each group (R strong hand, Li left index, Lr left ring; even = hand to hand, uneven = the last two Li Lr):\n${fings}`,
    rows: [...fwd, ...rev].map((b2) => [...bar1, ...b2]),
  };
};
const f = new URL('../android/app/src/main/assets/exercises.json', import.meta.url);
const old = JSON.parse(fs.readFileSync(f, 'utf8'));
const ph01 = old.categories.flatMap((c) => c.exercises).find((e) => e.code === 'PH01');
const list = RHYTHMS.map(exercise);
const mal = list.find((e) => e.code === 'PH01');
if (JSON.stringify(mal.rows) !== JSON.stringify(ph01.rows)) { console.error('Malfuf differs from PH01'); process.exit(1); }
mal.notes = ph01.notes;   // PH01 keeps its own long note from the class
fs.writeFileSync(f, JSON.stringify({ categories: [{ name: 'Phrasing over traditional rhythms', exercises: list }] }, null, 1) + '\n');
for (const e of list) console.log(e.code, e.rhythm.padEnd(13), e.beats, e.rows.length, 'rows', e.rows[0].slice(e.beats).join(''), e.rows[e.beats - 1].slice(e.beats).join(''), e.rows.at(-1).slice(e.beats).join(''));
