package app.tuxguitar.io.musicxml.reader;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class MusicXMLDom {

	private MusicXMLDom() {
	}

	static List<Element> children(Element parent) {
		List<Element> list = new ArrayList<Element>();
		if( parent != null ) {
			NodeList nodes = parent.getChildNodes();
			for (int i = 0; i < nodes.getLength(); i++) {
				Node node = nodes.item(i);
				if( node.getNodeType() == Node.ELEMENT_NODE ) {
					list.add((Element) node);
				}
			}
		}
		return list;
	}

	static List<Element> children(Element parent, String name) {
		List<Element> list = new ArrayList<Element>();
		for (Element child : children(parent)) {
			if( child.getNodeName().equals(name) ) {
				list.add(child);
			}
		}
		return list;
	}

	static Element child(Element parent, String name) {
		for (Element child : children(parent)) {
			if( child.getNodeName().equals(name) ) {
				return child;
			}
		}
		return null;
	}

	static Element child(Element parent, String... path) {
		Element current = parent;
		for (String name : path) {
			current = child(current, name);
			if( current == null ) {
				return null;
			}
		}
		return current;
	}

	static boolean has(Element parent, String name) {
		return (child(parent, name) != null);
	}

	static String text(Element parent, String... path) {
		Element element = child(parent, path);
		return (element != null ? element.getTextContent().trim() : null);
	}

	static int intText(Element parent, int defaultValue, String... path) {
		return toInt(text(parent, path), defaultValue);
	}

	static double doubleText(Element parent, double defaultValue, String... path) {
		String value = text(parent, path);
		if( value != null && !value.isEmpty() ) {
			try {
				return Double.parseDouble(value);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}
		return defaultValue;
	}

	static int intAttribute(Element element, String name, int defaultValue) {
		return toInt(element != null ? element.getAttribute(name) : null, defaultValue);
	}

	static int toInt(String value, int defaultValue) {
		if( value != null && !value.trim().isEmpty() ) {
			try {
				return (int) Math.round(Double.parseDouble(value.trim()));
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}
		return defaultValue;
	}

	static List<Element> descendants(Element parent, String name) {
		List<Element> list = new ArrayList<Element>();
		NodeList nodes = parent.getElementsByTagName(name);
		for (int i = 0; i < nodes.getLength(); i++) {
			list.add((Element) nodes.item(i));
		}
		return list;
	}
}
