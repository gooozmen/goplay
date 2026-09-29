package app.tuxguitar.android.view.keyboard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import app.tuxguitar.editor.action.note.TGSetNoteFretNumberAction;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

public class TGTabKeyboard extends FrameLayout {

	private static final int[] NOTATION_HIDDEN_KEY_IDS = new int[] {
		R.id.tab_kb_button_insert,
		R.id.tab_kb_button_delete,
		R.id.tab_kb_button_increment_duration,
		R.id.tab_kb_button_set_duration,
		R.id.tab_kb_button_decrement_duration,
		R.id.tab_kb_button_number_0,
		R.id.tab_kb_button_number_1,
		R.id.tab_kb_button_number_2,
		R.id.tab_kb_button_number_3,
		R.id.tab_kb_button_number_4,
		R.id.tab_kb_button_number_5,
		R.id.tab_kb_button_number_6,
		R.id.tab_kb_button_number_7,
		R.id.tab_kb_button_number_8,
		R.id.tab_kb_button_number_9
	};

	private static final int[] NOTATION_INVISIBLE_KEY_IDS = new int[] {
		R.id.tab_kb_button_left,
		R.id.tab_kb_button_select,
		R.id.tab_kb_button_right
	};

	private boolean notationMode;
	private TGActionProcessorListener moveUp;
	private TGActionProcessorListener moveDown;
	private TGActionProcessorListener semitoneUp;
	private TGActionProcessorListener semitoneDown;

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
		this.semitoneUp = new TGActionProcessorListener(context, TGIncrementNoteSemitoneAction.NAME);
		this.semitoneDown = new TGActionProcessorListener(context, TGDecrementNoteSemitoneAction.NAME);

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
		boolean noteEditing = (notation && caret.isNoteEditing());
		boolean noteFocus = (notation && caret.isNoteFocus());

		if( notation != this.notationMode ) {
			this.notationMode = notation;
			for( int id : NOTATION_HIDDEN_KEY_IDS ) {
				findViewById(id).setVisibility(notation ? GONE : VISIBLE);
			}
			for( int id : NOTATION_INVISIBLE_KEY_IDS ) {
				findViewById(id).setVisibility(notation ? INVISIBLE : VISIBLE);
			}
			findViewById(R.id.tab_kb_button_add_note).setVisibility(notation ? VISIBLE : GONE);
			findViewById(R.id.tab_kb_button_up).setOnClickListener(notation ? this.semitoneUp : this.moveUp);
			findViewById(R.id.tab_kb_button_down).setOnClickListener(notation ? this.semitoneDown : this.moveDown);
			if( !notation ) {
				findViewById(R.id.tab_kb_button_up).setEnabled(true);
				findViewById(R.id.tab_kb_button_down).setEnabled(true);
			}
		}

		if( notation ) {
			findViewById(R.id.tab_kb_button_up).setEnabled(noteFocus);
			findViewById(R.id.tab_kb_button_down).setEnabled(noteFocus);
			if( noteEditing ) {
				this.applyShown();
			} else {
				this.applyHidden();
			}
		}
	}

	private void applyShown() {
		this.clearAnimation();
		this.setTranslationY(0f);
		this.setVisibility(VISIBLE);
	}

	private void applyHidden() {
		this.clearAnimation();
		this.setVisibility(GONE);
	}

	public void toggleVisibility() {
		this.clearAnimation();
		if( this.getVisibility() == VISIBLE ) {
			this.animate().setDuration(300).translationY(this.getHeight()).setListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator animation) {
					super.onAnimationEnd(animation);
					TGTabKeyboard.this.clearAnimation();
					TGTabKeyboard.this.setVisibility(GONE);
				}
			});
		} else {
			this.setVisibility(VISIBLE);
			this.animate().setDuration(300).translationY(0f).setListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator animation) {
					super.onAnimationEnd(animation);
					TGTabKeyboard.this.clearAnimation();
				}
			});
		}
	}

	private TGActivity findActivity() {
		return (TGActivity) getContext();
	}

	private TGContext findContext() {
		return TGApplicationUtil.findContext(this);
	}
}
