package app.tuxguitar.io.musicxml.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.base.TGSongReaderHelper;
import app.tuxguitar.io.base.TGSongStreamContext;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGString;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.TGVoice;
import app.tuxguitar.util.TGContext;

public class MusicXMLSongReaderTest {

	private final TGFactory factory = new TGFactory();
	private final TGSongManager manager = new TGSongManager(this.factory);

	@Test
	public void pianoStavesBecomeNotationOnlyTracks() throws Exception {
		TGSong song = read(resource("piano.musicxml"));

		assertEquals("Piano Test", song.getName());
		assertEquals("Test Composer", song.getAuthor());
		assertEquals(2, song.countTracks());
		assertEquals(2, song.countMeasureHeaders());

		TGTrack right = song.getTrack(0);
		TGTrack left = song.getTrack(1);
		for (TGTrack track : new TGTrack[] {right, left}) {
			assertFalse(track.hasTablature());
			assertTrue(track.hasScore());
			for (TGString string : track.getStrings()) {
				assertEquals(0, string.getValue());
			}
		}
		assertEquals(0, song.getChannel(0).getProgram());

		TGMeasureHeader first = song.getMeasureHeader(0);
		assertEquals(3, first.getTimeSignature().getNumerator());
		assertEquals(4, first.getTimeSignature().getDenominator().getValue());
		assertEquals(90, first.getTempo().getQuarterValue());
		assertEquals(90, song.getMeasureHeader(1).getTempo().getQuarterValue());
		assertNotNull(first.getMarker());
		assertEquals("A", first.getMarker().getTitle());

		TGMeasure rightFirst = right.getMeasure(0);
		assertEquals(TGMeasure.CLEF_TREBLE, rightFirst.getClef());
		assertEquals(TGMeasure.CLEF_BASS, left.getMeasure(0).getClef());
		assertEquals(8, rightFirst.getKeySignature());

		List<TGBeat> beats = soundingBeats(rightFirst);
		assertEquals(2, beats.size());
		assertEquals(List.of(72, 76), pitches(beats.get(0)));
		assertEquals(List.of(70), pitches(beats.get(1)));
		assertNotNull(beats.get(0).getChord());
		assertEquals("F", beats.get(0).getChord().getName());

		TGNote tiedStart = findNote(beats.get(0), 76);
		List<TGBeat> second = soundingBeats(right.getMeasure(1));
		assertEquals(5, second.size());
		TGNote tiedEnd = second.get(0).getVoice(0).getNote(0);
		assertTrue(tiedEnd.isTiedNote());
		assertEquals(76, realValue(tiedEnd));
		assertEquals(tiedStart.getString(), tiedEnd.getString());

		TGDuration triplet = second.get(1).getVoice(0).getDuration();
		assertEquals(TGDuration.EIGHTH, triplet.getValue());
		assertEquals(3, triplet.getDivision().getEnters());
		assertEquals(2, triplet.getDivision().getTimes());
		assertEquals(List.of(74), pitches(second.get(1)));
		assertEquals(List.of(67), pitches(second.get(4)));

		List<TGBeat> bass = soundingBeats(left.getMeasure(0));
		assertEquals(1, bass.size());
		assertEquals(List.of(53), pitches(bass.get(0)));
		assertTrue(bass.get(0).getVoice(0).getDuration().isDotted());
		assertTrue(soundingBeats(left.getMeasure(1)).isEmpty());
	}

