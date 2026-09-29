package app.tuxguitar.io.musicxml.reader;

public class MusicXMLFormatException extends Exception {

	private static final long serialVersionUID = 1L;

	public MusicXMLFormatException(String message) {
		super(message);
	}

	public MusicXMLFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
