package app.tuxguitar.io.musicxml.reader;

import static app.tuxguitar.io.musicxml.reader.MusicXMLDom.child;
import static app.tuxguitar.io.musicxml.reader.MusicXMLDom.children;
import static app.tuxguitar.io.musicxml.reader.MusicXMLDom.has;
import static app.tuxguitar.io.musicxml.reader.MusicXMLDom.intAttribute;
import static app.tuxguitar.io.musicxml.reader.MusicXMLDom.intText;
import static app.tuxguitar.io.musicxml.reader.MusicXMLDom.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import app.tuxguitar.gm.GMChannelRoute;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGChannelParameter;
import app.tuxguitar.song.models.TGChord;
import app.tuxguitar.song.models.TGColor;
import app.tuxguitar.song.models.TGDivisionType;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGMarker;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGNoteEffect;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGString;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.TGVelocities;
import app.tuxguitar.song.models.TGVoice;
import app.tuxguitar.song.models.effects.TGEffectHarmonic;

/**
 * Converts a partwise MusicXML document into a TGSong.
 *
 * Tablature is created only for staves the file itself declares as TAB (clef sign "TAB").
 * Every other staff becomes a notation-only track (see {@link TGTrack#hasTablature()}).
 * Pitches are stored as written; the part transposition goes to the track offset,
 * which only affects playback.
 */
public class MusicXMLSongParser {

	private static final int DEFAULT_TEMPO = 120;
	private static final int PERCUSSION_GM_CHANNEL = 9;
	private static final int GM_CHANNELS = 16;
	private static final int NOTATION_MAX_FRET = 127;
	private static final int TAB_MAX_FRET = 29;
	private static final int[] STEP_SEMITONES = {9, 11, 0, 2, 4, 5, 7}; // A..G

	private static final int[][] DEFAULT_TUNINGS = {
		{},
		{64},
		{64, 59},
		{64, 59, 55},
		{43, 38, 33, 28},
		{43, 38, 33, 28, 23},
		{64, 59, 55, 50, 45, 40},
		{64, 59, 55, 50, 45, 40, 35},
		{64, 59, 55, 50, 45, 40, 35, 30},
	};

	private final TGFactory factory;
	private final TGSongManager manager;
	private TGSong song;
	private int nextGmChannel;
	private final List<PendingChord> pendingChords = new ArrayList<PendingChord>();

	public MusicXMLSongParser(TGFactory factory) {
		this.factory = factory;
		this.manager = new TGSongManager(factory);
	}

	public TGSong parse(Document document) throws MusicXMLFormatException {
		Element root = document.getDocumentElement();
		List<Element> parts = children(root, "part");
		if( parts.isEmpty() ) {
			throw new MusicXMLFormatException("Score has no parts");
		}
		int measureCount = 0;
		for (Element part : parts) {
			measureCount = Math.max(measureCount, children(part, "measure").size());
		}
		if( measureCount == 0 ) {
			throw new MusicXMLFormatException("Score has no measures");
		}

		this.song = this.factory.newSong();
		this.nextGmChannel = 0;
		this.pendingChords.clear();

		this.parseInfo(root);
		Map<String, PartInfo> partInfos = this.parsePartList(root);
		this.createHeaders(parts, measureCount);

		List<PartContext> contexts = new ArrayList<PartContext>();
		for (Element part : parts) {
			PartInfo info = partInfos.get(part.getAttribute("id"));
			PartContext context = this.createPart(part, (info != null ? info : new PartInfo()));
			this.parseMeasures(context, measureCount);
			contexts.add(context);
		}
		this.alignPickupMeasure(parts.get(0), contexts);
		for (PartContext context : contexts) {
			for (TrackContext track : context.tracks) {
				this.finishTrack(track);
			}
		}
		this.manager.orderBeats(this.song);
		this.manager.autoCompleteSilences(this.song);
		this.attachChords();

		return this.song;
	}

	// ---------------------------------------------------------------- song info

	private void parseInfo(Element root) {
		String workTitle = text(root, "work", "work-title");
		String movementTitle = text(root, "movement-title");
		String title = (notEmpty(workTitle) ? workTitle : movementTitle);
		if( notEmpty(title) ) {
			this.song.setName(title);
		}
		if( notEmpty(workTitle) && notEmpty(movementTitle) && !workTitle.equals(movementTitle) ) {
			this.song.setComments(movementTitle);
		}
		Element identification = child(root, "identification");
		for (Element creator : children(identification, "creator")) {
			String type = creator.getAttribute("type");
			String value = creator.getTextContent().trim();
			if( value.isEmpty() ) {
				continue;
			}
			if( type.equals("composer") || type.isEmpty() ) {
				this.song.setAuthor(value);
			} else if( type.equals("arranger") ) {
				this.song.setWriter(value);
			}
		}
		String rights = text(identification, "rights");
		if( notEmpty(rights) ) {
			this.song.setCopyright(rights);
		}
	}

