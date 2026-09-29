package app.tuxguitar.io.musicxml.reader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

public final class MusicXMLDocumentLoader {

	public static final String ROOT_PARTWISE = "score-partwise";
	public static final String ROOT_TIMEWISE = "score-timewise";

	private static final String MXL_CONTAINER = "META-INF/container.xml";
	private static final String GP7_SCORE = "Content/score.gpif";
	private static final int SNIFF_LENGTH = 4096;

	private MusicXMLDocumentLoader() {
	}

	public static boolean isMusicXML(byte[] data) throws IOException {
		if( isZip(data) ) {
			Map<String, byte[]> entries = unzip(data);
			if( entries.containsKey(GP7_SCORE) ) {
				return false;
			}
			byte[] root = findRootFile(entries);
			return (root != null && hasMusicXMLRoot(root));
		}
		return hasMusicXMLRoot(data);
	}

	public static Document load(byte[] data) throws MusicXMLFormatException, IOException {
		byte[] xml = data;
		if( isZip(data) ) {
			xml = findRootFile(unzip(data));
			if( xml == null ) {
				throw new MusicXMLFormatException("No MusicXML score inside the archive");
			}
		}
		Document document = parse(xml);
		Element root = document.getDocumentElement();
		if( ROOT_TIMEWISE.equals(root.getNodeName()) ) {
			return MusicXMLTimewiseConverter.toPartwise(document);
		}
		if(!ROOT_PARTWISE.equals(root.getNodeName()) ) {
			throw new MusicXMLFormatException("Not a MusicXML score: <" + root.getNodeName() + ">");
		}
		return document;
	}

	private static Document parse(byte[] xml) throws MusicXMLFormatException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			factory.setValidating(false);
			factory.setIgnoringComments(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			// MusicXML files reference the public DTD by URL; never fetch it.
			builder.setEntityResolver((publicId, systemId) -> new InputSource(new ByteArrayInputStream(new byte[0])));
			return builder.parse(new ByteArrayInputStream(xml));
		} catch (Exception e) {
			throw new MusicXMLFormatException("Invalid XML: " + e.getMessage(), e);
		}
	}

	private static boolean isZip(byte[] data) {
		return (data.length > 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4);
	}

	private static Map<String, byte[]> unzip(byte[] data) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if(!entry.isDirectory()) {
					entries.put(entry.getName(), readAll(zip));
				}
			}
		}
		return entries;
	}

	private static byte[] findRootFile(Map<String, byte[]> entries) throws IOException {
		byte[] container = entries.get(MXL_CONTAINER);
		if( container != null ) {
			try {
				Document document = parse(container);
				List<Element> rootFiles = MusicXMLDom.descendants(document.getDocumentElement(), "rootfile");
				for (Element rootFile : rootFiles) {
					String mediaType = rootFile.getAttribute("media-type");
					if( mediaType.isEmpty() || mediaType.contains("musicxml") ) {
						byte[] root = entries.get(rootFile.getAttribute("full-path"));
						if( root != null ) {
							return root;
						}
					}
				}
			} catch (MusicXMLFormatException e) {
				// fall through to the name-based lookup
			}
		}
		for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
			String name = entry.getKey().toLowerCase();
			if(!name.startsWith("meta-inf/") && (name.endsWith(".musicxml") || name.endsWith(".xml")) && hasMusicXMLRoot(entry.getValue())) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static boolean hasMusicXMLRoot(byte[] data) {
		String head = sniff(data);
		return (head.contains("<" + ROOT_PARTWISE) || head.contains("<" + ROOT_TIMEWISE));
	}

	private static String sniff(byte[] data) {
		int length = Math.min(data.length, SNIFF_LENGTH);
		if( length >= 2 && ((data[0] == (byte) 0xFE && data[1] == (byte) 0xFF) || (data[0] == (byte) 0xFF && data[1] == (byte) 0xFE)) ) {
			return new String(data, 0, length, StandardCharsets.UTF_16);
		}
		return new String(data, 0, length, StandardCharsets.UTF_8);
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) > 0) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}
}
