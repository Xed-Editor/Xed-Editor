/**
 * Copyright (c) 2015-2018 Angelo ZERR.
 * Copyright (c) 2023 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * - Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation, see
 * https://github.com/eclipse/tm4e/blob/95c17ade86677f3e2fd32e76222f71adfce18371/org.eclipse.tm4e.core/src/main/java/org/eclipse/tm4e/core/internal/parser/PList.java
 * - Sebastian Thomschke (Vegard IT) - major rewrite to support more configuration variants
 */
package org.eclipse.tm4e.core.internal.parser;

import static org.eclipse.tm4e.core.internal.utils.NullSafetyHelper.castNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import io.github.rosemoe.sora.util.Logger;

public final class TMParserPList implements TMParser {

    private static final Logger LOGGER = Logger.instance(TMParserPList.class.getName());

	private static final String PLIST_ARRAY = "array";
	private static final String PLIST_DICT = "dict";

	public static final TMParserPList INSTANCE = new TMParserPList();

	private static final class ParentRef {
		final String sourceKind;
		final PropertySettable<?> parent;
		@Nullable
		Object nextPropertyToSet;

		ParentRef(final String sourceKind, final PropertySettable<?> parent) {
			this.sourceKind = sourceKind;
			this.parent = parent;
		}
	}

	@Override
	public <T extends PropertySettable<?>> T parse(final Reader source, final ObjectFactory<T> factory)
			throws Exception {
		final var spf = SAXParserFactory.newInstance();
		spf.setNamespaceAware(true);

		// make parser invulnerable to XXE attacks, see https://rules.sonarsource.com/java/RSPEC-2755
		spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
		spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

		final var saxParser = spf.newSAXParser();

        // make parser invulnerable to XXE attacks, see https://rules.sonarsource.com/java/RSPEC-2755
        // but not support in android
		/*saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");*/

		final XMLReader xmlReader = saxParser.getXMLReader();
		xmlReader.setEntityResolver((publicId, systemId) -> new InputSource(
				new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes())));

		final T root = factory.createRoot();

		xmlReader.setContentHandler(new DefaultHandler() {

			final List<ParentRef> parents = new ArrayList<>();
			final TMParserPropertyPath path = new TMParserPropertyPath();

			/** captures the text content of an XML node */
			final StringBuilder text = new StringBuilder();

			@Override
			public void characters(final char @Nullable [] chars, final int start, final int count) {
				text.append(chars, start, count);
			}

			@Override
			@NonNullByDefault({})
			public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
				text.setLength(0);
				switch (localName) {
					case PLIST_DICT: {
						if (parents.isEmpty()) {
							parents.add(new ParentRef(localName, root));
							return;
						}
						parents.add(new ParentRef(localName, factory.createChild(path, Map.class)));
						break;
					}

					case PLIST_ARRAY: {
						final var newParentRef = new ParentRef(localName, factory.createChild(path, List.class));
						parents.add(newParentRef);

						newParentRef.nextPropertyToSet = 0;
						path.add(newParentRef.nextPropertyToSet);
						break;
					}
				}
			}

			@Override
			@NonNullByDefault({})
			public void endElement(final String uri, final String localName, final String qName) {
				switch (localName) {
					case PLIST_ARRAY: {
						final var parentRef = parents.remove(parents.size() - 1);

						path.removeLast(); // removes the remaining array index from the path
						setCurrentProperty(parentRef.parent); // register the constructed object with it's parent
						break;
					}

					case PLIST_DICT: {
						final var parentRef = parents.remove(parents.size() - 1);

						if (!parents.isEmpty())
							setCurrentProperty(parentRef.parent); // register the constructed object with it's parent
						break;
					}

					case "key": {
						final var parentRef = parents.get(parents.size() - 1);

                        if (!PLIST_DICT.equals(parentRef.sourceKind)) {
                            LOGGER.e("<key> tag can only be used inside an open <dict> element");
                            break;
                        }

						final String key = text.toString();
						parentRef.nextPropertyToSet = key;
						path.add(key);
						break;
					}

					case "data", "string":
						setCurrentProperty(text.toString());
						break;

                    case "date": // e.g. <date>2007-10-25T12:36:35Z</date>
                        try {
                            setCurrentProperty(ZonedDateTime.parse(text.toString()));
                        } catch (final DateTimeParseException ex) {
                            LOGGER.e("Failed to parse date '" + text + "'. " + ex);
                        }
                        break;

                    case "integer":
                        try {
                            setCurrentProperty(Integer.parseInt(text.toString()));
                        } catch (final NumberFormatException ex) {
                            LOGGER.e("Failed to parse integer '" + text + "'. " + ex);
                        }
                        break;

                    case "real":
                        try {
                            setCurrentProperty(Float.parseFloat(text.toString()));
                        } catch (final NumberFormatException ex) {
                            LOGGER.e("Failed to parse real as float '" + text + "'. " + ex);
                        }
                        break;

					case "true":
						setCurrentProperty(Boolean.TRUE);
						break;

					case "false":
						setCurrentProperty(Boolean.FALSE);
						break;

					case "plist":
						// ignore
						break;

                    default:
                        LOGGER.e("Invalid tag name: " + localName);
                }
            }

			@SuppressWarnings("unchecked")
			private void setCurrentProperty(final Object value) {
				path.removeLast();
				final var obj = parents.get(parents.size() - 1);
				switch (obj.sourceKind) {
					case PLIST_ARRAY:
						final var idx = castNonNull((Integer) obj.nextPropertyToSet);
						((PropertySettable<Object>) obj.parent).setProperty(idx.toString(), value);
						obj.nextPropertyToSet = idx + 1;
						path.add(obj.nextPropertyToSet);
						break;
					case PLIST_DICT:
						((PropertySettable<Object>) obj.parent).setProperty(castNonNull(obj.nextPropertyToSet).toString(), value);
						break;
				}
			}
		});

		xmlReader.parse(new InputSource(source));

		return root;
	}
}