	private Map<String, PartInfo> parsePartList(Element root) {
		Map<String, PartInfo> infos = new HashMap<String, PartInfo>();
		for (Element scorePart : children(child(root, "part-list"), "score-part")) {
			PartInfo info = new PartInfo();
			info.name = text(scorePart, "part-name");
			for (Element midi : children(scorePart, "midi-instrument")) {
				if( info.program < 0 ) {
					info.program = intText(midi, 0, "midi-program") - 1;
				}
				if( info.midiChannel < 0 ) {
					info.midiChannel = intText(midi, -1, "midi-channel");
				}
				int unpitched = intText(midi, 0, "midi-unpitched");
				if( unpitched > 0 ) {
					info.unpitched.put(midi.getAttribute("id"), unpitched - 1);
				}
			}
			infos.put(scorePart.getAttribute("id"), info);
		}
		return infos;
	}

	// ---------------------------------------------------------------- measure headers

	private void createHeaders(List<Element> parts, int measureCount) {
		int numerator = 4;
		int denominator = 4;
		int tempo = DEFAULT_TEMPO;
		int endingBits = 0;
		long preciseStart = TGDuration.getPreciseStartingPoint();

		for (int m = 0; m < measureCount; m++) {
			TGMeasureHeader header = this.factory.newHeader();
			header.setNumber(m + 1);

			boolean timeFound = false;
			boolean tempoFound = false;
			boolean endingStops = false;
			String rehearsal = null;

			for (Element part : parts) {
				Element measure = measureAt(part, m);
				if( measure == null ) {
					continue;
				}
				for (Element element : children(measure)) {
					String name = element.getNodeName();
					if( name.equals("attributes") && !timeFound ) {
						int[] time = parseTime(child(element, "time"));
						if( time != null ) {
							numerator = time[0];
							denominator = time[1];
							timeFound = true;
						}
					} else if( name.equals("barline") ) {
						Element repeat = child(element, "repeat");
						if( repeat != null ) {
							if( "forward".equals(repeat.getAttribute("direction")) ) {
								header.setRepeatOpen(true);
							} else if( "backward".equals(repeat.getAttribute("direction")) ) {
								header.setRepeatClose(Math.max(1, intAttribute(repeat, "times", 2) - 1));
							}
						}
						Element ending = child(element, "ending");
						if( ending != null ) {
							String type = ending.getAttribute("type");
							if( type.equals("start") ) {
								endingBits = parseEndingNumbers(ending.getAttribute("number"));
							} else if( type.equals("stop") || type.equals("discontinue") ) {
								endingStops = true;
							}
						}
					} else if( name.equals("direction") ) {
						Integer directionTempo = parseDirectionTempo(element);
						if( directionTempo != null && !tempoFound ) {
							tempo = directionTempo;
							tempoFound = true;
						}
						String mark = text(element, "direction-type", "rehearsal");
						if( rehearsal == null && notEmpty(mark) ) {
							rehearsal = mark;
						}
					} else if( name.equals("sound") && !tempoFound ) {
						int soundTempo = intAttribute(element, "tempo", -1);
						if( soundTempo > 0 ) {
							tempo = soundTempo;
							tempoFound = true;
						}
					}
				}
			}

			header.getTimeSignature().setNumerator(numerator);
			header.getTimeSignature().getDenominator().setValue(denominator);
			header.getTempo().setQuarterValue(tempo);
			header.setRepeatAlternative(endingBits);
			if( rehearsal != null ) {
				TGMarker marker = this.factory.newMarker();
				marker.setMeasure(header.getNumber());
				marker.setTitle(rehearsal);
				header.setMarker(marker);
			}
			header.setPreciseStart(preciseStart);
			preciseStart += header.getPreciseLength();
			this.song.addMeasureHeader(header);

			if( endingStops ) {
				endingBits = 0;
			}
		}
	}

	private static int[] parseTime(Element time) {
		if( time == null || has(time, "senza-misura") ) {
			return null;
		}
		int beats = 0;
		for (Element element : children(time, "beats")) {
			for (String value : element.getTextContent().split("\\+")) {
				beats += MusicXMLDom.toInt(value, 0);
			}
		}
		int beatType = intText(time, 4, "beat-type");
		if( beats <= 0 || !isPowerOfTwo(beatType) || beatType > TGDuration.SHORTEST ) {
			return null;
		}
		return new int[]{beats, beatType};
	}

	private static int parseEndingNumbers(String numbers) {
		int bits = 0;
		for (String value : numbers.split("[,\\s]+")) {
			int number = MusicXMLDom.toInt(value, 0);
			if( number >= 1 && number <= 8 ) {
				bits |= (1 << (number - 1));
			}
		}
		return bits;
	}

