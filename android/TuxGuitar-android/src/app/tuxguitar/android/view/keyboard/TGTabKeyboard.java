package app.tuxguitar.android.view.keyboard;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import app.tuxguitar.android.R;
import app.tuxguitar.android.action.TGActionProcessorListener;
import app.tuxguitar.android.action.impl.caret.TGGoDownAction;
import app.tuxguitar.android.action.impl.caret.TGGoLeftAction;
import app.tuxguitar.android.action.impl.caret.TGGoRightAction;
import app.tuxguitar.android.action.impl.caret.TGGoUpAction;
import app.tuxguitar.android.action.impl.gui.TGOpenMenuAction;
import app.tuxguitar.android.action.impl.note.TGInsertScoreNoteAction;
import app.tuxguitar.android.action.impl.view.TGShowSmartMenuAction;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.application.TGApplicationUtil;
import app.tuxguitar.android.menu.controller.TGMenuController;
import app.tuxguitar.android.menu.controller.impl.contextual.TGDurationMenu;
import app.tuxguitar.editor.action.duration.TGDecrementDurationAction;
import app.tuxguitar.editor.action.duration.TGIncrementDurationAction;
import app.tuxguitar.android.view.tablature.TGCaret;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.editor.action.note.TGDecrementNoteSemitoneAction;
import app.tuxguitar.editor.action.note.TGDeleteNoteOrRestAction;
import app.tuxguitar.editor.action.note.TGIncrementNoteSemitoneAction;
import app.tuxguitar.editor.action.note.TGInsertRestBeatAction;
import app.tuxguitar.editor.action.note.TGMoveBeatsLeftAction;
import app.tuxguitar.editor.action.note.TGMoveBeatsRightAction;
import app.tuxguitar.editor.action.note.TGSetNoteFretNumberAction;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.android.view.sheet.TGQuickSheetController;
import app.tuxguitar.util.TGContext;

public class TGTabKeyboard extends FrameLayout {

	private static final int[] NOTATION_HIDDEN_KEY_IDS = new int[] {
		R.id.tab_kb_fret_keys,
		R.id.tab_kb_duration_keys,
		R.id.tab_kb_caret_keys
	};

	private boolean notationMode;
	private TGActionProcessorListener moveUp;
	private TGActionProcessorListener moveDown;

