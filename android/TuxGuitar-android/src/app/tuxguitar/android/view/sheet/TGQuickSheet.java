package app.tuxguitar.android.view.sheet;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import app.tuxguitar.android.R;
import app.tuxguitar.android.action.TGActionProcessorListener;
import app.tuxguitar.android.action.impl.gui.TGOpenDialogAction;
import app.tuxguitar.android.action.impl.gui.TGOpenMenuAction;
import app.tuxguitar.android.action.impl.layout.TGToggleHighlightPlayedBeatAction;
import app.tuxguitar.android.action.impl.transport.TGTransportSetLoopEHeaderAction;
import app.tuxguitar.android.action.impl.transport.TGTransportSetLoopSHeaderAction;
import app.tuxguitar.android.action.impl.transport.TGTransportStopAction;
import app.tuxguitar.android.action.impl.view.TGToggleTabKeyboardAction;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.application.TGApplicationUtil;
import app.tuxguitar.android.menu.controller.TGMenuController;
import app.tuxguitar.android.menu.controller.impl.contextual.TGBeatMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGCompositionMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGDurationMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGEditMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGEffectMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGMeasureMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGTrackMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGVelocityMenu;
import app.tuxguitar.android.view.dialog.transport.TGTransportModeDialogController;
import app.tuxguitar.android.view.tablature.TGCaret;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.editor.action.transport.TGTransportCountDownAction;
import app.tuxguitar.editor.action.transport.TGTransportMetronomeAction;
import app.tuxguitar.graphics.control.TGLayout;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerMode;
import app.tuxguitar.util.TGContext;

public class TGQuickSheet extends LinearLayout {

	private BottomSheetBehavior<View> behavior;
	private int lastPeekHeight;

