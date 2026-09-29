package app.tuxguitar.io.musicxml.reader;

import app.tuxguitar.io.base.TGFileFormatDetector;
import app.tuxguitar.io.base.TGSongReader;
import app.tuxguitar.io.plugin.TGSongReaderPlugin;
import app.tuxguitar.util.TGContext;

public class MusicXMLReaderPlugin extends TGSongReaderPlugin {

	public static final String MODULE_ID = "tuxguitar-musicxml-reader";

	public MusicXMLReaderPlugin() {
		super(true);
	}

	protected TGSongReader createInputStream(TGContext context) {
		return new MusicXMLSongReader();
	}

	protected TGFileFormatDetector createFileFormatDetector(TGContext context) {
		return new MusicXMLFileFormatDetector();
	}

	public String getModuleId() {
		return MODULE_ID;
	}
}
