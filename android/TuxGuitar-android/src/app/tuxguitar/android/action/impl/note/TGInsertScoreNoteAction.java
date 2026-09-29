package app.tuxguitar.android.action.impl.note;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.android.action.TGActionBase;
import app.tuxguitar.android.view.tablature.TGCaret;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.document.TGDocumentContextAttributes;
import app.tuxguitar.graphics.control.TGMeasureImpl;
import app.tuxguitar.graphics.control.TGTrackImpl;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGString;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.TGVoice;
import app.tuxguitar.util.TGContext;

public class TGInsertScoreNoteAction extends TGActionBase {

	public static final String NAME = "action.note.insert-score-note";

	private static final int DEFAULT_PITCH = 72;

	public TGInsertScoreNoteAction(TGContext context) {
		super(context, NAME);
	}

	protected void processAction(TGActionContext context) {
		TGSongManager songManager = getSongManager(context);
		TGTrack track = context.getAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK);
		TGBeat beat = context.getAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT);
		TGVoice voice = context.getAttribute(TGDocumentContextAttributes.ATTRIBUTE_VOICE);
		if( track == null || track.hasTablature() || beat == null || voice == null ) {
			return;
		}

		TGCaret caret = TGSongViewController.getInstance(getContext()).getCaret();
		TGNote selected = caret.getSelectedNote();
		int pitch = (selected != null ? songManager.getMeasureManager().getRealNoteValue(selected) : nearestPitch(songManager, track, beat, voice));
		pitch = freePitch(songManager, beat, Math.max(0, Math.min(pitch, track.getMaxFret())), track.getMaxFret());
		if( pitch < 0 ) {
			return;
		}

		int stringNumber = freeString(songManager, track, beat);
		TGNote note = songManager.getFactory().newNote();
		note.setString(stringNumber);
		note.setValue(pitch);
		note.setVelocity(caret.getVelocity());
		songManager.getMeasureManager().addNote(beat, note, voice.getIndex());

		if( track instanceof TGTrackImpl && beat.getMeasure() instanceof TGMeasureImpl ) {
			caret.moveTo((TGTrackImpl) track, (TGMeasureImpl) beat.getMeasure(), beat, stringNumber);
			caret.setNoteFocus(true);
		}
		context.setAttribute(ATTRIBUTE_SUCCESS, Boolean.TRUE);
	}

	private int freePitch(TGSongManager songManager, TGBeat beat, int pitch, int maxPitch) {
		if( !hasPitch(songManager, beat, pitch) ) {
			return pitch;
		}
		for( int offset = 1; offset <= maxPitch; offset ++ ) {
			if( pitch + offset <= maxPitch && !hasPitch(songManager, beat, pitch + offset) ) {
				return pitch + offset;
			}
			if( pitch - offset >= 0 && !hasPitch(songManager, beat, pitch - offset) ) {
				return pitch - offset;
			}
		}
		return -1;
	}

	private boolean hasPitch(TGSongManager songManager, TGBeat beat, int pitch) {
		for( int voiceIndex = 0; voiceIndex < beat.countVoices(); voiceIndex ++ ) {
			for( TGNote note : beat.getVoice(voiceIndex).getNotes() ) {
				if( songManager.getMeasureManager().getRealNoteValue(note) == pitch ) {
					return true;
				}
			}
		}
		return false;
	}

	private int freeString(TGSongManager songManager, TGTrack track, TGBeat beat) {
		boolean[] used = new boolean[track.stringCount() + 1];
		for( int voiceIndex = 0; voiceIndex < beat.countVoices(); voiceIndex ++ ) {
			for( TGNote note : beat.getVoice(voiceIndex).getNotes() ) {
				if( note.getString() > 0 && note.getString() < used.length ) {
					used[note.getString()] = true;
				}
			}
		}
		for( int number = 1; number < used.length; number ++ ) {
			if( !used[number] ) {
				return number;
			}
		}

		List<TGString> strings = new ArrayList<TGString>(track.getStrings());
		TGString added = songManager.getFactory().newString();
		added.setNumber(strings.size() + 1);
		added.setValue(0);
		strings.add(added);
		track.setStrings(strings);
		return added.getNumber();
	}

	private int nearestPitch(TGSongManager songManager, TGTrack track, TGBeat beat, TGVoice voice) {
		int pitch = pitchOf(songManager, voice);
		if( pitch >= 0 ) {
			return pitch;
		}
		for( int voiceIndex = 0; voiceIndex < beat.countVoices(); voiceIndex ++ ) {
			pitch = pitchOf(songManager, beat.getVoice(voiceIndex));
			if( pitch >= 0 ) {
				return pitch;
			}
		}

		TGMeasure measure = beat.getMeasure();
		int index = measureIndex(track, measure);
		for( int measureIndex = index; measureIndex >= 0; measureIndex -- ) {
			TGMeasure current = track.getMeasure(measureIndex);
			List<TGBeat> beats = current.getBeats();
			for( int beatIndex = beats.size() - 1; beatIndex >= 0; beatIndex -- ) {
				TGBeat candidate = beats.get(beatIndex);
				if( candidate.getPreciseStart() >= beat.getPreciseStart() ) {
					continue;
				}
				for( int voiceIndex = 0; voiceIndex < candidate.countVoices(); voiceIndex ++ ) {
					pitch = pitchOf(songManager, candidate.getVoice(voiceIndex));
					if( pitch >= 0 ) {
						return pitch;
					}
				}
			}
		}
		return DEFAULT_PITCH;
	}

	private int pitchOf(TGSongManager songManager, TGVoice voice) {
		for( TGNote note : voice.getNotes() ) {
			return songManager.getMeasureManager().getRealNoteValue(note);
		}
		return -1;
	}

	private int measureIndex(TGTrack track, TGMeasure measure) {
		for( int index = 0; index < track.countMeasures(); index ++ ) {
			if( track.getMeasure(index) == measure ) {
				return index;
			}
		}
		return track.countMeasures() - 1;
	}
}
