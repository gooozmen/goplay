package app.tuxguitar.io.musicxml.reader;

import java.io.InputStream;

import app.tuxguitar.io.base.TGFileFormat;
import app.tuxguitar.io.base.TGFileFormatDetector;
import app.tuxguitar.io.base.TGFileFormatUtils;

public class MusicXMLFileFormatDetector implements TGFileFormatDetector {

	public TGFileFormat getFileFormat(InputStream inputStream) {
		try {
			byte[] data = TGFileFormatUtils.getBytes(inputStream);
			if( MusicXMLDocumentLoader.isMusicXML(data) ) {
				return MusicXMLSongReader.FILE_FORMAT;
			}
		} catch (Throwable throwable) {
			return null;
		}
		return null;
	}
}