	public TGTabKeyboard(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void onFinishInflate() {
		this.attachView();
		this.addListeners();
		super.onFinishInflate();
	}

	public void attachView() {
		TGTabKeyboardController.getInstance(TGApplicationUtil.findContext(this)).setView(this);
	}

	public void addListeners() {
		TGContext context = this.findContext();
		this.moveUp = new TGActionProcessorListener(context, TGGoUpAction.NAME);
		this.moveDown = new TGActionProcessorListener(context, TGGoDownAction.NAME);

		findViewById(R.id.tab_kb_button_number_0).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(0)));
		findViewById(R.id.tab_kb_button_number_1).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(1)));
		findViewById(R.id.tab_kb_button_number_2).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(2)));
		findViewById(R.id.tab_kb_button_number_3).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(3)));
		findViewById(R.id.tab_kb_button_number_4).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(4)));
		findViewById(R.id.tab_kb_button_number_5).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(5)));
		findViewById(R.id.tab_kb_button_number_6).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(6)));
		findViewById(R.id.tab_kb_button_number_7).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(7)));
		findViewById(R.id.tab_kb_button_number_8).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(8)));
		findViewById(R.id.tab_kb_button_number_9).setOnClickListener(new TGActionProcessorListener(context, TGSetNoteFretNumberAction.getActionName(9)));

		findViewById(R.id.tab_kb_button_add_note).setOnClickListener(new TGActionProcessorListener(context, TGInsertScoreNoteAction.NAME));
		findViewById(R.id.tab_kb_button_note_left).setOnClickListener(new TGActionProcessorListener(context, TGMoveBeatsLeftAction.NAME));
		findViewById(R.id.tab_kb_button_note_right).setOnClickListener(new TGActionProcessorListener(context, TGMoveBeatsRightAction.NAME));
		findViewById(R.id.tab_kb_button_note_up).setOnClickListener(new TGActionProcessorListener(context, TGIncrementNoteSemitoneAction.NAME));
		findViewById(R.id.tab_kb_button_note_down).setOnClickListener(new TGActionProcessorListener(context, TGDecrementNoteSemitoneAction.NAME));
		findViewById(R.id.tab_kb_button_insert).setOnClickListener(new TGActionProcessorListener(context, TGInsertRestBeatAction.NAME));
		findViewById(R.id.tab_kb_button_delete).setOnClickListener(new TGActionProcessorListener(context, TGDeleteNoteOrRestAction.NAME));

		findViewById(R.id.tab_kb_button_up).setOnClickListener(this.moveUp);
		findViewById(R.id.tab_kb_button_down).setOnClickListener(this.moveDown);
		findViewById(R.id.tab_kb_button_left).setOnClickListener(new TGActionProcessorListener(context, TGGoLeftAction.NAME));
		findViewById(R.id.tab_kb_button_right).setOnClickListener(new TGActionProcessorListener(context, TGGoRightAction.NAME));

		findViewById(R.id.tab_kb_button_increment_duration).setOnClickListener(new TGActionProcessorListener(context, TGIncrementDurationAction.NAME));
		findViewById(R.id.tab_kb_button_decrement_duration).setOnClickListener(new TGActionProcessorListener(context, TGDecrementDurationAction.NAME));
		findViewById(R.id.tab_kb_button_set_duration).setOnClickListener(createContextMenuActionListener(new TGDurationMenu(this.findActivity())));

		findViewById(R.id.tab_kb_button_select).setOnClickListener(new TGActionProcessorListener(context, TGShowSmartMenuAction.NAME));
	}

	public TGActionProcessorListener createContextMenuActionListener(TGMenuController controller) {
		TGActionProcessorListener tgActionProcessor = new TGActionProcessorListener(this.findContext(), TGOpenMenuAction.NAME);
		tgActionProcessor.setAttribute(TGOpenMenuAction.ATTRIBUTE_MENU_CONTROLLER, controller);
		tgActionProcessor.setAttribute(TGOpenMenuAction.ATTRIBUTE_MENU_ACTIVITY, this.findActivity());
		return tgActionProcessor;
	}

	public void updateMode() {
		this.post(new Runnable() {
			public void run() {
				TGTabKeyboard.this.applyMode();
			}
		});
	}

	public void show() {
		this.post(new Runnable() {
			public void run() {
				TGTabKeyboard.this.applyShown();
			}
		});
	}

	public void hide() {
		this.post(new Runnable() {
			public void run() {
				TGTabKeyboard.this.applyHidden();
			}
		});
	}

	private void applyMode() {
		TGSongViewController controller = TGSongViewController.getInstance(this.findContext());
		TGCaret caret = (controller != null ? controller.getCaret() : null);
		TGTrack track = (caret != null ? caret.getTrack() : null);
		boolean notation = (track != null && !track.hasTablature());
		if( notation != this.notationMode ) {
			this.notationMode = notation;
			for( int id : NOTATION_HIDDEN_KEY_IDS ) {
				findViewById(id).setVisibility(notation ? GONE : VISIBLE);
			}
			findViewById(R.id.tab_kb_note_keys).setVisibility(notation ? VISIBLE : GONE);
		}

		boolean canMoveNote = (notation && caret != null && caret.getSelectedNote() != null);
		findViewById(R.id.tab_kb_button_note_up).setEnabled(!notation || canMoveNote);
		findViewById(R.id.tab_kb_button_note_down).setEnabled(!notation || canMoveNote);
		findViewById(R.id.tab_kb_button_note_left).setEnabled(!notation || canMoveNote);
		findViewById(R.id.tab_kb_button_note_right).setEnabled(!notation || canMoveNote);
	}

	private void applyShown() {
		this.clearAnimation();
		this.setTranslationY(0f);
		this.setVisibility(VISIBLE);
		this.notifySheet();
	}

	private void applyHidden() {
		this.clearAnimation();
		this.setVisibility(GONE);
		this.notifySheet();
	}

	public void toggleVisibility() {
		if( this.getVisibility() == VISIBLE ) {
			this.applyHidden();
		} else {
			this.applyShown();
		}
	}

	private void notifySheet() {
		TGQuickSheetController.getInstance(this.findContext()).updateItems();
	}

	private TGActivity findActivity() {
		return (TGActivity) getContext();
	}

	private TGContext findContext() {
		return TGApplicationUtil.findContext(this);
	}
}