	@Test
	public void tabStaffIsTheOnlySourceOfTablature() throws Exception {
		TGSong song = read(resource("guitar-notation-and-tab.musicxml"));

		assertEquals("Guitar Test", song.getName());
		assertEquals(1, song.countTracks());
		TGTrack guitar = song.getTrack(0);
		assertTrue(guitar.hasTablature());
		assertTrue(guitar.hasScore());
		assertEquals(-12, guitar.getOffset());
		assertEquals(List.of(76, 71, 67, 62, 57, 52), tuning(guitar));

		List<TGBeat> beats = soundingBeats(guitar.getMeasure(0));
		assertEquals(2, beats.size());
		TGNote open = beats.get(0).getVoice(0).getNote(0);
		assertEquals(6, open.getString());
		assertEquals(0, open.getValue());

		TGNote c = findNote(beats.get(1), 60);
		assertEquals(5, c.getString());
		assertEquals(3, c.getValue());
		assertTrue(c.getEffect().isHammer());
		TGNote e = findNote(beats.get(1), 64);
		assertEquals(4, e.getString());
		assertEquals(2, e.getValue());

		TGNote placed = soundingBeats(guitar.getMeasure(1)).get(0).getVoice(0).getNote(0);
		assertEquals(71, realValue(placed));
		assertEquals(2, placed.getString());
		assertEquals(0, placed.getValue());
	}

	@Test
	public void tabOnlyPartHasNoNotationStaff() throws Exception {
		TGSong song = read(resource("tab-only.musicxml"));

		TGTrack bass = song.getTrack(0);
		assertTrue(bass.hasTablature());
		assertFalse(bass.hasScore());
		assertEquals(33, song.getChannel(0).getProgram());
		assertEquals(4, bass.stringCount());

		TGNote note = soundingBeats(bass.getMeasure(0)).get(0).getVoice(0).getNote(0);
		assertEquals(3, note.getString());
		assertEquals(0, note.getValue());
		assertEquals(33, realValue(note) + bass.getOffset());
	}

	@Test
	public void pickupRepeatsAndEndings() throws Exception {
		TGSong song = read(resource("pickup-repeats.musicxml"));

		assertEquals(5, song.countMeasureHeaders());
		TGMeasureHeader pickup = song.getMeasureHeader(0);
		TGBeat pickupBeat = soundingBeats(song.getTrack(0).getMeasure(0)).get(0);
		assertEquals(pickup.getStart() + 3 * TGDuration.QUARTER_TIME, pickupBeat.getStart());

		assertTrue(song.getMeasureHeader(1).isRepeatOpen());
		assertEquals(2, song.getMeasureHeader(2).getRepeatClose());
		assertEquals(1, song.getMeasureHeader(2).getRepeatAlternative());
		assertEquals(2, song.getMeasureHeader(3).getRepeatAlternative());
		assertEquals(0, song.getMeasureHeader(4).getRepeatAlternative());
	}

	@Test
	public void compressedMxlUsesContainerRootFile() throws Exception {
		byte[] mxl = zip(
			"mimetype", "application/vnd.recordare.musicxml".getBytes(StandardCharsets.US_ASCII),
			"META-INF/container.xml", ("<?xml version=\"1.0\"?><container><rootfiles>"
				+ "<rootfile full-path=\"scores/piano.musicxml\" media-type=\"application/vnd.recordare.musicxml+xml\"/>"
				+ "</rootfiles></container>").getBytes(StandardCharsets.UTF_8),
			"decoy.xml", resource("tab-only.musicxml"),
			"scores/piano.musicxml", resource("piano.musicxml"));

		assertTrue(MusicXMLDocumentLoader.isMusicXML(mxl));
		TGSong song = read(mxl);
		assertEquals("Piano Test", song.getName());
		assertEquals(2, song.countTracks());
	}

	@Test
	public void guitarProSevenArchiveIsNotMusicXML() throws Exception {
		byte[] gp = zip(
			"Content/score.gpif", "<GPIF/>".getBytes(StandardCharsets.UTF_8),
			"Content/partwise.xml", resource("piano.musicxml"));
		assertFalse(MusicXMLDocumentLoader.isMusicXML(gp));
		assertFalse(MusicXMLDocumentLoader.isMusicXML("<html><body/></html>".getBytes(StandardCharsets.UTF_8)));
		assertTrue(MusicXMLDocumentLoader.isMusicXML(resource("piano.musicxml")));
	}