	private static Integer parseDirectionTempo(Element direction) {
		Element sound = child(direction, "sound");
		int soundTempo = intAttribute(sound, "tempo", -1);
		if( soundTempo > 0 ) {
			return soundTempo;
		}
		Element metronome = child(direction, "direction-type", "metronome");
		if( metronome != null && has(metronome, "per-minute") ) {
			double perMinute = MusicXMLDom.doubleText(metronome, -1, "per-minute");
			int unit = typeValue(text(metronome, "beat-unit"));
			if( perMinute > 0 && unit > 0 ) {
				double quarters = (4.0 / unit) * (has(metronome, "beat-unit-dot") ? 1.5 : 1.0);
				return (int) Math.round(perMinute * quarters);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- parts and tracks

	private PartContext createPart(Element part, PartInfo info) {
		PartContext context = new PartContext(info);
		context.part = part;
		Map<Integer, StaffInfo> staves = new TreeMap<Integer, StaffInfo>();
		int staffCount = 1;
		boolean transposeFound = false;

		for (Element measure : children(part, "measure")) {
			for (Element element : children(measure)) {
				if( element.getNodeName().equals("attributes") ) {
					staffCount = Math.max(staffCount, intText(element, 1, "staves"));
					for (Element clef : children(element, "clef")) {
						StaffInfo staff = staffInfo(staves, intAttribute(clef, "number", 1));
						String sign = text(clef, "sign");
						if( "TAB".equalsIgnoreCase(sign) ) {
							staff.tablature = true;
						} else if( "percussion".equalsIgnoreCase(sign) ) {
							context.percussion = true;
						}
					}
					for (Element details : children(element, "staff-details")) {
						StaffInfo staff = staffInfo(staves, intAttribute(details, "number", 1));
						staff.lines = intText(details, staff.lines, "staff-lines");
						for (Element tuning : children(details, "staff-tuning")) {
							Integer pitch = pitch(text(tuning, "tuning-step"), MusicXMLDom.doubleText(tuning, 0, "tuning-alter"), intText(tuning, 4, "tuning-octave"));
							if( pitch != null ) {
								staff.tuning.put(intAttribute(tuning, "line", 1), pitch);
							}
						}
					}
					Element transpose = child(element, "transpose");
					if( transpose != null && !transposeFound ) {
						context.transpose = intText(transpose, 0, "chromatic") + (12 * intText(transpose, 0, "octave-change"));
						transposeFound = true;
					}
				} else if( element.getNodeName().equals("note") && has(element, "unpitched") ) {
					context.percussion = true;
				}
			}
		}
		if( info.midiChannel == (PERCUSSION_GM_CHANNEL + 1) ) {
			context.percussion = true;
		}
		for (int s = 1; s <= staffCount; s++) {
			staffInfo(staves, s);
		}

		boolean hasTablature = false;
		boolean hasNotation = false;
		for (StaffInfo staff : staves.values()) {
			hasTablature |= staff.tablature;
			hasNotation |= !staff.tablature;
		}
		if( context.percussion ) {
			hasTablature = false;
		}

		TGChannel channel = this.createChannel(info, context.percussion, hasTablature);
		String partName = (notEmpty(info.name) ? info.name : ("Track " + (this.song.countTracks() + 1)));

		List<StaffInfo> trackStaves = new ArrayList<StaffInfo>();
		for (Map.Entry<Integer, StaffInfo> entry : staves.entrySet()) {
			StaffInfo staff = entry.getValue();
			staff.number = entry.getKey();
			// A TAB staff next to a notation staff is the same music written twice:
			// read it once, from the TAB staff, and show both representations.
			if(!hasTablature || staff.tablature) {
				trackStaves.add(staff);
			}
		}
		for (StaffInfo staff : trackStaves) {
			TGTrack track = this.factory.newTrack();
			track.setNumber(this.song.countTracks() + 1);
			track.setName(trackStaves.size() > 1 ? (partName + " " + staff.number) : partName);
			track.setChannelId(channel.getChannelId());
			track.getColor().copyFrom(TGColor.RED);
			track.setTablature(staff.tablature && !context.percussion);
			track.setScore(!track.hasTablature() || hasNotation);
			if(!context.percussion) {
				track.setOffset(Math.max(TGTrack.MIN_OFFSET, Math.min(TGTrack.MAX_OFFSET, context.transpose)));
			}
			this.song.addTrack(track);

			TrackContext trackContext = new TrackContext(track, staff, context.percussion);
			if( track.hasTablature() ) {
				trackContext.fileTuning = tablatureTuning(staff);
			}
			context.tracks.add(trackContext);
			context.byStaff.put(staff.number, trackContext);
		}
		return context;
	}

	private TGChannel createChannel(PartInfo info, boolean percussion, boolean tablature) {
		TGChannel channel = this.factory.newChannel();
		channel.setChannelId(this.song.countChannels() + 1);
		channel.setBank(percussion ? TGChannel.DEFAULT_PERCUSSION_BANK : TGChannel.DEFAULT_BANK);
		short program = (short) (info.program >= 0 ? info.program : (tablature ? TGChannel.DEFAULT_PROGRAM : 0));
		channel.setProgram(percussion ? TGChannel.DEFAULT_PERCUSSION_PROGRAM : program);

		String gmChannel = Integer.toString(percussion ? PERCUSSION_GM_CHANNEL : this.nextMelodicGmChannel());
		TGChannelParameter gmChannel1 = this.factory.newChannelParameter();
		gmChannel1.setKey(GMChannelRoute.PARAMETER_GM_CHANNEL_1);
		gmChannel1.setValue(gmChannel);
		TGChannelParameter gmChannel2 = this.factory.newChannelParameter();
		gmChannel2.setKey(GMChannelRoute.PARAMETER_GM_CHANNEL_2);
		gmChannel2.setValue(gmChannel);
		channel.addParameter(gmChannel1);
		channel.addParameter(gmChannel2);

		channel.setName(this.manager.createChannelNameFromProgram(this.song, channel));
		this.song.addChannel(channel);
		return channel;
	}

	private int nextMelodicGmChannel() {
		int channel = (this.nextGmChannel++ % GM_CHANNELS);
		if( channel == PERCUSSION_GM_CHANNEL ) {
			channel = (this.nextGmChannel++ % GM_CHANNELS);
		}
		return channel;
	}

	private static int[] tablatureTuning(StaffInfo staff) {
		int stringCount = staff.lines;
		if( stringCount <= 0 ) {
			stringCount = (staff.tuning.isEmpty() ? 6 : staff.tuning.size());
		}
		stringCount = Math.max(TGTrack.MIN_STRINGS, Math.min(TGTrack.MAX_STRINGS, stringCount));
		int[] defaults = (stringCount < DEFAULT_TUNINGS.length ? DEFAULT_TUNINGS[stringCount] : null);
		int[] tuning = new int[stringCount];
		for (int s = 1; s <= stringCount; s++) {
			// staff-tuning line 1 is the bottom line = lowest string; TG string 1 is the highest.
			Integer pitch = staff.tuning.get(stringCount - s + 1);
			if( pitch == null ) {
				pitch = (defaults != null ? defaults[s - 1] : (64 - (5 * (s - 1))));
			}
			tuning[s - 1] = pitch;
		}
		return tuning;
	}

	// ---------------------------------------------------------------- measures

	private void parseMeasures(PartContext context, int measureCount) {
		List<Element> measures = children(context.part, "measure");
		for (int m = 0; m < measureCount; m++) {
			TGMeasureHeader header = this.song.getMeasureHeader(m);
			for (TrackContext track : context.tracks) {
				TGMeasure measure = this.factory.newMeasure(header);
				measure.setClef(track.clef);
				measure.setKeySignature(keySignature(track.fifths));
				track.track.addMeasure(measure);
			}
			if( m < measures.size() ) {
				this.parseMeasure(context, measures.get(m), header, m);
			}
		}
	}

	private void parseMeasure(PartContext context, Element measureElement, TGMeasureHeader header, int index) {
		long measureLength = header.getPreciseLength();
		context.cursor = 0;
		context.lastStart = 0;
		context.measureEnd = 0;

		for (Element element : children(measureElement)) {
			String name = element.getNodeName();
			if( name.equals("attributes") ) {
				this.parseAttributes(context, element, index);
			} else if( name.equals("note") ) {
				this.parseNote(context, element, header, index);
			} else if( name.equals("backup") ) {
				context.cursor = Math.max(0, context.cursor - context.toPrecise(intText(element, 0, "duration")));
			} else if( name.equals("forward") ) {
				context.cursor += context.toPrecise(intText(element, 0, "duration"));
			} else if( name.equals("direction") ) {
				Integer velocity = parseDynamics(element);
				if( velocity != null ) {
					context.velocity = velocity;
				}
			} else if( name.equals("harmony") ) {
				this.parseHarmony(context, element, index);
			}
			context.measureEnd = Math.max(context.measureEnd, Math.min(context.cursor, measureLength));
		}
		if( index == 0 ) {
			context.firstMeasureEnd = context.measureEnd;
		}
	}

	private void parseAttributes(PartContext context, Element attributes, int index) {
		int divisions = intText(attributes, -1, "divisions");
		if( divisions > 0 ) {
			context.divisions = divisions;
		}
		for (Element key : children(attributes, "key")) {
			if(!has(key, "fifths")) {
				continue;
			}
			int fifths = intText(key, 0, "fifths");
			int staff = intAttribute(key, "number", -1);
			for (TrackContext track : context.tracks) {
				if( staff < 0 || staff == track.staff.number ) {
					track.fifths = fifths;
					track.track.getMeasure(index).setKeySignature(keySignature(fifths));
				}
			}
		}
		for (Element clef : children(attributes, "clef")) {
			TrackContext track = context.byStaff.get(intAttribute(clef, "number", 1));
			if( track != null ) {
				track.clef = clef(text(clef, "sign"), intText(clef, 0, "line"));
				track.track.getMeasure(index).setClef(track.clef);
			}
		}
	}

	private void parseNote(PartContext context, Element note, TGMeasureHeader header, int index) {
		boolean chord = has(note, "chord");
		boolean grace = has(note, "grace");
		Element restElement = child(note, "rest");
		long duration = (grace ? 0 : context.toPrecise(intText(note, 0, "duration")));

		long start;
		if( chord ) {
			start = context.lastStart;
		} else {
			start = context.cursor;
			context.lastStart = start;
			context.cursor += duration;
		}
		if( grace || has(note, "cue") ) {
			return;
		}
		if( restElement != null && ("yes".equals(restElement.getAttribute("measure")) || !has(note, "type") || "no".equals(note.getAttribute("print-object"))) ) {
			// autoCompleteSilences refills these gaps with rests of the exact length
			return;
		}
		TrackContext track = context.byStaff.get(intText(note, 1, "staff"));
		if( track == null || start >= header.getPreciseLength() ) {
			return;
		}
		int voiceIndex = track.voiceIndex(text(note, "voice"));
		if( voiceIndex < 0 ) {
			return;
		}

		TGMeasure measure = track.track.getMeasure(index);
		TGBeat beat = this.getBeat(measure, header.getPreciseStart() + start);
		TGVoice voice = beat.getVoice(voiceIndex);
		if(!chord || voice.isEmpty()) {
			if(!voice.isEmpty()) {
				return;
			}
			voice.getDuration().copyFrom(this.parseDuration(note, duration));
			voice.setEmpty(false);
		}
		if( restElement != null ) {
			return;
		}

		Integer pitch = this.parsePitch(context, note);
		if( pitch == null ) {
			return;
		}
		TGNote tgNote = this.factory.newNote();
		tgNote.setVelocity(context.velocity);
		tgNote.setTiedNote(isTieStop(note));
		this.parseEffects(note, tgNote, track);

		Element pitchElement = child(note, "pitch");
		if( pitchElement != null && !context.percussion ) {
			int alter = MusicXMLDom.toInt(text(pitchElement, "alter"), 0);
			if( (alter < 0 && track.fifths >= 0) || (alter > 0 && track.fifths < 0) ) {
				tgNote.toggleAltEnharmonic();
			}
		}

		if( track.track.hasTablature() ) {
			int string = MusicXMLDom.toInt(notation(note, "technical", "string"), -1);
			int fret = MusicXMLDom.toInt(notation(note, "technical", "fret"), -1);
			if( string >= 1 && string <= track.fileTuning.length && fret >= 0 && !isStringUsed(beat, string) ) {
				tgNote.setString(string);
				tgNote.setValue(fret);
				voice.addNote(tgNote);
				track.vote(pitch - (track.fileTuning[string - 1] + fret));
			} else {
				track.pending.add(new PendingNote(tgNote, voice, pitch));
			}
		} else {
			int slot = this.allocateSlot(track, beat, voiceIndex, pitch, tgNote.isTiedNote());
			if( slot > 0 ) {
				tgNote.setString(slot);
				tgNote.setValue(pitch);
				voice.addNote(tgNote);
				track.lastSlots.get(voiceIndex).put(pitch, slot);
				track.maxSlot = Math.max(track.maxSlot, slot);
			}
		}
	}

	private TGBeat getBeat(TGMeasure measure, long preciseStart) {
		for (TGBeat beat : measure.getBeats()) {
			if( beat.getPreciseStart() != null && beat.getPreciseStart() == preciseStart ) {
				return beat;
			}
		}
		TGBeat beat = this.factory.newBeat();
		beat.setPreciseStart(preciseStart);
		measure.addBeat(beat);
		return beat;
	}

	private TGDuration parseDuration(Element note, long preciseDuration) {
		TGDuration duration = this.factory.newDuration();
		int value = typeValue(text(note, "type"));
		if( value > 0 ) {
			duration.setValue(value);
			int dots = children(note, "dot").size();
			duration.setDotted(dots == 1);
			duration.setDoubleDotted(dots >= 2);
			Element modification = child(note, "time-modification");
			if( modification == null ) {
				return duration;
			}
			int actual = intText(modification, 1, "actual-notes");
			int normal = intText(modification, 1, "normal-notes");
			for (TGDivisionType divisionType : TGDivisionType.DIVISION_TYPES) {
				if( divisionType.getEnters() == actual && divisionType.getTimes() == normal ) {
					duration.getDivision().setEnters(actual);
					duration.getDivision().setTimes(normal);
					return duration;
				}
			}
		}
		if( preciseDuration > 0 ) {
			return TGDuration.fromTime(this.factory, TGDuration.toTime(preciseDuration));
		}
		return duration;
	}

	private Integer parsePitch(PartContext context, Element note) {
		Element pitch = child(note, "pitch");
		if( pitch != null ) {
			return pitch(text(pitch, "step"), MusicXMLDom.doubleText(pitch, 0, "alter"), intText(pitch, 4, "octave"));
		}
		Element unpitched = child(note, "unpitched");
		if( unpitched != null ) {
			Element instrument = child(note, "instrument");
			if( instrument != null ) {
				Integer key = context.info.unpitched.get(instrument.getAttribute("id"));
				if( key != null ) {
					return key;
				}
			}
			return pitch(text(unpitched, "display-step"), 0, intText(unpitched, 4, "display-octave"));
		}
		return null;
	}

	private void parseEffects(Element note, TGNote tgNote, TrackContext track) {
		TGNoteEffect effect = tgNote.getEffect();
		for (Element notations : children(note, "notations")) {
			for (Element articulation : children(child(notations, "articulations"))) {
				String name = articulation.getNodeName();
				if( name.equals("staccato") || name.equals("staccatissimo") ) {
					effect.setStaccato(true);
				} else if( name.equals("accent") ) {
					effect.setAccentuatedNote(true);
				} else if( name.equals("strong-accent") ) {
					effect.setHeavyAccentuatedNote(true);
				}
			}
			for (Element technical : children(child(notations, "technical"))) {
				String name = technical.getNodeName();
				if( (name.equals("hammer-on") || name.equals("pull-off")) && "start".equals(technical.getAttribute("type")) ) {
					effect.setHammer(true);
				} else if( name.equals("harmonic") && track.track.hasTablature() ) {
					TGEffectHarmonic harmonic = this.factory.newEffectHarmonic();
					harmonic.setType(TGEffectHarmonic.TYPE_NATURAL);
					int fret = MusicXMLDom.toInt(notation(note, "technical", "fret"), -1);
					for (int i = 0; i < TGEffectHarmonic.NATURAL_FREQUENCIES.length; i++) {
						if( TGEffectHarmonic.NATURAL_FREQUENCIES[i][0] == fret ) {
							harmonic.setData(i);
						}
					}
					effect.setHarmonic(harmonic);
				}
			}
			for (Element element : children(notations)) {
				if( (element.getNodeName().equals("slide") || element.getNodeName().equals("glissando")) && "start".equals(element.getAttribute("type")) ) {
					effect.setSlide(true);
				}
			}
		}
		Element notehead = child(note, "notehead");
		if( notehead != null ) {
			if( "x".equals(notehead.getTextContent().trim()) && !track.percussion ) {
				effect.setDeadNote(true);
			}
			if( "yes".equals(notehead.getAttribute("parentheses")) ) {
				effect.setGhostNote(true);
			}
		}
	}

	private int allocateSlot(TrackContext track, TGBeat beat, int voiceIndex, int pitch, boolean tied) {
		boolean[] used = new boolean[TGTrack.MAX_STRINGS + 1];
		for (int v = 0; v < beat.countVoices(); v++) {
			for (TGNote note : beat.getVoice(v).getNotes()) {
				used[note.getString()] = true;
			}
		}
		Map<Integer, Integer> previous = track.lastSlots.get(voiceIndex);
		if( tied ) {
			Integer slot = previous.get(pitch);
			if( slot != null && !used[slot] ) {
				return slot;
			}
		}
		// keep slots of the previous chord free, so notes tied later in this chord can reuse them
		Set<Integer> reserved = new HashSet<Integer>(previous.values());
		for (int pass = 0; pass < 2; pass++) {
			for (int slot = 1; slot <= TGTrack.MAX_STRINGS; slot++) {
				if(!used[slot] && (pass == 1 || !reserved.contains(slot)) ) {
					return slot;
				}
			}
		}
		return -1;
	}

	private void parseHarmony(PartContext context, Element harmony, int index) {
		String name = chordName(harmony);
		if( name == null || context.tracks.isEmpty() ) {
			return;
		}
		TrackContext track = context.byStaff.get(intText(harmony, 1, "staff"));
		if( track == null ) {
			track = context.tracks.get(0);
		}
		long position = context.cursor + context.toPrecise(intText(harmony, 0, "offset"));
		this.pendingChords.add(new PendingChord(track.track, index, position, name));
	}

	// ---------------------------------------------------------------- finishing

	private void alignPickupMeasure(Element firstPart, List<PartContext> contexts) {
		Element firstMeasure = measureAt(firstPart, 0);
		if( firstMeasure == null || !"yes".equals(firstMeasure.getAttribute("implicit")) ) {
			return;
		}
		TGMeasureHeader header = this.song.getMeasureHeader(0);
		long contentEnd = 0;
		for (PartContext context : contexts) {
			contentEnd = Math.max(contentEnd, context.firstMeasureEnd);
		}
		long shift = (header.getPreciseLength() - contentEnd);
		if( contentEnd <= 0 || shift <= 0 ) {
			return;
		}
		for (PartContext context : contexts) {
			for (TrackContext track : context.tracks) {
				for (TGBeat beat : track.track.getMeasure(0).getBeats()) {
					beat.setPreciseStart(beat.getPreciseStart() + shift);
				}
			}
		}
		for (PendingChord chord : this.pendingChords) {
			if( chord.measureIndex == 0 ) {
				chord.position += shift;
			}
		}
	}

	private void finishTrack(TrackContext context) {
		TGTrack track = context.track;
		if( track.hasTablature() ) {
			int delta = context.frameDelta(-track.getOffset());
			for (int s = 1; s <= context.fileTuning.length; s++) {
				track.getStrings().add(TGSongManager.newString(this.factory, s, context.fileTuning[s - 1] + delta));
			}
			for (PendingNote pending : context.pending) {
				TGString string = findFreeString(track, pending.voice.getBeat(), pending.pitch);
				if( string != null ) {
					pending.note.setString(string.getNumber());
					pending.note.setValue(pending.pitch - string.getValue());
					pending.voice.addNote(pending.note);
				}
			}
		} else {
			for (int s = 1; s <= context.maxSlot; s++) {
				track.getStrings().add(TGSongManager.newString(this.factory, s, 0));
			}
			track.setMaxFret(NOTATION_MAX_FRET);
		}
	}

	private static TGString findFreeString(TGTrack track, TGBeat beat, int pitch) {
		for (TGString string : track.getStrings()) {
			int fret = (pitch - string.getValue());
			if( fret >= 0 && fret <= TAB_MAX_FRET && !isStringUsed(beat, string.getNumber()) ) {
				return string;
			}
		}
		return null;
	}

	private void attachChords() {
		for (PendingChord pending : this.pendingChords) {
			TGMeasure measure = pending.track.getMeasure(pending.measureIndex);
			long position = (measure.getPreciseStart() + pending.position);
			TGBeat target = null;
			for (TGBeat beat : measure.getBeats()) {
				target = beat;
				if( beat.getPreciseStart() >= position ) {
					break;
				}
			}
			if( target != null && !target.isChordBeat() ) {
				TGChord chord = this.factory.newChord(pending.track.stringCount());
				chord.setName(pending.name);
				target.setChord(chord);
			}
		}
	}

	// ---------------------------------------------------------------- value helpers

	private static Element measureAt(Element part, int index) {
		List<Element> measures = children(part, "measure");
		return (index < measures.size() ? measures.get(index) : null);
	}

	private static StaffInfo staffInfo(Map<Integer, StaffInfo> staves, int number) {
		StaffInfo staff = staves.get(number);
		if( staff == null ) {
			staff = new StaffInfo();
			staff.number = number;
			staves.put(number, staff);
		}
		return staff;
	}

	static Integer pitch(String step, double alter, int octave) {
		if( step == null || step.length() != 1 ) {
			return null;
		}
		int index = Character.toUpperCase(step.charAt(0)) - 'A';
		if( index < 0 || index >= STEP_SEMITONES.length ) {
			return null;
		}
		int value = ((octave + 1) * 12) + STEP_SEMITONES[index] + (int) Math.round(alter);
		return (value >= 0 && value <= 127 ? value : null);
	}

	static int typeValue(String type) {
		if( type == null ) {
			return -1;
		}
		switch (type) {
			case "maxima":
			case "long":
			case "breve":
			case "whole": return TGDuration.WHOLE;
			case "half": return TGDuration.HALF;
			case "quarter": return TGDuration.QUARTER;
			case "eighth": return TGDuration.EIGHTH;
			case "16th": return TGDuration.SIXTEENTH;
			case "32nd": return TGDuration.THIRTY_SECOND;
			case "64th":
			case "128th":
			case "256th":
			case "512th":
			case "1024th": return TGDuration.SIXTY_FOURTH;
			default: return -1;
		}
	}

	static int keySignature(int fifths) {
		if( fifths >= 0 ) {
			return Math.min(fifths, 7);
		}
		return (7 + Math.min(-fifths, 7));
	}

	private static int clef(String sign, int line) {
		if( "F".equalsIgnoreCase(sign) ) {
			return TGMeasure.CLEF_BASS;
		}
		if( "C".equalsIgnoreCase(sign) ) {
			return (line == 4 ? TGMeasure.CLEF_TENOR : TGMeasure.CLEF_ALTO);
		}
		return TGMeasure.CLEF_TREBLE;
	}

	private static Integer parseDynamics(Element direction) {
		Element dynamics = child(direction, "direction-type", "dynamics");
		for (Element level : children(dynamics)) {
			switch (level.getNodeName()) {
				case "ppp": return TGVelocities.PIANO_PIANISSIMO;
				case "pp": return TGVelocities.PIANISSIMO;
				case "p": return TGVelocities.PIANO;
				case "mp": return TGVelocities.MEZZO_PIANO;
				case "mf": return TGVelocities.MEZZO_FORTE;
				case "f": return TGVelocities.FORTE;
				case "ff": return TGVelocities.FORTISSIMO;
				case "fff": return TGVelocities.FORTE_FORTISSIMO;
				default: break;
			}
		}
		return null;
	}

	private static boolean isTieStop(Element note) {
		for (Element tie : children(note, "tie")) {
			if( "stop".equals(tie.getAttribute("type")) ) {
				return true;
			}
		}
		for (Element notations : children(note, "notations")) {
			for (Element tied : children(notations, "tied")) {
				if( "stop".equals(tied.getAttribute("type")) ) {
					return true;
				}
			}
		}
		return false;
	}

	private static String notation(Element note, String group, String name) {
		for (Element notations : children(note, "notations")) {
			String value = text(notations, group, name);
			if( value != null ) {
				return value;
			}
		}
		return null;
	}

	private static boolean isStringUsed(TGBeat beat, int string) {
		for (int v = 0; v < beat.countVoices(); v++) {
			for (TGNote note : beat.getVoice(v).getNotes()) {
				if( note.getString() == string ) {
					return true;
				}
			}
		}
		return false;
	}

	static String chordName(Element harmony) {
		Element root = child(harmony, "root");
		if( root == null ) {
			Element kind = child(harmony, "kind");
			return (kind != null && "none".equals(kind.getTextContent().trim()) ? "N.C." : null);
		}
		String step = text(root, "root-step");
		if( step == null || step.isEmpty() ) {
			return null;
		}
		StringBuilder name = new StringBuilder(step);
		name.append(alterSymbol(MusicXMLDom.intText(root, 0, "root-alter")));

		Element kind = child(harmony, "kind");
		if( kind != null ) {
			if( kind.hasAttribute("text") ) {
				name.append(kind.getAttribute("text"));
			} else {
				name.append(kindSuffix(kind.getTextContent().trim()));
			}
		}
		Element bass = child(harmony, "bass");
		if( bass != null && notEmpty(text(bass, "bass-step")) ) {
			name.append('/').append(text(bass, "bass-step")).append(alterSymbol(intText(bass, 0, "bass-alter")));
		}
		return name.toString();
	}

	private static String alterSymbol(int alter) {
		StringBuilder symbol = new StringBuilder();
		for (int i = 0; i < Math.abs(alter); i++) {
			symbol.append(alter > 0 ? '#' : 'b');
		}
		return symbol.toString();
	}

	private static final Map<String, String> KIND_SUFFIXES = new LinkedHashMap<String, String>();
	static {
		KIND_SUFFIXES.put("major", "");
		KIND_SUFFIXES.put("minor", "m");
		KIND_SUFFIXES.put("augmented", "aug");
		KIND_SUFFIXES.put("diminished", "dim");
		KIND_SUFFIXES.put("dominant", "7");
		KIND_SUFFIXES.put("major-seventh", "maj7");
		KIND_SUFFIXES.put("minor-seventh", "m7");
		KIND_SUFFIXES.put("diminished-seventh", "dim7");
		KIND_SUFFIXES.put("augmented-seventh", "aug7");
		KIND_SUFFIXES.put("half-diminished", "m7b5");
		KIND_SUFFIXES.put("major-minor", "m(maj7)");
		KIND_SUFFIXES.put("major-sixth", "6");
		KIND_SUFFIXES.put("minor-sixth", "m6");
		KIND_SUFFIXES.put("dominant-ninth", "9");
		KIND_SUFFIXES.put("major-ninth", "maj9");
		KIND_SUFFIXES.put("minor-ninth", "m9");
		KIND_SUFFIXES.put("dominant-11th", "11");
		KIND_SUFFIXES.put("major-11th", "maj11");
		KIND_SUFFIXES.put("minor-11th", "m11");
		KIND_SUFFIXES.put("dominant-13th", "13");
		KIND_SUFFIXES.put("major-13th", "maj13");
		KIND_SUFFIXES.put("minor-13th", "m13");
		KIND_SUFFIXES.put("suspended-second", "sus2");
		KIND_SUFFIXES.put("suspended-fourth", "sus4");
		KIND_SUFFIXES.put("power", "5");
	}

	private static String kindSuffix(String kind) {
		String suffix = KIND_SUFFIXES.get(kind);
		return (suffix != null ? suffix : "");
	}

	private static boolean isPowerOfTwo(int value) {
		return (value > 0 && (value & (value - 1)) == 0);
	}

	private static boolean notEmpty(String value) {
		return (value != null && !value.isEmpty());
	}

	// ---------------------------------------------------------------- state holders

	private static class PartInfo {
		String name;
		int program = -1;
		int midiChannel = -1;
		final Map<String, Integer> unpitched = new HashMap<String, Integer>();
	}

	private static class StaffInfo {
		int number;
		boolean tablature;
		int lines = -1;
		final Map<Integer, Integer> tuning = new HashMap<Integer, Integer>();
	}

	private static class PartContext {
		final PartInfo info;
		Element part;
		final List<TrackContext> tracks = new ArrayList<TrackContext>();
		final Map<Integer, TrackContext> byStaff = new HashMap<Integer, TrackContext>();
		boolean percussion;
		int transpose;
		int divisions = 1;
		int velocity = TGVelocities.DEFAULT;
		long cursor;
		long lastStart;
		long measureEnd;
		long firstMeasureEnd;

		PartContext(PartInfo info) {
			this.info = info;
		}

		long toPrecise(long divisionsValue) {
			return Math.round((double) divisionsValue * TGDuration.WHOLE_PRECISE_DURATION / (4.0 * this.divisions));
		}
	}

	private static class TrackContext {
		final TGTrack track;
		final StaffInfo staff;
		final boolean percussion;
		final Map<String, Integer> voices = new HashMap<String, Integer>();
		final List<Map<Integer, Integer>> lastSlots = new ArrayList<Map<Integer, Integer>>();
		final Map<Integer, Integer> frameVotes = new HashMap<Integer, Integer>();
		final List<PendingNote> pending = new ArrayList<PendingNote>();
		int[] fileTuning = new int[0];
		int clef = TGMeasure.CLEF_TREBLE;
		int fifths;
		int maxSlot = 1;

		TrackContext(TGTrack track, StaffInfo staff, boolean percussion) {
			this.track = track;
			this.staff = staff;
			this.percussion = percussion;
			for (int v = 0; v < TGBeat.MAX_VOICES; v++) {
				this.lastSlots.add(new HashMap<Integer, Integer>());
			}
		}

		int voiceIndex(String voice) {
			String key = (voice != null ? voice : "1");
			Integer index = this.voices.get(key);
			if( index == null ) {
				if( this.voices.size() >= TGBeat.MAX_VOICES ) {
					return -1;
				}
				index = this.voices.size();
				this.voices.put(key, index);
			}
			return index;
		}

		void vote(int delta) {
			Integer count = this.frameVotes.get(delta);
			this.frameVotes.put(delta, (count != null ? count + 1 : 1));
		}

		// Offset between the file's tuning pitches and the written pitches of the notes
		// (files disagree on whether staff-tuning is given as written or sounding pitch).
		int frameDelta(int defaultDelta) {
			int best = defaultDelta;
			int bestCount = 0;
			for (Map.Entry<Integer, Integer> entry : this.frameVotes.entrySet()) {
				if( entry.getValue() > bestCount ) {
					best = entry.getKey();
					bestCount = entry.getValue();
				}
			}
			return best;
		}
	}

	private static class PendingNote {
		final TGNote note;
		final TGVoice voice;
		final int pitch;

		PendingNote(TGNote note, TGVoice voice, int pitch) {
			this.note = note;
			this.voice = voice;
			this.pitch = pitch;
		}
	}

	private static class PendingChord {
		final TGTrack track;
		final int measureIndex;
		long position;
		final String name;

		PendingChord(TGTrack track, int measureIndex, long position, String name) {
			this.track = track;
			this.measureIndex = measureIndex;
			this.position = position;
			this.name = name;
		}
	}
}