	public TGQuickSheet(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void onFinishInflate() {
		this.attachView();
		this.addListeners();
		super.onFinishInflate();
	}

	public void attachView() {
		TGQuickSheetController.getInstance(this.findContext()).setView(this);
	}

	public void addListeners() {
		TGContext context = this.findContext();
		TGActivity activity = this.findActivity();

		findViewById(R.id.quick_sheet_metronome).setOnClickListener(new TGActionProcessorListener(context, TGTransportMetronomeAction.NAME));
		findViewById(R.id.quick_sheet_keyboard).setOnClickListener(new TGActionProcessorListener(context, TGToggleTabKeyboardAction.NAME));
		findViewById(R.id.quick_sheet_count_in).setOnClickListener(new TGActionProcessorListener(context, TGTransportCountDownAction.NAME));

		findViewById(R.id.quick_sheet_stop).setOnClickListener(new TGActionProcessorListener(context, TGTransportStopAction.NAME));
		findViewById(R.id.quick_sheet_play_mode).setOnClickListener(createDialogActionListener(new TGTransportModeDialogController()));
		findViewById(R.id.quick_sheet_loop_start).setOnClickListener(new TGActionProcessorListener(context, TGTransportSetLoopSHeaderAction.NAME));
		findViewById(R.id.quick_sheet_loop_end).setOnClickListener(new TGActionProcessorListener(context, TGTransportSetLoopEHeaderAction.NAME));
		findViewById(R.id.quick_sheet_highlight).setOnClickListener(new TGActionProcessorListener(context, TGToggleHighlightPlayedBeatAction.NAME));

		findViewById(R.id.quick_sheet_edit).setOnClickListener(createContextMenuActionListener(new TGEditMenu(activity)));
		findViewById(R.id.quick_sheet_duration).setOnClickListener(createContextMenuActionListener(new TGDurationMenu(activity)));
		findViewById(R.id.quick_sheet_dynamic).setOnClickListener(createContextMenuActionListener(new TGVelocityMenu(activity)));
		findViewById(R.id.quick_sheet_effects).setOnClickListener(createContextMenuActionListener(new TGEffectMenu(activity)));
		findViewById(R.id.quick_sheet_beat).setOnClickListener(createContextMenuActionListener(new TGBeatMenu(activity)));
		findViewById(R.id.quick_sheet_measure).setOnClickListener(createContextMenuActionListener(new TGMeasureMenu(activity)));
		findViewById(R.id.quick_sheet_track).setOnClickListener(createContextMenuActionListener(new TGTrackMenu(activity)));
		findViewById(R.id.quick_sheet_composition).setOnClickListener(createContextMenuActionListener(new TGCompositionMenu(activity)));

		this.post(new Runnable() {
			public void run() {
				TGQuickSheet.this.updateItems();
				TGQuickSheet.this.updatePeekHeight();
			}
		});
	}

	public TGActionProcessorListener createContextMenuActionListener(TGMenuController controller) {
		TGActionProcessorListener tgActionProcessor = new TGActionProcessorListener(this.findContext(), TGOpenMenuAction.NAME);
		tgActionProcessor.setAttribute(TGOpenMenuAction.ATTRIBUTE_MENU_CONTROLLER, controller);
		tgActionProcessor.setAttribute(TGOpenMenuAction.ATTRIBUTE_MENU_ACTIVITY, this.findActivity());
		return tgActionProcessor;
	}

	public TGActionProcessorListener createDialogActionListener(app.tuxguitar.android.view.dialog.TGDialogController controller) {
		TGActionProcessorListener tgActionProcessor = new TGActionProcessorListener(this.findContext(), TGOpenDialogAction.NAME);
		tgActionProcessor.setAttribute(TGOpenDialogAction.ATTRIBUTE_DIALOG_CONTROLLER, controller);
		tgActionProcessor.setAttribute(TGOpenDialogAction.ATTRIBUTE_DIALOG_ACTIVITY, this.findActivity());
		return tgActionProcessor;
	}

	public void updateItems() {
		this.post(new Runnable() {
			public void run() {
				TGQuickSheet.this.applyItems();
			}
		});
	}

	private void applyItems() {
		TGContext context = this.findContext();
		if( context == null || findViewById(R.id.quick_sheet_metronome) == null ) {
			return;
		}

		MidiPlayer midiPlayer = MidiPlayer.getInstance(context);
		TGCaret caret = TGSongViewController.getInstance(context).getCaret();
		if( caret == null || caret.getMeasure() == null ) {
			return;
		}
		MidiPlayerMode playMode = midiPlayer.getMode();
		boolean running = midiPlayer.isRunning();
		int measureNumber = caret.getMeasure().getNumber();
		TGLayout layout = TGSongViewController.getInstance(context).getLayout();
		int style = layout.getStyle();
		boolean keyboardVisible = isKeyboardVisible();

		setPeekSelected(R.id.quick_sheet_metronome, midiPlayer.isMetronomeEnabled());
		setPeekSelected(R.id.quick_sheet_count_in, midiPlayer.getCountDown().isEnabled());
		setPeekSelected(R.id.quick_sheet_keyboard, keyboardVisible);

		findViewById(R.id.quick_sheet_stop).setEnabled(running);

		CheckBox loopStart = (CheckBox) findViewById(R.id.quick_sheet_loop_start);
		loopStart.setEnabled(playMode.isLoop());
		loopStart.setChecked(playMode.isLoop() && measureNumber == playMode.getLoopSHeader());

		CheckBox loopEnd = (CheckBox) findViewById(R.id.quick_sheet_loop_end);
		loopEnd.setEnabled(playMode.isLoop());
		loopEnd.setChecked(playMode.isLoop() && measureNumber == playMode.getLoopEHeader());

		CheckBox highlight = (CheckBox) findViewById(R.id.quick_sheet_highlight);
		highlight.setChecked((style & TGLayout.HIGHLIGHT_PLAYED_BEAT) != 0);

		this.updatePeekHeight();
	}

	private void setPeekSelected(int id, boolean selected) {
		ImageButton button = (ImageButton) findViewById(id);
		button.setSelected(selected);
		button.setAlpha(selected ? 1f : 0.55f);
	}

	private boolean isKeyboardVisible() {
		View keyboard = findViewById(R.id.tgTabKeyboard);
		return keyboard != null && keyboard.getVisibility() == VISIBLE;
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		this.updatePeekHeight();
	}

	private void updatePeekHeight() {
		View peek = findViewById(R.id.quick_sheet_peek);
		if( peek == null || peek.getBottom() <= 0 ) {
			return;
		}

		int peekHeight = peek.getBottom();
		View keyboard = findViewById(R.id.tgTabKeyboard);
		if( keyboard != null && keyboard.getVisibility() == VISIBLE && keyboard.getBottom() > peekHeight ) {
			peekHeight = keyboard.getBottom();
		}
		peekHeight += getPaddingBottom();

		BottomSheetBehavior<View> sheetBehavior = this.getBehavior();
		if( sheetBehavior != null && peekHeight != this.lastPeekHeight ) {
			this.lastPeekHeight = peekHeight;
			sheetBehavior.setPeekHeight(peekHeight);
			this.updateBodyPadding(peekHeight);
		}
	}

	private void updateBodyPadding(int peekHeight) {
		ViewParent parent = getParent();
		if( parent instanceof View ) {
			View body = ((View) parent).findViewById(R.id.main_body);
			if( body != null && body.getPaddingBottom() != peekHeight ) {
				body.setPadding(body.getPaddingLeft(), body.getPaddingTop(), body.getPaddingRight(), peekHeight);
			}
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		this.ensureBehavior();
		this.updateItems();
	}

	@SuppressWarnings("unchecked")
	private BottomSheetBehavior<View> getBehavior() {
		this.ensureBehavior();
		return this.behavior;
	}

	@SuppressWarnings("unchecked")
	private void ensureBehavior() {
		if( this.behavior != null ) {
			return;
		}
		ViewGroup.LayoutParams params = getLayoutParams();
		if( params instanceof CoordinatorLayout.LayoutParams ) {
			CoordinatorLayout.LayoutParams coordinatorParams = (CoordinatorLayout.LayoutParams) params;
			if( coordinatorParams.getBehavior() instanceof BottomSheetBehavior ) {
				this.behavior = (BottomSheetBehavior<View>) coordinatorParams.getBehavior();
			} else {
				this.behavior = new BottomSheetBehavior<View>();
				coordinatorParams.setBehavior(this.behavior);
			}
			this.behavior.setHideable(false);
			this.behavior.setFitToContents(true);
			this.behavior.setSkipCollapsed(false);
		}
	}

	private TGActivity findActivity() {
		return (TGActivity) getContext();
	}

	private TGContext findContext() {
		return TGApplicationUtil.findContext(this);
	}
}