	@Test
	public void timewiseScoreIsConverted() throws Exception {
		String timewise = "<?xml version=\"1.0\"?><score-timewise version=\"4.0\">"
			+ "<part-list><score-part id=\"P1\"><part-name>Violin</part-name></score-part>"
			+ "<score-part id=\"P2\"><part-name>Cello</part-name></score-part></part-list>"
			+ "<measure number=\"1\">"
			+ "<part id=\"P1\"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time>"
			+ "<clef><sign>G</sign><line>2</line></clef></attributes>"
			+ "<note><pitch><step>A</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note></part>"
			+ "<part id=\"P2\"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time>"
			+ "<clef><sign>F</sign><line>4</line></clef></attributes>"
			+ "<note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration><type>whole</type></note></part>"
			+ "</measure></score-timewise>";
		TGSong song = read(timewise.getBytes(StandardCharsets.UTF_8));

		assertEquals(2, song.countTracks());
		assertEquals("Cello", song.getTrack(1).getName());
		assertEquals(List.of(69), pitches(soundingBeats(song.getTrack(0).getMeasure(0)).get(0)));
		assertEquals(List.of(48), pitches(soundingBeats(song.getTrack(1).getMeasure(0)).get(0)));
		assertFalse(song.getTrack(1).hasTablature());
	}

	@Test
	public void pluginIsDetectedThroughReaderHelper() throws Exception {
		TGContext context = new TGContext();
		new MusicXMLReaderPlugin().connect(context);

		TGSongReaderHandle handle = new TGSongReaderHandle();
		handle.setFactory(this.factory);
		handle.setContext(new TGSongStreamContext());
		handle.setInputStream(new ByteArrayInputStream(resource("guitar-notation-and-tab.musicxml")));
		new TGSongReaderHelper(context).read(handle);

		assertEquals(MusicXMLSongReader.FILE_FORMAT.getName(), handle.getFormat().getName());
		assertEquals("Guitar Test", handle.getSong().getName());
	}

	private TGSong read(byte[] data) throws Exception {
		return new MusicXMLSongParser(this.factory).parse(MusicXMLDocumentLoader.load(data));
	}

	private byte[] resource(String name) throws IOException {
		try (InputStream stream = getClass().getResourceAsStream("/musicxml/" + name)) {
			assertNotNull(stream, name);
			return stream.readAllBytes();
		}
	}

	private static byte[] zip(Object... entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (int i = 0; i < entries.length; i += 2) {
				zip.putNextEntry(new ZipEntry((String) entries[i]));
				zip.write((byte[]) entries[i + 1]);
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	private static List<TGBeat> soundingBeats(TGMeasure measure) {
		List<TGBeat> beats = new ArrayList<TGBeat>();
		for (TGBeat beat : measure.getBeats()) {
			if (!beat.getVoice(0).isEmpty() && !beat.getVoice(0).isRestVoice()) {
				beats.add(beat);
			}
		}
		return beats;
	}

	private List<Integer> pitches(TGBeat beat) {
		List<Integer> pitches = new ArrayList<Integer>();
		TGVoice voice = beat.getVoice(0);
		for (TGNote note : voice.getNotes()) {
			pitches.add(realValue(note));
		}
		pitches.sort(null);
		return pitches;
	}

	private TGNote findNote(TGBeat beat, int pitch) {
		for (TGNote note : beat.getVoice(0).getNotes()) {
			if (realValue(note) == pitch) {
				return note;
			}
		}
		throw new AssertionError("pitch " + pitch + " not found");
	}

	private int realValue(TGNote note) {
		return this.manager.getMeasureManager().getRealNoteValue(note);
	}

	private static List<Integer> tuning(TGTrack track) {
		List<Integer> values = new ArrayList<Integer>();
		for (TGString string : track.getStrings()) {
			values.add(string.getValue());
		}
		return values;
	}
}
