package app.tuxguitar.io.musicxml.reader;

import java.util.LinkedHashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

final class MusicXMLTimewiseConverter {

	private MusicXMLTimewiseConverter() {
	}

	static Document toPartwise(Document timewise) {
		Element source = timewise.getDocumentElement();
		Element target = timewise.createElement(MusicXMLDocumentLoader.ROOT_PARTWISE);
		Map<String, Element> parts = new LinkedHashMap<String, Element>();

		for (Element child : MusicXMLDom.children(source)) {
			if(!child.getNodeName().equals("measure")) {
				target.appendChild(child.cloneNode(true));
				continue;
			}
			for (Element part : MusicXMLDom.children(child, "part")) {
				String id = part.getAttribute("id");
				Element partwisePart = parts.get(id);
				if( partwisePart == null ) {
					partwisePart = timewise.createElement("part");
					partwisePart.setAttribute("id", id);
					parts.put(id, partwisePart);
				}
				Element measure = (Element) child.cloneNode(false);
				for (Node node = part.getFirstChild(); node != null; node = node.getNextSibling()) {
					measure.appendChild(node.cloneNode(true));
				}
				partwisePart.appendChild(measure);
			}
		}
		for (Element part : parts.values()) {
			target.appendChild(part);
		}
		timewise.replaceChild(target, source);
		return timewise;
	}
}
