import assert from "node:assert/strict";
import {
  InstrumentType,
  fTuningNoteNames,
  tuningNoteNamesToMidi,
  importMusicXmlToSimplifiedScore,
  importMidiToSimplifiedScore,
  mapSimplifiedScoreToKora,
  buildLayoutModel,
  exportKoraPerformanceToMidiBytes,
  exportSimplifiedScoreToMidiBytes,
  exportKoraPerformanceToWavBytes,
  exportSimplifiedScoreToWavBytes,
  exportLessonToPdfBytes,
  createEditorState,
  applyEditorEdit,
  estimateDifficultyFromScore,
  buildSimplifiedTeachingReduction,
} from "../kora_engine/index.js";

const SAMPLE_MUSICXML = `<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1">
      <part-name>Melody</part-name>
    </score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <key><fifths>0</fifths></key>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <direction placement="above">
        <direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>96</per-minute></metronome></direction-type>
        <sound tempo="96"/>
      </direction>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><type>quarter</type></note>
      <note><pitch><step>D</step><octave>4</octave></pitch><duration>1</duration><type>quarter</type></note>
      <note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><type>quarter</type></note>
      <note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><type>quarter</type></note>
    </measure>
  </part>
</score-partwise>`;

const cases = [
  { label: "KORA_21", instrumentType: InstrumentType.KORA_21 },
  { label: "KORA_22", instrumentType: InstrumentType.KORA_22_CHROMATIC },
];

const reductionWithHeldNote = buildSimplifiedTeachingReduction({
  noteEvents: [
    { eventId: "held", tick: 0, durationTicks: 3840, pitchMidi: 72, melodyHint: true },
    { eventId: "bass_0", tick: 0, durationTicks: 960, pitchMidi: 48 },
    { eventId: "bass_1", tick: 960, durationTicks: 960, pitchMidi: 49 },
    { eventId: "bass_2", tick: 1920, durationTicks: 960, pitchMidi: 50 },
    { eventId: "bass_3", tick: 2880, durationTicks: 960, pitchMidi: 51 },
  ],
  cap: 2,
  splitMidi: 60,
  mappingCostForMidi: () => 0,
});
const heldReduced = reductionWithHeldNote.events.filter((event) => event.sourceEventId === "held");
assert.equal(heldReduced.length, 1, "held source note should not be split into crotchets");
assert.equal(heldReduced[0].durationTicks, 3840, "held source note should preserve its full duration");

function assertBytePrefix(bytes, text, label) {
  const prefix = new TextDecoder().decode(bytes.slice(0, text.length));
  assert.equal(prefix, text, `${label} should start with ${text}`);
}

for (const testCase of cases) {
  const tuning = tuningNoteNamesToMidi(fTuningNoteNames(testCase.instrumentType));
  const score = importMusicXmlToSimplifiedScore({
    xmlText: SAMPLE_MUSICXML,
    instrumentType: testCase.instrumentType,
    tuningMidiByStringId: tuning.strings,
  });

  assert.equal(score.events.filter((event) => event.type === "NOTE").length, 4, `${testCase.label} XML note count`);

  const mapped = mapSimplifiedScoreToKora({
    instrumentType: testCase.instrumentType,
    tuningMidiByStringId: tuning.strings,
    score,
  });
  assert.ok(mapped.events.length >= 4, `${testCase.label} mapped events`);

  const layout = buildLayoutModel({ score, mappedEvents: mapped.events });
  assert.ok(layout.systems.length > 0, `${testCase.label} layout systems`);
  assert.ok(layout.systems.some((system) => system.staffLayer?.length > 0), `${testCase.label} staff layout`);

  const difficulty = estimateDifficultyFromScore({ score });
  assert.ok(Number.isFinite(difficulty.score), `${testCase.label} difficulty score`);

  const koraMidi = exportKoraPerformanceToMidiBytes({ score, mappedEvents: mapped.events });
  const simplifiedMidi = exportSimplifiedScoreToMidiBytes({ score });
  assertBytePrefix(koraMidi, "MThd", `${testCase.label} kora MIDI`);
  assertBytePrefix(simplifiedMidi, "MThd", `${testCase.label} simplified MIDI`);

  const reimported = importMidiToSimplifiedScore({
    midiBytes: simplifiedMidi,
    instrumentType: testCase.instrumentType,
    tuningMidiByStringId: tuning.strings,
  });
  assert.ok(reimported.events.some((event) => event.type === "NOTE"), `${testCase.label} MIDI reimport`);

  const koraWav = exportKoraPerformanceToWavBytes({ score, mappedEvents: mapped.events });
  const simplifiedWav = exportSimplifiedScoreToWavBytes({ score });
  assertBytePrefix(koraWav, "RIFF", `${testCase.label} kora WAV`);
  assertBytePrefix(simplifiedWav, "RIFF", `${testCase.label} simplified WAV`);

  const pdf = exportLessonToPdfBytes({
    score,
    mappedEvents: mapped.events,
    retunePlan: mapped.retunePlan,
    metadata: {
      pieceName: `Smoke ${testCase.label}`,
      tuningName: tuning.name,
      exportedAtIso: "2026-06-14T00:00:00.000Z",
      difficulty: difficulty.score,
    },
  });
  assertBytePrefix(pdf, "%PDF", `${testCase.label} PDF`);

  const editResult = applyEditorEdit({
    state: createEditorState({ score }),
    edit: { type: "TRANSPOSE_SEMITONE", semitones: 1 },
  });
  assert.notDeepEqual(editResult.state.score.events, score.events, `${testCase.label} transpose edits score`);
}

console.log("Kora engine smoke tests passed for MusicXML, MIDI, WAV, PDF, and transpose flows.");
