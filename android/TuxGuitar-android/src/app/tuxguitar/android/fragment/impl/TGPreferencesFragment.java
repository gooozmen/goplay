package app.tuxguitar.android.fragment.impl;

import android.content.SharedPreferences;
import android.os.Bundle;

import app.tuxguitar.android.R;
import app.tuxguitar.android.action.impl.layout.TGSetChordDiagramEnabledAction;
import app.tuxguitar.android.action.impl.layout.TGSetChordNameEnabledAction;
import app.tuxguitar.android.action.impl.layout.TGSetMultitrackEnabledAction;
import app.tuxguitar.android.action.impl.layout.TGSetScoreEnabledAction;
import app.tuxguitar.android.action.impl.storage.TGStorageLoadSettingsAction;
import app.tuxguitar.android.action.impl.transport.TGTransportLoadSettingsAction;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.properties.TGSharedPreferencesUtil;
import app.tuxguitar.android.storage.TGStorageProperties;
import app.tuxguitar.android.transport.TGTransportProperties;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.graphics.control.TGLayout;
import app.tuxguitar.player.base.MidiOutputPort;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.util.TGContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class TGPreferencesFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

	public static final String MODULE = "tuxguitar";
	public static final String RESOURCE = "settings";

	private static final String PREFERENCE_VIEW_SCORE = "view.show.score";
	private static final String PREFERENCE_VIEW_MULTITRACK = "view.show.multitrack";
	private static final String PREFERENCE_VIEW_CHORD_NAME = "view.show.chord.name";
	private static final String PREFERENCE_VIEW_CHORD_DIAGRAM = "view.show.chord.diagram";

	private Map<String, String> updateActionsMap;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onDestroy() {
		this.getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		this.bindViewPreferences();
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		this.getPreferenceManager().setSharedPreferencesName(TGSharedPreferencesUtil.getSharedPreferencesName(this.getActivity(), MODULE, RESOURCE));
		this.addPreferencesFromResource(R.xml.preferences_main);
		this.getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		this.createUpdateActionsMap();
		this.createSafPreferences();
		this.createOutputPortPreferences();
		this.createViewPreferences();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if( this.updateActionsMap != null && this.updateActionsMap.containsKey(key) ) {
			TGActionProcessor tgActionProcessor = new TGActionProcessor(this.findContext(), this.updateActionsMap.get(key));
			tgActionProcessor.process();
		}
	}

	public void createUpdateActionsMap() {
		this.updateActionsMap = new HashMap<String, String>();
		this.updateActionsMap.put(TGTransportProperties.PROPERTY_MIDI_OUTPUT_PORT, TGTransportLoadSettingsAction.NAME);
		this.updateActionsMap.put(TGStorageProperties.PROPERTY_COLLECTION_BROWSER, TGStorageLoadSettingsAction.NAME);
	}

	public void createSafPreferences() {
		CheckBoxPreference checkBoxPreference = (CheckBoxPreference) this.findPreference(TGStorageProperties.PROPERTY_COLLECTION_BROWSER);
		checkBoxPreference.setChecked(new TGStorageProperties(this.findContext()).isUseCollectionBrowser());
	}

	public void createViewPreferences() {
		this.bindViewToggle(PREFERENCE_VIEW_SCORE, TGLayout.DISPLAY_SCORE, TGSetScoreEnabledAction.NAME);
		this.bindViewToggle(PREFERENCE_VIEW_MULTITRACK, TGLayout.DISPLAY_MULTITRACK, TGSetMultitrackEnabledAction.NAME);
		this.bindViewToggle(PREFERENCE_VIEW_CHORD_NAME, TGLayout.DISPLAY_CHORD_NAME, TGSetChordNameEnabledAction.NAME);
		this.bindViewToggle(PREFERENCE_VIEW_CHORD_DIAGRAM, TGLayout.DISPLAY_CHORD_DIAGRAM, TGSetChordDiagramEnabledAction.NAME);
		this.bindViewPreferences();
	}

	private void bindViewToggle(String key, final int styleFlag, final String actionName) {
		final CheckBoxPreference preference = (CheckBoxPreference) this.findPreference(key);
		if( preference == null ) {
			return;
		}
		preference.setPersistent(false);
		preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference changed, Object value) {
				boolean wanted = Boolean.TRUE.equals(value);
				boolean current = isLayoutStyleEnabled(styleFlag);
				if( wanted != current ) {
					TGActionProcessor tgActionProcessor = new TGActionProcessor(findContext(), actionName);
					tgActionProcessor.process();
				}
				return true;
			}
		});
	}

	private void bindViewPreferences() {
		this.setViewChecked(PREFERENCE_VIEW_SCORE, TGLayout.DISPLAY_SCORE);
		this.setViewChecked(PREFERENCE_VIEW_MULTITRACK, TGLayout.DISPLAY_MULTITRACK);
		this.setViewChecked(PREFERENCE_VIEW_CHORD_NAME, TGLayout.DISPLAY_CHORD_NAME);
		this.setViewChecked(PREFERENCE_VIEW_CHORD_DIAGRAM, TGLayout.DISPLAY_CHORD_DIAGRAM);
	}

	private void setViewChecked(String key, int styleFlag) {
		CheckBoxPreference preference = (CheckBoxPreference) this.findPreference(key);
		if( preference != null ) {
			preference.setChecked(isLayoutStyleEnabled(styleFlag));
		}
	}

	private boolean isLayoutStyleEnabled(int styleFlag) {
		TGLayout layout = TGSongViewController.getInstance(this.findContext()).getLayout();
		return layout != null && (layout.getStyle() & styleFlag) != 0;
	}

	public void createOutputPortPreferences() {
		String currentValue = null;
		String currentLabel = null;
		final List<String> entryNames = new ArrayList<String>();
		final List<String> entryValues = new ArrayList<String>();

		MidiPlayer midiPlayer = MidiPlayer.getInstance(this.findContext());
		List<MidiOutputPort> outputPorts = midiPlayer.listOutputPorts();
		for(MidiOutputPort outputPort : outputPorts) {
			entryNames.add(outputPort.getName());
			entryValues.add(outputPort.getKey());
			if( midiPlayer.isOutputPortOpen(outputPort.getKey()) ) {
				currentValue = outputPort.getKey();
				currentLabel = outputPort.getName();
			}
		}

		final ListPreference listPreference = (ListPreference) this.findPreference(TGTransportProperties.PROPERTY_MIDI_OUTPUT_PORT);
		listPreference.setEntries(entryNames.toArray(new CharSequence[entryNames.size()]));
		listPreference.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));
		if( currentValue != null ) {
			listPreference.setValue(currentValue);
		}
		listPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object o) {
				int index = ( o != null ? entryValues.indexOf(o.toString()) : -1);
				String selectedLabel = ( index >= 0 ? entryNames.get(index) : null);

				updatePreferenceSummary(preference, selectedLabel, R.string.preferences_midi_output_port_summary, R.string.preferences_midi_output_port_summary_empty);

				return true;
			}
		});
		updatePreferenceSummary(listPreference, currentLabel, R.string.preferences_midi_output_port_summary, R.string.preferences_midi_output_port_summary_empty);
	}

	public void updatePreferenceSummary(Preference preference, String label, Integer summaryId, Integer emptySummaryId) {
		if( label != null && !label.isEmpty() ) {
			preference.setSummary(this.getActivity().getString(summaryId, label));
		} else if (emptySummaryId != null) {
			preference.setSummary(this.getActivity().getString(emptySummaryId));
		}
	}

	public TGContext findContext() {
		return ((TGActivity) getActivity()).findContext();
	}
}
