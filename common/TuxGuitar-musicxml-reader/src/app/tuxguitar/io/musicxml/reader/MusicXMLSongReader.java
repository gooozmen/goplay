package app.tuxguitar.io.musicxml.reader;

import org.w3c.dom.Document;

import app.tuxguitar.io.base.TGFileFormat;
import app.tuxguitar.io.base.TGFileFormatException;
import app.tuxguitar.io.base.TGFileFormatUtils;
import app.tuxguitar.io.base.TGSongReader;
import app.tuxguitar.io.base.TGSongReaderHandle;

public class MusicXMLSongReader implements TGSongReader {

	public static final TGFileFormat FILE_FORMAT = new TGFileFormat("MusicXML", "application/vnd.recordare.musicxml+xml", new String[]{"musicxml", "mxl", "xml"});

	public TGFileFormat getFileFormat() {
		return FILE_FORMAT;
	}

	public void read(TGSongReaderHandle handle) throws TGFileFormatException {
		try {
			byte[] data = TGFileFormatUtils.getBytes(handle.getInputStream());
			Document document = MusicXMLDocumentLoader.load(data);
			handle.setSong(new MusicXMLSongParser(handle.getFactory()).parse(document));
		} catch (TGFileFormatException e) {
			throw e;
		} catch (Throwable throwable) {
			throw new TGFileFormatException(throwable);
		}
	}
}
