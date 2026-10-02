package app.tuxguitar.android.menu.controller.impl.fragment;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.TextView;

import app.tuxguitar.android.R;
import app.tuxguitar.android.action.TGActionProcessorListener;
import app.tuxguitar.android.action.impl.gui.TGOpenDialogAction;
import app.tuxguitar.android.action.impl.gui.TGOpenFragmentAction;
import app.tuxguitar.android.action.impl.transport.TGTransportPlayAction;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.activity.TGActivityController;
import app.tuxguitar.android.fragment.TGFragmentController;
import app.tuxguitar.android.fragment.impl.TGPreferencesFragmentController;
import app.tuxguitar.android.menu.controller.TGMenuController;
import app.tuxguitar.android.menu.util.TGToggleStyledIconHandler;
import app.tuxguitar.android.menu.util.TGToggleStyledIconHelper;
import app.tuxguitar.android.view.dialog.tempo.TGTempoDialogController;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.song.models.TGTempo;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

public class TGMainMenu implements TGMenuController {

	private TGContext context;
	private TGToggleStyledIconHelper styledIconHelper;
	private TextView tempoDisplayItem;

	private TGMainMenu(TGContext context) {
		this.context = context;
		this.styledIconHelper = new TGToggleStyledIconHelper(context);
		this.fillStyledIconHandlers();
	}

	public void inflate(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_main, menu);
		this.initializeItems(menu);
		this.styledIconHelper.initialize(this.getActivity(), menu);
	}

	public void initializeItems(Menu menu) {
		menu.findItem(R.id.action_transport_play).setOnMenuItemClickListener(createActionProcessor(TGTransportPlayAction.NAME));
		menu.findItem(R.id.action_menu_settings).setOnMenuItemClickListener(createFragmentActionProcessor(new TGPreferencesFragmentController()));

		this.tempoDisplayItem =  (TextView) menu.findItem(R.id.action_tempo_display).getActionView().findViewById(R.id.tempo_display_item);
		this.tempoDisplayItem.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (MidiPlayer.getInstance(getContext()).isRunning()) return;
				TGActionProcessorListener tgActionProcessor = new TGActionProcessorListener(getContext(), TGOpenDialogAction.NAME);
				tgActionProcessor.setAttribute(TGOpenDialogAction.ATTRIBUTE_DIALOG_ACTIVITY, getActivity());
				tgActionProcessor.setAttribute(TGOpenDialogAction.ATTRIBUTE_DIALOG_CONTROLLER, new TGTempoDialogController());
				tgActionProcessor.process();
			}
		});
		this.updateTempoDisplay();
	}

	public void updateTempoDisplay() {
		if (this.tempoDisplayItem == null) return;

		TGTempo tempo;
		int tempoPercent = 100;

		MidiPlayer midiPlayer = MidiPlayer.getInstance(this.context);
		if ((midiPlayer.isRunning() && (midiPlayer.getCurrentTempo() != null))) {
			tempo = midiPlayer.getCurrentTempo();
			tempoPercent = midiPlayer.getMode().getCurrentPercent();
		} else {
			tempo = TGSongViewController.getInstance(getContext()).getCaret().getMeasure().getTempo();
		}
		String iconName = "duration_" + tempo.getBase();
		if(tempo.isDotted()) iconName += "dotted";
		int iconId = getActivity().getResources().getIdentifier(iconName, "drawable", getActivity().getPackageName());
		this.tempoDisplayItem.setCompoundDrawablesWithIntrinsicBounds(getActivity().getResources().getDrawable(iconId), null, null, null);
		int tempoValue = tempo.getRawValue() * tempoPercent / 100;
		this.tempoDisplayItem.setText("= " + tempoValue + " ");
	}

	public void fillStyledIconHandlers() {
		this.styledIconHelper.addHandler(this.createStyledIconTransportHandler());
	}

	public TGToggleStyledIconHandler createStyledIconTransportHandler() {
		return new TGToggleStyledIconHandler() {

			public Integer getMenuItemId() {
				return R.id.action_transport_play;
			}

			public Integer resolveStyle() {
				boolean running = MidiPlayer.getInstance(getContext()).isRunning();
				return (running ? R.style.TGImageButton_Stop : R.style.TGImageButton_Play);
			}
		};
	}

	public TGActionProcessorListener createActionProcessor(String actionId) {
		return new TGActionProcessorListener(getContext(), actionId);
	}

	public TGActionProcessorListener createFragmentActionProcessor(TGFragmentController<?> controller) {
		TGActionProcessorListener tgActionProcessor = new TGActionProcessorListener(getContext(), TGOpenFragmentAction.NAME);
		tgActionProcessor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_CONTROLLER, controller);
		tgActionProcessor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_ACTIVITY, getActivity());
		return tgActionProcessor;
	}

	public TGContext getContext() {
		return context;
	}

	public TGActivity getActivity() {
		return TGActivityController.getInstance(this.context).getActivity();
	}

	public static TGMainMenu getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGMainMenu.class.getName(), new TGSingletonFactory<TGMainMenu>() {
			public TGMainMenu createInstance(TGContext context) {
				return new TGMainMenu(context);
			}
		});
	}
}
